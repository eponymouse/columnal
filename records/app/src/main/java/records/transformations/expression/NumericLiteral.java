package records.transformations.expression;

import annotation.qual.Value;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.sosy_lab.common.rationals.Rational;
import org.sosy_lab.java_smt.api.Formula;
import org.sosy_lab.java_smt.api.FormulaManager;
import records.data.ColumnId;
import records.data.RecordSet;
import records.data.TableId;
import records.data.datatype.DataType;
import records.data.datatype.NumberInfo;
import records.data.datatype.DataTypeUtility;
import records.data.unit.Unit;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.expression.ErrorRecorder.QuickFix;
import utility.Either;
import utility.Pair;
import utility.Utility;
import utility.gui.TranslationUtility;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Created by neil on 25/11/2016.
 */
public class NumericLiteral extends Literal
{
    private final @Value Number value;
    private final @Nullable UnitExpression unit;

    public NumericLiteral(Number value, @Nullable UnitExpression unit)
    {
        this.value = DataTypeUtility.value(value);
        this.unit = unit;
    }

    @Override
    public @Nullable DataType check(RecordSet data, TypeState state, ErrorRecorder onError)
    {
        if (unit == null)
            return DataType.NUMBER;

        Either<Pair<String, List<UnitExpression>>, Unit> errOrUnit = unit.asUnit(state.getUnitManager());
        return errOrUnit.<@Nullable DataType>either(err -> {
            onError.recordError(this, err.getFirst(), Utility.mapList(err.getSecond(), u -> new QuickFix(TranslationUtility.getString("quick.fix.unit"), () -> new NumericLiteral(value, u))));
            return null;
        }, u -> DataType.number(new NumberInfo(u, null)));
    }

    @Override
    public Optional<Rational> constantFold()
    {
        if (value instanceof BigDecimal)
            return Optional.of(Rational.ofBigDecimal((BigDecimal) value));
        else
            return Optional.of(Rational.of(value.longValue()));
    }

    @Override
    public @Value Object getValue(int rowIndex, EvaluateState state) throws UserException, InternalException
    {
        return value;
    }

    @Override
    public String save(boolean topLevel)
    {
        String num;
        num = numberAsString();
        if (unit == null || unit.equals(Unit.SCALAR))
            return num;
        else
            return num + "{" + unit.save(true) + "}";
    }

    private String numberAsString()
    {
        String num;
        if (value instanceof Double)
            num = String.format("%f", value.doubleValue());
        else if (value instanceof BigDecimal)
            num = ((BigDecimal)value).toPlainString();
        else
            num =  value.toString();
        return num;
    }

    @Override
    public Formula toSolver(FormulaManager formulaManager, RecordSet src, Map<Pair<@Nullable TableId, ColumnId>, Formula> columnVariables)
    {
        // TODO handle non-integers properly
        if (value instanceof BigDecimal)
            return formulaManager.getIntegerFormulaManager().makeNumber((BigDecimal)value);
        else
            return formulaManager.getIntegerFormulaManager().makeNumber(value.longValue());
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        NumericLiteral that = (NumericLiteral) o;

        if (unit == null ? that.unit != null : !unit.equals(that.unit)) return false;
        return Utility.compareNumbers(value, that.value) == 0;
    }

    @Override
    public int hashCode()
    {
        int result = value.hashCode();
        result = 31 * result + (unit != null ? unit.hashCode() : 0);
        return result;
    }

    @Override
    protected String editString()
    {
        return numberAsString();
    }

    public @Nullable UnitExpression getUnitExpression()
    {
        return unit;
    }
}
