package records.transformations.expression;

import records.data.ColumnId;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.util.List;
import java.util.stream.Stream;

/**
 * Created by neil on 29/11/2016.
 */
public abstract class NaryOpExpression extends Expression
{
    private final List<Expression> expressions;

    public NaryOpExpression(List<Expression> expressions)
    {
        this.expressions = expressions;
    }

    @Override
    public Stream<ColumnId> allColumnNames()
    {
        return expressions.stream().flatMap(Expression::allColumnNames);
    }

    @Override
    public String save()
    {
        StringBuilder s = new StringBuilder("(");
        for (int i = 0; i < expressions.size(); i++)
        {
            if (i > 0)
                s.append(" ").append(saveOp(i - 1)).append(" ");
            s.append(expressions.get(i).save());
        }
        s.append(")");
        return s.toString();
    }

    protected abstract String saveOp(int index);
}
