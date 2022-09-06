package records.transformations.expression;

import annotation.qual.Value;
import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;
import org.sosy_lab.common.rationals.Rational;
import records.data.datatype.DataTypeUtility;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.expression.visitor.ExpressionVisitor;
import records.typeExp.NumTypeExp;
import records.typeExp.TypeExp;
import records.typeExp.units.MutUnitVar;
import records.typeExp.units.UnitExp;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
import utility.Utility;

import java.math.BigInteger;
import java.util.Optional;

/**
 * Created by neil on 13/12/2016.
 */
public class RaiseExpression extends BinaryOpExpression
{
    public RaiseExpression(@Recorded Expression lhs, @Recorded Expression rhs)
    {
        super(lhs, rhs);
    }

    @Override
    protected String saveOp()
    {
        return "^";
    }

    @Override
    public BinaryOpExpression copy(@Nullable @Recorded Expression replaceLHS, @Nullable @Recorded Expression replaceRHS)
    {
        return new RaiseExpression(replaceLHS == null ? lhs : replaceLHS, replaceRHS == null ? rhs : replaceRHS);
    }

    @Override
    @RequiresNonNull({"lhsType", "rhsType"})
    protected @Nullable CheckedExp checkBinaryOp(@Recorded RaiseExpression this, ColumnLookup data, TypeState typeState, ExpressionKind kind, ErrorAndTypeRecorder onError) throws UserException, InternalException
    {
        final @NonNull @Recorded TypeExp lhsTypeFinal = lhsType.typeExp;
        final @NonNull TypeExp rhsTypeFinal = rhsType.typeExp;
        
        // Raise expression is sort of an overloaded operator.  If the right-hand side is an integer
        // constant, it adjusts the units on the left-hand side.  Otherwise, it requires unit-less
        // items on both sides.
        
        // So, we attempt to constant-fold the RHS to distinguish:
        Optional<Rational> rhsPower = rhs.constantFold();
        if (rhsPower.isPresent())
        {
            Rational r = rhsPower.get();
            boolean numeratorOne = r.getNum().equals(BigInteger.ONE);
            boolean denominatorOne = r.getDen().equals(BigInteger.ONE);
            if (numeratorOne && denominatorOne)
            {
                // Raising to power 1, just leave type as-is:
                return new CheckedExp(lhsTypeFinal, typeState);
            }
            else if (numeratorOne || denominatorOne)
            {
                final TypeExp ourType;
                // Either raising to an integer power, or rooting:
                try
                {
                    if (numeratorOne)
                    {
                        // Rooting by integer power:
                        
                        // We raise LHS units to the opposite:
                        MutUnitVar lhsUnit = new MutUnitVar();
                        if (onError.recordError(this, TypeExp.unifyTypes(lhsTypeFinal, new NumTypeExp(this, new UnitExp(lhsUnit).raisedTo(r.getDen().intValueExact())))) == null)
                            return null;
                        ourType = new NumTypeExp(this, new UnitExp(lhsUnit));
                    }
                    else
                    {
                        // Raising to integer power:
                        MutUnitVar lhsUnit = new MutUnitVar();
                        if (onError.recordError(this, TypeExp.unifyTypes(lhsTypeFinal, new NumTypeExp(this, new UnitExp(lhsUnit)))) == null)
                            return null;
                        ourType = new NumTypeExp(this, new UnitExp(lhsUnit).raisedTo(r.getNum().intValueExact()));
                    }
                    return new CheckedExp(onError.recordTypeNN(this, ourType), typeState);
                }
                catch (ArithmeticException e)
                {
                    onError.recordError(rhs, StyledString.s("Power is too large to track the units"));
                    return null;
                }
            }
            
            // If power is not 1, integer, or 1/integer, fall through:
        }
        
        if (onError.recordError(this, TypeExp.unifyTypes(TypeExp.plainNumber(this), lhsTypeFinal)) == null)
            return null;
        if (onError.recordError(this, TypeExp.unifyTypes(TypeExp.plainNumber(this), rhsTypeFinal)) == null)
            return null;
        return new CheckedExp(onError.recordTypeNN(this, TypeExp.plainNumber(this)), typeState);
    }

    @Override
    protected Pair<ExpressionKind, ExpressionKind> getOperandKinds()
    {
        return new Pair<>(ExpressionKind.EXPRESSION, ExpressionKind.EXPRESSION);
    }

    @Override
    public @Value Object getValueBinaryOp(ValueResult lhsValue, ValueResult rhsValue) throws UserException, InternalException
    {
        return Utility.raiseNumber(Utility.cast(lhsValue.value, Number.class), Utility.cast(rhsValue.value, Number.class));
    }

    @Override
    public <T> T visit(ExpressionVisitor<T> visitor)
    {
        return visitor.raise(this, lhs, rhs);
    }
}
