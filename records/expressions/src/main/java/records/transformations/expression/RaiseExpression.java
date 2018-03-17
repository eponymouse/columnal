package records.transformations.expression;

import annotation.qual.Value;
import annotation.recorded.qual.Recorded;
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
import records.data.datatype.DataTypeUtility;
import records.error.InternalException;
import records.error.UnimplementedException;
import records.error.UserException;
import records.types.NumTypeExp;
import records.types.TypeExp;
import records.types.units.MutUnitVar;
import records.types.units.UnitExp;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
import utility.Utility;

import java.math.BigInteger;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

/**
 * Created by neil on 13/12/2016.
 */
public class RaiseExpression extends BinaryOpExpression
{
    public RaiseExpression(@Recorded Expression lhs, @Recorded Expression rhs)
    {
        super(lhs, rhs);
    }

    @Override
    protected String saveOp()
    {
        return "^";
    }

    @Override
    public BinaryOpExpression copy(@Nullable @Recorded Expression replaceLHS, @Nullable @Recorded Expression replaceRHS)
    {
        return new RaiseExpression(replaceLHS == null ? lhs : replaceLHS, replaceRHS == null ? rhs : replaceRHS);
    }

    @Override
    @RequiresNonNull({"lhsType", "rhsType"})
    protected @Nullable TypeExp checkBinaryOp(TableLookup data, TypeState typeState, ErrorAndTypeRecorder onError) throws UserException, InternalException
    {
        final @NonNull TypeExp lhsTypeFinal = lhsType;
        final @NonNull TypeExp rhsTypeFinal = rhsType;
        
        // Raise expression is sort of an overloaded operator.  If the right-hand side is an integer
        // constant, it adjusts the units on the left-hand side.  Otherwise, it requires unit-less
        // items on both sides.
        
        // So, we attempt to constant-fold the RHS to distinguish:
        Optional<Rational> rhsPower = rhs.constantFold();
        if (rhsPower.isPresent())
        {
            Rational r = rhsPower.get();
            boolean numeratorOne = r.getNum().equals(BigInteger.ONE);
            boolean denominatorOne = r.getDen().equals(BigInteger.ONE);
            if (numeratorOne && denominatorOne)
            {
                // Raising to power 1, just leave type as-is:
                return lhsType;
            }
            else if (numeratorOne || denominatorOne)
            {
                // Either raising to an integer power, or rooting:
                try
                {
                    if (numeratorOne)
                    {
                        // Rooting by integer power:
                        
                        // We raise LHS units to the opposite:
                        MutUnitVar lhsUnit = new MutUnitVar();
                        if (onError.recordError(this, TypeExp.unifyTypes(lhsTypeFinal, new NumTypeExp(this, new UnitExp(lhsUnit).raisedTo(r.getDen().intValueExact())))) == null)
                            return null;
                        return new NumTypeExp(this, new UnitExp(lhsUnit));
                    }
                    else
                    {
                        // Raising to integer power:
                        MutUnitVar lhsUnit = new MutUnitVar();
                        if (onError.recordError(this, TypeExp.unifyTypes(lhsTypeFinal, new NumTypeExp(this, new UnitExp(lhsUnit)))) == null)
                            return null;
                        return new NumTypeExp(this, new UnitExp(lhsUnit).raisedTo(r.getNum().intValueExact()));
                    }
                }
                catch (ArithmeticException e)
                {
                    onError.recordError(rhs, StyledString.s("Power is too large to track the units"));
                    return null;
                }
            }
            // If power is not 1, integer, or 1/integer, fall through...
        }
        
        if (onError.recordError(this, TypeExp.unifyTypes(TypeExp.fromConcrete(this, DataType.NUMBER), lhsTypeFinal)) == null)
            return null;
        if (onError.recordError(this, TypeExp.unifyTypes(TypeExp.fromConcrete(this, DataType.NUMBER), rhsTypeFinal)) == null)
            return null;
        return TypeExp.fromConcrete(this, DataType.NUMBER);    }

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
