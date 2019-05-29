package records.transformations.expression;

import annotation.qual.Value;
import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;
import records.data.datatype.DataTypeUtility;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.expression.NaryOpExpression.TypeProblemDetails;
import records.transformations.expression.visitor.ExpressionVisitor;
import records.typeExp.TypeExp;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
import utility.Utility;

import java.util.Optional;

/**
 * Created by neil on 30/11/2016.
 */
public class NotEqualExpression extends BinaryOpExpression
{
    // null means no pattern, true means left is pattern, false means right.
    private @Nullable Boolean patternIsLeft;
    
    public NotEqualExpression(@Recorded Expression lhs, @Recorded Expression rhs)
    {
        super(lhs, rhs);
    }

    @Override
    protected String saveOp()
    {
        return "<>";
    }

    @Override
    @RequiresNonNull({"lhsType", "rhsType"})
    public @Nullable CheckedExp checkBinaryOp(ColumnLookup data, TypeState state, ErrorAndTypeRecorder onError) throws UserException, InternalException
    {
        if (lhsType.expressionKind == ExpressionKind.PATTERN && rhsType.expressionKind == ExpressionKind.PATTERN)
        {
            onError.recordError(this, StyledString.s("Only one side of <> can be a pattern"));
            return null;
        }
        boolean oneIsPattern = lhsType.expressionKind == ExpressionKind.PATTERN || rhsType.expressionKind == ExpressionKind.PATTERN;
        if (oneIsPattern)
            patternIsLeft = Boolean.valueOf(lhsType.expressionKind == ExpressionKind.PATTERN); 
        // If one is pattern, only apply restrictions to the pattern side.  Otherwise if both expressions, apply to both:
        lhsType.requireEquatable(oneIsPattern);
        rhsType.requireEquatable(oneIsPattern);
        if (onError.recordError(this, TypeExp.unifyTypes(lhsType.typeExp, rhsType.typeExp)) == null)
        {
            ImmutableList<Optional<TypeExp>> expressionTypes = ImmutableList.<Optional<TypeExp>>of(Optional.<TypeExp>of(lhsType.typeExp), Optional.<TypeExp>of(rhsType.typeExp));
            onError.recordQuickFixes(lhs, ExpressionUtil.getFixesForMatchingNumericUnits(state, new TypeProblemDetails(expressionTypes, ImmutableList.of(lhs, rhs), 0)));
            onError.recordQuickFixes(rhs, ExpressionUtil.getFixesForMatchingNumericUnits(state, new TypeProblemDetails(expressionTypes, ImmutableList.of(lhs, rhs), 1)));
            return null;
        }
        return new CheckedExp(onError.recordTypeNN(this, TypeExp.bool(this)), state, ExpressionKind.EXPRESSION);
    }

    @Override
    @OnThread(Tag.Simulation)
    public Pair<@Value Object, ImmutableList<ValueResult>> getValueBinaryOp(EvaluateState state) throws UserException, InternalException
    {
        if (patternIsLeft != null)
        {
            boolean left = patternIsLeft;
            // Get value from the non-pattern:
            ValueResult val = (left ? rhs : lhs).calculateValue(state);
            // Match it against the pattern:
            ValueResult pattern = (left ? lhs : rhs).matchAsPattern(val.value, state);
            // Don't forget the not; we are not-equals:
            boolean result = ! Utility.cast(pattern.value, Boolean.class);
            // We are not-equals, so looking for failure,
            // and we don't affect the state:
            return new Pair<>(DataTypeUtility.value(result), ImmutableList.of(val, pattern));
        }
        else
        {
            ValueResult lhsVal = lhs.calculateValue(state);
            ValueResult rhsVal = rhs.calculateValue(state);
            return new Pair<>(DataTypeUtility.value(0 != Utility.compareValues(lhsVal.value, rhsVal.value)), ImmutableList.of(lhsVal, rhsVal));
        }
    }

    @Override
    public BinaryOpExpression copy(@Nullable @Recorded Expression replaceLHS, @Nullable @Recorded Expression replaceRHS)
    {
        return new NotEqualExpression(replaceLHS == null ? lhs : replaceLHS, replaceRHS == null ? rhs : replaceRHS);
    }

    @Override
    protected LocationInfo argLocationInfo()
    {
        return LocationInfo.UNIT_CONSTRAINED;
    }

    @Override
    public <T> T visit(ExpressionVisitor<T> visitor)
    {
        return visitor.notEqual(this, lhs, rhs);
    }
}
