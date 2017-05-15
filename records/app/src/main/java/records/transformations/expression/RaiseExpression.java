package records.transformations.expression;

import annotation.qual.Value;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;
import org.sosy_lab.common.rationals.Rational;
import org.sosy_lab.java_smt.api.Formula;
import org.sosy_lab.java_smt.api.FormulaManager;
import records.data.ColumnId;
import records.data.RecordSet;
import records.data.TableId;
import records.data.datatype.DataType;
import records.data.datatype.NumberInfo;
import records.data.datatype.DataTypeUtility;
import records.error.InternalException;
import records.error.UnimplementedException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
import utility.Utility;

import java.math.BigInteger;
import java.util.Map;
import java.util.Optional;

/**
 * Created by neil on 13/12/2016.
 */
public class RaiseExpression extends BinaryOpExpression
{
    public RaiseExpression(Expression lhs, Expression rhs)
    {
        super(lhs, rhs);
    }

    @Override
    protected String saveOp()
    {
        return "^";
    }

    @Override
    public BinaryOpExpression copy(@Nullable Expression replaceLHS, @Nullable Expression replaceRHS)
    {
        return new RaiseExpression(replaceLHS == null ? lhs : replaceLHS, replaceRHS == null ? rhs : replaceRHS);
    }

    @Override
    @RequiresNonNull({"lhsType", "rhsType"})
    protected @Nullable DataType checkBinaryOp(RecordSet data, TypeState state, ErrorRecorder onError) throws UserException, InternalException
    {
        final @NonNull DataType lhsTypeFinal = lhsType;
        if (lhsType.equals(DataType.NUMBER) && rhsType.equals(DataType.NUMBER))
            // Scalar both sides, no problem
            return lhsType;
        // Otherwise it's units on LHS or RHS.  Can't be RHS:
        if (!rhsType.equals(DataType.NUMBER))
        {
            onError.recordError(rhs, "Can't raise to a power which is non-numeric or has non-scalar unit");
            return null;
        }
        // RHS scalar, so must be LHS with units:
        if (!lhsType.isNumber())
        {
            onError.recordError(lhs, "Can't raise non-number to a power");
            return null;
        }
        Optional<Rational> rhsPower = rhs.constantFold();
        if (rhsPower.isPresent())
        {
            Rational r = rhsPower.get();
            boolean numeratorOne = r.getNum().equals(BigInteger.ONE);
            boolean denominatorOne = r.getDen().equals(BigInteger.ONE);
            if (!numeratorOne && !denominatorOne)
            {
                onError.recordError(this, "Cannot raise non-scalar number to a power other than an integer, or reciprocal of an integer");
                return null;
            }
            else if (numeratorOne && denominatorOne)
            {
                // Raising to power 1, leave as-is:
                return lhsType;
            }
            try
            {
                if (numeratorOne)
                {
                    return DataType.number(new NumberInfo(lhsTypeFinal.getNumberInfo().getUnit().rootedBy(r.getDen().intValueExact()), 0));
                }
                else
                {
                    return DataType.number(new NumberInfo(lhsTypeFinal.getNumberInfo().getUnit().raisedTo(r.getNum().intValueExact()), 0));
                }
            }
            catch (ArithmeticException e)
            {
                onError.recordError(rhs, "Power is too large to track the units");
                return null;
            }

        }
        else
        {
            onError.recordError(this, "Cannot raise non-scalar number to a non-constant power (units would be unknown)");
            return null;
        }

    }

    @Override
    public @OnThread(Tag.Simulation) @Value Object getValue(int rowIndex, EvaluateState state) throws UserException, InternalException
    {
        return DataTypeUtility.value(Utility.raiseNumber((Number)lhs.getValue(rowIndex, state), (Number) rhs.getValue(rowIndex, state)));
    }

    @Override
    public Formula toSolver(FormulaManager formulaManager, RecordSet src, Map<Pair<@Nullable TableId, ColumnId>, Formula> columnVariables) throws InternalException, UserException
    {
        throw new UnimplementedException();
    }
}
