package records.transformations.expression;

import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.ColumnId;

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
}
