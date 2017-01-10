package records.transformations.expression;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.sosy_lab.common.rationals.Rational;
import org.sosy_lab.java_smt.api.Formula;
import org.sosy_lab.java_smt.api.FormulaManager;
import records.data.ColumnId;
import records.data.RecordSet;
import records.data.TableId;
import records.data.datatype.DataType;
import records.data.datatype.DataType.NumberInfo;
import records.data.unit.Unit;
import records.error.InternalException;
import records.error.UserException;
import utility.ExBiConsumer;
import utility.Pair;
import utility.Utility;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Created by neil on 25/11/2016.
 */
public class NumericLiteral extends Literal
{
    private final Number value;
    private final @Nullable Unit unit;
    private final DataType type;

    public NumericLiteral(Number value, @Nullable Unit unit)
    {
        this.value = value;
        this.unit = unit;
        this.type = unit == null ? DataType.NUMBER : DataType.number(new NumberInfo(unit, 0));
    }

    @Override
    public DataType check(RecordSet data, TypeState state, ExBiConsumer<Expression, String> onError)
    {
        return type;
    }

    @Override
    public Optional<Rational> constantFold()
    {
        if (value instanceof BigDecimal)
            return Optional.of(Rational.ofBigDecimal((BigDecimal) value));
        else if (value instanceof BigInteger)
            return Optional.of(Rational.ofBigInteger((BigInteger) value));
        else
            return Optional.of(Rational.of(value.longValue()));
    }

    @Override
    public Object getValue(int rowIndex, EvaluateState state) throws UserException, InternalException
    {
        return value;
    }

    @Override
    public String save(boolean topLevel)
    {
        String num;
        if (value instanceof Double)
            num = String.format("%f", value.doubleValue());
        else
            num =  value.toString();
        if (unit == null || unit.equals(Unit.SCALAR))
            return num;
        else
            return num + "{" + unit.toString() + "}";
    }

    @Override
    public Formula toSolver(FormulaManager formulaManager, RecordSet src, Map<Pair<@Nullable TableId, ColumnId>, Formula> columnVariables)
    {
        // TODO handle non-integers properly
        if (value instanceof BigDecimal)
            return formulaManager.getIntegerFormulaManager().makeNumber((BigDecimal)value);
        if (value instanceof BigInteger)
            return formulaManager.getIntegerFormulaManager().makeNumber((BigInteger)value);
        else
            return formulaManager.getIntegerFormulaManager().makeNumber(value.longValue());
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        NumericLiteral that = (NumericLiteral) o;

        if (!that.type.equals(type)) return false;
        return Utility.compareNumbers(value, that.value) == 0;
    }

    @Override
    public int hashCode()
    {
        return value.hashCode() + 31 * type.hashCode();
    }
}
