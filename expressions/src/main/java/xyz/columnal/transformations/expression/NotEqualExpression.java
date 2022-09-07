package xyz.columnal.transformations.expression;

import annotation.qual.Value;
import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;
import xyz.columnal.data.datatype.DataTypeUtility;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.transformations.expression.NaryOpExpression.TypeProblemDetails;
import xyz.columnal.transformations.expression.visitor.ExpressionVisitor;
import xyz.columnal.typeExp.TypeExp;
import xyz.columnal.styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.Pair;
import xyz.columnal.utility.Utility;

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
    public @Nullable CheckedExp checkBinaryOp(@Recorded NotEqualExpression this, ColumnLookup data, TypeState state, ExpressionKind kind, ErrorAndTypeRecorder onError) throws UserException, InternalException
    {
        // TODO add this as a quick fix
        //onError.recordError(this, StyledString.s("Patterns not allowed in <>  Use not(... =~ ...) instead"));
        lhsType.requireEquatable();
        rhsType.requireEquatable();
        if (onError.recordError(this, TypeExp.unifyTypes(lhsType.typeExp, rhsType.typeExp)) == null)
        {
            ImmutableList<Optional<TypeExp>> expressionTypes = ImmutableList.<Optional<TypeExp>>of(Optional.<TypeExp>of(lhsType.typeExp), Optional.<TypeExp>of(rhsType.typeExp));
            onError.recordQuickFixes(lhs, ExpressionUtil.getFixesForMatchingNumericUnits(state, new TypeProblemDetails(expressionTypes, ImmutableList.of(lhs, rhs), 0)));
            onError.recordQuickFixes(rhs, ExpressionUtil.getFixesForMatchingNumericUnits(state, new TypeProblemDetails(expressionTypes, ImmutableList.of(lhs, rhs), 1)));
            return null;
        }
        return new CheckedExp(onError.recordTypeNN(this, TypeExp.bool(this)), state);
    }

    @Override
    protected Pair<ExpressionKind, ExpressionKind> getOperandKinds()
    {
        // We could offer a <>~ operator -- but we don't at the moment:
        return new Pair<>(ExpressionKind.EXPRESSION, ExpressionKind.EXPRESSION);
    }

    @Override
    @OnThread(Tag.Simulation)
    public @Value Object getValueBinaryOp(ValueResult lhsValue, ValueResult rhsValue) throws UserException, InternalException
    {
        return DataTypeUtility.value(0 != Utility.compareValues(lhsValue.value, rhsValue.value));
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
