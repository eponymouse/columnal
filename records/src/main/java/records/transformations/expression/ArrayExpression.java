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
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UnimplementedException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.ExBiConsumer;
import utility.Pair;
import utility.Utility;

import java.util.ArrayList;
import java.util.Arrays;
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
public class ArrayExpression extends Expression
{
    private final ImmutableList<Expression> items;
    private @Nullable DataType type;

    public ArrayExpression(ImmutableList<Expression> items)
    {
        this.items = items;
    }

    @Override
    public @Nullable DataType check(RecordSet data, TypeState state, ExBiConsumer<Expression, String> onError) throws UserException, InternalException
    {
        @NonNull DataType[] typeArray = new DataType[items.size()];
        for (int i = 0; i < typeArray.length; i++)
        {
            @Nullable DataType t = items.get(i).check(data, state, onError);
            if (t == null)
                return null;
            typeArray[i] = t;
        }
        this.type = DataType.checkAllSame(Arrays.asList(typeArray), s -> onError.accept(this, s));
        return type;
    }

    @Override
    public @OnThread(Tag.Simulation) @Value Object getValue(int rowIndex, EvaluateState state) throws UserException, InternalException
    {
        List<@Value Object> values = new ArrayList<>(items.size());
        for (Expression item : items)
        {
            values.add(item.getValue(rowIndex, state));
        }
        return Utility.value(values);
    }

    @Override
    public Stream<ColumnId> allColumnNames()
    {
        return items.stream().flatMap(Expression::allColumnNames);
    }

    @Override
    public String save(boolean topLevel)
    {
        return "[" + items.stream().map(e -> e.save(false)).collect(Collectors.joining(", ")) + "]";
    }

    @Override
    public Formula toSolver(FormulaManager formulaManager, RecordSet src, Map<Pair<@Nullable TableId, ColumnId>, Formula> columnVariables) throws InternalException, UserException
    {
        throw new UnimplementedException();
    }

    @Override
    public Stream<Pair<Expression, Function<Expression, Expression>>> _test_childMutationPoints()
    {
        return IntStream.range(0, items.size()).mapToObj(i ->
            items.get(i)._test_allMutationPoints().map(p -> p.<Function<Expression, Expression>>replaceSecond(newExp -> new ArrayExpression(Utility.replaceList(items, i, newExp))))).flatMap(s -> s);
    }

    @Override
    public @Nullable Expression _test_typeFailure(Random r, _test_TypeVary newExpressionOfDifferentType, UnitManager unitManager) throws InternalException, UserException
    {
        int index = r.nextInt(items.size());
        if (type == null)
            throw new InternalException("Calling _test_typeFailure despite type-check failure");
        return new ArrayExpression(Utility.replaceList(items, index, newExpressionOfDifferentType.getDifferentType(type)));
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ArrayExpression that = (ArrayExpression) o;

        return items.equals(that.items);
    }

    @Override
    public int hashCode()
    {
        return items.hashCode();
    }
}
