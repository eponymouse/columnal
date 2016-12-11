package records.transformations.expression;

import edu.emory.mathcs.backport.java.util.Collections;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.ColumnId;
import records.error.UserException;
import utility.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Created by neil on 29/11/2016.
 */
public abstract class NaryOpExpression extends Expression
{
    protected final List<Expression> expressions;

    public NaryOpExpression(List<Expression> expressions)
    {
        this.expressions = expressions;
    }

    @Override
    public Stream<ColumnId> allColumnNames()
    {
        return expressions.stream().flatMap(Expression::allColumnNames);
    }

    // Will be same length as expressions, if null use existing
    public final NaryOpExpression copy(List<@Nullable Expression> replacements)
    {
        List<Expression> newExps = new ArrayList<>();
        for (int i = 0; i < expressions.size(); i++)
        {
            @Nullable Expression newExp = replacements.get(i);
            newExps.add(newExp != null ? newExp : expressions.get(i));
        }
        return copyNoNull(newExps);
    }

    public abstract NaryOpExpression copyNoNull(List<Expression> replacements);

    @Override
    public String save(boolean topLevel)
    {
        StringBuilder s = new StringBuilder(topLevel ? "" : "(");
        for (int i = 0; i < expressions.size(); i++)
        {
            if (i > 0)
                s.append(" ").append(saveOp(i - 1)).append(" ");
            s.append(expressions.get(i).save(false));
        }
        if (!topLevel)
            s.append(")");
        return s.toString();
    }

    protected abstract String saveOp(int index);

    @Override
    public Stream<Pair<Expression, Function<Expression, Expression>>> _test_childMutationPoints()
    {
        return IntStream.range(0, expressions.size()).mapToObj(i ->
            expressions.get(i)._test_allMutationPoints().map(p -> p.<Function<Expression, Expression>>replaceSecond(newExp -> copy(makeNullList(i, newExp))))).flatMap(s -> s);
    }

    protected List<@Nullable Expression> makeNullList(int index, Expression newExp)
    {
        ArrayList<@Nullable Expression> r = new ArrayList<>();
        for (int i = 0; i < expressions.size(); i++)
        {
            r.add(i == index ? newExp : null);
        }
        return r;
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        NaryOpExpression that = (NaryOpExpression) o;

        return expressions.equals(that.expressions);
    }

    @Override
    public int hashCode()
    {
        return expressions.hashCode();
    }

    public List<Expression> getChildren()
    {
        return Collections.unmodifiableList(expressions);
    }
}
