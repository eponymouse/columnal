package records.transformations.expression;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.sosy_lab.java_smt.api.Formula;
import org.sosy_lab.java_smt.api.FormulaManager;
import records.data.ColumnId;
import records.data.RecordSet;
import records.data.TableId;
import records.data.datatype.DataType;
import records.data.datatype.DataType.NumberInfo;
import records.data.unit.Unit;
import records.error.InternalException;
import records.error.UnimplementedException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.ExBiConsumer;
import utility.Pair;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Created by neil on 29/11/2016.
 */
public class TimesExpression extends NaryOpExpression
{
    public TimesExpression(List<Expression> expressions)
    {
        super(expressions);
    }

    @Override
    public NaryOpExpression copyNoNull(List<Expression> replacements)
    {
        return new TimesExpression(replacements);
    }

    @Override
    protected String saveOp(int index)
    {
        return "*";
    }

    @Override
    public @Nullable DataType check(RecordSet data, TypeState state, ExBiConsumer<Expression, String> onError) throws UserException, InternalException
    {
        Unit runningUnit = Unit.SCALAR;
        int minDP = 0;
        for (Expression expression : expressions)
        {
            @Nullable DataType expType = expression.check(data, state, onError);
            if (expType == null)
                return null;
            if (!expType.isNumber())
            {
                onError.accept(expression, "Non-numeric type in multiplication expression: " + expType);
                return null;
            }
            NumberInfo numberInfo = expType.getNumberInfo();
            runningUnit = runningUnit.times(numberInfo.getUnit());
            minDP = Math.max(minDP, numberInfo.getMinimumDP());
        }
        return DataType.number(new NumberInfo(runningUnit, minDP));
    }

    @Override
    public @OnThread(Tag.Simulation) List<Object> getValue(int rowIndex, EvaluateState state) throws UserException, InternalException
    {
        return Collections.singletonList(0);
    }

    @Override
    public Formula toSolver(FormulaManager formulaManager, RecordSet src, Map<Pair<@Nullable TableId, ColumnId>, Formula> columnVariables) throws InternalException, UserException
    {
        throw new UnimplementedException();
    }

    @Override
    public @Nullable Expression _test_typeFailure(Random r, _test_TypeVary newExpressionOfDifferentType) throws InternalException, UserException
    {
        int index = r.nextInt(expressions.size());
        return copy(makeNullList(index, newExpressionOfDifferentType.getNonNumericType()));
    }
}
