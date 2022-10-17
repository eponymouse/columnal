/*
 * Columnal: Safer, smoother data table processing.
 * Copyright (c) Neil Brown, 2016-2020, 2022.
 *
 * This file is part of Columnal.
 *
 * Columnal is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 *
 * Columnal is distributed in the hope that it will be useful, but WITHOUT 
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or 
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for 
 * more details.
 *
 * You should have received a copy of the GNU General Public License along 
 * with Columnal. If not, see <https://www.gnu.org/licenses/>.
 */

package xyz.columnal.transformations.expression;

import annotation.qual.Value;
import annotation.recorded.qual.Recorded;
import annotation.units.CanonicalLocation;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.id.TableAndColumnRenames;
import xyz.columnal.data.datatype.DataTypeUtility;
import xyz.columnal.data.unit.UnitManager;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.transformations.expression.NaryOpExpression.TypeProblemDetails;
import xyz.columnal.transformations.expression.explanation.Explanation;
import xyz.columnal.transformations.expression.explanation.Explanation.ExecutionType;
import xyz.columnal.transformations.expression.explanation.Explanation.ExplanationSource;
import xyz.columnal.transformations.expression.explanation.ExplanationLocation;
import xyz.columnal.transformations.expression.visitor.ExpressionVisitor;
import xyz.columnal.typeExp.TypeExp;
import xyz.columnal.styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.Utility;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Random;
import java.util.Set;
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
    public static class MatchClause implements ExplanationSource
    {
        private final CanonicalSpan caseLocation;
        private final ImmutableList<Pattern> patterns;
        private final @Recorded Expression outcome;

        public MatchClause(CanonicalSpan caseLocation, ImmutableList<Pattern> patterns, @Recorded Expression outcome)
        {
            this.caseLocation = caseLocation;
            this.patterns = patterns;
            this.outcome = outcome;
        }
        
        public static MatchClause unrecorded(ImmutableList<Pattern> patterns, @Recorded Expression outcome)
        {
            return new MatchClause(new CanonicalSpan(CanonicalLocation.ZERO, CanonicalLocation.ZERO), patterns, outcome);
        }
        
        public List<Pattern> getPatterns()
        {
            return patterns;
        }

        public @Recorded Expression getOutcome()
        {
            return outcome;
        }

        @Override
        public Stream<String> allVariableReferences()
        {
            return Stream.<String>concat(patterns.stream().<String>flatMap(Pattern::allVariableReferences), outcome.allVariableReferences());
        }

        /**
         * Returns pattern match type, outcome type
         */
        public @Nullable Pair<List<TypeExp>, TypeExp> check(ColumnLookup data, TypeState state, ErrorAndTypeRecorder onError) throws InternalException, UserException
        {
            TypeExp[] patternTypes = new TypeExp[patterns.size()];
            TypeState[] rhsStates = new TypeState[patterns.size()];
            if (patterns.isEmpty())
            {
                // Probably a test generation error:
                throw new UserException("Clause with no patterns");
            }
            for (int i = 0; i < patterns.size(); i++)
            {
                @Nullable CheckedExp ts = patterns.get(i).check(data, state, onError);
                if (ts == null)
                    return null;
                patternTypes[i] = ts.typeExp;
                rhsStates[i] = ts.typeState;
            }
            TypeState rhsState = TypeState.intersect(Arrays.asList(rhsStates));
            @Nullable CheckedExp outcomeType = outcome.check(data, rhsState, ExpressionKind.EXPRESSION, LocationInfo.UNIT_DEFAULT, onError);
            if (outcomeType == null)
                return null;
            else
                return new Pair<>(Arrays.asList(patternTypes), outcomeType.typeExp);
        }

        //Returns null if no match
        @OnThread(Tag.Simulation)
        public ValueResult matches(@Value Object value, EvaluateState state) throws EvaluationException, InternalException
        {
            ImmutableList.Builder<ValueResult> patternsSoFar = ImmutableList.builderWithExpectedSize(patterns.size());
            for (int i = 0; i < patterns.size(); i++)
            {
                Pattern p = patterns.get(i);
                ImmutableList<ValueResult> matches = p.match(value, state);
                patternsSoFar.addAll(matches);
                ValueResult patternOutcome = matches.get(matches.size() - 1);
                if (Utility.cast(patternOutcome.value, Boolean.class)) // Did it match?
                    return result(OptionalInt.of(i), patternOutcome.evaluateState, patternsSoFar.build());
            }
            return result(OptionalInt.empty(), state, patternsSoFar.build());
        }
        
        @OnThread(Tag.Simulation)
        private ValueResult result(OptionalInt matchedPatternIndex, EvaluateState state, ImmutableList<ValueResult> children)
        {
            return new ValueResult(DataTypeUtility.value(matchedPatternIndex.isPresent()), state)
            {
                @Override
                public Explanation makeExplanation(@Nullable ExecutionType overrideExecutionType) throws InternalException
                {
                    return new Explanation(MatchClause.this, overrideExecutionType != null ? overrideExecutionType : ExecutionType.MATCH, evaluateState, value, ImmutableList.of(), null)
                    {
                        @Override
                        public @OnThread(Tag.Simulation) @Nullable StyledString describe(Set<Explanation> alreadyDescribed, Function<ExplanationLocation, StyledString> hyperlinkLocation, ExpressionStyler expressionStyler, ImmutableList<ExplanationLocation> extraLocations, boolean skipIfTrivial) throws InternalException, UserException
                        {
                            // We only need to describe the patterns, if we are chosen
                            // then the outer MatchExpression will describe outcome.
                            // We know that all patterns before the last did not match
                            return null;
                        }

                        @Override
                        public @OnThread(Tag.Simulation) ImmutableList<Explanation> getDirectSubExplanations() throws InternalException
                        {
                            return Utility.mapListInt(children, c -> c.makeExplanation(null));
                        }
                    };
                }
            };
        }

        public String save(SaveDestination saveDestination, TableAndColumnRenames renames)
        {
            return " @case " + patterns.stream().map(p -> p.save(saveDestination, renames)).collect(Collectors.joining(" @orcase ")) + " @then " + outcome.save(saveDestination, BracketedStatus.DONT_NEED_BRACKETS, renames);
        }

        public StyledString toDisplay(DisplayType displayType, ExpressionStyler expressionStyler)
        {
            return StyledString.concat(
                StyledString.s(" @case "),
                patterns.stream().map(p -> p.toDisplay(displayType, expressionStyler)).collect(StyledString.joining(" or ")),
                StyledString.s(" @then "),
                outcome.toDisplay(displayType, BracketedStatus.DONT_NEED_BRACKETS, expressionStyler)
            );
        }

        @Override
        public String toString()
        {
            return toDisplay(DisplayType.FULL, (s, e) -> s).toPlain();
        }

        public MatchClause _test_copy()
        {
            return new MatchClause(new CanonicalSpan(CanonicalLocation.ZERO, CanonicalLocation.ZERO), patterns, outcome); //TODO deep copy patterns
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

        @SuppressWarnings("recorded") // Because the replaced version is immediately loaded again
        public MatchClause replaceSubExpression(Expression toReplace, Expression replaceWith)
        {
            return new MatchClause(new CanonicalSpan(CanonicalLocation.ZERO, CanonicalLocation.ZERO), Utility.mapListI(patterns, p -> p.replaceSubExpression(toReplace, replaceWith)), outcome.replaceSubExpression(toReplace, replaceWith));
        }

        public CanonicalSpan getCaseLocation()
        {
            return caseLocation;
        }
    }

    /**
     * A pattern is an expression, plus an optional guard
     */
    public static class Pattern implements ExplanationSource
    {
        private final @Recorded Expression pattern;
        private final @Nullable @Recorded Expression guard;

        public Pattern(@Recorded Expression pattern, @Nullable @Recorded Expression guard)
        {
            this.pattern = pattern;
            this.guard = guard;
        }

        @Override
        public Stream<String> allVariableReferences()
        {
            if (guard == null)
                return pattern.allVariableReferences();
            else
                return Stream.concat(pattern.allVariableReferences(), guard.allVariableReferences());
        }

        /**
         * Returns pattern type, and resulting type state (including any declared vars)
         */
        public @Nullable CheckedExp check(ColumnLookup data, TypeState state, ErrorAndTypeRecorder onError) throws InternalException, UserException
        {
            final @Nullable CheckedExp rhsState = pattern.check(data, state, ExpressionKind.PATTERN, LocationInfo.UNIT_CONSTRAINED, onError);
            if (rhsState == null)
                return null;
            // No need to check expression versus pattern, either is fine, but we will require Equatable either way:
            rhsState.requireEquatable();
            
            if (guard != null)
            {
                @Nullable CheckedExp type = guard.check(data, rhsState.typeState, ExpressionKind.EXPRESSION, LocationInfo.UNIT_DEFAULT, onError);
                if (type == null || onError.recordError(guard, TypeExp.unifyTypes(TypeExp.bool(guard), type.typeExp)) == null)
                {
                    return null;
                }
                return new CheckedExp(rhsState.typeExp, type.typeState);
            }
            return rhsState;
        }

        // Returns just pattern, or pattern + guard.
        // Either way, the real result is the one in last list item. 
        @OnThread(Tag.Simulation)
        public ImmutableList<ValueResult> match(@Value Object value, EvaluateState state) throws InternalException, EvaluationException
        {
            ValueResult patternOutcome = pattern.matchAsPattern(value, state);
            // Only check guard if initial match was valid:
            if (guard != null && Utility.cast(patternOutcome.value, Boolean.class))
            {
                ValueResult guardOutcome = guard.calculateValue(patternOutcome.evaluateState);
                return ImmutableList.of(patternOutcome, guardOutcome);
            }
            return ImmutableList.of(patternOutcome);
        }

        public String save(SaveDestination saveDestination, TableAndColumnRenames renames)
        {
            return pattern.save(saveDestination, BracketedStatus.DONT_NEED_BRACKETS, renames) + (guard == null ? "" : " @given " + guard.save(saveDestination, BracketedStatus.DONT_NEED_BRACKETS, renames));
        }

        public StyledString toDisplay(DisplayType displayType, ExpressionStyler expressionStyler)
        {
            StyledString patternDisplay = pattern.toDisplay(displayType, BracketedStatus.DONT_NEED_BRACKETS, expressionStyler);
            return guard == null ? patternDisplay : StyledString.concat(patternDisplay, StyledString.s(" @given "), guard.toDisplay(displayType, BracketedStatus.DONT_NEED_BRACKETS, expressionStyler));
        }

        public @Recorded Expression getPattern()
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

        public @Nullable @Recorded Expression getGuard()
        {
            return guard;
        }

        @SuppressWarnings("recorded") // Because the replaced version is immediately loaded again
        public Pattern replaceSubExpression(Expression toReplace, Expression replaceWith)
        {
            return new Pattern(pattern.replaceSubExpression(toReplace, replaceWith), guard == null ? null : guard.replaceSubExpression(toReplace, replaceWith));
        }
    }

    private final CanonicalSpan matchLocation;
    private final @Recorded Expression expression;
    private final ImmutableList<MatchClause> clauses;
    private final CanonicalSpan endLocation;

    public MatchExpression(CanonicalSpan matchLocation, @Recorded Expression expression, ImmutableList<MatchClause> clauses, CanonicalSpan endLocation)
    {
        this.matchLocation = matchLocation;
        this.expression = expression;
        this.clauses = clauses;
        this.endLocation = endLocation;
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
    public ValueResult calculateValue(EvaluateState state) throws EvaluationException, InternalException
    {
        ImmutableList.Builder<ValueResult> subItems = ImmutableList.builderWithExpectedSize(1 + clauses.size() + 1);
        // It's type checked so can just copy first clause:
        ValueResult originalResult = fetchSubExpression(expression, state, subItems);
        @Value Object value = originalResult.value;
        for (MatchClause clause : clauses)
        {
            ValueResult patternMatch;
            try
            {
                patternMatch = clause.matches(value, state);
                subItems.add(patternMatch);
            }
            catch (EvaluationException e)
            {
                throw new EvaluationException(e, this, ExecutionType.MATCH, state, subItems.build());
            }
            if (Utility.cast(patternMatch.value, Boolean.class))
            {
                ValueResult clauseOutcomeResult = fetchSubExpression(clause.outcome, patternMatch.evaluateState,subItems);
                return result(clauseOutcomeResult.value, state, subItems.build());
            }
        }
        throw new EvaluationException(new UserException("No matching clause found in expression: \"" + save(SaveDestination.TO_STRING, BracketedStatus.NEED_BRACKETS, TableAndColumnRenames.EMPTY) + "\""), this, ExecutionType.MATCH, state, subItems.build());
    }

    @Override
    public String save(SaveDestination saveDestination, BracketedStatus surround, TableAndColumnRenames renames)
    {
        String inner = "@match " + expression.save(saveDestination, BracketedStatus.DONT_NEED_BRACKETS, renames) + clauses.stream().map(c -> c.save(saveDestination, renames)).collect(Collectors.joining("")) + " @endmatch";
        return (surround == BracketedStatus.DONT_NEED_BRACKETS) ? inner : ("(" + inner + ")");
    }

    @Override
    public StyledString toDisplay(DisplayType displayType, BracketedStatus surround, ExpressionStyler expressionStyler)
    {
        StyledString inner = StyledString.concat(StyledString.s("@match "), expression.toDisplay(displayType, BracketedStatus.DONT_NEED_BRACKETS, expressionStyler), clauses.stream().map(c -> c.toDisplay(displayType, expressionStyler)).collect(StyledString.joining("")), StyledString.s(" @endmatch"));
        return expressionStyler.styleExpression(inner, this); //(surround == BracketedStatus.DIRECT_ROUND_BRACKETED || surround == BracketedStatus.DONT_NEED_BRACKETS) ? inner : StyledString.roundBracket(inner);
    }

    @Override
    public @Nullable CheckedExp check(@Recorded MatchExpression this, ColumnLookup dataLookup, TypeState state, ExpressionKind kind, LocationInfo locationInfo, ErrorAndTypeRecorder onError) throws UserException, InternalException
    {        
        // Need to check several things:
        //   - That all of the patterns have the same type as the expression being matched
        //   - That all of the pattern guards have boolean type
        //   - That all of the outcome expressions have the same type as each other (and is what we will return)

        @Nullable CheckedExp srcType = expression.check(dataLookup, state, ExpressionKind.EXPRESSION, LocationInfo.UNIT_DEFAULT, onError);
        if (srcType == null)
            return null;
        // The Equatable checks are done on the patterns, not the source item, because e.g. if all patterns match
        // certain tuple item as @anything, we don't need Equatable on that value.

        if (clauses.isEmpty())
        {
            onError.recordError(this, StyledString.s("Must have at least one clause in a match"));
            return null;
        }
        
        // Add one extra for the srcType.  This size will be wrong if a clause
        // has multiple patterns, but it's only a hint.
        List<TypeExp> patternTypes = new ArrayList<>(1 + clauses.size());
        TypeExp[] outcomeTypes = new TypeExp[clauses.size()];
        // Includes the original source pattern:
        ImmutableList.Builder<@Recorded Expression> patternExpressions = ImmutableList.builderWithExpectedSize(patternTypes.size());
        // Note: patternTypes and patternExpressions should always be the same length.        
        patternTypes.add(srcType.typeExp);
        patternExpressions.add(expression);
        
        for (int i = 0; i < clauses.size(); i++)
        {
            patternExpressions.addAll(Utility.<Pattern, @Recorded Expression>mapList(clauses.get(i).getPatterns(), p -> p.getPattern()));
            @Nullable Pair<List<TypeExp>, TypeExp> patternAndOutcomeType = clauses.get(i).check(dataLookup, state, onError);
            if (patternAndOutcomeType == null)
                return null;
            patternTypes.addAll(patternAndOutcomeType.getFirst());
            outcomeTypes[i] = patternAndOutcomeType.getSecond();
        }
        ImmutableList<@Recorded Expression> immPatternExpressions = patternExpressions.build();
        
        for (int i = 0; i < immPatternExpressions.size(); i++)
        {
            @Recorded Expression expression = immPatternExpressions.get(i);
            List<QuickFix<Expression>> fixesForMatchingNumericUnits = ExpressionUtil.getFixesForMatchingNumericUnits(state, new TypeProblemDetails(patternTypes.stream().map(p -> Optional.of(p)).collect(ImmutableList.<Optional<TypeExp>>toImmutableList()), immPatternExpressions, i));
            if (!fixesForMatchingNumericUnits.isEmpty())
            {
                // Must show an error to get the quick fixes to show:
                onError.recordError(expression, StyledString.s("Pattern match items must have matching items"));
                onError.recordQuickFixes(expression, fixesForMatchingNumericUnits);
            }
        }
        
        if (onError.recordError(this, TypeExp.unifyTypes(patternTypes)) == null)
            return null;

        // TypeState doesn't extend outside the match expression, so we discard and return original:
        return onError.recordTypeAndError(this, TypeExp.unifyTypes(outcomeTypes), state);
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
        return expression._test_allMutationPoints().map(p -> new Pair<Expression, Function<Expression, Expression>>(p.getFirst(), e -> new MatchExpression(new CanonicalSpan(CanonicalLocation.ZERO, CanonicalLocation.ZERO), p.getSecond().apply(e), Utility.<MatchClause, MatchClause>mapListI(clauses, MatchClause::_test_copy), new CanonicalSpan(CanonicalLocation.ZERO, CanonicalLocation.ZERO))));
    }

    @Override
    public @Nullable Expression _test_typeFailure(Random r, _test_TypeVary newExpressionOfDifferentType, UnitManager unitManager) throws InternalException, UserException
    {
        return null;
    }

    @Override
    @SuppressWarnings("recorded") // Because the replaced version is immediately loaded again
    public Expression replaceSubExpression(Expression toReplace, Expression replaceWith)
    {
        if (toReplace == this)
            return replaceWith;
        else
            return new MatchExpression(new CanonicalSpan(CanonicalLocation.ZERO, CanonicalLocation.ZERO), expression.replaceSubExpression(toReplace, replaceWith), Utility.mapListI(clauses, c -> c.replaceSubExpression(toReplace, replaceWith)), new CanonicalSpan(CanonicalLocation.ZERO, CanonicalLocation.ZERO));
    }

    @Override
    public <T> T visit(ExpressionVisitor<T> visitor)
    {
        return visitor.match(this, expression, clauses);
    }

    public CanonicalSpan getEndLocation()
    {
        return endLocation;
    }
}
