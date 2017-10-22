package records.transformations.expression;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
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
import records.gui.expressioneditor.ExpressionNodeParent;
import records.gui.expressioneditor.OperandNode;
import records.gui.expressioneditor.OperatorEntry;
import records.gui.expressioneditor.SquareBracketedExpression;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
import utility.Utility;
import utility.Utility.ListEx;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * An array expression like [0, x, 3].  This could be called an array literal, but didn't want to confuse
 * as the items in the array don't have to be literals.  But this expression is for constructing
 * arrays of a known length from a fixed set of expressions (like [0, y] but not just "xs" which happens
 * to be of array type).
 */
public class ArrayExpression extends Expression
{
    private final ImmutableList<Expression> items;
    private @Nullable DataType type;
    private @MonotonicNonNull List<DataType> _test_originalTypes;

    public ArrayExpression(ImmutableList<Expression> items)
    {
        this.items = items;
    }

    @Override
    public @Nullable DataType check(RecordSet data, TypeState state, ErrorRecorder onError) throws UserException, InternalException
    {
        // Empty array - special case:
        if (items.isEmpty())
            return DataType.array();
        @NonNull DataType[] typeArray = new DataType[items.size()];
        for (int i = 0; i < typeArray.length; i++)
        {
            @Nullable DataType t = items.get(i).check(data, state, onError);
            if (t == null)
                return null;
            typeArray[i] = t;
        }
        this.type = DataType.checkAllSame(Arrays.asList(typeArray), onError.recordError(this));
        _test_originalTypes = Arrays.asList(typeArray);
        if (type == null)
            return null;
        return DataType.array(type);
    }

    @Override
    public @Nullable Pair<DataType, TypeState> checkAsPattern(boolean varAllowed, DataType srcType, RecordSet data, final TypeState state, ErrorRecorder onError) throws UserException, InternalException
    {
        if (!srcType.isArray())
        {
            onError.recordError(this, "Cannot match non-list type " + srcType + " against a list");
            return null;
        }
        // Empty array - special case:
        if (items.isEmpty())
            return new Pair<>(DataType.array(), state);

        if (srcType.getMemberType().isEmpty())
        {
            onError.recordError(this, "Cannot match empty list against non-empty list");
            return null;
        }

        DataType innerType = srcType.getMemberType().get(0);
        @NonNull DataType[] typeArray = new DataType[items.size()];
        @NonNull TypeState[] typeStates = new TypeState[items.size()];
        for (int i = 0; i < typeArray.length; i++)
        {
            @Nullable Pair<DataType, TypeState> t = items.get(i).checkAsPattern(varAllowed, innerType, data, state, onError);
            if (t == null)
                return null;
            typeArray[i] = t.getFirst();
            typeStates[i] = t.getSecond();
        }
        _test_originalTypes = Arrays.asList(typeArray);
        this.type = DataType.checkAllSame(Arrays.asList(typeArray), onError.recordError(this));
        if (type == null)
            return null;
        @Nullable TypeState endState = TypeState.union(state, onError.recordError(this), typeStates);
        if (endState == null)
            return null;
        return new Pair<>(DataType.array(type), endState);
    }

    @Override
    public @OnThread(Tag.Simulation) @Nullable EvaluateState matchAsPattern(int rowIndex, @Value Object value, EvaluateState state) throws InternalException, UserException
    {
        if (value instanceof ListEx)
        {
            ListEx list = (ListEx)value;
            if (list.size() != items.size())
                return null; // Not an exception, just means the value has different size to the pattern, so can't match
            @Nullable EvaluateState curState = state;
            for (int i = 0; i < items.size(); i++)
            {
                curState = items.get(i).matchAsPattern(rowIndex, list.get(i), curState);
                if (curState == null)
                    return null;
            }
            return curState;
        }
        throw new InternalException("Expected array but found " + value.getClass());
    }

    @Override
    public @OnThread(Tag.Simulation) @Value Object getValue(int rowIndex, EvaluateState state) throws UserException, InternalException
    {
        List<@Value Object> values = new ArrayList<>(items.size());
        for (Expression item : items)
        {
            values.add(item.getValue(rowIndex, state));
        }
        return DataTypeUtility.value(values);
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
    public Pair<List<SingleLoader<OperandNode<Expression>>>, List<SingleLoader<OperatorEntry<Expression, ExpressionNodeParent>>>> loadAsConsecutive(boolean implicitlyRoundBracketed)
    {
        return new Pair<>(Collections.singletonList(loadAsSingle()), Collections.emptyList());
    }

    @Override
    public SingleLoader<OperandNode<Expression>> loadAsSingle()
    {
        List<SingleLoader<OperandNode<Expression>>> loadOperands = Utility.mapList(items, x -> x.loadAsSingle());
        List<SingleLoader<OperatorEntry<Expression, ExpressionNodeParent>>> loadCommas = Utility.replicate(Math.max(items.size() - 1, 0), (p, s) -> new OperatorEntry<>(Expression.class, ",", false, p));
        return (p, s) -> new SquareBracketedExpression(ConsecutiveBase.EXPRESSION_OPS, p, SingleLoader.withSemanticParent(new Pair<>(loadOperands, loadCommas), s));
    }

    @Override
    public Stream<Pair<Expression, Function<Expression, Expression>>> _test_childMutationPoints()
    {
        return IntStream.range(0, items.size()).mapToObj(i ->
            items.get(i)._test_allMutationPoints().map(p -> p.<Function<Expression, Expression>>replaceSecond(newExp -> new ArrayExpression(Utility.replaceList(items, i, p.getSecond().apply(newExp)))))).flatMap(s -> s);
    }

    @Override
    public @Nullable Expression _test_typeFailure(Random r, _test_TypeVary newExpressionOfDifferentType, UnitManager unitManager) throws InternalException, UserException
    {
        if (items.size() <= 1)
            return null; // Can't cause a failure with 1 or less items; need 2+ to have a mismatch
        int index = r.nextInt(items.size());
        if (type == null || _test_originalTypes == null)
            throw new InternalException("Calling _test_typeFailure despite type-check failure");
        // If all items other than this one are blank arrays, won't cause type error:
        boolean hasOtherNonBlank = false;
        for (int i = 0; i < items.size(); i++)
        {
            if (i != index && (!_test_originalTypes.get(i).isArray() || !_test_originalTypes.get(i).getMemberType().isEmpty()))
                hasOtherNonBlank = true;
        }
        if (!hasOtherNonBlank)
            return null; // Won't make a failure
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

    public ImmutableList<Expression> _test_getElements()
    {
        return items;
    }
}
