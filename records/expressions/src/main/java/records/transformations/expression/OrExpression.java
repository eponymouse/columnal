package records.transformations.expression;

import annotation.qual.Value;
import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataTypeUtility;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.gui.expressioneditor.ExpressionSaver;
import records.gui.expressioneditor.GeneralExpressionEntry.Op;
import records.typeExp.TypeExp;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
import utility.Utility;
import utility.Utility.TransparentBuilder;

import java.util.List;
import java.util.Random;

/**
 * Created by neil on 10/12/2016.
 */
public class OrExpression extends NaryOpShortCircuitExpression
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
    protected Op loadOp(int index)
    {
        return Op.OR;
    }

    @Override
    public @Nullable CheckedExp checkNaryOp(ColumnLookup dataLookup, TypeState state, ErrorAndTypeRecorder onError) throws UserException, InternalException
    {
        return onError.recordType(this, ExpressionKind.EXPRESSION, state, checkAllOperandsSameTypeAndNotPatterns(TypeExp.bool(this), dataLookup, state, LocationInfo.UNIT_DEFAULT, onError, (typeAndExpression) -> {
            return new Pair<@Nullable StyledString, ImmutableList<QuickFix<Expression,ExpressionSaver>>>(typeAndExpression.getOurType() == null ? null : StyledString.concat(StyledString.s("Operands to '|' must be boolean but found "), typeAndExpression.getOurType().toStyledString()), ImmutableList.of());
        }));
    }

    @Override
    @OnThread(Tag.Simulation)
    public ValueResult getValueNaryOp(EvaluateState state) throws UserException, InternalException
    {
        TransparentBuilder<ValueResult> values = new TransparentBuilder<>(expressions.size());
        for (int i = 0; i < expressions.size(); i++)
        {
            Expression expression = expressions.get(i);
            Boolean b = Utility.cast(values.add(expression.calculateValue(state)).value, Boolean.class);
            if (b == true)
            {
                return new ValueResult(DataTypeUtility.value(true), state, values.build());
            }
        }
        return new ValueResult(DataTypeUtility.value(false), state, values.build());
    }

    @SuppressWarnings("recorded")
    @Override
    public Expression _test_typeFailure(Random r, _test_TypeVary newExpressionOfDifferentType, UnitManager unitManager) throws InternalException, UserException
    {
        int index = r.nextInt(expressions.size());
        return copy(makeNullList(index, newExpressionOfDifferentType.getDifferentType(TypeExp.bool(null))));
    }
}
