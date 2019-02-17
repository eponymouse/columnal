package records.transformations.expression;

import annotation.qual.Value;
import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;
import records.data.datatype.DataTypeUtility;
import records.error.InternalException;
import records.error.UserException;
import records.gui.expressioneditor.ExpressionEditorUtil;
import records.gui.expressioneditor.GeneralExpressionEntry.Op;
import records.transformations.expression.NaryOpExpression.TypeProblemDetails;
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
    protected Op loadOp()
    {
        return Op.NOT_EQUAL;
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
            onError.recordQuickFixes(lhs, ExpressionEditorUtil.getFixesForMatchingNumericUnits(state, new TypeProblemDetails(expressionTypes, ImmutableList.of(lhs, rhs), 0)));
            onError.recordQuickFixes(rhs, ExpressionEditorUtil.getFixesForMatchingNumericUnits(state, new TypeProblemDetails(expressionTypes, ImmutableList.of(lhs, rhs), 1)));
            return null;
        }
        return new CheckedExp(onError.recordTypeNN(this, TypeExp.bool(this)), state, ExpressionKind.EXPRESSION);
    }

    @Override
    @OnThread(Tag.Simulation)
    public Pair<@Value Object, EvaluateState> getValueBinaryOp(EvaluateState state) throws UserException, InternalException
    {
        if (patternIsLeft != null)
        {
            boolean left = patternIsLeft;
            // Get value from the non-pattern:
            @Value Object val = left ? rhs.getValue(state).getFirst() : lhs.getValue(state).getFirst();
            @Nullable EvaluateState result;
            if (left)
                result = lhs.matchAsPattern(val, state);
            else
                result = rhs.matchAsPattern(val, state);
            // We are not-equals, so looking for failure,
            // and we don't affect the state:
            return new Pair<>(DataTypeUtility.value(result == null), state);
        }
        else
        {
            @Value Object lhsVal = lhs.getValue(state).getFirst();
            @Value Object rhsVal = rhs.getValue(state).getFirst();
            return new Pair<>(DataTypeUtility.value(0 != Utility.compareValues(lhsVal, rhsVal)), state);
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
}
