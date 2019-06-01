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
        if (lhsType.expressionKind == ExpressionKind.PATTERN || rhsType.expressionKind == ExpressionKind.PATTERN)
        {
            // TODO add this as a quick fix
            onError.recordError(this, StyledString.s("Patterns not allowed in <>  Use not(... =~ ...) instead"));
            return null;
        }
        lhsType.requireEquatable();
        rhsType.requireEquatable();
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
        ValueResult lhsVal = lhs.calculateValue(state);
        ValueResult rhsVal = rhs.calculateValue(state);
        return new Pair<>(DataTypeUtility.value(0 != Utility.compareValues(lhsVal.value, rhsVal.value)), ImmutableList.of(lhsVal, rhsVal));
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
