package records.transformations.expression;

import annotation.qual.Value;
import annotation.recorded.qual.Recorded;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.sosy_lab.common.rationals.Rational;
import org.sosy_lab.java_smt.api.Formula;
import org.sosy_lab.java_smt.api.FormulaManager;
import records.data.ColumnId;
import records.data.RecordSet;
import records.data.TableId;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeUtility;
import records.data.unit.Unit;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.expression.ErrorAndTypeRecorder.QuickFix;
import records.types.NumTypeExp;
import records.types.TypeExp;
import records.types.units.UnitExp;
import styled.StyledString;
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
    public @Nullable @Recorded TypeExp check(RecordSet data, TypeState state, ErrorAndTypeRecorder onError) throws InternalException
    {
        if (unit == null)
            return onError.recordType(this, TypeExp.fromConcrete(this, DataType.NUMBER));

        Either<Pair<StyledString, List<UnitExpression>>, UnitExp> errOrUnit = unit.asUnit(state.getUnitManager());
        return errOrUnit.<@Nullable @Recorded TypeExp>either(err -> {
            onError.recordError(this, err.getFirst(), Utility.mapList(err.getSecond(), u -> new QuickFix<>(TranslationUtility.getString("quick.fix.unit"), p -> new NumericLiteral(value, u))));
            return null;
        }, u -> onError.recordType(this, new NumTypeExp(this, u)));
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
        return Utility.numberToString(value);
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
