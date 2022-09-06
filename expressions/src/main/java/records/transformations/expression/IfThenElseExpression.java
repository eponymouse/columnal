package records.transformations.expression;

import annotation.identifier.qual.ExpressionIdentifier;
import annotation.recorded.qual.Recorded;
import annotation.units.CanonicalLocation;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.TableAndColumnRenames;
import records.data.datatype.TypeManager;
import records.data.unit.UnitManager;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import records.transformations.expression.visitor.ExpressionVisitor;
import records.transformations.expression.visitor.ExpressionVisitorFlat;
import records.typeExp.TypeExp;
import xyz.columnal.styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.IdentifierUtility;
import xyz.columnal.utility.Pair;
import xyz.columnal.utility.Utility;

import java.util.Random;
import java.util.Set;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

/**
 * Really, this could be done in a match expression, but I suspect
 * that would confuse users.
 *
 * This *cannot* be done in an if function as originally planned,
 * because functions are eager in their arguments, and you want
 * to be lazy in the unused then/else part for situations like
 * if y <> 0 then x / y else 0
 */
public class IfThenElseExpression extends NonOperatorExpression
{
    private final CanonicalSpan ifLocation;
    private final CanonicalSpan thenLocation;
    private final CanonicalSpan elseLocation;
    private final CanonicalSpan endLocation;
    private final @Recorded Expression condition;
    private final @Recorded Expression thenExpression;
    private final @Recorded Expression elseExpression;

    public static IfThenElseExpression unrecorded(@Recorded Expression condition, @Recorded Expression thenExpression, @Recorded Expression elseExpression)
    {
        CanonicalSpan dummy = new CanonicalSpan(CanonicalLocation.ZERO, CanonicalLocation.ZERO);
        return new IfThenElseExpression(dummy, condition, dummy, thenExpression, dummy, elseExpression, dummy);
    }

    public IfThenElseExpression(CanonicalSpan ifLocation, @Recorded Expression condition, 
                                CanonicalSpan thenLocation, @Recorded Expression thenExpression, 
                                CanonicalSpan elseLocation, @Recorded Expression elseExpression,
                                CanonicalSpan endLocation)
    {
        this.ifLocation = ifLocation;
        this.thenLocation = thenLocation;
        this.elseLocation = elseLocation;
        this.endLocation = endLocation;
        this.condition = condition;
        this.thenExpression = thenExpression;
        this.elseExpression = elseExpression;
    }


    @Override
    public @Nullable CheckedExp check(@Recorded IfThenElseExpression this, ColumnLookup dataLookup, TypeState state, ExpressionKind kind, LocationInfo locationInfo, ErrorAndTypeRecorder onError) throws UserException, InternalException
    {
        Pair<@Nullable UnaryOperator<@Recorded TypeExp>, TypeState> lambda = ImplicitLambdaArg.detectImplicitLambda(this, ImmutableList.of(condition, thenExpression, elseExpression), state, onError);
        state = lambda.getSecond();
        @Nullable CheckedExp checked = checkIfThenElse(dataLookup, state, onError);
        return checked == null ? null : checked.applyToType(lambda.getFirst());
    }

    private @Nullable CheckedExp checkIfThenElse(@Recorded IfThenElseExpression this, ColumnLookup dataLookup, TypeState state, ErrorAndTypeRecorder onError) throws UserException, InternalException
    {
        @Nullable CheckedExp conditionType = condition.check(dataLookup, state, ExpressionKind.EXPRESSION, LocationInfo.UNIT_DEFAULT, onError);
        if (conditionType == null)
            return null;
        if (onError.recordError(condition, TypeExp.unifyTypes(TypeExp.bool(this), conditionType.typeExp)) == null)
        {
            return null;
        }
        @Nullable CheckedExp thenType = thenExpression.check(dataLookup, conditionType.typeState, ExpressionKind.EXPRESSION, LocationInfo.UNIT_DEFAULT, onError);
        @Nullable CheckedExp elseType = elseExpression.check(dataLookup, state, ExpressionKind.EXPRESSION, LocationInfo.UNIT_DEFAULT, onError);
        if (thenType == null || elseType == null)
            return null;

        // We show a type mismatch between then and else as an error in the else clause, effectively assuming that the then is correct: 
        return onError.recordTypeAndError(this, elseExpression, TypeExp.unifyTypes(thenType.typeExp, elseType.typeExp), state);
    }

    @Override
    public ValueResult calculateValue(EvaluateState state) throws EvaluationException, InternalException
    {
        ImmutableList<@Recorded Expression> expressions = ImmutableList.of(condition, thenExpression, elseExpression);
        if (expressions.stream().anyMatch(e -> e instanceof ImplicitLambdaArg))
        {
            return ImplicitLambdaArg.makeImplicitFunction(this, expressions, state, s -> getIfThenElseValue(s));
        }
        else
        {
            return getIfThenElseValue(state);
        }
    }

    @OnThread(Tag.Simulation)
    private ValueResult getIfThenElseValue(EvaluateState state) throws EvaluationException, InternalException
    {
        ImmutableList.Builder<ValueResult> valueResults = ImmutableList.builderWithExpectedSize(2);
        ValueResult condValState = fetchSubExpression(condition, state, valueResults);
        Boolean b = Utility.cast(condValState.value, Boolean.class);
        // We always return original state to outermost,
        // but then-branch gets state from condition:
        if (b)
        {
            ValueResult thenResult = fetchSubExpression(thenExpression, condValState.evaluateState, valueResults);
            return result(thenResult.value, state, ImmutableList.of(condValState, thenResult));
        }
        else
        {
            // Else gets original state, condition didn't pass:
            ValueResult elseResult = fetchSubExpression(elseExpression,state, valueResults);
            return result(elseResult.value, state, ImmutableList.of(condValState, elseResult));
        }
    }

    @Override
    public String save(SaveDestination saveDestination, BracketedStatus surround, TableAndColumnRenames renames)
    {
        Set<String> patternVars = findPatternVars(condition).collect(ImmutableSet.<String>toImmutableSet());
        
        Set<String> definedVars = saveDestination.definedNames("var").stream().<String>map(v -> v.get(0)).collect(ImmutableSet.<String>toImmutableSet());
        SaveDestination thenSave = saveDestination.withNames(Utility.filterOutNulls(Sets.<String>difference(patternVars, definedVars).stream().<@Nullable @ExpressionIdentifier String>map(IdentifierUtility::asExpressionIdentifier)).<Pair<@Nullable @ExpressionIdentifier String, ImmutableList<@ExpressionIdentifier String>>>map(v -> new Pair<@Nullable @ExpressionIdentifier String, ImmutableList<@ExpressionIdentifier String>>("var", ImmutableList.of(v))).collect(ImmutableList.<Pair<@Nullable @ExpressionIdentifier String, ImmutableList<@ExpressionIdentifier String>>>toImmutableList()));
        
        
        String content = "@if " + condition.save(saveDestination, BracketedStatus.DONT_NEED_BRACKETS, renames) + " @then " + thenExpression.save(thenSave, BracketedStatus.DONT_NEED_BRACKETS, renames) + " @else " + elseExpression.save(saveDestination, BracketedStatus.DONT_NEED_BRACKETS, renames) + " @endif";
        return content;
    }

    private static Stream<String> findPatternVars(@Recorded Expression conditionPart)
    {
        return conditionPart.visit(new ExpressionVisitorFlat<Stream<String>>()
        {
            // Only ands of equal expressions with patterns:
            @Override
            public Stream<String> and(AndExpression self, ImmutableList<@Recorded Expression> expressions)
            {
                return expressions.stream().flatMap(e -> findPatternVars(e));
            }

            @Override
            public Stream<String> equal(EqualExpression self, ImmutableList<@Recorded Expression> expressions, boolean lastIsPattern)
            {
                if (lastIsPattern)
                {
                    return expressions.get(1).allVariableReferences();
                }
                return Stream.of();
            }

            @Override
            protected Stream<String> makeDef(Expression expression)
            {
                return Stream.of();
            }
        });
    }

    @Override
    public StyledString toDisplay(DisplayType displayType, BracketedStatus surround, ExpressionStyler expressionStyler)
    {
        StyledString content = StyledString.concat(
            StyledString.s("@if "),
            condition.toDisplay(displayType, BracketedStatus.DONT_NEED_BRACKETS, expressionStyler),
            StyledString.s(" @then "),
            thenExpression.toDisplay(displayType, BracketedStatus.DONT_NEED_BRACKETS, expressionStyler),
            StyledString.s(" @else "),
            elseExpression.toDisplay(displayType, BracketedStatus.DONT_NEED_BRACKETS, expressionStyler),
            StyledString.s(" @endif")
        );
        return expressionStyler.styleExpression(content, this); //surround != BracketedStatus.NEED_BRACKETS ? content : StyledString.roundBracket(content);
    }


    @Override
    public Stream<Pair<Expression, Function<Expression, Expression>>> _test_childMutationPoints()
    {
        return Stream.empty(); // TODO
    }

    @Override
    public @Nullable Expression _test_typeFailure(Random r, _test_TypeVary newExpressionOfDifferentType, UnitManager unitManager) throws InternalException, UserException
    {
        return null; // TODO
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        IfThenElseExpression that = (IfThenElseExpression) o;

        if (!condition.equals(that.condition)) return false;
        if (!thenExpression.equals(that.thenExpression)) return false;
        return elseExpression.equals(that.elseExpression);
    }

    @Override
    public int hashCode()
    {
        int result = condition.hashCode();
        result = 31 * result + thenExpression.hashCode();
        result = 31 * result + elseExpression.hashCode();
        return result;
    }

    @Override
    @SuppressWarnings("recorded") // Because the replaced version is immediately loaded again
    public Expression replaceSubExpression(Expression toReplace, Expression replaceWith)
    {
        if (this == toReplace)
            return replaceWith;
        else 
            return IfThenElseExpression.unrecorded(
                condition.replaceSubExpression(toReplace, replaceWith),
                thenExpression.replaceSubExpression(toReplace, replaceWith),
                elseExpression.replaceSubExpression(toReplace, replaceWith)
            );
    }

    @Override
    public <T> T visit(ExpressionVisitor<T> visitor)
    {
        return visitor.ifThenElse(this, condition, thenExpression, elseExpression);
    }

    public CanonicalSpan getThenLocation()
    {
        return thenLocation;
    }

    public CanonicalSpan getElseLocation()
    {
        return elseLocation;
    }

    public CanonicalSpan getEndIfLocation()
    {
        return endLocation;
    }
}
