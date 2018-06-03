package records.transformations.expression;

import annotation.qual.Value;
import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import log.Log;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.qual.Pure;
import records.data.TableAndColumnRenames;
import records.error.InternalException;
import records.error.UserException;
import records.gui.expressioneditor.ExpressionNodeParent;
import records.gui.expressioneditor.GeneralExpressionEntry;
import records.gui.expressioneditor.GeneralExpressionEntry.Op;
import records.typeExp.TypeExp;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.Pair;
import utility.StreamTreeBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Created by neil on 29/11/2016.
 */
public abstract class NaryOpExpression extends Expression
{
    protected final ImmutableList<@Recorded Expression> expressions;

    public NaryOpExpression(List<@Recorded Expression> expressions)
    {
        this.expressions = ImmutableList.copyOf(expressions);
        if (expressions.size() < 2)
            Log.logStackTrace("Expressions size: " + expressions.size());
    }

    @Override
    public final @Nullable CheckedExp check(TableLookup dataLookup, TypeState typeState, ErrorAndTypeRecorder onError) throws UserException, InternalException
    {
        Pair<@Nullable UnaryOperator<TypeExp>, TypeState> lambda = ImplicitLambdaArg.detectImplicitLambda(this, expressions, typeState);
        typeState = lambda.getSecond();
        @Nullable CheckedExp checked = checkNaryOp(dataLookup, typeState, onError);
        return checked == null ? null : checked.applyToType(lambda.getFirst());
    }

    public abstract @Nullable CheckedExp checkNaryOp(TableLookup dataLookup, TypeState typeState, ErrorAndTypeRecorder onError) throws UserException, InternalException;

    @Override
    public final Pair<@Value Object, EvaluateState> getValue(EvaluateState state) throws UserException, InternalException
    {
        if (expressions.stream().anyMatch(e -> e instanceof ImplicitLambdaArg))
        {
            return new Pair<>(ImplicitLambdaArg.makeImplicitFunction(expressions, state, s -> getValueNaryOp(s).getFirst()), state);
        }
        else
            return getValueNaryOp(state);
    }

    @OnThread(Tag.Simulation)
    public abstract Pair<@Value Object, EvaluateState> getValueNaryOp(EvaluateState state) throws UserException, InternalException;

    @Override
    public Stream<ColumnReference> allColumnReferences()
    {
        return expressions.stream().flatMap(Expression::allColumnReferences);
    }

    // Will be same length as expressions, if null use existing
    public final NaryOpExpression copy(List<@Nullable @Recorded Expression> replacements)
    {
        List<@Recorded Expression> newExps = new ArrayList<>();
        for (int i = 0; i < expressions.size(); i++)
        {
            @Nullable @Recorded Expression newExp = replacements.get(i);
            newExps.add(newExp != null ? newExp : expressions.get(i));
        }
        return copyNoNull(newExps);
    }

    public abstract NaryOpExpression copyNoNull(List<@Recorded Expression> replacements);

    @Override
    public String save(BracketedStatus surround, TableAndColumnRenames renames)
    {
        StringBuilder s = new StringBuilder(surround == BracketedStatus.MISC ? "(" : "");
        s.append(getSpecialPrefix());
        for (int i = 0; i < expressions.size(); i++)
        {
            if (i > 0)
                s.append(" ").append(saveOp(i - 1)).append(" ");
            s.append(expressions.get(i).save(BracketedStatus.MISC, renames));
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

    protected abstract Op loadOp(int index);

    @SuppressWarnings("recorded")
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
    public Stream<SingleLoader<Expression, ExpressionNodeParent>> loadAsConsecutive(BracketedStatus bracketedStatus)
    {
        StreamTreeBuilder<SingleLoader<Expression, ExpressionNodeParent>> nodes = new StreamTreeBuilder<>();
        for (int i = 0; i < expressions.size() - 1; i++)
        {
            int iFinal = i;
            nodes.addAll(expressions.get(i).loadAsConsecutive(BracketedStatus.MISC));
            nodes.add(GeneralExpressionEntry.load(loadOp(iFinal)));
        }
        return nodes.stream();
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
        public final ImmutableList<@Recorded Expression> expressions;
        final int index;

        TypeProblemDetails(ImmutableList<Optional<TypeExp>> expressionTypes, ImmutableList<@Recorded Expression> expressions, int index)
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
    
    public @Nullable TypeExp checkAllOperandsSameTypeAndNotPatterns(TypeExp target, TableLookup data, TypeState state, ErrorAndTypeRecorder onError, Function<TypeProblemDetails, Pair<@Nullable StyledString, ImmutableList<QuickFix<Expression, ExpressionNodeParent>>>> getCustomErrorAndFix) throws InternalException, UserException
    {
        boolean allValid = true;
        ArrayList<@Nullable Pair<@Nullable StyledString, TypeExp>> unificationOutcomes = new ArrayList<>(expressions.size());
        for (Expression expression : expressions)
        {
            @Nullable CheckedExp type = expression.check(data, state, onError);
            
            // Make sure to execute always (don't use short-circuit and with allValid):
            if (type == null)
            {
                allValid = false;
                unificationOutcomes.add(null);
            }
            else
            {
                if (type.expressionKind == ExpressionKind.PATTERN)
                {
                    onError.recordError(expression, StyledString.s("Pattern not allowed here"));
                    return null;
                }
                
                Either<StyledString, TypeExp> unified = TypeExp.unifyTypes(target, type.typeExp);
                // We have to recreate either to add nullable constraint:
                unificationOutcomes.add(new Pair<>(unified.<@Nullable StyledString>either(err -> err, u -> null), type.typeExp));
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
                Pair<@Nullable StyledString, ImmutableList<QuickFix<Expression, ExpressionNodeParent>>> errorAndQuickFix = getCustomErrorAndFix.apply(new TypeProblemDetails(expressionTypes, expressions, i));
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
