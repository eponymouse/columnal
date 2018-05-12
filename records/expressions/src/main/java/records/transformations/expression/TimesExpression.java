package records.transformations.expression;

import annotation.qual.Value;
import annotation.recorded.qual.Recorded;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.sosy_lab.common.rationals.Rational;
import records.data.datatype.DataTypeUtility;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.typeExp.NumTypeExp;
import records.typeExp.TypeExp;
import records.typeExp.units.UnitExp;
import utility.Utility;

import java.util.List;
import java.util.Optional;
import java.util.Random;

/**
 * Created by neil on 29/11/2016.
 */
public class TimesExpression extends NaryOpExpression
{
    public TimesExpression(List<@Recorded Expression> expressions)
    {
        super(expressions);
    }

    @Override
    public NaryOpExpression copyNoNull(List<@Recorded Expression> replacements)
    {
        return new TimesExpression(replacements);
    }

    @Override
    public Optional<Rational> constantFold()
    {
        Rational running = Rational.ONE;
        for (Expression expression : expressions)
        {
            Optional<Rational> r = expression.constantFold();
            if (r.isPresent())
                running = running.times(r.get());
            else
                return Optional.empty();
        }
        return Optional.of(running);
    }

    @Override
    protected String saveOp(int index)
    {
        return "*";
    }

    @Override
    public @Nullable TypeExp checkNaryOp(TableLookup dataLookup, TypeState state, ErrorAndTypeRecorder onError) throws UserException, InternalException
    {
        UnitExp runningUnit = UnitExp.SCALAR;
        for (Expression expression : expressions)
        {
            UnitExp unitVar = UnitExp.makeVariable();
            TypeExp expectedType = new NumTypeExp(this, unitVar);
            @Nullable TypeExp inferredType = expression.check(dataLookup, state, onError);
            if (inferredType == null)
                return null;
            
            // This should unify our unitVar appropriately:
            if (onError.recordError(this, TypeExp.unifyTypes(expectedType, inferredType)) == null)
                return null;
            
            runningUnit = runningUnit.times(unitVar);
        }
        return onError.recordType(this, new NumTypeExp(this, runningUnit));
    }

    @Override
    public @Value Object getValueNaryOp(EvaluateState state) throws UserException, InternalException
    {
        Number n = (Number) expressions.get(0).getValue(state);
        for (int i = 1; i < expressions.size(); i++)
            n = Utility.multiplyNumbers(n, (Number) expressions.get(i).getValue(state));
        return DataTypeUtility.value(n);
    }

    @SuppressWarnings("recorded")
    @Override
    public Expression _test_typeFailure(Random r, _test_TypeVary newExpressionOfDifferentType, UnitManager unitManager) throws InternalException, UserException
    {
        int index = r.nextInt(expressions.size());
        return copy(makeNullList(index, newExpressionOfDifferentType.getNonNumericType()));
    }
}
