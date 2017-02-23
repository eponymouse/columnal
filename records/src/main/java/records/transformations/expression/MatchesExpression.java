package records.transformations.expression;

import annotation.qual.Value;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.sosy_lab.java_smt.api.Formula;
import org.sosy_lab.java_smt.api.FormulaManager;
import records.data.ColumnId;
import records.data.RecordSet;
import records.data.TableId;
import records.data.datatype.DataType;
import records.error.InternalException;
import records.error.UnimplementedException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.ExBiConsumer;
import utility.Pair;
import utility.Utility;

import java.util.Map;

/**
 * An expression of the form "value ~ pattern", which is of boolean type:
 * returns true if the LHS value matches the RHS pattern, false if not.
 * Basically, "V ~ P" is a shorthand for "@match V @case P @then true @case @any @then false"
 * There is no support for multiple patterns, or for guards (just use a full @match).
 * Special values like any are allowed, but not variables as they are not meaningful
 * (they cannot escape the match to be used outside it; again, use full @match).
 */
public class MatchesExpression extends BinaryOpExpression
{
    protected MatchesExpression(Expression lhs, Expression rhs)
    {
        super(lhs, rhs);
    }

    @Override
    protected String saveOp()
    {
        return "~";
    }

    @Override
    public BinaryOpExpression copy(@Nullable Expression replaceLHS, @Nullable Expression replaceRHS)
    {
        return new MatchesExpression(replaceLHS == null ? lhs : replaceLHS, replaceRHS == null ? rhs : replaceRHS);
    }

    // Must use checkAsPattern on RHS, not check:
    @Override
    public @Nullable DataType check(RecordSet data, TypeState state, ExBiConsumer<Expression, String> onError) throws UserException, InternalException
    {
        lhsType = lhs.check(data, state, onError);
        if (lhsType == null)
            return null;
        @Nullable Pair<DataType, TypeState> rhsPatType = rhs.checkAsPattern(false, lhsType, data, state, onError);
        if (rhsPatType == null)
            return null;
        // We can just discard the RHS type state because it can't introduce any new variables
        rhsType = rhsPatType.getFirst();
        return checkBinaryOp(data, state, onError);
    }

    @Override
    protected @Nullable DataType checkBinaryOp(RecordSet data, TypeState state, ExBiConsumer<Expression, String> onError) throws UserException, InternalException
    {
        // If we get this far, the RHS pattern must have matched the LHS expression
        // So we just return our type, which is boolean:
        return DataType.BOOLEAN;
    }

    @Override
    public @OnThread(Tag.Simulation) @Value Object getValue(int rowIndex, EvaluateState state) throws UserException, InternalException
    {
        return Utility.value(rhs.matchAsPattern(rowIndex, lhs.getValue(rowIndex, state), state) != null);
    }

    @Override
    public Formula toSolver(FormulaManager formulaManager, RecordSet src, Map<Pair<@Nullable TableId, ColumnId>, Formula> columnVariables) throws InternalException, UserException
    {
        throw new UnimplementedException();
    }
}
