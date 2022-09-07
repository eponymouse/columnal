package records.transformations.expression;

import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.datatype.DataTypeUtility;
import xyz.columnal.data.unit.UnitManager;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import records.transformations.expression.visitor.ExpressionVisitor;
import xyz.columnal.typeExp.TypeExp;
import xyz.columnal.styled.StyledString;
import xyz.columnal.utility.Utility;

import java.util.List;
import java.util.Random;

/**
 * Created by neil on 10/12/2016.
 */
public class AndExpression extends NaryOpShortCircuitExpression
{
    public AndExpression(List<@Recorded Expression> expressions)
    {
        super(expressions);
    }

    @Override
    public NaryOpExpression copyNoNull(List<@Recorded Expression> replacements)
    {
        return new AndExpression(replacements);
    }

    @Override
    protected String saveOp(int index)
    {
        return "&";
    }

    @Override
    public @Nullable CheckedExp checkNaryOp(ColumnLookup dataLookup, TypeState state, ExpressionKind kind, ErrorAndTypeRecorder onError) throws UserException, InternalException
    {
        // And has a special property: the type state is threaded through to the next item.
        for (@Recorded Expression expression : expressions)
        {
            @Nullable CheckedExp checked = expression.check(dataLookup, state, ExpressionKind.EXPRESSION, LocationInfo.UNIT_DEFAULT, onError);
            if (checked == null)
                return null;
            
            if (onError.recordError(expression, TypeExp.unifyTypes(TypeExp.bool(this), checked.typeExp)) == null)
                return null;
            state = checked.typeState;
        }
        return new CheckedExp(onError.recordTypeNN(this, TypeExp.bool(this)), state);
    }

    @Override
    public ValueResult getValueNaryOp(final EvaluateState origState) throws EvaluationException, InternalException
    {
        EvaluateState state = origState;
        ImmutableList.Builder<ValueResult> values = ImmutableList.builderWithExpectedSize(expressions.size());
        for (int i = 0; i < expressions.size(); i++)
        {
            Expression expression = expressions.get(i);
            ValueResult valState = fetchSubExpression(expression, state, values);
            Boolean b = Utility.cast(valState.value, Boolean.class);
            if (b == false)
            {
                return result(DataTypeUtility.value(false), origState, values.build());
            }
            // In and, state is threaded through to next items:
            state = valState.evaluateState;
        }
        return result(DataTypeUtility.value(true), state, values.build());
    }

    @SuppressWarnings("recorded")
    @Override
    public Expression _test_typeFailure(Random r, _test_TypeVary newExpressionOfDifferentType, UnitManager unitManager) throws InternalException, UserException
    {
        int index = r.nextInt(expressions.size());
        return copy(makeNullList(index, newExpressionOfDifferentType.getDifferentType(TypeExp.bool(null))));
    }

    @Override
    public <T> T visit(ExpressionVisitor<T> visitor)
    {
        return visitor.and(this, expressions);
    }
}
