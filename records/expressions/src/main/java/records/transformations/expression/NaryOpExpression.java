package records.transformations.expression;

import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.ColumnId;
import records.data.RecordSet;
import records.data.datatype.DataType;
import records.error.InternalException;
import records.error.UserException;
import records.gui.expressioneditor.BracketedExpression;
import records.gui.expressioneditor.ConsecutiveBase;
import records.gui.expressioneditor.ExpressionNodeParent;
import records.gui.expressioneditor.OperandNode;
import records.gui.expressioneditor.OperatorEntry;
import records.types.TypeExp;
import utility.Pair;
import utility.Utility;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
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
        s.append(getSpecialPrefix());
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

    // Can be overridden by child classes to insert prefix before expression
    protected String getSpecialPrefix()
    {
        return "";
    }

    protected abstract String saveOp(int index);

    @Override
    public Stream<Pair<Expression, Function<Expression, Expression>>> _test_childMutationPoints()
    {
        return IntStream.range(0, expressions.size()).mapToObj(i ->
            expressions.get(i)._test_allMutationPoints().map(p -> new Pair<Expression, Function<Expression, Expression>>(p.getFirst(), (Expression exp) -> copy(makeNullList(i, p.getSecond().apply(exp)))))).flatMap(s -> s);
    }

    protected List<@Nullable Expression> makeNullList(int index, Expression newExp)
    {
        if (index < 0 || index >= expressions.size())
            throw new RuntimeException("makeNullList invalid " + index + " compared to " + expressions.size());
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

        if (!getOpList().equals(that.getOpList())) return false;
        return expressions.equals(that.expressions);
    }

    @Override
    public int hashCode()
    {
        return expressions.hashCode() + 31 * getOpList().hashCode();
    }

    public List<Expression> getChildren()
    {
        return Collections.unmodifiableList(expressions);
    }

    protected @Nullable TypeExp checkAllOperandsSameType(RecordSet data, TypeState state, ErrorRecorder onError) throws UserException, InternalException
    {
        List<TypeExp> types = new ArrayList<>();
        for (Expression expression : expressions)
        {
            @Nullable TypeExp expType = expression.check(data, state, onError);
            if (expType == null)
                return null;
            types.add(expType);
        }
        return onError.recordError(this, TypeExp.unifyTypes(types));
    }

    @Override
    public Pair<List<SingleLoader<OperandNode<Expression>>>, List<SingleLoader<OperatorEntry<Expression, ExpressionNodeParent>>>> loadAsConsecutive(boolean implicitlyRoundBracketed)
    {
        List<SingleLoader<OperatorEntry<Expression, ExpressionNodeParent>>> ops = new ArrayList<>();
        for (int i = 0; i < expressions.size() - 1; i++)
        {
            int iFinal = i;
            ops.add((p, s) -> new OperatorEntry<>(Expression.class, saveOp(iFinal), false, p));
        }
        return new Pair<>(Utility.mapList(expressions, e -> e.loadAsSingle()), ops);
    }

    @Override
    public SingleLoader<OperandNode<Expression>> loadAsSingle()
    {
        return (p, s) -> new BracketedExpression(ConsecutiveBase.EXPRESSION_OPS, p, null, null, SingleLoader.withSemanticParent(loadAsConsecutive(true), s), ')');
    }

    // Can be overriden by subclasses if needed:
    public String _test_getOperatorEntry(int index)
    {
        return saveOp(index);
    }

    private List<String> getOpList()
    {
        List<String> r = new ArrayList<>();
        for (int i = 0; i < expressions.size() - 1; i++)
        {
            r.add(saveOp(i));
        }
        return r;
    }
}
