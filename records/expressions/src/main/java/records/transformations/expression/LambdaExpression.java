package records.transformations.expression;

import annotation.qual.Value;
import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.TableAndColumnRenames;
import records.data.datatype.TypeManager;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.expression.explanation.Explanation.ExecutionType;
import records.transformations.expression.function.ValueFunction;
import records.transformations.expression.visitor.ExpressionVisitor;
import records.typeExp.TypeExp;
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

public class LambdaExpression extends Expression
{
    private final ImmutableList<@Recorded Expression> parameters;
    private final @Recorded Expression body;

    public LambdaExpression(ImmutableList<@Recorded Expression> parameters, @Recorded Expression body)
    {
        this.parameters = parameters;
        this.body = body;
    }

    @Override
    public @Nullable CheckedExp check(ColumnLookup dataLookup, TypeState original, ExpressionKind kind, LocationInfo locationInfo, ErrorAndTypeRecorder onError) throws UserException, InternalException
    {
        ImmutableList.Builder<TypeExp> paramTypes = ImmutableList.builder();

        TypeState typeState = original;
        
        for (@Recorded Expression parameter : parameters)
        {
            CheckedExp checkedExp = parameter.check(dataLookup, typeState, ExpressionKind.PATTERN, LocationInfo.UNIT_DEFAULT, onError);
            if (checkedExp == null)
                return null;
            paramTypes.add(checkedExp.typeExp);
            typeState = checkedExp.typeState;
        }
        
        CheckedExp returnType = body.check(dataLookup, typeState, ExpressionKind.EXPRESSION, LocationInfo.UNIT_DEFAULT, onError);
        if (returnType == null)
            return null;
        
        return new CheckedExp(onError.recordTypeNN(this, TypeExp.function(this, paramTypes.build(), returnType.typeExp)), original);
    }

    @Override
    public @OnThread(Tag.Simulation) ValueResult calculateValue(EvaluateState original) throws UserException, InternalException
    {
        ValueFunction valueFunction = new ValueFunction()
        {
            @Override
            protected @OnThread(Tag.Simulation) @Value Object _call() throws InternalException, UserException
            {
                EvaluateState state = original;
                for (int i = 0; i < parameters.size(); i++)
                {
                    ValueResult result = parameters.get(i).matchAsPattern(arg(i), state);
                    state = result.evaluateState;
                }
                ValueResult bodyOutcome = body.calculateValue(state);
                addExtraExplanation(() -> bodyOutcome.makeExplanation(ExecutionType.VALUE));
                return bodyOutcome.value;
            }
        };
        
        return explanation(ValueFunction.value(valueFunction), ExecutionType.VALUE, original, ImmutableList.of(), ImmutableList.of(), false);
    }

    @Override
    public <T> T visit(ExpressionVisitor<T> visitor)
    {
        return visitor.lambda(this, parameters, body);
    }

    @Override
    public String save(SaveDestination saveDestination, BracketedStatus surround, @Nullable TypeManager typeManager, TableAndColumnRenames renames)
    {
        String params = parameters.stream().map(e -> e.save(saveDestination, BracketedStatus.DONT_NEED_BRACKETS, typeManager, renames)).collect(Collectors.joining(", "));
        String body = this.body.save(saveDestination, BracketedStatus.DONT_NEED_BRACKETS, typeManager, renames);
        if (saveDestination.needKeywords())
            return "@function(" + params + ") @then " + body + "@endfunction";
        else
            return "@function" + params + " @then " + body + "@endfunction";
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
        LambdaExpression that = (LambdaExpression) o;
        return parameters.equals(that.parameters) &&
                body.equals(that.body);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(parameters, body);
    }

    @Override
    protected StyledString toDisplay(BracketedStatus bracketedStatus, ExpressionStyler expressionStyler)
    {
        return expressionStyler.styleExpression(StyledString.concat(
            StyledString.s("@function("),
            parameters.stream().map(e -> e.toDisplay(BracketedStatus.DONT_NEED_BRACKETS, expressionStyler)).collect(StyledString.joining(", ")),
            StyledString.s(") @then "),
            body.toDisplay(BracketedStatus.DONT_NEED_BRACKETS, expressionStyler),
            StyledString.s(" @endfunction")
        ), this);
    }

    @SuppressWarnings("recorded")
    @Override
    public Expression replaceSubExpression(Expression toReplace, Expression replaceWith)
    {
        if (this == toReplace)
            return replaceWith;
        else 
            return new LambdaExpression(Utility.mapListI(parameters, e -> e.replaceSubExpression(toReplace, replaceWith)), body.replaceSubExpression(toReplace, replaceWith));
    }
}
