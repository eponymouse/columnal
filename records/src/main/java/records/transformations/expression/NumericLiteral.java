package records.transformations.expression;

import edu.emory.mathcs.backport.java.util.Collections;
import org.sosy_lab.java_smt.api.Formula;
import org.sosy_lab.java_smt.api.FormulaManager;
import records.data.RecordSet;
import records.data.datatype.DataType;
import records.error.InternalException;
import records.error.UserException;
import utility.ExBiConsumer;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;

/**
 * Created by neil on 25/11/2016.
 */
public class NumericLiteral extends Literal
{
    private final Number value;

    public NumericLiteral(Number value)
    {
        this.value = value;
    }

    @Override
    public DataType check(RecordSet data, TypeState state, ExBiConsumer<Expression, String> onError)
    {
        return DataType.NUMBER;
    }

    @Override
    public List<Object> getValue(int rowIndex, EvaluateState state) throws UserException, InternalException
    {
        return Collections.singletonList(value);
    }

    @Override
    public String save()
    {
        if (value instanceof Double)
            return String.format("%f", value.doubleValue());
        else
            return value.toString();
    }

    @Override
    public Formula toSolver(FormulaManager formulaManager, RecordSet src)
    {
        // TODO handle non-integers
        if (value instanceof BigDecimal)
            return formulaManager.getIntegerFormulaManager().makeNumber((BigDecimal)value);
        if (value instanceof BigInteger)
            return formulaManager.getIntegerFormulaManager().makeNumber((BigInteger)value);
        else
            return formulaManager.getIntegerFormulaManager().makeNumber(value.longValue());
    }
}
