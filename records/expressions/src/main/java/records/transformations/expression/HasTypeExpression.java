package records.transformations.expression;

import annotation.identifier.qual.ExpressionIdentifier;
import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.TableAndColumnRenames;
import records.data.datatype.TypeManager;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.jellytype.JellyType;
import records.transformations.expression.type.TypeExpression;
import records.transformations.expression.type.TypeExpression.JellyRecorder;
import records.transformations.expression.visitor.ExpressionVisitor;
import records.transformations.expression.visitor.ExpressionVisitorFlat;
import records.typeExp.TypeExp;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;

import java.util.Objects;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * A variable name, and a fixed type it should conform to.  Like the :: operator in Haskell expressions,
 * (or like the `asTypeOf` function if you specified a type). 
 */
public class HasTypeExpression extends Expression
{
    // Var name, without the leading decorator
    private final @Recorded Expression lhsVar;
    private final @Recorded Expression rhsType;
    private @MonotonicNonNull @ExpressionIdentifier String checkedVarName;

    public HasTypeExpression(@Recorded Expression lhsVar, @Recorded Expression rhsType)
    {
        this.lhsVar = lhsVar;
        this.rhsType = rhsType;
    }

    @Override
    public @Nullable CheckedExp check(ColumnLookup dataLookup, TypeState original, ExpressionKind kind, LocationInfo locationInfo, ErrorAndTypeRecorder onError) throws UserException, InternalException
    {
        @Nullable @ExpressionIdentifier String varName = IdentExpression.getSingleIdent(lhsVar);
        if (varName == null)
            onError.recordError(lhsVar, StyledString.s("Left-hand side of :: must be a valid name"));
        
        @Nullable @Recorded TypeExpression rhsTypeExpression = rhsType.visit(new ExpressionVisitorFlat<@Nullable @Recorded TypeExpression>()
        {
            @Override
            protected @Nullable @Recorded TypeExpression makeDef(Expression expression)
            {
                onError.recordError(rhsType, StyledString.s("Right-hand side of :: must be a type{} expression"));
                return null;
            }

            @Override
            public @Nullable @Recorded TypeExpression litType(TypeLiteralExpression self, @Recorded TypeExpression type)
            {
                return type;
            }
        });
        
        if (varName == null || rhsTypeExpression == null)
            return null;
        this.checkedVarName = varName;
        
        TypeState typeState = original.addPreType(varName, rhsTypeExpression.toJellyType(original.getTypeManager(), new JellyRecorder()
        {
            @SuppressWarnings("recorded")
            @Override
            public @Recorded JellyType record(JellyType jellyType, @Recorded TypeExpression source)
            {
                return jellyType;
            }
        }).makeTypeExp(ImmutableMap.of()), (StyledString s) -> onError.<Expression>recordError(this, s));
        
        if (typeState == null)
            return null;
        
        // Our type shouldn't be directly used, but we don't want to return null, hence void:
        return new CheckedExp(onError.recordTypeNN(this, TypeExp.fromDataType(this, typeState.getTypeManager().getVoidType().instantiate(ImmutableList.of(), typeState.getTypeManager()))), typeState);
    }

    @Override
    public @OnThread(Tag.Simulation) ValueResult calculateValue(EvaluateState state) throws UserException, InternalException
    {
        throw new InternalException("Type definitions have no value");
    }

    @Override
    public <T> T visit(@Recorded HasTypeExpression this, ExpressionVisitor<T> visitor)
    {
        return visitor.hasType(this, lhsVar, rhsType);
    }

    @Override
    public String save(SaveDestination saveDestination, BracketedStatus surround, @Nullable TypeManager typeManager, TableAndColumnRenames renames)
    {
        return lhsVar.save(saveDestination, BracketedStatus.NEED_BRACKETS, typeManager, renames) + " :: " + rhsType.save(saveDestination, BracketedStatus.NEED_BRACKETS, typeManager, renames);
    }

    @Override
    public Stream<Pair<Expression, Function<Expression, Expression>>> _test_childMutationPoints()
    {
        return Stream.of();
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
        HasTypeExpression that = (HasTypeExpression) o;
        return lhsVar.equals(that.lhsVar) &&
                rhsType.equals(that.rhsType);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(lhsVar, rhsType);
    }

    @Override
    protected StyledString toDisplay(BracketedStatus bracketedStatus, ExpressionStyler expressionStyler)
    {
        return expressionStyler.styleExpression(StyledString.concat(lhsVar.toDisplay(BracketedStatus.NEED_BRACKETS, expressionStyler), StyledString.s(" :: "), rhsType.toDisplay(BracketedStatus.NEED_BRACKETS, expressionStyler)), this);
    }

    @SuppressWarnings("recorded")
    @Override
    public Expression replaceSubExpression(Expression toReplace, Expression replaceWith)
    {
        if (toReplace == this)
            return replaceWith;
        else
            return new HasTypeExpression(lhsVar.replaceSubExpression(toReplace, replaceWith), rhsType.replaceSubExpression(toReplace, replaceWith));
    }

    public @Nullable @ExpressionIdentifier String getVarName()
    {
        return checkedVarName;
    }
}
