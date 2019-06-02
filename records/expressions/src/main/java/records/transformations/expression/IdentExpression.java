package records.transformations.expression;

import annotation.identifier.qual.ExpressionIdentifier;
import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.TableAndColumnRenames;
import records.data.datatype.DataTypeUtility;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.grammar.GrammarUtility;
import records.transformations.expression.explanation.Explanation.ExecutionType;
import records.transformations.expression.visitor.ExpressionVisitor;
import records.typeExp.MutVar;
import records.typeExp.TypeExp;
import styled.StyledString;
import utility.Pair;

import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * A plain identifier.  If it resolves to a variable, it's a variable-use.  If not, it's an unresolved identifier.
 * 
 * IdentExpression differs from InvalidIdentExpression
 * in that IdentExpression is always *syntactically* valid,
 * whereas InvalidIdentExpression is always syntactically
 * *invalid*, because it does not parse as an ident
 * and given its position, it cannot be treated as an
 * operator part of the expression (e.g. a trailing +
 * with no following operand)
 */
public class IdentExpression extends NonOperatorExpression
{
    // TODO add resolver listener
    private final @ExpressionIdentifier String text;
    private @MonotonicNonNull Boolean isDeclaration;

    public IdentExpression(@ExpressionIdentifier String text)
    {
        this.text = text;
    }

    @Override
    public @Nullable CheckedExp check(ColumnLookup dataLookup, TypeState original, ExpressionKind kind, LocationInfo locationInfo, ErrorAndTypeRecorder onError) throws UserException, InternalException
    {
        // I think should now be impossible:
        if (!GrammarUtility.validIdentifier(text))
        {
            onError.recordError(this, StyledString.s("Invalid identifier: \"" + text + "\""));
            return null;
        }
        
        isDeclaration = false;
        @Nullable TypeState state = original;
        List<TypeExp> varType = state.findVarType(text);
        if (varType == null)
        {
            if (kind == ExpressionKind.PATTERN)
            {
                varType = ImmutableList.of(new MutVar(this));
                state = state.add(text, varType.get(0), s -> onError.recordError(this, s));
                if (state == null)
                    return null;
                isDeclaration = true;
            }
            else
            {
                onError.recordError(this, StyledString.s("Unknown name: \"" + text + "\""));
                return null;
            }
        }
        // If they're trying to use a variable with many types, it justifies us trying to unify all the types:
        return onError.recordTypeAndError(this, TypeExp.unifyTypes(varType), state);
    }

    @Override
    public ValueResult matchAsPattern(@Value Object value, EvaluateState state) throws InternalException, UserException
    {
        if (isDeclaration == null)
            throw new InternalException("Calling matchAsPattern on variable without typecheck");
        else if (isDeclaration)
            return explanation(DataTypeUtility.value(true), ExecutionType.MATCH, state.add(text, value), ImmutableList.of(), ImmutableList.of(), false);
        else
            return super.matchAsPattern(value, state);
    }

    @Override
    public ValueResult calculateValue(EvaluateState state) throws UserException, InternalException
    {
        if (isDeclaration == null)
            throw new InternalException("Calling getValue on variable without typecheck");
        else if (isDeclaration)
            throw new InternalException("Calling getValue on variable declaration (should only call matchAsPattern)");
        else
            return result(state.get(text), state);
    }

    @Override
    public boolean hideFromExplanation(boolean skipIfTrivial)
    {
        // We are a trivial match, no point saying _foo matched successfully if
        // we appear inside a tuple, etc.
        if (isDeclaration != null && isDeclaration)
            return skipIfTrivial;
        else
            return super.hideFromExplanation(skipIfTrivial);
    }

    @Override
    public String save(boolean structured, BracketedStatus surround, TableAndColumnRenames renames)
    {
        return text;
    }

    @Override
    protected StyledString toDisplay(BracketedStatus bracketedStatus, ExpressionStyler expressionStyler)
    {
        return expressionStyler.styleExpression(StyledString.s(text), this);
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
        return o instanceof IdentExpression && text.equals(((IdentExpression)o).text);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(text);
    }

    public @ExpressionIdentifier String getText()
    {
        return text;
    }

    @Override
    public Expression replaceSubExpression(Expression toReplace, Expression replaceWith)
    {
        return this == toReplace ? replaceWith : this;
    }

    @Override
    public <T> T visit(ExpressionVisitor<T> visitor)
    {
        return visitor.ident(this, text);
    }

    /**
     * Only valid to call after type-checking!  Before that we can't know.
     */
    public boolean isVarDeclaration()
    {
        return isDeclaration != null && isDeclaration;
    }
}
