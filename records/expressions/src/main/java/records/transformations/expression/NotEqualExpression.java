package records.transformations.expression;

import annotation.qual.Value;
import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;
import records.data.datatype.DataTypeUtility;
import records.error.InternalException;
import records.error.UserException;
import records.gui.expressioneditor.ExpressionEditorUtil;
import records.transformations.expression.NaryOpExpression.TypeProblemDetails;
import records.typeExp.NumTypeExp;
import records.typeExp.TypeExp;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Utility;

import java.util.Optional;

/**
 * Created by neil on 30/11/2016.
 */
public class NotEqualExpression extends BinaryOpExpression
{
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
    public @Nullable CheckedExp checkBinaryOp(TableLookup data, TypeState state, ErrorAndTypeRecorder onError) throws UserException, InternalException
    {
        if (lhsType.expressionKind == ExpressionKind.PATTERN && rhsType.expressionKind == ExpressionKind.PATTERN)
        {
            onError.recordError(this, StyledString.s("Only one side of <> can be a pattern"));
            return null;
        }
        boolean oneIsPattern = lhsType.expressionKind == ExpressionKind.PATTERN || rhsType.expressionKind == ExpressionKind.PATTERN;
        // If one is pattern, only apply restrictions to the pattern side.  Otherwise if both expressions, apply to both:
        lhsType.requireEquatable(oneIsPattern);
        rhsType.requireEquatable(oneIsPattern);
        if (onError.recordError(this, TypeExp.unifyTypes(lhsType.typeExp, rhsType.typeExp)) == null)
        {
            return null;
        }
        return new CheckedExp(TypeExp.bool(this), state, ExpressionKind.EXPRESSION);
    }

    @Override
    @OnThread(Tag.Simulation)
    public @Value Object getValueBinaryOp(EvaluateState state) throws UserException, InternalException
    {
        @Value Object lhsVal = lhs.getValue(state);
        @Value Object rhsVal = rhs.getValue(state);
        return DataTypeUtility.value(0 != Utility.compareValues(lhsVal, rhsVal));
    }

    @Override
    public BinaryOpExpression copy(@Nullable @Recorded Expression replaceLHS, @Nullable @Recorded Expression replaceRHS)
    {
        return new NotEqualExpression(replaceLHS == null ? lhs : replaceLHS, replaceRHS == null ? rhs : replaceRHS);
    }

}
