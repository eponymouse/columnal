package records.transformations.expression;

import annotation.qual.Value;
import annotation.recorded.qual.Recorded;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;
import org.sosy_lab.common.rationals.Rational;
import records.data.datatype.DataTypeUtility;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.types.NumTypeExp;
import records.types.TypeExp;
import records.types.units.UnitExp;
import utility.Utility;

import java.util.Optional;
import java.util.Random;

/**
 * Created by neil on 10/12/2016.
 */
public class DivideExpression extends BinaryOpExpression
{
    public DivideExpression(@Recorded Expression lhs, @Recorded Expression rhs)
    {
        super(lhs, rhs);
    }

    @Override
    protected String saveOp()
    {
        return "/";
    }

    @Override
    public BinaryOpExpression copy(@Nullable @Recorded Expression replaceLHS, @Nullable @Recorded Expression replaceRHS)
    {
        return new DivideExpression(replaceLHS == null ? lhs : replaceLHS, replaceRHS == null ? rhs : replaceRHS);
    }

    @Override
    @RequiresNonNull({"lhsType", "rhsType"})
    protected @Nullable TypeExp checkBinaryOp(TableLookup data, TypeState state, ErrorAndTypeRecorder onError) throws UserException, InternalException
    {
        @NonNull TypeExp lhsTypeFinal = lhsType;
        @NonNull TypeExp rhsTypeFinal = rhsType;
        UnitExp topUnit = UnitExp.makeVariable();
        UnitExp bottomUnit = UnitExp.makeVariable();
        // You can divide any number by any other number
        if (onError.recordError(this, TypeExp.unifyTypes(new NumTypeExp(this, topUnit), lhsTypeFinal)) == null
            || onError.recordError(this, TypeExp.unifyTypes(new NumTypeExp(this, bottomUnit), rhsTypeFinal)) == null)
        {
            return null;
        }
        return new NumTypeExp(this, topUnit.divideBy(bottomUnit));
    }

    @Override
    public @Value Object getValueBinaryOp(EvaluateState state) throws UserException, InternalException
    {
        return DataTypeUtility.value(Utility.divideNumbers((Number)lhs.getValue(state), (Number)rhs.getValue(state)));
    }

    @Override
    public Optional<Rational> constantFold()
    {
        Optional<Rational> l = lhs.constantFold();
        Optional<Rational> r = rhs.constantFold();
        if (l.isPresent() && r.isPresent())
            return Optional.of(l.get().divides(r.get()));
        else
            return Optional.empty();
    }

    @SuppressWarnings("recorded")
    @Override
    public Expression _test_typeFailure(Random r, _test_TypeVary newExpressionOfDifferentType, UnitManager unitManager) throws UserException, InternalException
    {
        if (r.nextBoolean())
        {
            return copy(newExpressionOfDifferentType.getNonNumericType(), rhs);
        }
        else
        {
            return copy(lhs, newExpressionOfDifferentType.getNonNumericType());
        }
    }
}
