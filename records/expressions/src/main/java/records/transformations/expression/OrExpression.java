package records.transformations.expression;

import annotation.qual.Value;
import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataTypeUtility;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.gui.expressioneditor.ExpressionNodeParent;
import records.transformations.expression.ErrorAndTypeRecorder.QuickFix;
import records.typeExp.TypeExp;
import styled.StyledString;
import utility.Pair;
import utility.Utility;

import java.util.List;
import java.util.Random;

/**
 * Created by neil on 10/12/2016.
 */
public class OrExpression extends NaryOpExpression
{
    public OrExpression(List<@Recorded Expression> expressions)
    {
        super(expressions);
    }

    @Override
    public NaryOpExpression copyNoNull(List<@Recorded Expression> replacements)
    {
        return new OrExpression(replacements);
    }

    @Override
    protected String saveOp(int index)
    {
        return "|";
    }

    @Override
    public @Nullable TypeExp checkNaryOp(TableLookup dataLookup, TypeState state, ErrorAndTypeRecorder onError) throws UserException, InternalException
    {
        return onError.recordType(this, checkAllOperandsSameType(TypeExp.bool(this), dataLookup, state, onError, (typeAndExpression) -> {
            return new Pair<@Nullable StyledString, ImmutableList<QuickFix<Expression,ExpressionNodeParent>>>(typeAndExpression.getOurType() == null ? null : StyledString.concat(StyledString.s("Operands to '|' must be boolean but found "), typeAndExpression.getOurType().toStyledString()), ImmutableList.of());
        }));
    }

    @Override
    public @Value Object getValueNaryOp(EvaluateState state) throws UserException, InternalException
    {
        for (Expression expression : expressions)
        {
            Boolean b = Utility.cast(expression.getValue(state), Boolean.class);
            if (b == true)
                return DataTypeUtility.value(true);
        }
        return DataTypeUtility.value(false);
    }

    @SuppressWarnings("recorded")
    @Override
    public Expression _test_typeFailure(Random r, _test_TypeVary newExpressionOfDifferentType, UnitManager unitManager) throws InternalException, UserException
    {
        int index = r.nextInt(expressions.size());
        return copy(makeNullList(index, newExpressionOfDifferentType.getDifferentType(TypeExp.bool(null))));
    }
}
