package records.transformations.expression;

import annotation.qual.Value;
import annotation.recorded.qual.Recorded;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.sosy_lab.java_smt.api.Formula;
import org.sosy_lab.java_smt.api.FormulaManager;
import records.data.ColumnId;
import records.data.RecordSet;
import records.data.TableId;
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

import java.util.Collections;
import java.util.Map;

/**
 * This is a pattern match item, value +- tolerance.
 */
public class PlusMinusPatternExpression extends BinaryOpExpression
{
    protected PlusMinusPatternExpression(Expression lhs, Expression rhs)
    {
        super(lhs, rhs);
    }

    @Override
    protected String saveOp()
    {
        return "+-";
    }

    @Override
    public BinaryOpExpression copy(@Nullable Expression replaceLHS, @Nullable Expression replaceRHS)
    {
        return new PlusMinusPatternExpression(replaceLHS == null ? lhs : replaceLHS, replaceRHS == null ? rhs : replaceRHS);
    }

    @Override
    protected @Nullable TypeExp checkBinaryOp(RecordSet data, TypeState state, ErrorAndTypeRecorder onError) throws UserException, InternalException
    {
        // If normal check is called, something has gone wrong because we are only
        // valid in a pattern
        onError.recordError(this, StyledString.s("Plus-minus cannot be declared outside pattern match"), Collections.emptyList());
        return null;
    }

    @Override
    public @OnThread(Tag.Simulation) @Value Object getValue(int rowIndex, EvaluateState state) throws UserException, InternalException
    {
        throw new InternalException("Calling getValue on plus minus pattern (should only call matchAsPattern)");
    }

    @Override
    public @Nullable Pair<@Recorded TypeExp, TypeState> checkAsPattern(boolean varAllowed, RecordSet data, TypeState state, ErrorAndTypeRecorder onError) throws UserException, InternalException
    {
        // The LHS and RHS must be numbers with matching units.  The result is then that same number
        
        // We don't do checkAsPattern on LHS or RHS.  LHS needs to be specific number value,
        // a variable declaration would not be valid (how do you match 5.6 against defvar x +- 0.1 ?  Either match
        // just against var x, or give value.  That would also allow weird nestings like (1 +- 0.1) +- 0.2), and exact same idea for RHS.
        lhsType = lhs.check(data, state, onError);
        rhsType = rhs.check(data, state, onError);

        if (lhsType == null || rhsType == null)
            return null;

        @Recorded TypeExp combined = onError.recordTypeAndError(this, TypeExp.unifyTypes(new NumTypeExp(this, new UnitExp(new MutUnitVar())), lhsType, rhsType));
        if (combined == null)
            return null;
        else
            return new Pair<>(combined, state);
    }

    @Override
    public @OnThread(Tag.Simulation) @Nullable EvaluateState matchAsPattern(int rowIndex, @Value Object value, EvaluateState state) throws InternalException, UserException
    {
        boolean match = Utility.compareNumbers(value, lhs.getValue(rowIndex, state), Utility.toBigDecimal((Number)rhs.getValue(rowIndex, state))) == 0;
        return match ? state : null;
    }

    @Override
    public Formula toSolver(FormulaManager formulaManager, RecordSet src, Map<Pair<@Nullable TableId, ColumnId>, Formula> columnVariables) throws InternalException, UserException
    {
        throw new UnimplementedException();
    }
}
