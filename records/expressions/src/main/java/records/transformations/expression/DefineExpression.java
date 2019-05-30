package records.transformations.expression;

import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.TableAndColumnRenames;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.expression.visitor.ExpressionVisitor;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
import utility.Utility;

import java.util.Objects;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DefineExpression extends Expression
{
    private final ImmutableList<@Recorded EqualExpression> defines;
    private final @Recorded Expression body;

    public DefineExpression(ImmutableList<@Recorded EqualExpression> defines, @Recorded Expression body)
    {
        this.defines = defines;
        this.body = body;
    }

    @Override
    public @Nullable CheckedExp check(ColumnLookup dataLookup, TypeState typeState, LocationInfo locationInfo, ErrorAndTypeRecorder onError) throws UserException, InternalException
    {
        for (EqualExpression define : defines)
        {
            @Nullable CheckedExp checkEq = define.check(dataLookup, typeState, locationInfo, onError);
            if (checkEq == null)
                return null;
            typeState = checkEq.typeState;
        }
        
        return body.check(dataLookup, typeState, locationInfo, onError);
    }

    @Override
    public @OnThread(Tag.Simulation) ValueResult calculateValue(EvaluateState state) throws UserException, InternalException
    {
        for (EqualExpression define : defines)
        {
            ValueResult outcome = define.calculateValue(state);
            if (Utility.cast(outcome.value, Boolean.class).booleanValue() == false)
            {
                throw new UserException(StyledString.concat(StyledString.s("Pattern did not match: "), define.toStyledString()));
            }
            state = outcome.evaluateState;
        }
        return body.calculateValue(state);
    }

    @Override
    public <T> T visit(ExpressionVisitor<T> visitor)
    {
        return visitor.define(this, defines, body);
    }

    @Override
    public String save(boolean structured, BracketedStatus surround, TableAndColumnRenames renames)
    {
        return defines.stream().map(e -> "@define " + e.save(structured, BracketedStatus.DONT_NEED_BRACKETS, renames)).collect(Collectors.joining(" ")) + " @in " + body.save(structured, BracketedStatus.DONT_NEED_BRACKETS, renames) + " @endin";
    }

    @Override
    public Stream<Pair<Expression, Function<Expression, Expression>>> _test_childMutationPoints()
    {
        return Stream.empty();
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
        DefineExpression that = (DefineExpression) o;
        return defines.equals(that.defines) &&
                body.equals(that.body);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(defines, body);
    }

    @Override
    protected StyledString toDisplay(BracketedStatus bracketedStatus, ExpressionStyler expressionStyler)
    {
        return expressionStyler.styleExpression(StyledString.concat(
            StyledString.s("@define "),
            defines.stream().map(e -> e.toStyledString()).collect(StyledString.joining(" @define ")),
            StyledString.s(" @in "),
            body.toStyledString(),
            StyledString.s(" @endin")
        ), this);
    }

    @SuppressWarnings("recorded")
    @Override
    public Expression replaceSubExpression(Expression toReplace, Expression replaceWith)
    {
        if (this == toReplace)
            return replaceWith;
        else
            return new DefineExpression(Utility.mapListI(defines, e -> e.replaceSubExpression(toReplace, replaceWith)), body.replaceSubExpression(toReplace, replaceWith));
    }
}
