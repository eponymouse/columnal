package records.transformations.expression;

import annotation.identifier.qual.ExpressionIdentifier;
import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.TableAndColumnRenames;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.jellytype.JellyType;
import records.transformations.expression.type.TypeExpression;
import records.transformations.expression.type.TypeExpression.JellyRecorder;
import records.transformations.expression.visitor.ExpressionVisitor;
import records.typeExp.TypeExp;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;

import java.util.Objects;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * A variable name, and a fixed type it should conform to.  Like the :: operator in Haskell expressions,
 * (or like the `asTypeOf` function if you specified a type). 
 */
public class HasTypeExpression extends Expression
{
    // Var name, without the leading decorator
    private final @ExpressionIdentifier String varName;
    private final @Recorded TypeLiteralExpression type;

    public HasTypeExpression(@ExpressionIdentifier String varName, @Recorded TypeLiteralExpression type)
    {
        this.varName = varName;
        this.type = type;
    }

    @Override
    public @Nullable CheckedExp check(ColumnLookup dataLookup, TypeState original, LocationInfo locationInfo, ErrorAndTypeRecorder onError) throws UserException, InternalException
    {
        TypeState typeState = original.addPreType(varName, type.getType().toJellyType(original.getTypeManager(), new JellyRecorder()
        {
            @SuppressWarnings("recorded")
            @Override
            public @Recorded JellyType record(JellyType jellyType, @Recorded TypeExpression source)
            {
                return jellyType;
            }
        }).makeTypeExp(ImmutableMap.of()), (StyledString s) -> onError.<Expression>recordError(this, s));
        
        if (typeState == null)
            return null;
        
        // Our type shouldn't be directly used, but we don't want to return null, hence void:
        return new CheckedExp(onError.recordTypeNN(this, TypeExp.fromDataType(this, typeState.getTypeManager().getVoidType().instantiate(ImmutableList.of(), typeState.getTypeManager()))), typeState, ExpressionKind.EXPRESSION);
    }

    @Override
    public @OnThread(Tag.Simulation) ValueResult calculateValue(EvaluateState state) throws UserException, InternalException
    {
        throw new InternalException("Type definitions have no value");
    }

    @Override
    public <T> T visit(ExpressionVisitor<T> visitor)
    {
        return visitor.hasType(this, varName, type);
    }

    @Override
    public String save(boolean structured, BracketedStatus surround, TableAndColumnRenames renames)
    {
        return "_" + varName + " :: " + type.save(structured, BracketedStatus.DONT_NEED_BRACKETS, renames);
    }

    @Override
    public Stream<Pair<Expression, Function<Expression, Expression>>> _test_childMutationPoints()
    {
        return Stream.of();
    }

    @Override
    public @Nullable Expression _test_typeFailure(Random r, _test_TypeVary newExpressionOfDifferentType, UnitManager unitManager) throws InternalException, UserException
    {
        return null;
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        HasTypeExpression that = (HasTypeExpression) o;
        return varName.equals(that.varName) &&
                type.equals(that.type);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(varName, type);
    }

    @Override
    protected StyledString toDisplay(BracketedStatus bracketedStatus, ExpressionStyler expressionStyler)
    {
        return expressionStyler.styleExpression(StyledString.concat(StyledString.s("_" + varName), type.toDisplay(BracketedStatus.DONT_NEED_BRACKETS, expressionStyler)), this);
    }

    @SuppressWarnings("recorded")
    @Override
    public Expression replaceSubExpression(Expression toReplace, Expression replaceWith)
    {
        if (toReplace == this)
            return replaceWith;
        else
            return new HasTypeExpression(varName, (TypeLiteralExpression)type.replaceSubExpression(toReplace, replaceWith));
    }

    public @ExpressionIdentifier String getVarName()
    {
        return varName;
    }
}
