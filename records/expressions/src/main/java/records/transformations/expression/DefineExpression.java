package records.transformations.expression;

import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.TableAndColumnRenames;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.expression.visitor.ExpressionVisitor;
import records.typeExp.TypeExp;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.ExFunction;
import utility.Pair;
import utility.Utility;

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
        public @Nullable EvaluateState evaluate(EvaluateState state) throws InternalException, UserException
        {
            ValueResult valueResult = rhsValue.calculateValue(state);
            valueResult = lhsPattern.matchAsPattern(valueResult.value, valueResult.evaluateState);
            if (Utility.cast(valueResult.value, Boolean.class))
                return valueResult.evaluateState;
            else
                return null;
        }

        public String save(boolean structured, TableAndColumnRenames renames)
        {
            return lhsPattern.save(structured, BracketedStatus.NEED_BRACKETS, renames) + " = " + rhsValue.save(structured, BracketedStatus.NEED_BRACKETS, renames);
        }

        @SuppressWarnings("recorded")
        public Definition replaceSubExpression(Expression toReplace, Expression replaceWith)
        {
            return new Definition(lhsPattern.replaceSubExpression(toReplace, replaceWith), rhsValue.replaceSubExpression(toReplace, replaceWith));
        }

        public StyledString toDisplay(ExpressionStyler expressionStyler)
        {
            return StyledString.concat(
                lhsPattern.toDisplay(BracketedStatus.NEED_BRACKETS, expressionStyler),
                StyledString.s(" = "),
                rhsValue.toDisplay(BracketedStatus.NEED_BRACKETS, expressionStyler)
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
    
    private final ImmutableList<Either<@Recorded HasTypeExpression, Definition>> defines;
    private final @Recorded Expression body;
    
    public DefineExpression(ImmutableList<Either<@Recorded HasTypeExpression, Definition>> defines, @Recorded Expression body)
    {
        this.defines = defines;
        this.body = body;
    }

    @Override
    public @Nullable CheckedExp check(ColumnLookup dataLookup, final TypeState original, ExpressionKind kind, LocationInfo locationInfo, ErrorAndTypeRecorder onError) throws UserException, InternalException
    {
        TypeState typeState = original;
        
        HashSet<String> shouldBeDeclaredInNextDefine = new HashSet<>();
        
        for (Either<@Recorded HasTypeExpression, Definition> define : defines)
        {
            TypeState typeStateThisTime = typeState;
            @Nullable CheckedExp checkEq = define.<@Nullable CheckedExp>eitherEx(hasType -> {
                if (!shouldBeDeclaredInNextDefine.add(hasType.getVarName()))
                {
                    onError.recordError(hasType, StyledString.s("Duplicate type for variable " + hasType.getVarName()));
                    return null;
                }
                return hasType.check(dataLookup, typeStateThisTime, ExpressionKind.EXPRESSION, LocationInfo.UNIT_DEFAULT, onError);
            }, equal -> {
                // We observe the declared variables by differencing TypeState before and after:
                CheckedExp checkedExp = equal.check(dataLookup, typeStateThisTime, onError);
                if (checkedExp != null)
                {
                    Set<String> declared = Sets.difference(checkedExp.typeState.getAvailableVariables(), typeStateThisTime.getAvailableVariables());
                    Set<String> typedButNotDeclared = Sets.difference(shouldBeDeclaredInNextDefine, declared);
                    if (!typedButNotDeclared.isEmpty())
                    {
                        onError.recordError(equal, StyledString.s("Type was given above for " + typedButNotDeclared.stream().collect(Collectors.joining(", ")) + " but variable(s) were not declared"));
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
            onError.recordError(defines.get(defines.size() - 1).<Expression>either(x -> x, x -> x.lhsPattern), StyledString.s("Type was given for " + shouldBeDeclaredInNextDefine.stream().collect(Collectors.joining(", ")) + " but variable(s) were not declared"));
            return null;
        }

        CheckedExp checkedBody = body.check(dataLookup, typeState, ExpressionKind.EXPRESSION, locationInfo, onError);
        if (checkedBody == null)
            return null;
        else
            return new CheckedExp(checkedBody.typeExp, original);
    }

    @Override
    public @OnThread(Tag.Simulation) ValueResult calculateValue(EvaluateState state) throws UserException, InternalException
    {
        for (Definition define : Either.getRights(defines))
        {
            @Nullable EvaluateState outcome = define.evaluate(state);
            if (outcome == null)
            {
                throw new UserException(StyledString.concat(StyledString.s("Pattern did not match: "), define.lhsPattern.toStyledString()));
            }
            state = outcome;
        }
        return body.calculateValue(state);
    }

    @Override
    public <T> T visit(ExpressionVisitor<T> visitor)
    {
        return visitor.define(this, defines, body);
    }

    @Override
    public String save(boolean structured, BracketedStatus surround, TableAndColumnRenames renames)
    {
        return defines.stream().map(e -> "@define " + e.either(x -> x.save(structured, BracketedStatus.DONT_NEED_BRACKETS, renames), x -> x.save(structured, renames))).collect(Collectors.joining(" ")) + " @then " + body.save(structured, BracketedStatus.DONT_NEED_BRACKETS, renames) + " @enddefine";
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
    protected StyledString toDisplay(BracketedStatus bracketedStatus, ExpressionStyler expressionStyler)
    {
        return expressionStyler.styleExpression(StyledString.concat(
            StyledString.s("@define "),
            defines.stream().map(e -> e.either(x -> x.toDisplay(BracketedStatus.DONT_NEED_BRACKETS, expressionStyler), x -> x.toDisplay(expressionStyler))).collect(StyledString.joining(" @define ")),
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
            return new DefineExpression(Utility.mapListI(defines, e -> e.mapBoth(x -> (HasTypeExpression)x.replaceSubExpression(toReplace, replaceWith), x -> x.replaceSubExpression(toReplace, replaceWith))), body.replaceSubExpression(toReplace, replaceWith));
    }
}
