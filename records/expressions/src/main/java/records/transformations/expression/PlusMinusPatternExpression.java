package records.transformations.expression;

import annotation.qual.Value;
import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;
import records.data.datatype.DataTypeUtility;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.expression.explanation.Explanation.ExecutionType;
import records.transformations.expression.visitor.ExpressionVisitor;
import records.typeExp.NumTypeExp;
import records.typeExp.TypeExp;
import records.typeExp.units.MutUnitVar;
import records.typeExp.units.UnitExp;
import styled.StyledString;
import utility.Pair;
import utility.Utility;
import utility.Utility.EpsilonType;

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
        return "\u00B1";
    }

    @Override
    public BinaryOpExpression copy(@Nullable @Recorded Expression replaceLHS, @Nullable @Recorded Expression replaceRHS)
    {
        return new PlusMinusPatternExpression(replaceLHS == null ? lhs : replaceLHS, replaceRHS == null ? rhs : replaceRHS);
    }

    @Override
    @RequiresNonNull({"lhsType", "rhsType"})
    protected @Nullable CheckedExp checkBinaryOp(ColumnLookup data, TypeState state, ErrorAndTypeRecorder onError) throws UserException, InternalException
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
    public Pair<@Value Object, ImmutableList<ValueResult>> getValueBinaryOp(EvaluateState state) throws UserException, InternalException
    {
        throw new InternalException("Calling getValue on plus minus pattern (should only call matchAsPattern)");
    }

    @Override
    public ValueResult matchAsPattern(@Value Object value, EvaluateState state) throws InternalException, UserException
    {
        ValueResult lhsOutcome = lhs.calculateValue(state);
        @Value Object lhsValue = lhsOutcome.value;
        ValueResult rhsOutcome = rhs.calculateValue(state);
        @Value Object rhsValue = rhsOutcome.value;
        boolean match = Utility.compareNumbers(Utility.cast(value, Number.class), Utility.cast(lhsValue, Number.class), new Pair<>(EpsilonType.ABSOLUTE, Utility.toBigDecimal(Utility.cast(rhsValue, Number.class)))) == 0;
        return explanation(DataTypeUtility.value(match), ExecutionType.MATCH, state, ImmutableList.of(lhsOutcome, rhsOutcome), ImmutableList.of(), false);
    }

    @Override
    protected LocationInfo argLocationInfo()
    {
        return LocationInfo.UNIT_CONSTRAINED;
    }

    @Override
    public <T> T visit(ExpressionVisitor<T> visitor)
    {
        return visitor.plusMinus(this, lhs, rhs);
    }
}
