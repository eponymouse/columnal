package records.transformations.expression;

import annotation.qual.Value;
import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.sosy_lab.java_smt.api.Formula;
import org.sosy_lab.java_smt.api.FormulaManager;
import records.data.ColumnId;
import records.data.RecordSet;
import records.data.TableAndColumnRenames;
import records.data.TableId;
import records.data.datatype.DataType;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UnimplementedException;
import records.error.UserException;
import records.gui.expressioneditor.ClauseNode;
import records.gui.expressioneditor.ConsecutiveBase.BracketedStatus;
import records.gui.expressioneditor.ExpressionEditorUtil;
import records.gui.expressioneditor.ExpressionNodeParent;
import records.gui.expressioneditor.OperandNode;
import records.gui.expressioneditor.PatternMatchNode;
import records.transformations.expression.NaryOpExpression.TypeProblemDetails;
import records.types.TypeExp;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
import utility.Utility;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by neil on 28/11/2016.
 */
public class MatchExpression extends NonOperatorExpression
{
    /**
     * A clause is a list of patterns, and an outcome expression
     */
    public class MatchClause
    {
        private final List<Pattern> patterns;
        private final @Recorded Expression outcome;

        public MatchClause(List<Pattern> patterns, @Recorded Expression outcome)
        {
            this.patterns = patterns;
            this.outcome = outcome;
        }

        public List<Pattern> getPatterns()
        {
            return patterns;
        }

        public Expression getOutcome()
        {
            return outcome;
        }

        /**
         * Returns pattern match type, outcome type
         */
        public @Nullable Pair<List<TypeExp>, TypeExp> check(TableLookup data, TypeState state, ErrorAndTypeRecorder onError) throws InternalException, UserException
        {
            TypeExp[] patternTypes = new TypeExp[patterns.size()];
            TypeState[] rhsStates = new TypeState[patterns.size()];
            if (patterns.isEmpty())
            {
                // Probably a test generation error:
                onError.recordError(MatchExpression.this, StyledString.s("Clause with no patterns"));
                return null;
            }
            for (int i = 0; i < patterns.size(); i++)
            {
                @Nullable Pair<@Recorded TypeExp, TypeState> ts = patterns.get(i).check(data, state, onError);
                if (ts == null)
                    return null;
                patternTypes[i] = ts.getFirst();
                rhsStates[i] = ts.getSecond();
            }
            TypeState rhsState = TypeState.intersect(Arrays.asList(rhsStates));
            @Nullable TypeExp outcomeType = outcome.check(data, rhsState, onError);
            if (outcomeType == null)
                return null;
            else
                return new Pair<>(Arrays.asList(patternTypes), outcomeType);
        }

        //Returns null if no match
        @OnThread(Tag.Simulation)
        public @Nullable EvaluateState matches(@Value Object value, EvaluateState state, int rowIndex) throws UserException, InternalException
        {
            for (Pattern p : patterns)
            {
                EvaluateState newState = p.match(value, rowIndex, state);
                if (newState != null) // Did it match?
                    return newState;
            }
            return null;
        }

        public String save(TableAndColumnRenames renames)
        {
            return " @case " + patterns.stream().map(p -> p.save(renames)).collect(Collectors.joining(" @or ")) + " @then " + outcome.save(BracketedStatus.MISC, renames);
        }

        public StyledString toDisplay()
        {
            return StyledString.concat(
                StyledString.s(" case "),
                patterns.stream().map(p -> p.toDisplay()).collect(StyledString.joining(" or ")),
                StyledString.s(" then "),
                outcome.toDisplay(BracketedStatus.MISC)
            );
        }

        public MatchClause copy(MatchExpression e)
        {
            return e.new MatchClause(patterns, outcome); //TODO deep copy patterns
        }

        @Override
        public boolean equals(@Nullable Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            MatchClause that = (MatchClause)o;

            if (!patterns.equals(that.patterns)) return false;
            return outcome.equals(that.outcome);

        }

        @Override
        public int hashCode()
        {
            int result = patterns.hashCode();
            result = 31 * result + outcome.hashCode();
            return result;
        }

        @OnThread(Tag.FXPlatform)
        public ClauseNode load(PatternMatchNode parent)
        {
            Pair<List<Pair<Expression, @Nullable Expression>>, Expression> patternsAndGuardsToOutcome
                = new Pair<List<Pair<Expression, @Nullable Expression>>, Expression>(
                    Utility.<Pattern, Pair<Expression, @Nullable Expression>>mapList(patterns, p -> p.load()), outcome);
            return new ClauseNode(parent, patternsAndGuardsToOutcome);
        }
    }

    /**
     * A pattern is an expression, plus an optional guard
     */
    public static class Pattern
    {
        private final Expression pattern;
        private final @Nullable Expression guard;

        public Pattern(@Recorded Expression pattern, @Nullable @Recorded Expression guard)
        {
            this.pattern = pattern;
            this.guard = guard;
        }

        /**
         * Returns pattern type, and resulting type state (including any declared vars)
         */
        public @Nullable Pair<@Recorded TypeExp, TypeState> check(TableLookup data, TypeState state, ErrorAndTypeRecorder onError) throws InternalException, UserException
        {
            final @Nullable Pair<@Recorded TypeExp, TypeState> rhsState = pattern.checkAsPattern(true, data, state, onError);
            if (rhsState == null)
                return null;
            
            if (guard != null)
            {
                @Nullable TypeExp type = guard.check(data, rhsState.getSecond(), onError);
                if (type == null || onError.recordError(guard, TypeExp.unifyTypes(TypeExp.fromConcrete(guard, DataType.BOOLEAN), type)) == null)
                {
                    return null;
                }
            }
            return rhsState;
        }

        // Returns non-null if it matched, null if it didn't match.
        @OnThread(Tag.Simulation)
        public @Nullable EvaluateState match(@Value Object value, int rowIndex, EvaluateState state) throws InternalException, UserException
        {
            @Nullable EvaluateState newState = pattern.matchAsPattern(rowIndex, value, state);
            if (newState == null)
                return null;
            if (guard != null && !guard.getBoolean(rowIndex, newState, null))
                return null;
            return newState;
        }

        public String save(TableAndColumnRenames renames)
        {
            return pattern.save(BracketedStatus.MISC, renames) + (guard == null ? "" : " @given " + guard.save(BracketedStatus.MISC, renames));
        }

        public StyledString toDisplay()
        {
            StyledString patternDisplay = pattern.toDisplay(BracketedStatus.MISC);
            return guard == null ? patternDisplay : StyledString.concat(StyledString.s(" given "), guard.toDisplay(BracketedStatus.MISC));
        }

        public Expression getPattern()
        {
            return pattern;
        }

        @Override
        public boolean equals(@Nullable Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Pattern pattern1 = (Pattern) o;

            if (!pattern.equals(pattern1.pattern)) return false;
            return guard != null ? guard.equals(pattern1.guard) : pattern1.guard == null;
        }

        @Override
        public int hashCode()
        {
            int result = pattern.hashCode();
            result = 31 * result + (guard != null ? guard.hashCode() : 0);
            return result;
        }

        // Load pattern and guard
        public Pair<Expression, @Nullable Expression> load()
        {
            return new Pair<>(pattern, guard);
        }

        public @Nullable Expression getGuard()
        {
            return guard;
        }
    }

    private final @Recorded Expression expression;
    private final List<MatchClause> clauses;

    @SuppressWarnings("initialization") // Because we pass this to sub-clauses which we are creating.
    public MatchExpression(@Recorded Expression expression, List<Function<MatchExpression, MatchClause>> clauses)
    {
        this.expression = expression;
        this.clauses = Utility.<Function<MatchExpression, MatchClause>, MatchClause>mapList(clauses, f -> f.apply(this));
    }

    public Expression getExpression()
    {
        return expression;
    }

    public List<MatchClause> getClauses()
    {
        return clauses;
    }

    @Override
    public @Value Object getValue(int rowIndex, EvaluateState state) throws UserException, InternalException
    {
        // It's type checked so can just copy first clause:
        @Value Object value = expression.getValue(rowIndex, state);
        for (MatchClause clause : clauses)
        {
            EvaluateState newState = clause.matches(value, state, rowIndex);
            if (newState != null)
            {
                return clause.outcome.getValue(rowIndex, newState);
            }
        }
        throw new UserException("No matching clause found in expression: \"" + save(BracketedStatus.MISC, TableAndColumnRenames.EMPTY) + "\"");
    }

    @Override
    public Stream<ColumnReference> allColumnReferences()
    {
        return Stream.<ColumnReference>concat(expression.allColumnReferences(), clauses.stream().flatMap(c -> c.outcome.allColumnReferences()));
    }

    @Override
    public String save(BracketedStatus surround, TableAndColumnRenames renames)
    {
        String inner = "@match " + expression.save(BracketedStatus.MISC, renames) + clauses.stream().map(c -> c.save(renames)).collect(Collectors.joining(""));
        return (surround == BracketedStatus.DIRECT_ROUND_BRACKETED || surround == BracketedStatus.TOP_LEVEL) ? inner : ("(" + inner + ")");
    }

    @Override
    public StyledString toDisplay(BracketedStatus surround)
    {
        StyledString inner = StyledString.concat(StyledString.s("match "), expression.toDisplay(BracketedStatus.MISC), clauses.stream().map(c -> c.toDisplay()).collect(StyledString.joining("")));
        return (surround == BracketedStatus.DIRECT_ROUND_BRACKETED || surround == BracketedStatus.TOP_LEVEL) ? inner : StyledString.roundBracket(inner);
    }

    @Override
    public @Nullable @Recorded TypeExp check(TableLookup dataLookup, TypeState state, ErrorAndTypeRecorder onError) throws UserException, InternalException
    {        
        // Need to check several things:
        //   - That all of the patterns have the same type as the expression being matched
        //   - That all of the pattern guards have boolean type
        //   - That all of the outcome expressions have the same type as each other (and is what we will return)

        @Nullable TypeExp srcType = expression.check(dataLookup, state, onError);
        if (srcType == null)
            return null;

        if (clauses.isEmpty())
        {
            onError.recordError(this, StyledString.s("Must have at least one clause in a match"));
            return null;
        }
        
        // Add one extra for the srcType:
        List<TypeExp> patternTypes = new ArrayList<>(1 + clauses.size());
        patternTypes.add(srcType);
        TypeExp[] outcomeTypes = new TypeExp[clauses.size()];
        // Includes the original source pattern:
        List<Expression> patternExpressions = new ArrayList<>();
        patternExpressions.add(expression);
        boolean allValid = true;
        for (int i = 0; i < clauses.size(); i++)
        {
            patternExpressions.addAll(Utility.mapList(clauses.get(i).getPatterns(), p -> p.pattern));
            @Nullable Pair<List<TypeExp>, TypeExp> patternAndOutcomeType = clauses.get(i).check(dataLookup, state, onError);
            if (patternAndOutcomeType == null)
                return null;
            patternTypes.addAll(patternAndOutcomeType.getFirst());
            outcomeTypes[i] = patternAndOutcomeType.getSecond();
        }
        @SuppressWarnings("recorded")
        ImmutableList<@Recorded Expression> immPatternExpressions = ImmutableList.copyOf(patternExpressions);
        
        for (int i = 0; i < patternExpressions.size(); i++)
        {
            Expression expression = patternExpressions.get(i);
            // Must show an error to get the quick fixes to show:
            onError.recordError(expression, StyledString.s("Pattern match items must have matching items")); 
            onError.recordQuickFixes(expression, ExpressionEditorUtil.getFixesForMatchingNumericUnits(state, new TypeProblemDetails(patternTypes.stream().map(p -> Optional.of(p)).collect(ImmutableList.toImmutableList()), immPatternExpressions, i)));
        }
        
        if (onError.recordError(this, TypeExp.unifyTypes(patternTypes)) == null)
            return null;

        return onError.recordTypeAndError(this, TypeExp.unifyTypes(outcomeTypes));
    }

    @Override
    public Formula toSolver(FormulaManager formulaManager, RecordSet src, Map<Pair<@Nullable TableId, ColumnId>, Formula> columnVariables) throws InternalException, UserException
    {
        throw new UnimplementedException();
    }

    @Override
    public SingleLoader<Expression, ExpressionNodeParent, OperandNode<Expression, ExpressionNodeParent>> loadAsSingle()
    {
        return (p, s) -> new PatternMatchNode(p, new Pair<>(expression, clauses));
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        MatchExpression that = (MatchExpression)o;

        if (!expression.equals(that.expression)) return false;
        return clauses.equals(that.clauses);

    }

    @Override
    public int hashCode()
    {
        int result = expression.hashCode();
        result = 31 * result + clauses.hashCode();
        return result;
    }

    @SuppressWarnings("recorded")
    @Override
    public Stream<Pair<Expression, Function<Expression, Expression>>> _test_childMutationPoints()
    {
        // TODO allow replacement within clauses
        return expression._test_allMutationPoints().map(p -> new Pair<Expression, Function<Expression, Expression>>(p.getFirst(), e -> new MatchExpression(p.getSecond().apply(e), Utility.<MatchClause, Function<MatchExpression, MatchClause>>mapList(clauses, c -> c::copy))));
    }

    @Override
    public @Nullable Expression _test_typeFailure(Random r, _test_TypeVary newExpressionOfDifferentType, UnitManager unitManager) throws InternalException, UserException
    {
        return null;
    }
}
