package records.transformations.function;

import annotation.qual.Value;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.sosy_lab.common.rationals.Rational;
import records.data.datatype.DataType;
import records.data.datatype.NumberInfo;
import records.data.datatype.DataTypeUtility;
import records.data.unit.Unit;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import utility.Pair;
import utility.Utility;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.function.Consumer;

/**
 * Created by neil on 11/12/2016.
 */
public class AsUnit extends FunctionDefinition
{
    public AsUnit()
    {
        super("as", "as.short");
    }

    @Override
    public @Nullable Pair<FunctionInstance, DataType> typeCheck(List<Unit> units, DataType param, Consumer<String> onError, UnitManager mgr) throws InternalException, UserException
    {
        if (units.size() != 1)
        {
            onError.accept("Must specify one unit to convert to");
            return null;
        }

        Unit srcUnit = param.getNumberInfo().getUnit();
        Optional<Rational> multiplier = ((Unit) srcUnit).canScaleTo(units.get(0), mgr);

        if (!multiplier.isPresent())
        {
            onError.accept("Cannot scale " + srcUnit + " to " + units.get(0));
            return null;
        }

        return new Pair<>(new Instance(multiplier.get()), DataType.number(new NumberInfo(units.get(0), param.getNumberInfo().getDisplayInfo())));
    }

    @Override
    public <E> Pair<List<Unit>, E> _test_typeFailure(Random r, _test_TypeVary<E> newExpressionOfDifferentType, UnitManager unitManager) throws UserException, InternalException
    {
        // TODO also return units which can convert
        return new Pair<>(Collections.emptyList(), newExpressionOfDifferentType.getNonNumericType());
    }

    @Override
    public List<FunctionType> getOverloads(UnitManager mgr) throws InternalException
    {
        throw new InternalException("Overloads inapplicable to astype");
    }

    private static class Instance extends FunctionInstance
    {
        private final @Nullable BigDecimal scaleBD;
        private final long scaleInt;

        public Instance(Rational scale)
        {
            if (scale.isIntegral() && scale.longValue() == scale.getNum().longValue())
            {
                this.scaleBD = null;
                this.scaleInt = scale.longValue();
            }
            else
            {
                this.scaleBD = new BigDecimal(scale.getNum()).divide(new BigDecimal(scale.getDen()), MathContext.DECIMAL128);
                this.scaleInt = 0;
            }
        }

        @Override
        public @Value Object getValue(int rowIndex, @Value Object param)
        {
            return DataTypeUtility.value(Utility.multiplyNumbers((Number)param, scaleBD != null ? scaleBD : scaleInt));
        }
    }
}
