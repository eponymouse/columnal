package records.transformations.expression.type;

import com.google.common.collect.ImmutableList;
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
import records.error.InternalException;
import records.error.UnimplementedException;
import records.error.UserException;
import records.grammar.FormatLexer;
import records.grammar.FormatParser;
import records.grammar.FormatParser.ArrayTypeExpressionContext;
import records.grammar.FormatParser.InvalidOpsTypeExpressionContext;
import records.grammar.FormatParser.RoundTypeExpressionContext;
import records.grammar.FormatParser.TypeExpressionTerminalContext;
import records.grammar.FormatParserBaseVisitor;
import records.gui.expressioneditor.OperandNode;
import records.gui.expressioneditor.OperatorEntry;
import records.transformations.expression.LoadableExpression;
import records.transformations.expression.UnitExpression;
import styled.StyledShowable;
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
            public TypeExpression tagged(TypeId typeName, ImmutableList<DataType> typeVars, ImmutableList<TagType<DataType>> tags) throws InternalException, InternalException
            {
                TaggedTypeNameExpression taggedTypeNameExpression = new TaggedTypeNameExpression(typeName);
                if (typeVars.isEmpty())
                    return taggedTypeNameExpression;
                ImmutableList.Builder<TypeExpression> args = ImmutableList.builderWithExpectedSize(typeVars.size() + 1);
                args.add(taggedTypeNameExpression);
                for (DataType typeVar : typeVars)
                {
                    args.add(fromDataType(typeVar));
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

            @Override
            public TypeExpression typeVariable(String typeVariableName) throws InternalException, InternalException
            {
                return new UnfinishedTypeExpression(typeVariableName);
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
}
