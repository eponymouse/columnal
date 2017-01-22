package records.transformations.function;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.sosy_lab.common.rationals.Rational;
import records.data.datatype.DataType;
import records.data.datatype.DataType.NumberInfo;
import records.data.unit.Unit;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.expression.Expression;
import records.transformations.expression.Expression._test_TypeVary;
import utility.ExConsumer;
import utility.Pair;
import utility.Utility;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;

/**
 * Created by neil on 11/12/2016.
 */
public class AsType extends FunctionDefinition
{
    public AsType()
    {
        super("as");
    }

    @Override
    public @Nullable Pair<FunctionInstance, DataType> typeCheck(List<Unit> units, List<DataType> params, ExConsumer<String> onError, UnitManager mgr) throws InternalException, UserException
    {
        if (units.size() != 1)
        {
            onError.accept("Must specify one unit to convert to");
            return null;
        }
        if (params.size() != 1)
        {
            onError.accept("Function takes exactly one parameter");
            return null;
        }

        Unit srcUnit = params.get(0).getNumberInfo().getUnit();
        Optional<Rational> multiplier = ((Unit) srcUnit).canScaleTo(units.get(0), mgr);

        if (!multiplier.isPresent())
        {
            onError.accept("Cannot scale " + srcUnit + " to " + units.get(0));
            return null;
        }

        return new Pair<>(new Instance(multiplier.get()), DataType.number(new NumberInfo(units.get(0), 0)));
    }

    @Override
    public Pair<List<Unit>, List<Expression>> _test_typeFailure(Random r, _test_TypeVary newExpressionOfDifferentType, UnitManager unitManager) throws UserException, InternalException
    {
        // TODO also return units which can convert
        return new Pair<>(Collections.emptyList(), Collections.singletonList(newExpressionOfDifferentType.getNonNumericType()));
    }

    @Override
    public List<DataType> getLikelyArgTypes(UnitManager unitManager) throws UserException, InternalException
    {
        return Collections.singletonList(DataType.NUMBER);
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
        public @Value Object getValue(int rowIndex, ImmutableList<@Value Object> params)
        {
            return Utility.value(Utility.multiplyNumbers((Number)params.get(0), scaleBD != null ? scaleBD : scaleInt));
        }
    }
}
