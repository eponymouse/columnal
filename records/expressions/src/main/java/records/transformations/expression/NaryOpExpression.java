package records.transformations.expression;

import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.ColumnId;
import records.data.RecordSet;
import records.error.InternalException;
import records.error.UserException;
import records.gui.expressioneditor.BracketedExpression;
import records.gui.expressioneditor.ConsecutiveBase;
import records.gui.expressioneditor.ExpressionNodeParent;
import records.gui.expressioneditor.OperandNode;
import records.gui.expressioneditor.OperatorEntry;
import records.transformations.expression.ErrorAndTypeRecorder.QuickFix;
import records.types.TypeExp;
import styled.StyledString;
import utility.Either;
import utility.FXPlatformFunction;
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

    @Override
    public Pair<List<SingleLoader<Expression, ExpressionNodeParent, OperandNode<Expression, ExpressionNodeParent>>>, List<SingleLoader<Expression, ExpressionNodeParent, OperatorEntry<Expression, ExpressionNodeParent>>>> loadAsConsecutive(boolean implicitlyRoundBracketed)
    {
        List<SingleLoader<Expression, ExpressionNodeParent, OperatorEntry<Expression, ExpressionNodeParent>>> ops = new ArrayList<>();
        for (int i = 0; i < expressions.size() - 1; i++)
        {
            int iFinal = i;
            ops.add((p, s) -> new OperatorEntry<>(Expression.class, saveOp(iFinal), false, p));
        }
        return new Pair<>(Utility.mapList(expressions, e -> e.loadAsSingle()), ops);
    }

    @Override
    public SingleLoader<Expression, ExpressionNodeParent, OperandNode<Expression, ExpressionNodeParent>> loadAsSingle()
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
    
    public @Nullable @Recorded TypeExp checkAllOperandsSameType(TypeExp target, RecordSet data, TypeState state, ErrorAndTypeRecorder onError, Function<Pair<TypeExp, Expression>, Pair<@Nullable StyledString, @Nullable QuickFix<Expression>>> getCustomErrorAndFix) throws InternalException, UserException
    {
        boolean allValid = true;
        for (Expression expression : expressions)
        {
            @Nullable TypeExp type = expression.check(data, state, onError);
            // Make sure to execute always (don't use short-circuit and with allValid):
            boolean valid;
            if (type == null)
            {
                valid = false;
            }
            else
            {
                Pair<@Nullable StyledString, @Nullable QuickFix<Expression>> errorAndQuickFix = getCustomErrorAndFix.apply(new Pair<>(type, expression));
                ImmutableList<QuickFix<Expression>> quickFixes = errorAndQuickFix.getSecond() == null ? ImmutableList.of() : ImmutableList.of(errorAndQuickFix.getSecond());
                valid = onError.recordError(expression, TypeExp.unifyTypes(target, type).<Either<StyledString, TypeExp>>either(err -> {
                    if (errorAndQuickFix.getFirst() != null)
                        return Either.left(errorAndQuickFix.getFirst());
                    else
                        return Either.left(err);
                }, t -> Either.right(t)), quickFixes) != null;
            }
            allValid &= valid;
        }
        return allValid ? onError.recordType(this, target) : null;
    }
}
