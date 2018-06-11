package records.transformations.expression;

import annotation.qual.Value;
import annotation.recorded.qual.Recorded;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataTypeUtility;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.gui.expressioneditor.GeneralExpressionEntry.Op;
import records.typeExp.TypeExp;
import styled.StyledString;
import threadchecker.OnThread;
import utility.Pair;
import utility.Utility;

import java.util.List;
import java.util.Random;

/**
 * Created by neil on 10/12/2016.
 */
public class AndExpression extends NaryOpExpression
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
    protected Op loadOp(int index)
    {
        return Op.AND;
    }

    @Override
    public @Nullable CheckedExp checkNaryOp(TableLookup dataLookup, TypeState state, ErrorAndTypeRecorder onError) throws UserException, InternalException
    {
        // And has a special property: the type state is threaded through to the next item.
        for (Expression expression : expressions)
        {
            @Nullable CheckedExp checked = expression.check(dataLookup, state, onError);
            if (checked == null)
                return null;
            if (checked.expressionKind == ExpressionKind.PATTERN)
            {
                onError.recordError(this, StyledString.s("Pattern is not allowed in & expression"));
                return null;
            }
            
            if (onError.recordError(expression, TypeExp.unifyTypes(TypeExp.bool(this), checked.typeExp)) == null)
                return null;
            state = checked.typeState;
        }
        return new CheckedExp(onError.recordTypeNN(this, TypeExp.bool(this)), state, ExpressionKind.EXPRESSION);
    }

    @Override
    public Pair<@Value Object, EvaluateState> getValueNaryOp(final EvaluateState origState) throws UserException, InternalException
    {
        EvaluateState state = origState;
        for (Expression expression : expressions)
        {
            Pair<@Value Object, EvaluateState> valState = expression.getValue(state);
            Boolean b = Utility.cast(valState.getFirst(), Boolean.class);
            if (b == false)
                return new Pair<>(DataTypeUtility.value(false), origState);
            state = valState.getSecond();
        }
        return new Pair<>(DataTypeUtility.value(true), state);
    }

    @SuppressWarnings("recorded")
    @Override
    public Expression _test_typeFailure(Random r, _test_TypeVary newExpressionOfDifferentType, UnitManager unitManager) throws InternalException, UserException
    {
        int index = r.nextInt(expressions.size());
        return copy(makeNullList(index, newExpressionOfDifferentType.getDifferentType(TypeExp.bool(null))));
    }
}
