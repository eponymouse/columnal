package records.transformations.expression;

import annotation.qual.Value;
import annotation.recorded.qual.Recorded;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
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
import records.types.TypeExp;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;

import java.util.Map;

/**
 * An expression of the form "value ~ pattern", which is of boolean type:
 * returns true if the LHS value matches the RHS pattern, false if not.
 * Basically, "V ~ P" is a shorthand for "@match V @case P @then true @case @any @then false"
 * There is no support for multiple patterns, or for guards (just use a full @match).
 * Special values like any are allowed, but not variables as they are not meaningful
 * (they cannot escape the match to be used outside it; again, use full @match).
 */
public class MatchesOneExpression extends BinaryOpExpression
{
    public MatchesOneExpression(@Recorded Expression lhs, @Recorded Expression rhs)
    {
        super(lhs, rhs);
    }

    @Override
    protected String saveOp()
    {
        return "~";
    }

    @Override
    public BinaryOpExpression copy(@Nullable @Recorded Expression replaceLHS, @Nullable @Recorded Expression replaceRHS)
    {
        return new MatchesOneExpression(replaceLHS == null ? lhs : replaceLHS, replaceRHS == null ? rhs : replaceRHS);
    }

    // Must use checkAsPattern on RHS, not check:
    @Override
    public @Nullable TypeExp check(TableLookup dataLookup, TypeState typeState, ErrorAndTypeRecorder onError) throws UserException, InternalException
    {
        lhsType = lhs.check(dataLookup, typeState, onError);
        if (lhsType == null)
            return null;
        @NonNull TypeExp lhsFinal = lhsType;
        @Nullable Pair<@Recorded TypeExp, TypeState> rhsPatType = rhs.checkAsPattern(false, dataLookup, typeState, onError);
        if (rhsPatType == null)
            return null;
        // We can just discard the RHS type state because it can't introduce any new variables
        rhsType = rhsPatType.getFirst();
        
        if (onError.recordError(this, TypeExp.unifyTypes(lhsFinal, rhsType)) != null)
            return onError.recordType(this, checkBinaryOp(dataLookup, typeState, onError));
        else
            return null;
    }

    @Override
    protected @Nullable TypeExp checkBinaryOp(TableLookup data, TypeState typeState, ErrorAndTypeRecorder onError) throws UserException, InternalException
    {
        // If we get this far, the RHS pattern must have matched the LHS expression
        // So we just return our type, which is boolean:
        return TypeExp.fromConcrete(this, DataType.BOOLEAN);
    }

    @Override
    public @OnThread(Tag.Simulation) @Value Object getValue(int rowIndex, EvaluateState state) throws UserException, InternalException
    {
        return DataTypeUtility.value(rhs.matchAsPattern(rowIndex, lhs.getValue(rowIndex, state), state) != null);
    }

    @Override
    public Formula toSolver(FormulaManager formulaManager, RecordSet src, Map<Pair<@Nullable TableId, ColumnId>, Formula> columnVariables) throws InternalException, UserException
    {
        throw new UnimplementedException();
    }
}
