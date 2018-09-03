package records.transformations.expression.type;

import annotation.identifier.qual.ExpressionIdentifier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.RuleNode;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.TableAndColumnRenames;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DataTypeVisitorEx;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataType.TagType;
import records.data.datatype.NumberInfo;
import records.data.datatype.TypeId;
import records.data.datatype.TypeManager;
import records.data.unit.Unit;
import records.error.InternalException;
import records.error.UnimplementedException;
import records.error.UserException;
import records.grammar.FormatLexer;
import records.grammar.FormatParser;
import records.grammar.FormatParser.ApplyTypeExpressionContext;
import records.grammar.FormatParser.ArrayTypeExpressionContext;
import records.grammar.FormatParser.InvalidOpsTypeExpressionContext;
import records.grammar.FormatParser.RoundTypeExpressionContext;
import records.grammar.FormatParser.TypeExpressionTerminalContext;
import records.grammar.FormatParserBaseVisitor;
import records.gui.expressioneditor.TypeEntry;
import records.jellytype.JellyType;
import records.jellytype.JellyType.JellyTypeVisitorEx;
import records.jellytype.JellyUnit;
import records.transformations.expression.BracketedStatus;
import records.transformations.expression.LoadableExpression;
import records.transformations.expression.Replaceable;
import records.transformations.expression.UnitExpression;
import styled.StyledShowable;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.FXPlatformRunnable;
import utility.StreamTreeBuilder;
import utility.Utility;

public abstract class TypeExpression implements LoadableExpression<TypeExpression, TypeSaver>, StyledShowable, Replaceable<TypeExpression>
{

    public static TypeExpression fromDataType(DataType dataType) throws InternalException
    {
        return dataType.apply(new DataTypeVisitorEx<TypeExpression, InternalException>()
        {
            @Override
            public TypeExpression number(NumberInfo numberInfo) throws InternalException, InternalException
            {
                return new NumberTypeExpression(UnitExpression.load(numberInfo.getUnit()));
            }

            @Override
            public TypeExpression text() throws InternalException, InternalException
            {
                return new TypePrimitiveLiteral(dataType);
            }

            @Override
            public TypeExpression date(DateTimeInfo dateTimeInfo) throws InternalException, InternalException
            {
                return new TypePrimitiveLiteral(dataType);
            }

            @Override
            public TypeExpression bool() throws InternalException, InternalException
            {
                return new TypePrimitiveLiteral(dataType);
            }

            @Override
            public TypeExpression tagged(TypeId typeName, ImmutableList<Either<Unit, DataType>> typeVars, ImmutableList<TagType<DataType>> tags) throws InternalException, InternalException
            {
                if (typeVars.isEmpty())
                    return new IdentTypeExpression(typeName.getRaw());
                ImmutableList.Builder<Either<UnitExpression, TypeExpression>> args = ImmutableList.builderWithExpectedSize(typeVars.size());
                for (Either<Unit, DataType> typeVar : typeVars)
                {
                    args.add(typeVar.<UnitExpression, TypeExpression>mapBothInt(u -> UnitExpression.load(u), t -> fromDataType(t)));
                }
                return new TypeApplyExpression(typeName.getRaw(), args.build());
            }

            @Override
            public TypeExpression tuple(ImmutableList<DataType> inner) throws InternalException, InternalException
            {
                ImmutableList.Builder<TypeExpression> members = ImmutableList.builder();
                for (DataType type : inner)
                {
                    members.add(fromDataType(type));
                }
                return new TupleTypeExpression(members.build());
            }

            @Override
            public TypeExpression array(@Nullable DataType inner) throws InternalException, InternalException
            {
                return new ListTypeExpression(inner == null ? new InvalidIdentTypeExpression("") : fromDataType(inner));
            }
        });
    }

    public static TypeExpression fromJellyType(JellyType original, TypeManager mgr) throws InternalException, UserException
    {
        return original.apply(new JellyTypeVisitorEx<TypeExpression, UserException>()
        {
            @Override
            public TypeExpression number(JellyUnit unit) throws InternalException, InternalException
            {
                return new NumberTypeExpression(UnitExpression.load(unit));
            }

            @Override
            public TypeExpression text() throws InternalException, UserException
            {
                return new TypePrimitiveLiteral(original.makeDataType(ImmutableMap.of(), mgr));
            }

            @Override
            public TypeExpression date(DateTimeInfo dateTimeInfo) throws InternalException, UserException
            {
                return new TypePrimitiveLiteral(original.makeDataType(ImmutableMap.of(), mgr));
            }

            @Override
            public TypeExpression bool() throws InternalException, UserException
            {
                return new TypePrimitiveLiteral(original.makeDataType(ImmutableMap.of(), mgr));
            }

            @Override
            public TypeExpression applyTagged(TypeId typeName, ImmutableList<Either<JellyUnit, JellyType>> typeVars) throws InternalException, UserException
            {
                if (typeVars.isEmpty())
                    return new IdentTypeExpression(typeName.getRaw());
                ImmutableList.Builder<Either<UnitExpression, TypeExpression>> args = ImmutableList.builderWithExpectedSize(typeVars.size());
                for (Either<JellyUnit, JellyType> typeVar : typeVars)
                {
                    args.add(typeVar.<UnitExpression, TypeExpression>mapBothEx(u -> UnitExpression.load(u), t -> fromJellyType(t, mgr)));
                }
                return new TypeApplyExpression(typeName.getRaw(), args.build());
            }

            @Override
            public TypeExpression tuple(ImmutableList<JellyType> inner) throws InternalException, UserException
            {
                ImmutableList.Builder<TypeExpression> members = ImmutableList.builder();
                for (JellyType type : inner)
                {
                    members.add(fromJellyType(type, mgr));
                }
                return new TupleTypeExpression(members.build());
            }

            @Override
            public TypeExpression array(JellyType inner) throws InternalException, UserException
            {
                return new ListTypeExpression(fromJellyType(inner, mgr));
            }

            @Override
            public TypeExpression function(JellyType argType, JellyType resultType) throws InternalException, InternalException
            {
                throw new UnimplementedException();
            }

            @Override
            public TypeExpression ident(String name) throws InternalException, InternalException
            {
                return InvalidIdentTypeExpression.identOrUnfinished(name);
            }
        });
    }

    public abstract String save(TableAndColumnRenames renames);

    public abstract @Nullable DataType toDataType(TypeManager typeManager);

    public abstract JellyType toJellyType(TypeManager typeManager) throws InternalException, UserException;

    public static TypeExpression parseTypeExpression(TypeManager typeManager, String src) throws UserException, InternalException
    {
        class WrappedUserException extends RuntimeException
        {
            private final UserException e;

            WrappedUserException(UserException e)
            {
                this.e = e;
            }
        }

        class WrappedInternalException extends RuntimeException
        {
            private final InternalException e;

            WrappedInternalException(InternalException e)
            {
                this.e = e;
            }
        }
        
        try
        {
            return Utility.parseAsOne(src, FormatLexer::new, FormatParser::new, p -> p.completeTypeExpression()).accept(new FormatParserBaseVisitor<TypeExpression>()
            {
                @Override
                public TypeExpression visitTypeExpressionTerminal(TypeExpressionTerminalContext ctx)
                {
                    if (ctx.INCOMPLETE() != null)
                    {
                        return new IdentTypeExpression(ctx.STRING().getText());
                    }
                    else
                    {
                        try
                        {
                            // Bit weird to reparse, but saves code duplication:
                            return TypeExpression.fromDataType(typeManager.loadTypeUse(ctx.getText()));
                        } catch (UserException e)
                        {
                            throw new WrappedUserException(e);
                        } catch (InternalException e)
                        {
                            throw new WrappedInternalException(e);
                        }
                        // TODO number is special
                    }
                    //return super.visitTypeExpressionTerminal(ctx);
                }

                @Override
                public TypeExpression visitArrayTypeExpression(ArrayTypeExpressionContext ctx)
                {
                    return new ListTypeExpression(visit(ctx.typeExpression()));
                }

                @Override
                public TypeExpression visitRoundTypeExpression(RoundTypeExpressionContext ctx)
                {
                    if (ctx.typeExpression().size() > 1)
                    {
                        return new TupleTypeExpression(Utility.mapListI(ctx.typeExpression(), t -> visitTypeExpression(t)));
                    }
                    // If size 1, ignore brackets, just process inner:
                    return super.visitTypeExpression(ctx.typeExpression(0));
                }

                @Override
                public TypeExpression visitInvalidOpsTypeExpression(InvalidOpsTypeExpressionContext ctx)
                {
                    return new InvalidOpTypeExpression(ImmutableList.of()/*Utility.mapListI(ctx.typeExpression(), t -> visitTypeExpression(t)),
                        Utility.mapListI(ctx.STRING(), s -> s.getText())
                    */);
                }

                @Override
                public TypeExpression visitApplyTypeExpression(ApplyTypeExpressionContext ctx)
                {
                    @SuppressWarnings("identifier")
                    @ExpressionIdentifier String typeName = ctx.ident().getText();

                    ImmutableList.Builder<Either<UnitExpression, TypeExpression>> args = ImmutableList.builderWithExpectedSize(ctx.roundTypeExpression().size());
                    for (RoundTypeExpressionContext roundTypeExpressionContext : ctx.roundTypeExpression())
                    {
                        args.add(Either.right(visitRoundTypeExpression(roundTypeExpressionContext)));
                    }
                    return new TypeApplyExpression(typeName, args.build());
                }

                public TypeExpression visitChildren(RuleNode node) {
                    @Nullable TypeExpression result = this.defaultResult();
                    int n = node.getChildCount();

                    for(int i = 0; i < n && this.shouldVisitNextChild(node, result); ++i) {
                        ParseTree c = node.getChild(i);
                        TypeExpression childResult = c.accept(this);
                        if (childResult == null)
                            break;
                        result = this.aggregateResult(result, childResult);
                    }
                    if (result == null)
                        throw new WrappedInternalException(new InternalException("No parseTypeExpression rules matched for " + node.getText()));
                    else
                        return result;
                }
            });
        }
        catch (WrappedUserException e)
        {
            throw e.e;
        }
        catch (WrappedInternalException e)
        {
            throw e.e;
        }
        
    }
    
    public abstract boolean isEmpty();

    // Force sub-expressions to implement equals and hashCode:
    @Override
    public abstract boolean equals(@Nullable Object o);
    @Override
    public abstract int hashCode();

    // Round brackets if needed
    @OnThread(Tag.FXPlatform)
    protected static void roundBracket(BracketedStatus bracketedStatus, StreamTreeBuilder<SingleLoader<TypeExpression, TypeSaver>> builder, FXPlatformRunnable buildContent)
    {
        if (bracketedStatus == BracketedStatus.DIRECT_ROUND_BRACKETED)
        {
            buildContent.run();
        }
        else
        {
            builder.add(TypeEntry.load("("));
            buildContent.run();
            builder.add(TypeEntry.load(")"));
        }
    }

    // Useful for debugging:
    @Override
    public String toString()
    {
        return save(TableAndColumnRenames.EMPTY);
    }
}
