package records.transformations.expression;

import annotation.qual.Value;
import annotation.recorded.qual.Recorded;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;
import records.error.InternalException;
import records.error.UserException;
import records.gui.expressioneditor.GeneralExpressionEntry.Op;
import records.typeExp.NumTypeExp;
import records.typeExp.TypeExp;
import records.typeExp.units.MutUnitVar;
import records.typeExp.units.UnitExp;
import styled.StyledString;
import utility.Pair;
import utility.Utility;

/**
 * This is a pattern match item, value +- tolerance.
 */
public class PlusMinusPatternExpression extends BinaryOpExpression
{
    public PlusMinusPatternExpression(@Recorded Expression lhs, @Recorded Expression rhs)
    {
        super(lhs, rhs);
    }

    @Override
    protected String saveOp()
    {
        return "+-";
    }

    @Override
    protected Op loadOp()
    {
        return Op.PLUS_MINUS;
    }

    @Override
    public BinaryOpExpression copy(@Nullable @Recorded Expression replaceLHS, @Nullable @Recorded Expression replaceRHS)
    {
        return new PlusMinusPatternExpression(replaceLHS == null ? lhs : replaceLHS, replaceRHS == null ? rhs : replaceRHS);
    }

    @Override
    @RequiresNonNull({"lhsType", "rhsType"})
    protected @Nullable CheckedExp checkBinaryOp(TableLookup data, TypeState state, ErrorAndTypeRecorder onError) throws UserException, InternalException
    {
        // The LHS and RHS must be numbers with matching units.  The result is then that same number
        // LHS needs to be specific number value,
        // a variable declaration would not be valid (how do you match 5.6 against defvar x +- 0.1 ?  Either match
        // just against var x, or give value.  That would also allow weird nestings like (1 +- 0.1) +- 0.2), and exact same idea for RHS.
        
        if (lhsType.expressionKind == ExpressionKind.PATTERN || rhsType.expressionKind == ExpressionKind.PATTERN)
        {
            onError.recordError(this, StyledString.s("Patterns are not valid around "));
            return null;
        }
        
        return onError.recordTypeAndError(this, TypeExp.unifyTypes(new NumTypeExp(this, new UnitExp(new MutUnitVar())), lhsType.typeExp, rhsType.typeExp), ExpressionKind.PATTERN, state);
    }

    @Override
    public Pair<@Value Object, EvaluateState> getValueBinaryOp(EvaluateState state) throws UserException, InternalException
    {
        throw new InternalException("Calling getValue on plus minus pattern (should only call matchAsPattern)");
    }

    @Override
    public @Nullable EvaluateState matchAsPattern(@Value Object value, EvaluateState state) throws InternalException, UserException
    {
        boolean match = Utility.compareNumbers(value, lhs.getValue(state).getFirst(), Utility.toBigDecimal(Utility.cast(rhs.getValue(state).getFirst(), Number.class))) == 0;
        return match ? state : null;
    }

    @Override
    protected LocationInfo argLocationInfo()
    {
        return LocationInfo.UNIT_CONSTRAINED;
    }
}
