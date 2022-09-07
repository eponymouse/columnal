package records.transformations.expression;

import annotation.qual.Value;
import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableMap;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.qual.Pure;
import org.sosy_lab.common.rationals.Rational;
import xyz.columnal.data.TableAndColumnRenames;
import xyz.columnal.data.datatype.DataTypeUtility;
import xyz.columnal.data.datatype.TypeManager;
import xyz.columnal.data.unit.Unit;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.jellytype.JellyUnit;
import records.transformations.expression.UnitExpression.UnitLookupException;
import records.transformations.expression.visitor.ExpressionVisitor;
import xyz.columnal.typeExp.NumTypeExp;
import xyz.columnal.typeExp.TypeExp;
import xyz.columnal.typeExp.units.MutUnitVar;
import xyz.columnal.typeExp.units.UnitExp;
import xyz.columnal.styled.StyledString;
import xyz.columnal.utility.Utility;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Created by neil on 25/11/2016.
 */
public class NumericLiteral extends Literal
{
    private final @Value Number value;
    private final @Nullable @Recorded UnitExpression unit;

    public NumericLiteral(@Value Number value, @Nullable @Recorded UnitExpression unit)
    {
        this.value = value;
        this.unit = unit;
    }

    @Override
    public @Nullable TypeExp checkType(TypeState state, LocationInfo locationInfo, ErrorAndTypeRecorder onError) throws InternalException
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
            return new NumTypeExp(this, unit);
        }

        try
        {
            JellyUnit u = unit.asUnit(state.getUnitManager());
            return new NumTypeExp(this, u.makeUnitExp(ImmutableMap.of()));
        }
        catch (UnitLookupException e)
        {
            if (e.errorMessage != null)
                onError.recordError(e.errorItem, e.errorMessage);
            if (!e.quickFixes.isEmpty())
                onError.recordQuickFixes(e.errorItem, e.quickFixes);
            return null;
        }
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
    public ValueResult calculateValue(EvaluateState state)
    {
        return result(value, state);
    }

    @Override
    public String save(SaveDestination saveDestination, BracketedStatus surround, TableAndColumnRenames renames)
    {
        String num = numberAsString();
        if (unit == null)
            return num;
        else
            return num + "{" + unit.save(saveDestination, true) + "}";
    }

    @Override
    public StyledString toDisplay(DisplayType displayType, BracketedStatus surround, ExpressionStyler expressionStyler)
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

    @Pure
    public @Nullable @Recorded UnitExpression getUnitExpression()
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

    @Override
    public <T> T visit(ExpressionVisitor<T> visitor)
    {
        return visitor.litNumber(this, value, unit);
    }
}
