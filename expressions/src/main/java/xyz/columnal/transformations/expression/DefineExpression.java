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

import annotation.identifier.qual.ExpressionIdentifier;
import annotation.recorded.qual.Recorded;
import annotation.units.CanonicalLocation;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.id.TableAndColumnRenames;
import xyz.columnal.data.unit.UnitManager;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.transformations.expression.explanation.Explanation.ExecutionType;
import xyz.columnal.transformations.expression.visitor.ExpressionVisitor;
import xyz.columnal.typeExp.TypeExp;
import xyz.columnal.styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.IdentifierUtility;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.Utility;

import java.util.HashSet;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DefineExpression extends Expression
{
    public static class Definition
    {
        public final @Recorded Expression lhsPattern;
        public final @Recorded Expression rhsValue;

        public Definition(@Recorded Expression lhsPattern, @Recorded Expression rhsValue)
        {
            this.lhsPattern = lhsPattern;
            this.rhsValue = rhsValue;
        }

        public @Nullable CheckedExp check(ColumnLookup dataLookup, TypeState typeState, ErrorAndTypeRecorder onError) throws InternalException, UserException
        {
            CheckedExp rhs = rhsValue.check(dataLookup, typeState, ExpressionKind.EXPRESSION, LocationInfo.UNIT_DEFAULT, onError);
            if (rhs == null)
                return null;
            CheckedExp lhs = lhsPattern.check(dataLookup, typeState, ExpressionKind.PATTERN, LocationInfo.UNIT_DEFAULT, onError);
            if (lhs == null)
                return null;
            
            // Need to unify:
            return onError.recordTypeAndError(lhsPattern, TypeExp.unifyTypes(lhs.typeExp, rhs.typeExp), lhs.typeState);
        }

        @OnThread(Tag.Simulation)
        public @Nullable EvaluateState evaluate(EvaluateState state) throws InternalException, EvaluationException
        {
            ValueResult valueResult = rhsValue.calculateValue(state);
            valueResult = lhsPattern.matchAsPattern(valueResult.value, valueResult.evaluateState);
            if (Utility.cast(valueResult.value, Boolean.class))
                return valueResult.evaluateState;
            else
                return null;
        }

        public String save(SaveDestination saveDestination, TableAndColumnRenames renames)
        {
            return lhsPattern.save(saveDestination, BracketedStatus.NEED_BRACKETS, renames) + " = " + rhsValue.save(saveDestination, BracketedStatus.NEED_BRACKETS, renames);
        }

        @SuppressWarnings("recorded")
        public Definition replaceSubExpression(Expression toReplace, Expression replaceWith)
        {
            return new Definition(lhsPattern.replaceSubExpression(toReplace, replaceWith), rhsValue.replaceSubExpression(toReplace, replaceWith));
        }

        public StyledString toDisplay(DisplayType displayType, ExpressionStyler expressionStyler)
        {
            return StyledString.concat(
                lhsPattern.toDisplay(displayType, BracketedStatus.NEED_BRACKETS, expressionStyler),
                StyledString.s(" = "),
                rhsValue.toDisplay(displayType, BracketedStatus.NEED_BRACKETS, expressionStyler)
            );
        }

        @Override
        public boolean equals(@Nullable Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Definition that = (Definition) o;
            return lhsPattern.equals(that.lhsPattern) &&
                    rhsValue.equals(that.rhsValue);
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(lhsPattern, rhsValue);
        }
    }
    
    public static class DefineItem
    {
        public final Either<@Recorded HasTypeExpression, Definition> typeOrDefinition;
        public final CanonicalSpan trailingCommaOrThenLocation;

        public DefineItem(Either<@Recorded HasTypeExpression, Definition> typeOrDefinition, CanonicalSpan trailingCommaOrThenLocation)
        {
            this.typeOrDefinition = typeOrDefinition;
            this.trailingCommaOrThenLocation = trailingCommaOrThenLocation;
        }

        @Override
        public boolean equals(@Nullable Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            DefineItem that = (DefineItem) o;
            return typeOrDefinition.equals(that.typeOrDefinition);
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(typeOrDefinition);
        }
    }

    private final CanonicalSpan defineLocation;
    // List will not be empty for a valid define:
    private final ImmutableList<DefineItem> defines;
    private final @Recorded Expression body;
    private final CanonicalSpan endLocation;
    
    public DefineExpression(CanonicalSpan defineLocation, ImmutableList<DefineItem> defines, 
                            @Recorded Expression body,
                            CanonicalSpan endLocation)
    {
        this.defineLocation = defineLocation;
        this.defines = defines;
        this.body = body;
        this.endLocation = endLocation;
    }

    public static DefineExpression unrecorded(ImmutableList<Either<@Recorded HasTypeExpression, Definition>> defines, @Recorded Expression body)
    {
        CanonicalSpan dummy = new CanonicalSpan(CanonicalLocation.ZERO, CanonicalLocation.ZERO);
        return new DefineExpression(dummy, Utility.mapListI(defines, d -> new DefineItem(d, dummy)), body, dummy);
    }

    @Override
    public @Nullable CheckedExp check(ColumnLookup dataLookup, final TypeState original, ExpressionKind kind, LocationInfo locationInfo, ErrorAndTypeRecorder onError) throws UserException, InternalException
    {
        TypeState typeState = original;
        
        HashSet<String> shouldBeDeclaredInNextDefine = new HashSet<>();
        
        for (DefineItem defineItem : defines)
        {
            TypeState typeStateThisTime = typeState;
            Either<@Recorded HasTypeExpression, Definition> define = defineItem.typeOrDefinition;
            @Nullable CheckedExp checkEq = define.<@Nullable CheckedExp>eitherEx(hasType -> {
                CheckedExp checkedExp = hasType.check(dataLookup, typeStateThisTime, ExpressionKind.EXPRESSION, LocationInfo.UNIT_DEFAULT, onError);
                if (checkedExp == null)
                    return null;
                @ExpressionIdentifier String varName = hasType.getVarName();
                if (!shouldBeDeclaredInNextDefine.add(varName))
                {
                    onError.recordError(hasType, StyledString.s("Duplicate type for variable " + varName));
                    return null;
                }
                return checkedExp;
            }, equal -> {
                // We observe the declared variables by differencing TypeState before and after:
                CheckedExp checkedExp = equal.check(dataLookup, typeStateThisTime, onError);
                if (checkedExp != null)
                {
                    Set<String> declared = Sets.difference(checkedExp.typeState.getAvailableVariables(), typeStateThisTime.getAvailableVariables());
                    Set<String> typedButNotDeclared = Sets.difference(shouldBeDeclaredInNextDefine, declared);
                    if (!typedButNotDeclared.isEmpty())
                    {
                        onError.recordError(equal.lhsPattern, StyledString.s("Type was given above for " + typedButNotDeclared.stream().collect(Collectors.joining(", ")) + " but variable(s) were not declared"));
                        return null;
                    }
                    if (declared.isEmpty())
                    {
                        onError.recordError(equal.lhsPattern, StyledString.s("No new variables were declared"));
                        return null;
                    }
                }
                shouldBeDeclaredInNextDefine.clear();
                return checkedExp;
            });
            if (checkEq == null)
                return null;
            typeState = checkEq.typeState;
        }

        if (!shouldBeDeclaredInNextDefine.isEmpty())
        {
            onError.recordError(defines.get(defines.size() - 1).typeOrDefinition.<Expression>either(x -> x, x -> x.lhsPattern), StyledString.s("Type was given for " + shouldBeDeclaredInNextDefine.stream().collect(Collectors.joining(", ")) + " but variable(s) were not declared"));
            return null;
        }

        CheckedExp checkedBody = body.check(dataLookup, typeState, ExpressionKind.EXPRESSION, locationInfo, onError);
        if (checkedBody == null)
            return null;
        else
            return new CheckedExp(checkedBody.typeExp, original);
    }

    @Override
    public @OnThread(Tag.Simulation) ValueResult calculateValue(EvaluateState state) throws EvaluationException, InternalException
    {
        for (Definition define : Either.<@Recorded HasTypeExpression, Definition>getRights(Utility.<DefineItem, Either<@Recorded HasTypeExpression, Definition>>mapListI(defines, d -> d.typeOrDefinition)))
        {
            @Nullable EvaluateState outcome;
            try
            {
                outcome = define.evaluate(state);
                if (outcome == null)
                {
                    throw new UserException(StyledString.concat(StyledString.s("Pattern did not match: "), define.lhsPattern.toStyledString()));
                }
            }
            catch (UserException e)
            {
                throw new EvaluationException(e, this, ExecutionType.VALUE, state, ImmutableList.of());
            }
            state = outcome;
        }
        return fetchSubExpression(body, state, ImmutableList.builder());
    }

    @Override
    public <T> T visit(ExpressionVisitor<T> visitor)
    {
        return visitor.define(this, defines, body);
    }

    @Override
    public String save(SaveDestination saveDestination, BracketedStatus surround, TableAndColumnRenames renames)
    {
        StringBuilder b = new StringBuilder();
        b.append("@define ");
        for (int i = 0; i < defines.size(); i++)
        {
            if (i > 0)
                b.append(", ");
            SaveDestination latest = saveDestination;
            saveDestination = defines.get(i).typeOrDefinition.<SaveDestination>either(x -> {
                b.append(x.save(latest, BracketedStatus.DONT_NEED_BRACKETS, renames));
                return latest;
            }, x -> {
                b.append(x.save(latest, renames));
                Set<String> patternVars = x.lhsPattern.allVariableReferences().collect(ImmutableSet.<String>toImmutableSet());
                Set<String> definedVars = latest.definedNames("var").stream().<String>map(v -> v.get(0)).collect(ImmutableSet.<String>toImmutableSet());
                return latest.withNames(Utility.filterOutNulls(Sets.<String>difference(patternVars, definedVars).stream().<@Nullable @ExpressionIdentifier String>map(IdentifierUtility::asExpressionIdentifier)).<Pair<@Nullable @ExpressionIdentifier String, ImmutableList<@ExpressionIdentifier String>>>map(v -> new Pair<@Nullable @ExpressionIdentifier String, ImmutableList<@ExpressionIdentifier String>>("var", ImmutableList.of(v))).collect(ImmutableList.<Pair<@Nullable @ExpressionIdentifier String, ImmutableList<@ExpressionIdentifier String>>>toImmutableList()));
            });
        }
        b.append(" @then ");
        b.append(body.save(saveDestination, BracketedStatus.DONT_NEED_BRACKETS, renames));
        b.append(" @enddefine");
        return b.toString();
    }

    @Override
    public Stream<Pair<Expression, Function<Expression, Expression>>> _test_childMutationPoints()
    {
        return Stream.empty();
    }

    @Override
    public @Nullable Expression _test_typeFailure(Random r, _test_TypeVary newExpressionOfDifferentType, UnitManager unitManager) throws InternalException, UserException
    {
        return null;
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DefineExpression that = (DefineExpression) o;
        return defines.equals(that.defines) &&
                body.equals(that.body);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(defines, body);
    }

    @Override
    protected StyledString toDisplay(DisplayType displayType, BracketedStatus bracketedStatus, ExpressionStyler expressionStyler)
    {
        return expressionStyler.styleExpression(StyledString.concat(
            StyledString.s("@define "),
            defines.stream().map(e -> e.typeOrDefinition.either(x -> x.toDisplay(displayType, BracketedStatus.DONT_NEED_BRACKETS, expressionStyler), x -> x.toDisplay(displayType, expressionStyler))).collect(StyledString.joining(", ")),
            StyledString.s(" @then "),
            body.toStyledString(),
            StyledString.s(" @enddefine")
        ), this);
    }

    @SuppressWarnings("recorded")
    @Override
    public Expression replaceSubExpression(Expression toReplace, Expression replaceWith)
    {
        if (this == toReplace)
            return replaceWith;
        else
            return DefineExpression.unrecorded(Utility.mapListI(defines, e -> e.typeOrDefinition.mapBoth(x -> (HasTypeExpression)x.replaceSubExpression(toReplace, replaceWith), x -> x.replaceSubExpression(toReplace, replaceWith))), body.replaceSubExpression(toReplace, replaceWith));
    }

    public CanonicalSpan getEndLocation()
    {
        return endLocation;
    }
}
