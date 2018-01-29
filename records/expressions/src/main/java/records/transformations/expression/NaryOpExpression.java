package records.transformations.expression;

import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import log.Log;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.qual.Pure;
import records.data.ColumnId;
import records.data.RecordSet;
import records.error.InternalException;
import records.error.UserException;
import records.gui.expressioneditor.BracketedExpression;
import records.gui.expressioneditor.ConsecutiveBase;
import records.gui.expressioneditor.ConsecutiveBase.BracketedStatus;
import records.gui.expressioneditor.ExpressionNodeParent;
import records.gui.expressioneditor.OperandNode;
import records.gui.expressioneditor.OperatorEntry;
import records.transformations.expression.ErrorAndTypeRecorder.QuickFix;
import records.types.TypeExp;
import styled.StyledString;
import utility.Either;
import utility.Pair;
import utility.Utility;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Created by neil on 29/11/2016.
 */
public abstract class NaryOpExpression extends Expression
{
    protected final ImmutableList<Expression> expressions;

    public NaryOpExpression(List<@Recorded Expression> expressions)
    {
        this.expressions = ImmutableList.copyOf(expressions);
        // Bit hacky to use instanceof, but only for logging purposes anyway:
        if (expressions.size() < 2 && !(this instanceof InvalidOperatorExpression))
            Log.logStackTrace("Expressions size: " + expressions.size());
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
    public String save(BracketedStatus surround)
    {
        StringBuilder s = new StringBuilder(surround == BracketedStatus.MISC ? "(" : "");
        s.append(getSpecialPrefix());
        for (int i = 0; i < expressions.size(); i++)
        {
            if (i > 0)
                s.append(" ").append(saveOp(i - 1)).append(" ");
            s.append(expressions.get(i).save(BracketedStatus.MISC));
        }
        if (surround == BracketedStatus.MISC)
            s.append(")");
        return s.toString();
    }

    @Override
    public StyledString toDisplay(BracketedStatus surround)
    {
        StyledString.Builder s = new StyledString.Builder();
        s.append(surround == BracketedStatus.MISC ? "(" : "");
        s.append(getSpecialPrefix());
        for (int i = 0; i < expressions.size(); i++)
        {
            if (i > 0)
                s.append(" ").append(saveOp(i - 1)).append(" ");
            s.append(expressions.get(i).toDisplay(BracketedStatus.MISC));
        }
        if (surround == BracketedStatus.MISC)
            s.append(")");
        return s.build();
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
        return (p, s) -> new BracketedExpression(ConsecutiveBase.EXPRESSION_OPS, p, SingleLoader.withSemanticParent(loadAsConsecutive(true), s), ')');
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
    
    public static class TypeProblemDetails
    {
        // Same length as expressions.  Boolean indicates whether unification
        // was successful or not.
        final ImmutableList<Optional<TypeExp>> expressionTypes;
        public final ImmutableList<Expression> expressions;
        final int index;

        TypeProblemDetails(ImmutableList<Optional<TypeExp>> expressionTypes, ImmutableList<Expression> expressions, int index)
        {
            this.expressionTypes = expressionTypes;
            this.expressions = expressions;
            this.index = index;
        }

        @Pure
        public @Nullable TypeExp getOurType()
        {
            return getType(index);
        }
        
        public @Nullable TypeExp getType(int index)
        {
            return expressionTypes.get(index).orElse(null);
        }

        public Expression getOurExpression()
        {
            return expressions.get(index);
        }
    }
    
    public @Nullable TypeExp checkAllOperandsSameType(TypeExp target, RecordSet data, TypeState state, ErrorAndTypeRecorder onError, Function<TypeProblemDetails, Pair<@Nullable StyledString, ImmutableList<QuickFix<Expression>>>> getCustomErrorAndFix) throws InternalException, UserException
    {
        boolean allValid = true;
        ArrayList<@Nullable Pair<@Nullable StyledString, TypeExp>> unificationOutcomes = new ArrayList<>(expressions.size());
        for (Expression expression : expressions)
        {
            @Nullable TypeExp type = expression.check(data, state, onError);
            // Make sure to execute always (don't use short-circuit and with allValid):
            if (type == null)
            {
                allValid = false;
                unificationOutcomes.add(null);
            }
            else
            {
                Either<StyledString, TypeExp> unified = TypeExp.unifyTypes(target, type);
                // We have to recreate either to add nullable constraint:
                unificationOutcomes.add(new Pair<>(unified.<@Nullable StyledString>either(err -> err, u -> null), type));
                if (unified.isLeft())
                    allValid = false;
            }
        }

        ImmutableList<Optional<TypeExp>> expressionTypes = unificationOutcomes.stream().<Optional<TypeExp>>map((@Nullable Pair<@Nullable StyledString, TypeExp> p) -> p == null ? Optional.<TypeExp>empty() : Optional.<TypeExp>of(p.getSecond())).collect(ImmutableList.toImmutableList());

        if (!allValid)
        {
            for (int i = 0; i < expressions.size(); i++)
            {
                Expression expression = expressions.get(i);
                Pair<@Nullable StyledString, ImmutableList<QuickFix<Expression>>> errorAndQuickFix = getCustomErrorAndFix.apply(new TypeProblemDetails(expressionTypes, expressions, i));
                onError.recordQuickFixes(expression, errorAndQuickFix.getSecond());
                StyledString error;
                if (errorAndQuickFix.getFirst() != null)
                    error = errorAndQuickFix.getFirst();
                else if (unificationOutcomes.get(i) != null && unificationOutcomes.get(i).getFirst() != null)
                    error = unificationOutcomes.get(i).getFirst();
                else
                    // Hack to ensure there is an error:
                    error = StyledString.s(" ");
                onError.recordError(expression, error);
            }
        }
        
        return allValid ? target : null;
    }
}
