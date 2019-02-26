package records.transformations.expression;

import annotation.qual.Value;
import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;
import records.data.datatype.DataTypeUtility;
import records.error.InternalException;
import records.error.UserException;
import records.gui.expressioneditor.GeneralExpressionEntry.Op;
import records.transformations.expression.explanation.Explanation;
import records.transformations.expression.explanation.ExplanationLocation;
import records.typeExp.NumTypeExp;
import records.typeExp.TypeExp;
import records.typeExp.units.MutUnitVar;
import records.typeExp.units.UnitExp;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
import utility.Utility;
import utility.Utility.EpsilonType;

import java.util.Set;
import java.util.function.Function;

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
    public Pair<@Value Object, EvaluateState> getValueBinaryOp(EvaluateState state) throws UserException, InternalException
    {
        throw new InternalException("Calling getValue on plus minus pattern (should only call matchAsPattern)");
    }

    @Override
    public @Nullable EvaluateState matchAsPattern(@Value Object value, EvaluateState state) throws InternalException, UserException
    {
        @Value Object lhsValue = lhs.getValue(state).getFirst();
        @Value Object rhsValue = rhs.getValue(state).getFirst();
        boolean match = Utility.compareNumbers(value, lhsValue, new Pair<>(EpsilonType.ABSOLUTE, Utility.toBigDecimal(Utility.cast(rhsValue, Number.class)))) == 0;
        if (state.recordExplanation())
        {
            explanation = new Explanation(this, state, null, ImmutableList.of())
            {
                @Override
                public @OnThread(Tag.Simulation) StyledString describe(Set<Explanation> alreadyDescribed, Function<ExplanationLocation, StyledString> hyperlinkLocation) throws InternalException, UserException
                {
                    return StyledString.concat(PlusMinusPatternExpression.this.toStyledString(), StyledString.s(" was "), StyledString.s(DataTypeUtility.valueToString(state.getTypeFor(lhs), lhsValue, null)), StyledString.s(" " + saveOp() + " "), StyledString.s(DataTypeUtility.valueToString(state.getTypeFor(rhs), rhsValue, null)));
                }

                @Override
                public @OnThread(Tag.Simulation) ImmutableList<Explanation> getDirectSubExplanations() throws InternalException
                {
                    return ImmutableList.of(lhs.getExplanation(), rhs.getExplanation());
                }
            };
        }
        return match ? state : null;
    }

    @Override
    protected LocationInfo argLocationInfo()
    {
        return LocationInfo.UNIT_CONSTRAINED;
    }
}
