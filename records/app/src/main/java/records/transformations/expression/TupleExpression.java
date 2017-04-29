package records.transformations.expression;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.sosy_lab.java_smt.api.Formula;
import org.sosy_lab.java_smt.api.FormulaManager;
import records.data.ColumnId;
import records.data.RecordSet;
import records.data.TableId;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeUtility;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UnimplementedException;
import records.error.UserException;
import records.gui.expressioneditor.ConsecutiveBase;
import records.gui.expressioneditor.OperandNode;
import records.gui.expressioneditor.OperatorEntry;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformFunction;
import utility.Pair;
import utility.Utility;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Created by neil on 12/01/2017.
 */
public class TupleExpression extends Expression
{
    private final ImmutableList<Expression> members;
    private @Nullable ImmutableList<DataType> types;

    public TupleExpression(ImmutableList<Expression> members)
    {
        this.members = members;
    }

    @Override
    public @Nullable DataType check(RecordSet data, TypeState state, ErrorRecorder onError) throws UserException, InternalException
    {
        @NonNull DataType[] typeArray = new DataType[members.size()];
        for (int i = 0; i < typeArray.length; i++)
        {
            @Nullable DataType t = members.get(i).check(data, state, onError);
            if (t == null)
                return null;
            typeArray[i] = t;
        }
        types = ImmutableList.copyOf(typeArray);
        return DataType.tuple(types);
    }

    @Override
    public @Nullable Pair<DataType, TypeState> checkAsPattern(boolean varAllowed, DataType srcType, RecordSet data, final TypeState state, ErrorRecorder onError) throws UserException, InternalException
    {
        if (!srcType.isTuple())
        {
            onError.recordError(this, "Cannot match non-tuple type " + srcType + " against a tuple pattern");
            return null;
        }
        if (srcType.getMemberType().size() != members.size())
        {
            onError.recordError(this, "Cannot match tuple of size " + srcType.getMemberType().size() + " against tuple pattern of size " + members.size());
            return null;
        }

        @NonNull DataType[] typeArray = new DataType[members.size()];
        @NonNull TypeState[] typeStates = new TypeState[members.size()];
        for (int i = 0; i < typeArray.length; i++)
        {
            @Nullable Pair<DataType, TypeState> t = members.get(i).checkAsPattern(varAllowed, srcType.getMemberType().get(i), data, state, onError);
            if (t == null)
                return null;
            typeArray[i] = t.getFirst();
            typeStates[i] = t.getSecond();
        }
        types = ImmutableList.copyOf(typeArray);
        @Nullable TypeState endState = TypeState.union(state, onError.recordError(this), typeStates);
        if (endState == null)
            return null;
        return new Pair<>(DataType.tuple(types), endState);
    }

    @Override
    public @Nullable EvaluateState matchAsPattern(int rowIndex, @Value Object value, final EvaluateState state) throws InternalException, UserException
    {
        if (value instanceof Object[])
        {
            @Value Object @Value[] tuple = (@Value Object @Value[]) value;
            if (tuple.length != members.size())
                throw new InternalException("Mismatch in tuple size, type is " + members.size() + " but found " + tuple.length);
            @Nullable EvaluateState curState = state;
            for (int i = 0; i < tuple.length; i++)
            {
                curState = members.get(i).matchAsPattern(rowIndex, tuple[i], curState);
                if (curState == null)
                    return null;
            }
            return curState;
        }
        throw new InternalException("Expected tuple but found " + value.getClass());
    }

    @Override
    public @OnThread(Tag.Simulation) @Value Object getValue(int rowIndex, EvaluateState state) throws UserException, InternalException
    {
        @Value Object[] values = new Object[members.size()];
        for (int i = 0; i < values.length; i++)
        {
            values[i] = members.get(i).getValue(rowIndex, state);
        }
        return DataTypeUtility.value(values);
    }

    @Override
    public Stream<ColumnId> allColumnNames()
    {
        return members.stream().flatMap(Expression::allColumnNames);
    }

    @Override
    public String save(boolean topLevel)
    {
        return "(" + members.stream().map(e -> e.save(false)).collect(Collectors.joining(", ")) + ")";
    }

    @Override
    public Formula toSolver(FormulaManager formulaManager, RecordSet src, Map<Pair<@Nullable TableId, ColumnId>, Formula> columnVariables) throws InternalException, UserException
    {
        throw new UnimplementedException();
    }

    @Override
    public Pair<List<FXPlatformFunction<ConsecutiveBase, OperandNode>>, List<FXPlatformFunction<ConsecutiveBase, OperatorEntry>>> loadAsConsecutive()
    {
        throw new RuntimeException("TODO");
    }

    @Override
    public FXPlatformFunction<ConsecutiveBase, OperandNode> loadAsSingle()
    {
        throw new RuntimeException("TODO");
    }

    @Override
    public Stream<Pair<Expression, Function<Expression, Expression>>> _test_childMutationPoints()
    {
        return IntStream.range(0, members.size()).mapToObj(i ->
            members.get(i)._test_allMutationPoints().map(p -> p.<Function<Expression, Expression>>replaceSecond(newExp -> new TupleExpression(Utility.replaceList(members, i, p.getSecond().apply(newExp)))))).flatMap(s -> s);
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

        TupleExpression that = (TupleExpression) o;

        return members.equals(that.members);
    }

    @Override
    public int hashCode()
    {
        return members.hashCode();
    }
}
