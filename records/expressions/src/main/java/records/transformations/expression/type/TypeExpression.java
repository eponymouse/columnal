package records.transformations.expression.type;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.antlr.v4.runtime.tree.AbstractParseTreeVisitor;
import org.antlr.v4.runtime.tree.ErrorNode;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeVisitor;
import org.antlr.v4.runtime.tree.RuleNode;
import org.antlr.v4.runtime.tree.TerminalNode;
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
import records.grammar.FormatParser.ArrayTypeExpressionContext;
import records.grammar.FormatParser.InvalidOpsTypeExpressionContext;
import records.grammar.FormatParser.RoundTypeExpressionContext;
import records.grammar.FormatParser.TaggedTypeExpressionContext;
import records.grammar.FormatParser.TypeExpressionTerminalContext;
import records.grammar.FormatParserBaseVisitor;
import records.gui.expressioneditor.OperandNode;
import records.gui.expressioneditor.OperatorEntry;
import records.jellytype.JellyType;
import records.jellytype.JellyType.JellyTypeVisitorEx;
import records.jellytype.JellyUnit;
import records.transformations.expression.Expression;
import records.transformations.expression.LoadableExpression;
import records.transformations.expression.UnitExpression;
import styled.StyledShowable;
import utility.Either;
import utility.Pair;
import utility.UnitType;
import utility.Utility;

import java.util.Collections;
import java.util.List;

public abstract class TypeExpression implements LoadableExpression<TypeExpression, TypeParent>, StyledShowable
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
                TaggedTypeNameExpression taggedTypeNameExpression = new TaggedTypeNameExpression(typeName);
                if (typeVars.isEmpty())
                    return taggedTypeNameExpression;
                ImmutableList.Builder<Either<UnitExpression, TypeExpression>> args = ImmutableList.builderWithExpectedSize(typeVars.size() + 1);
                if (typeVars.isEmpty())
                    return taggedTypeNameExpression;
                args.add(Either.right(taggedTypeNameExpression));
                for (Either<Unit, DataType> typeVar : typeVars)
                {
                    args.add(typeVar.<UnitExpression, TypeExpression>mapBothInt(u -> UnitExpression.load(u), t -> fromDataType(t)));
                }
                return new TypeApplyExpression(args.build());
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
                return new ListTypeExpression(inner == null ? new UnfinishedTypeExpression("") : fromDataType(inner));
            }
        });
    }

    public static TypeExpression fromJellyType(JellyType original) throws InternalException
    {
        return original.apply(new JellyTypeVisitorEx<TypeExpression, InternalException>()
        {
            @Override
            public TypeExpression number(JellyUnit unit) throws InternalException, InternalException
            {
                throw new UnimplementedException();
                //return new NumberTypeExpression(UnitExpression.load(numberInfo.getUnit()));
            }

            @Override
            public TypeExpression text() throws InternalException, InternalException
            {
                return new TypePrimitiveLiteral(original.makeDataType(ImmutableMap.of()));
            }

            @Override
            public TypeExpression date(DateTimeInfo dateTimeInfo) throws InternalException, InternalException
            {
                return new TypePrimitiveLiteral(original.makeDataType(ImmutableMap.of()));
            }

            @Override
            public TypeExpression bool() throws InternalException, InternalException
            {
                return new TypePrimitiveLiteral(original.makeDataType(ImmutableMap.of()));
            }

            @Override
            public TypeExpression tagged(TypeId typeName, ImmutableList<Either<JellyUnit, JellyType>> typeVars) throws InternalException, InternalException
            {
                TaggedTypeNameExpression taggedTypeNameExpression = new TaggedTypeNameExpression(typeName);
                if (typeVars.isEmpty())
                    return taggedTypeNameExpression;
                ImmutableList.Builder<Either<UnitExpression, TypeExpression>> args = ImmutableList.builderWithExpectedSize(typeVars.size() + 1);
                if (typeVars.isEmpty())
                    return taggedTypeNameExpression;
                args.add(Either.right(taggedTypeNameExpression));
                for (Either<JellyUnit, JellyType> typeVar : typeVars)
                {
                    args.add(typeVar.<UnitExpression, TypeExpression>mapBothInt(u -> {throw new UnimplementedException();}, t -> fromJellyType(t)));
                }
                return new TypeApplyExpression(args.build());
            }

            @Override
            public TypeExpression tuple(ImmutableList<JellyType> inner) throws InternalException, InternalException
            {
                ImmutableList.Builder<TypeExpression> members = ImmutableList.builder();
                for (JellyType type : inner)
                {
                    members.add(fromJellyType(type));
                }
                return new TupleTypeExpression(members.build());
            }

            @Override
            public TypeExpression array(JellyType inner) throws InternalException, InternalException
            {
                return new ListTypeExpression(fromJellyType(inner));
            }

            @Override
            public TypeExpression function(JellyType argType, JellyType resultType) throws InternalException, InternalException
            {
                throw new UnimplementedException();
            }

            @Override
            public TypeExpression typeVariable(String name) throws InternalException, InternalException
            {
                throw new UnimplementedException();
            }
        });
    }

    // Remember to override this when appropriate (I think only in TypeApplyExpression)
    @Override
    public Pair<List<SingleLoader<TypeExpression, TypeParent, OperandNode<TypeExpression, TypeParent>>>, List<SingleLoader<TypeExpression, TypeParent, OperatorEntry<TypeExpression, TypeParent>>>> loadAsConsecutive(boolean implicitlyRoundBracketed)
    {
        return new Pair<>(Collections.singletonList(loadAsSingle()), Collections.emptyList());
    }

    public abstract String save(TableAndColumnRenames renames);

    public abstract @Nullable DataType toDataType(TypeManager typeManager);

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
                        return new UnfinishedTypeExpression(ctx.STRING().getText());
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
                    return new InvalidOpTypeExpression(Utility.mapListI(ctx.typeExpression(), t -> visitTypeExpression(t)),
                        Utility.mapListI(ctx.STRING(), s -> s.getText())
                    );
                }

                @Override
                public TypeExpression visitTaggedTypeExpression(TaggedTypeExpressionContext ctx)
                {
                    ImmutableList.Builder<Either<UnitExpression, TypeExpression>> args = ImmutableList.builder();
                    TaggedTypeNameExpression taggedTypeNameExpression = new TaggedTypeNameExpression(new TypeId(ctx.ident().getText()));
                    if (ctx.roundTypeExpression().isEmpty())
                        return taggedTypeNameExpression;
                    args.add(Either.right(taggedTypeNameExpression));
                    for (RoundTypeExpressionContext roundTypeExpressionContext : ctx.roundTypeExpression())
                    {
                        args.add(Either.right(visitRoundTypeExpression(roundTypeExpressionContext)));
                    }
                    return new TypeApplyExpression(args.build());
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
}
