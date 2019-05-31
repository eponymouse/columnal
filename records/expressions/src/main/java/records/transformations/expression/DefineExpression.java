package records.transformations.expression;

import annotation.identifier.qual.ExpressionIdentifier;
import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.TableAndColumnRenames;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.expression.visitor.ExpressionVisitor;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.ExFunction;
import utility.Pair;
import utility.Utility;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DefineExpression extends Expression
{
    private final ImmutableList<Either<@Recorded HasTypeExpression, @Recorded EqualExpression>> defines;
    private final @Recorded Expression body;

    public DefineExpression(ImmutableList<Either<@Recorded HasTypeExpression, @Recorded EqualExpression>> defines, @Recorded Expression body)
    {
        this.defines = defines;
        this.body = body;
    }

    @Override
    public @Nullable CheckedExp check(ColumnLookup dataLookup, final TypeState original, LocationInfo locationInfo, ErrorAndTypeRecorder onError) throws UserException, InternalException
    {
        TypeState typeState = original;
        
        HashSet<String> shouldBeDeclaredInNextDefine = new HashSet<>();
        
        for (Either<@Recorded HasTypeExpression, @Recorded EqualExpression> define : defines)
        {
            TypeState typeStateThisTime = typeState;
            ExFunction<@Recorded Expression, @Nullable CheckedExp> typeCheck = e -> e.check(dataLookup, typeStateThisTime, locationInfo, onError);
            @Nullable CheckedExp checkEq = define.<@Nullable CheckedExp>eitherEx(hasType -> {
                if (!shouldBeDeclaredInNextDefine.add(hasType.getVarName()))
                {
                    onError.recordError(hasType, StyledString.s("Duplicate type for variable " + hasType.getVarName()));
                    return null;
                }
                return hasType.check(dataLookup, typeStateThisTime, locationInfo, onError);
            }, equal -> {
                // We observe the declared variables by differencing TypeState before and after:
                CheckedExp checkedExp = equal.check(dataLookup, typeStateThisTime, locationInfo, onError);
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
            onError.recordError(defines.get(defines.size() - 1).<Expression>either(x -> x, x -> x), StyledString.s("Type was given for " + shouldBeDeclaredInNextDefine.stream().collect(Collectors.joining(", ")) + " but variable(s) were not declared"));
            return null;
        }

        CheckedExp checkedBody = body.check(dataLookup, typeState, locationInfo, onError);
        if (checkedBody == null)
            return null;
        else
            return new CheckedExp(checkedBody.typeExp, original, checkedBody.expressionKind);
    }

    @Override
    public @OnThread(Tag.Simulation) ValueResult calculateValue(EvaluateState state) throws UserException, InternalException
    {
        for (EqualExpression define : Either.getRights(defines))
        {
            ValueResult outcome = define.calculateValue(state);
            if (Utility.cast(outcome.value, Boolean.class).booleanValue() == false)
            {
                throw new UserException(StyledString.concat(StyledString.s("Pattern did not match: "), define.toStyledString()));
            }
            state = outcome.evaluateState;
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
        return defines.stream().map(e -> "@define " + e.either(x -> x, x -> x).save(structured, BracketedStatus.DONT_NEED_BRACKETS, renames)).collect(Collectors.joining(" ")) + " @in " + body.save(structured, BracketedStatus.DONT_NEED_BRACKETS, renames) + " @endin";
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
            defines.stream().map(e -> e.either(x -> x, x -> x).toStyledString()).collect(StyledString.joining(" @define ")),
            StyledString.s(" @in "),
            body.toStyledString(),
            StyledString.s(" @endin")
        ), this);
    }

    @SuppressWarnings("recorded")
    @Override
    public Expression replaceSubExpression(Expression toReplace, Expression replaceWith)
    {
        if (this == toReplace)
            return replaceWith;
        else
            return new DefineExpression(Utility.mapListI(defines, e -> e.mapBoth(x -> (HasTypeExpression)x.replaceSubExpression(toReplace, replaceWith), x -> (EqualExpression)x.replaceSubExpression(toReplace, replaceWith))), body.replaceSubExpression(toReplace, replaceWith));
    }
}
