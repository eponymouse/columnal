package records.transformations.expression;

import annotation.qual.Value;
import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.TableAndColumnRenames;
import records.data.datatype.DataType;
import records.data.datatype.TypeManager;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.gui.expressioneditor.ExpressionNodeParent;
import records.gui.expressioneditor.TypeLiteralNode;
import records.jellytype.JellyType;
import records.transformations.expression.type.TypeExpression;
import records.transformations.function.FunctionDefinition;
import records.transformations.function.FunctionList;
import records.typeExp.TypeExp;
import styled.StyledString;
import utility.Either;
import utility.Pair;
import utility.TaggedValue;

import java.util.Objects;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * An expression, and a fixed type it should conform to.  Like the :: operator in Haskell expressions,
 * (or like the `asTypeOf` function if you specified a type).  Just wraps an inner expression with a type. 
 */
public class TypeLiteralExpression extends NonOperatorExpression
{
    // This may a type expression that doesn't save to a valid type:
    private final TypeExpression type;
    
    public TypeLiteralExpression(@Recorded TypeExpression type)
    {
        this.type = type;
    }

    public static Expression fixType(TypeManager typeManager, JellyType fix, @Recorded Expression expression) throws InternalException
    {
        try
        {
            TypeExpression typeExpression = TypeExpression.fromJellyType(fix, typeManager);
            FunctionDefinition asType = FunctionList.lookup(typeManager.getUnitManager(), "asType");
            if (asType == null)
                throw new InternalException("Missing asType function");
            if (expression instanceof CallExpression
                && ((CallExpression) expression).getFunction().equals(new StandardFunction(asType))
                && ((CallExpression) expression).getParam() instanceof TupleExpression)
            {
                expression = ((TupleExpression) ((CallExpression) expression).getParam()).getMembers().get(1);
            }

            return new CallExpression(new StandardFunction(asType), new TupleExpression(ImmutableList.of(
                new TypeLiteralExpression(typeExpression),
                expression
            )));
        }
        catch (UserException e)
        {
            // If we have a user exception, we are trying to fix to a non-existent type, which means
            // we fucked up, not the user:
            throw new InternalException("Problem locating type for suggested fix", e);
        }
    }
        
    @Override
    public @Nullable CheckedExp check(TableLookup dataLookup, TypeState typeState, ErrorAndTypeRecorder onError) throws UserException, InternalException
    {
        @Nullable DataType dataType = type.toDataType(typeState.getTypeManager());
        if (dataType == null)
        {
            // Should already be an error in the type itself
            return null;
        }
        return onError.recordTypeAndError(this, Either.right(TypeExp.dataTypeToTypeGADT(this, dataType)), ExpressionKind.EXPRESSION, typeState);
    }

    @Override
    public Pair<@Value Object, EvaluateState> getValue(EvaluateState state) throws UserException, InternalException
    {
        // TODO return the actual type literal once we define the GADT
        return new Pair<>(new TaggedValue(0, null), state);
    }

    @Override
    public Stream<ColumnReference> allColumnReferences()
    {
        return Stream.of();
    }

    @Override
    public String save(BracketedStatus surround, TableAndColumnRenames renames)
    {
        return "type{" + type.save(renames) + "}";
    }

    @Override
    public StyledString toDisplay(BracketedStatus surround)
    {
        return StyledString.concat(StyledString.s("type{"), type.toStyledString(), StyledString.s("}"));
    }

    @Override
    public Stream<SingleLoader<Expression, ExpressionNodeParent>> loadAsConsecutive(BracketedStatus bracketedStatus)
    {
        return Stream.<SingleLoader<Expression, ExpressionNodeParent>>of(p -> new TypeLiteralNode(p, type));
    }

    @Override
    public Stream<Pair<Expression, Function<Expression, Expression>>> _test_childMutationPoints()
    {
        // TODO
        return Stream.empty();
    }

    @Override
    public @Nullable Expression _test_typeFailure(Random r, _test_TypeVary newExpressionOfDifferentType, UnitManager unitManager) throws InternalException, UserException
    {
        // TODO
        return null;
    }


    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TypeLiteralExpression that = (TypeLiteralExpression) o;
        return Objects.equals(type, that.type);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(type);
    }

    public TypeExpression getType()
    {
        return type;
    }
}
