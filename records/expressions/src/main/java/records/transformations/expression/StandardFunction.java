package records.transformations.expression;

import annotation.qual.Value;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.TableAndColumnRenames;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeUtility;
import records.data.unit.Unit;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.gui.expressioneditor.ExpressionSaver;
import records.gui.expressioneditor.GeneralExpressionEntry;
import records.loadsave.OutputBuilder;
import records.transformations.function.FunctionDefinition;
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
    private final FunctionDefinition functionDefinition;
    // null if type check fails for some reason:
    private @MonotonicNonNull Pair<TypeExp, Map<String, Either<MutUnitVar, MutVar>>> type;

    public StandardFunction(FunctionDefinition functionDefinition)
    {
        this.functionDefinition = functionDefinition;
    }

    @Override
    public @Nullable CheckedExp check(TableLookup dataLookup, TypeState typeState, ErrorAndTypeRecorder onError) throws UserException, InternalException
    {
        type = functionDefinition.getType(typeState.getTypeManager());
        return new CheckedExp(type.getFirst(), typeState, ExpressionKind.EXPRESSION);
    }

    @Override
    @OnThread(Tag.Simulation)
    public Pair<@Value Object, EvaluateState> getValue(EvaluateState state) throws UserException, InternalException
    {
        if (type == null)
            throw new InternalException("Attempting to fetch function despite failing type check");

        @NonNull Pair<TypeExp, Map<String, Either<MutUnitVar, MutVar>>> typeFinal = type;
        return new Pair<>(DataTypeUtility.value(functionDefinition.getInstance(state.getTypeManager(), s -> {
            Either<MutUnitVar, MutVar> typeExp = typeFinal.getSecond().get(s);
            if (typeExp == null)
                throw new InternalException("Type " + s + " cannot be found for function " + functionDefinition.getName());
            return typeExp.<Unit, DataType>mapBothEx(u -> {
                Unit concrete = u.toConcreteUnit();
                if (concrete == null)
                    throw new UserException("Could not resolve unit " + s + " to a concrete unit from " + u);
                return concrete;
            }, t -> t.toConcreteType(state.getTypeManager()).eitherEx(
                l -> {throw new UserException(StyledString.concat(StyledString.s("Ambiguous type for call to " + functionDefinition.getName() + " "),  l.getErrorText()));},
                t2 -> t2
            ));
        })), state);
    }

    @Override
    public Stream<ColumnReference> allColumnReferences()
    {
        return Stream.empty();
    }

    @Override
    public String save(BracketedStatus surround, TableAndColumnRenames renames)
    {
        return "@function " + OutputBuilder.quotedIfNecessary(functionDefinition.getName());
    }

    @Override
    public Stream<SingleLoader<Expression, ExpressionSaver>> loadAsConsecutive(BracketedStatus bracketedStatus)
    {
        return Stream.of(GeneralExpressionEntry.load(this));
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
    protected StyledString toDisplay(BracketedStatus bracketedStatus)
    {
        return StyledString.s(functionDefinition.getName());
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

    public FunctionDefinition getFunction()
    {
        return functionDefinition;
    }
}
