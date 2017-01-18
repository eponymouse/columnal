package records.transformations.expression;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;
import records.data.ColumnId;
import records.data.RecordSet;
import records.data.datatype.DataType;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import utility.ExBiConsumer;
import utility.Pair;

import java.util.Random;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Created by neil on 25/11/2016.
 */
public abstract class BinaryOpExpression extends Expression
{
    /*
    public static enum Op
    {
        AND("&"), OR("|"), EQUALS("="), ADD("+"), SUBTRACT("-");

        private final String symbol;

        Op(String symbol)
        {
            this.symbol = symbol;
        }

        public static @Nullable Op parse(String text)
        {
            for (Op op : values())
                if (op.symbol.equals(text))
                    return op;
            return null;
        }
    }

    private final Op op;*/
    protected final Expression lhs;
    protected final Expression rhs;
    protected @Nullable DataType lhsType;
    protected @Nullable DataType rhsType;

    protected BinaryOpExpression(Expression lhs, Expression rhs)
    {
        this.lhs = lhs;
        this.rhs = rhs;
    }

    @Override
    public String save(boolean topLevel)
    {
        String inner = lhs.save(false) + " " + saveOp() + " " + rhs.save(false);
        if (topLevel)
            return inner;
        else
            return "(" + inner + ")";
    }

    protected abstract String saveOp();

    @Override
    public Stream<ColumnId> allColumnNames()
    {
        return Stream.concat(lhs.allColumnNames(), rhs.allColumnNames());
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        BinaryOpExpression that = (BinaryOpExpression) o;

        if (!saveOp().equals(that.saveOp())) return false;
        if (!lhs.equals(that.lhs)) return false;
        return rhs.equals(that.rhs);
    }

    @Override
    public int hashCode()
    {
        int result = lhs.hashCode();
        result = 31 * result + saveOp().hashCode();
        result = 31 * result + rhs.hashCode();
        return result;
    }
    
    public abstract BinaryOpExpression copy(@Nullable Expression replaceLHS, @Nullable Expression replaceRHS);

    @Override
    public final @Nullable DataType check(RecordSet data, TypeState state, ExBiConsumer<Expression, String> onError) throws UserException, InternalException
    {
        lhsType = lhs.check(data, state, onError);
        rhsType = rhs.check(data, state, onError);
        if (lhsType == null || rhsType == null)
            return null;
        return checkBinaryOp(data, state, onError);
    }

    @RequiresNonNull({"lhsType", "rhsType"})
    protected abstract @Nullable DataType checkBinaryOp(RecordSet data, TypeState state, ExBiConsumer<Expression, String> onError) throws UserException, InternalException;

    @Override
    public Stream<Pair<Expression, Function<Expression, Expression>>> _test_childMutationPoints()
    {
        return Stream.concat(
            lhs._test_allMutationPoints().map(p -> new Pair<>(p.getFirst(), newLHS -> copy(p.getSecond().apply(newLHS), rhs))),
            rhs._test_allMutationPoints().map(p -> new Pair<>(p.getFirst(), newRHS -> copy(lhs, p.getSecond().apply(newRHS))))
        );
    }

    @Override
    public Expression _test_typeFailure(Random r, _test_TypeVary newExpressionOfDifferentType, UnitManager unitManager) throws UserException, InternalException
    {
        // Most binary ops require same type, so this is typical (can always override):
        if (r.nextBoolean())
        {
            return copy(lhsType != null && lhsType.isNumber() ? newExpressionOfDifferentType.getNonNumericType() : newExpressionOfDifferentType.getDifferentType(rhsType), rhs);
        }
        else
        {
            return copy(lhs, rhsType != null && rhsType.isNumber() ? newExpressionOfDifferentType.getNonNumericType() : newExpressionOfDifferentType.getDifferentType(lhsType));
        }
    }

    public Expression getLHS()
    {
        return lhs;
    }

    public Expression getRHS()
    {
        return rhs;
    }
}
