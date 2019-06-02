package records.transformations.expression;

import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.TableAndColumnRenames;
import records.data.datatype.DataType;
import records.data.unit.Unit;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.expression.function.StandardFunctionDefinition;
import records.transformations.expression.function.ValueFunction;
import records.transformations.expression.visitor.ExpressionVisitor;
import records.typeExp.MutVar;
import records.typeExp.TypeExp;
import records.typeExp.units.MutUnitVar;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.Pair;

import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Stream;

public class StandardFunction extends NonOperatorExpression
{
    private final StandardFunctionDefinition functionDefinition;
    // null if type check fails for some reason:
    private @MonotonicNonNull Pair<TypeExp, Map<String, Either<MutUnitVar, MutVar>>> type;

    public StandardFunction(StandardFunctionDefinition functionDefinition)
    {
        this.functionDefinition = functionDefinition;
    }

    @Override
    public @Nullable CheckedExp check(ColumnLookup dataLookup, TypeState typeState, ExpressionKind kind, LocationInfo locationInfo, ErrorAndTypeRecorder onError) throws UserException, InternalException
    {
        type = functionDefinition.getType(typeState.getTypeManager());
        return new CheckedExp(onError.recordTypeNN(this, type.getFirst()), typeState);
    }

    @Override
    @OnThread(Tag.Simulation)
    public ValueResult calculateValue(EvaluateState state) throws UserException, InternalException
    {
        if (type == null)
            throw new InternalException("Attempting to fetch function despite failing type check");

        @NonNull Pair<TypeExp, Map<String, Either<MutUnitVar, MutVar>>> typeFinal = type;
        return result(ValueFunction.value(functionDefinition.getInstance(state.getTypeManager(), s -> {
            Either<MutUnitVar, MutVar> typeExp = typeFinal.getSecond().get(s);
            if (typeExp == null)
                throw new InternalException("Type " + s + " cannot be found for function " + functionDefinition.getName());
            return typeExp.<Unit, DataType>mapBothEx(u -> {
                Unit concrete = u.toConcreteUnit();
                if (concrete == null)
                    throw new UserException("Could not resolve unit " + s + " to a concrete unit from " + u);
                return concrete;
            }, t -> t.toConcreteType(state.getTypeManager(), true).eitherEx(
                l -> {throw new UserException(StyledString.concat(StyledString.s("Ambiguous type for call to " + functionDefinition.getName() + " "),  l.getErrorText()));},
                t2 -> t2
            ));
        })), state);
    }

    @Override
    public String save(boolean structured, BracketedStatus surround, TableAndColumnRenames renames)
    {
        if (structured)
            return "@function " + functionDefinition.getName();
        else
            return functionDefinition.getName();
    }

    @Override
    public Stream<Pair<Expression, Function<Expression, Expression>>> _test_childMutationPoints()
    {
        return Stream.empty();
    }

    @Override
    public @Nullable Expression _test_typeFailure(Random r, _test_TypeVary newExpressionOfDifferentType, UnitManager unitManager) throws InternalException, UserException
    {
        return type == null ? null : newExpressionOfDifferentType.getDifferentType(type.getFirst());
    }

    @Override
    protected StyledString toDisplay(BracketedStatus bracketedStatus, ExpressionStyler expressionStyler)
    {
        return expressionStyler.styleExpression(StyledString.s(functionDefinition.getName()), this);
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StandardFunction that = (StandardFunction) o;
        return Objects.equals(functionDefinition.getName(), that.functionDefinition.getName());
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(functionDefinition.getName());
    }

    public String getName()
    {
        return functionDefinition.getName();
    }

    public StandardFunctionDefinition getFunction()
    {
        return functionDefinition;
    }

    @Override
    public Expression replaceSubExpression(Expression toReplace, Expression replaceWith)
    {
        return this == toReplace ? replaceWith : this;
    }

    @Override
    public <T> T visit(ExpressionVisitor<T> visitor)
    {
        return visitor.standardFunction(this, functionDefinition);
    }
}
