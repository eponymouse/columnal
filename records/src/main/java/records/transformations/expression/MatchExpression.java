package records.transformations.expression;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.sosy_lab.java_smt.api.Formula;
import org.sosy_lab.java_smt.api.FormulaManager;
import records.data.ColumnId;
import records.data.RecordSet;
import records.data.TableId;
import records.data.datatype.DataType;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UnimplementedException;
import records.error.UserException;
import records.loadsave.OutputBuilder;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.ExBiConsumer;
import utility.Pair;
import utility.Utility;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by neil on 28/11/2016.
 */
public class MatchExpression extends Expression
{
    public class MatchClause
    {
        private final List<Pattern> patterns;
        private final Expression outcome;

        public MatchClause(List<Pattern> patterns, Expression outcome)
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

        public @Nullable DataType check(RecordSet data, TypeState state, ExBiConsumer<Expression, String> onError, DataType srcType) throws InternalException, UserException
        {
            List<TypeState> rhsStates = new ArrayList<>();
            if (patterns.isEmpty())
            {
                // Probably a test generation error:
                onError.accept(MatchExpression.this, "Clause with no patterns");
                return null;
            }
            for (Pattern p : patterns)
            {
                @Nullable TypeState ts = p.check(data, state, onError, srcType);
                if (ts == null)
                    return null;
                rhsStates.add(ts);
            }
            TypeState rhsState = TypeState.intersect(rhsStates);
            return outcome.check(data, rhsState, onError);
        }

        //Returns null if no match
        @OnThread(Tag.Simulation)
        public @Nullable EvaluateState matches(Object value, EvaluateState state, int rowIndex) throws UserException, InternalException
        {
            for (Pattern p : patterns)
            {
                EvaluateState newState = p.match(value, rowIndex, state);
                if (newState != null) // Did it match?
                    return newState;
            }
            return null;
        }

        public String save()
        {
            return patterns.stream().map(p -> p.save()).collect(Collectors.joining(";")) + " ~ " + outcome.save(false);
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
    }

    public class PatternMatchConstructor implements PatternMatch
    {
        private final String constructor;
        private final @Nullable PatternMatch subPattern;
        private int tagIndex = -1;

        public PatternMatchConstructor(String constructor, @Nullable PatternMatch subPattern)
        {
            this.constructor = constructor;
            this.subPattern = subPattern;
        }

        @Override
        public @Nullable TypeState check(RecordSet data, TypeState state, ExBiConsumer<Expression, String> onError, DataType srcType) throws InternalException, UserException
        {
            if (!srcType.isTagged())
            {
                onError.accept(MatchExpression.this, "Pattern has constructor, but match source is not a tagged type");
            }
            Pair<Integer, @Nullable DataType> foundAndInner = srcType.unwrapTag(constructor);
            if (foundAndInner.getFirst() == -1)
            {
                onError.accept(MatchExpression.this, "Match source type " + srcType + " has no constructor named: " + constructor);
                return null;
            }
            else
            {
                this.tagIndex = foundAndInner.getFirst();
                @Nullable DataType inner = foundAndInner.getSecond();
                if ((subPattern != null) && inner != null)
                    return subPattern.check(data, state, onError, inner);
                if (subPattern != null && inner == null)
                {
                    onError.accept(MatchExpression.this, "Constructor " + constructor + " has no inner data, yet found inner pattern");
                    return null;
                }
                if (subPattern == null && inner != null)
                {
                    onError.accept(MatchExpression.this, "Constructor " + constructor + " has inner data, yet found no inner pattern");
                    return null;
                }
                return state;
            }
        }

        @Override
        public @Nullable EvaluateState match(int rowIndex, Object val, EvaluateState state) throws InternalException, UserException
        {
            if (val instanceof Pair && ((Pair)val).getFirst() instanceof Integer)
                return ((Integer)((Pair<Integer, @Nullable Object>)val).getFirst()) == tagIndex ? (subPattern == null ? state : subPattern.match(rowIndex, ((Pair)val).getSecond(), state)) : null;
            throw new InternalException("Unexpected type; should be integer for tag index but was " + val.getClass());
        }

        @Override
        public String save()
        {
            return "\\" + OutputBuilder.quotedIfNecessary(constructor) + (subPattern == null ? "" : (":" + subPattern.save()));
        }

        @Override
        public boolean equals(@Nullable Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            PatternMatchConstructor that = (PatternMatchConstructor)o;

            if (!constructor.equals(that.constructor)) return false;
            return subPattern != null ? subPattern.equals(that.subPattern) : that.subPattern == null;

        }

        @Override
        public int hashCode()
        {
            int result = constructor.hashCode();
            result = 31 * result + (subPattern != null ? subPattern.hashCode() : 0);
            return result;
        }
    }

    public static class PatternMatchExpression implements PatternMatch
    {
        private final Expression expression;

        public PatternMatchExpression(Expression expression)
        {
            this.expression = expression;
        }

        @Override
        public @Nullable TypeState check(RecordSet data, TypeState state, ExBiConsumer<Expression, String> onError, DataType srcType) throws InternalException, UserException
        {
            @Nullable DataType ourType = expression.check(data, state, onError);
            if (ourType == null)
                return null;
            if (!srcType.equals(ourType))
            {
                onError.accept(expression, "Type mismatch in pattern: source type is " + srcType + " pattern type is: " + ourType);
                return null;
            }
            return state; // Expression doesn't modify the type state
        }

        @Override
        @OnThread(Tag.Simulation)
        public @Nullable EvaluateState match(int rowIndex, Object value, EvaluateState state) throws InternalException, UserException
        {
            Object expected = expression.getValue(rowIndex, state);
            if (Utility.compareValues(expected, value) == 0)
                return state;
            else
                return null;
        }

        @Override
        public String save()
        {
            return expression.save(false);
        }

        @Override
        public boolean equals(@Nullable Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            PatternMatchExpression that = (PatternMatchExpression)o;

            return expression.equals(that.expression);

        }

        @Override
        public int hashCode()
        {
            return expression.hashCode();
        }
    }

    public class PatternMatchVariable implements PatternMatch
    {
        private final String varName;

        public PatternMatchVariable(String varName)
        {
            this.varName = varName;
        }

        @Override
        public @Nullable TypeState check(RecordSet data, TypeState state, ExBiConsumer<Expression, String> onError, DataType srcType) throws UserException, InternalException
        {
            // Variable always type checks against anything
            return state.add(varName, srcType, err -> onError.accept(MatchExpression.this, err));
        }

        @Override
        public EvaluateState match(int rowIndex, Object value, EvaluateState state) throws InternalException
        {
            return state.add(varName, value);
        }

        @Override
        public String save()
        {
            return "$" + OutputBuilder.quotedIfNecessary(varName);
        }

        @Override
        public boolean equals(@Nullable Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            PatternMatchVariable that = (PatternMatchVariable)o;

            return varName.equals(that.varName);

        }

        @Override
        public int hashCode()
        {
            return varName.hashCode();
        }
    }

    public static interface PatternMatch
    {
        @Nullable TypeState check(RecordSet data, TypeState state, ExBiConsumer<Expression, String> onError, DataType srcType) throws InternalException, UserException;

        @OnThread(Tag.Simulation)
        @Nullable EvaluateState match(int rowIndex, Object value, EvaluateState state) throws InternalException, UserException;

        String save();
    }

    public static class Pattern
    {
        private final PatternMatch pattern;
        private final List<Expression> guards;

        public Pattern(PatternMatch pattern, List<Expression> guards)
        {
            this.pattern = pattern;
            this.guards = guards;
        }

        public @Nullable TypeState check(RecordSet data, TypeState state, ExBiConsumer<Expression, String> onError, DataType srcType) throws InternalException, UserException
        {
            TypeState rhsState = pattern.check(data, state, onError, srcType);
            if (rhsState == null)
                return null;
            for (Expression e : guards)
            {
                @Nullable DataType type = e.check(data, rhsState, onError);
                if (type == null || !DataType.BOOLEAN.equals(type))
                {
                    onError.accept(e, "Pattern guards must have boolean type, found: " + (type == null ? " error" : type));
                }
            }
            return rhsState;
        }

        @OnThread(Tag.Simulation)
        public @Nullable EvaluateState match(Object value, int rowIndex, EvaluateState state) throws InternalException, UserException
        {
            @Nullable EvaluateState newState = pattern.match(rowIndex, value, state);
            if (newState == null)
                return null;
            for (Expression guard : guards)
            {
                if (!guard.getBoolean(rowIndex, newState, null))
                    return null;
            }
            return newState;
        }

        public String save()
        {
            return pattern.save() + guards.stream().map(e -> " & " + e.save(false)).collect(Collectors.joining());
        }

        public PatternMatch getPattern()
        {
            return pattern;
        }

        @Override
        public boolean equals(@Nullable Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Pattern pattern1 = (Pattern)o;

            if (!pattern.equals(pattern1.pattern)) return false;
            return guards.equals(pattern1.guards);

        }

        @Override
        public int hashCode()
        {
            int result = pattern.hashCode();
            result = 31 * result + guards.hashCode();
            return result;
        }
    }

    private final Expression expression;
    private final List<MatchClause> clauses;

    @SuppressWarnings("initialization")
    public MatchExpression(Expression expression, List<Function<MatchExpression, MatchClause>> clauses)
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
    public Object getValue(int rowIndex, EvaluateState state) throws UserException, InternalException
    {
        // It's type checked so can just copy first clause:
        Object value = expression.getValue(rowIndex, state);
        for (MatchClause clause : clauses)
        {
            EvaluateState newState = clause.matches(value, state, rowIndex);
            if (newState != null)
            {
                return clause.outcome.getValue(rowIndex, newState);
            }
        }
        throw new UserException("No matching clause found");
    }

    @Override
    public Stream<ColumnId> allColumnNames()
    {
        return Stream.concat(expression.allColumnNames(), clauses.stream().flatMap(c -> c.outcome.allColumnNames()));
    }

    @Override
    public String save(boolean topLevel)
    {
        String inner = expression.save(false) + " ? " + clauses.stream().map(c -> c.save()).collect(Collectors.joining(";"));
        return topLevel ? inner : ("(" + inner + ")");
    }

    @Override
    public @Nullable DataType check(RecordSet data, TypeState state, ExBiConsumer<Expression, String> onError) throws UserException, InternalException
    {
        // Need to check several things:
        //   - That all of the patterns have the same type as the expression being matched
        //   - That all of the pattern guards have boolean type
        //   - That all of the outcome expressions have the same type as each other (and is what we will return)

        @Nullable DataType srcType = expression.check(data, state, onError);
        if (srcType == null)
            return null;

        List<DataType> outcomeTypes = new ArrayList<>();
        for (MatchClause c : clauses)
        {
            @Nullable DataType type = c.check(data, state, onError, srcType);
            if (type == null)
                return null;
            outcomeTypes.add(type);
        }

        return DataType.checkAllSame(outcomeTypes, err -> onError.accept(this, err));
    }

    @Override
    public Formula toSolver(FormulaManager formulaManager, RecordSet src, Map<Pair<@Nullable TableId, ColumnId>, Formula> columnVariables) throws InternalException, UserException
    {
        throw new UnimplementedException();
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

    @Override
    public Stream<Pair<Expression, Function<Expression, Expression>>> _test_childMutationPoints()
    {
        // TODO allow replacement within clauses
        return expression._test_allMutationPoints().map(p -> p.replaceSecond(e -> new MatchExpression(e, Utility.<MatchClause, Function<MatchExpression, MatchClause>>mapList(clauses, c -> c::copy))));
    }

    @Override
    public @Nullable Expression _test_typeFailure(Random r, _test_TypeVary newExpressionOfDifferentType, UnitManager unitManager) throws InternalException, UserException
    {
        return null;
    }
}
