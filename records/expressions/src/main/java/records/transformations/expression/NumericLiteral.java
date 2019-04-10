package records.transformations.expression;

import annotation.qual.Value;
import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableMap;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.qual.Pure;
import org.sosy_lab.common.rationals.Rational;
import records.data.TableAndColumnRenames;
import records.data.datatype.DataTypeUtility;
import records.data.unit.Unit;
import records.error.InternalException;
import records.error.UserException;
import records.jellytype.JellyUnit;
import records.typeExp.NumTypeExp;
import records.typeExp.TypeExp;
import records.typeExp.units.MutUnitVar;
import records.typeExp.units.UnitExp;
import styled.StyledString;
import utility.Either;
import utility.Pair;
import utility.Utility;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Created by neil on 25/11/2016.
 */
public class NumericLiteral extends Literal
{
    private final @Value Number value;
    private final @Nullable @Recorded UnitExpression unit;

    public NumericLiteral(Number value, @Nullable @Recorded UnitExpression unit)
    {
        this.value = DataTypeUtility.value(value);
        this.unit = unit;
    }

    @Override
    public Either<StyledString, TypeExp> checkType(TypeState state, LocationInfo locationInfo) throws InternalException
    {
        if (unit == null)
        {
            final UnitExp unit;
            switch (locationInfo)
            {
                case UNIT_CONSTRAINED:
                    unit = new UnitExp(new MutUnitVar());
                    break;
                default:
                    unit = UnitExp.SCALAR;
                    break;
            }
            return Either.right(new NumTypeExp(this, unit));
        }

        Either<Pair<StyledString, List<UnitExpression>>, JellyUnit> errOrUnit = unit.asUnit(state.getUnitManager());
        return errOrUnit.<StyledString, TypeExp>mapBothInt(err -> {
            /*
            onError.recordQuickFixes(this, Utility.mapList(err.getSecond(), u -> {
                @SuppressWarnings("recorded")
                NumericLiteral replacement = new NumericLiteral(value, u);
                return new QuickFix<>("quick.fix.unit", CURRENT, replacement);
            }));
            */
            return err.getFirst();
        }, u -> new NumTypeExp(this, u.makeUnitExp(ImmutableMap.of())));
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
    public ValueResult calculateValue(EvaluateState state) throws UserException, InternalException
    {
        return result(value, state);
    }

    @Override
    public String save(boolean structured, BracketedStatus surround, TableAndColumnRenames renames)
    {
        String num = numberAsString();
        if (unit == null)
            return num;
        else
            return num + "{" + unit.save(structured, true) + "}";
    }

    @Override
    public StyledString toDisplay(BracketedStatus surround, ExpressionStyler expressionStyler)
    {
        StyledString num = StyledString.s(numberAsString());
        if (unit == null)
            return expressionStyler.styleExpression(num, this);
        else
            return expressionStyler.styleExpression(StyledString.concat(num, StyledString.s("{"), unit.toStyledString(), StyledString.s("}")), this);
    }

    private String numberAsString()
    {
        return Utility.numberToString(value);
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
    public String editString()
    {
        return numberAsString();
    }

    public @Nullable @Recorded @Pure UnitExpression getUnitExpression()
    {
        return unit;
    }

    public NumericLiteral withUnit(Unit unit)
    {
        return new NumericLiteral(value, UnitExpression.load(unit));
    }

    public @Value Number getNumber()
    {
        return value;
    }
}
