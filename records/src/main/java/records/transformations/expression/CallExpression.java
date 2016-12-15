package records.transformations.expression;

import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.sosy_lab.java_smt.api.Formula;
import org.sosy_lab.java_smt.api.FormulaManager;
import records.data.ColumnId;
import records.data.RecordSet;
import records.data.TableId;
import records.data.datatype.DataType;
import records.data.unit.Unit;
import records.error.InternalException;
import records.error.UnimplementedException;
import records.error.UserException;
import records.transformations.function.FunctionDefinition;
import records.transformations.function.FunctionInstance;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.ExBiConsumer;
import utility.Pair;
import utility.Utility;

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
 * Created by neil on 11/12/2016.
 */
public class CallExpression extends Expression
{
    private final String functionName;
    private final List<Expression> params;
    private final List<Unit> units;
    @MonotonicNonNull
    private FunctionDefinition definition;
    @MonotonicNonNull
    private FunctionInstance instance;

    public CallExpression(String functionName, List<Unit> units, List<Expression> args)
    {
        this.functionName = functionName;
        this.units = new ArrayList<>(units);
        this.params = new ArrayList<>(args);
    }

    public CallExpression(String functionName, Expression... args)
    {
        this(functionName, Collections.emptyList(), Arrays.asList(args));
    }

    @Override
    public @Nullable DataType check(RecordSet data, TypeState state, ExBiConsumer<Expression, String> onError) throws UserException, InternalException
    {
        @Nullable FunctionDefinition def = state.findFunction(functionName).orElse(null);
        if (def == null)
            throw new UserException("Unknown function: " + functionName);
        this.definition = def;
        List<DataType> paramTypes = new ArrayList<>();
        for (Expression param : params)
        {
            @Nullable DataType t = param.check(data, state, onError);
            if (t == null)
                return null;
            paramTypes.add(t);
        }
        @Nullable Pair<FunctionInstance, DataType> checked = definition.typeCheck(units, paramTypes, s -> onError.accept(this, s), state.getUnitManager());
        if (checked == null)
            return null;
        this.instance = checked.getFirst();
        return checked.getSecond();
    }

    @Override
    public @OnThread(Tag.Simulation) List<Object> getValue(int rowIndex, EvaluateState state) throws UserException, InternalException
    {
        if (instance == null)
            throw new InternalException("Calling function which didn't typecheck");

        List<List<Object>> actuals = new ArrayList<>();
        for (Expression param : params)
        {
            actuals.add(param.getValue(rowIndex, state));
        }
        return instance.getValue(rowIndex, actuals);
    }

    @Override
    public Stream<ColumnId> allColumnNames()
    {
        return params.stream().flatMap(Expression::allColumnNames);
    }

    @Override
    public String save(boolean topLevel)
    {
        return functionName + "(" + params.stream().map(e -> e.save(false)).collect(Collectors.joining(", ")) + ")";
    }

    @Override
    public Formula toSolver(FormulaManager formulaManager, RecordSet src, Map<Pair<@Nullable TableId, ColumnId>, Formula> columnVariables) throws InternalException, UserException
    {
        throw new UnimplementedException();
    }

    @Override
    public Stream<Pair<Expression, Function<Expression, Expression>>> _test_childMutationPoints()
    {
        return IntStream.range(0, params.size()).mapToObj(i -> {
            ArrayList<Expression> newParams = new ArrayList<Expression>(params);
            return new Pair<Expression, Function<Expression, Expression>>(params.get(i), e -> {
                newParams.set(i, e);
                return new CallExpression(functionName, units, newParams);
            });
        });
    }

    @Override
    public @Nullable Expression _test_typeFailure(Random r, _test_TypeVary newExpressionOfDifferentType) throws InternalException, UserException
    {
        if (definition == null)
            throw new InternalException("Calling _test_typeFailure after type check failure");
        Pair<List<Unit>, List<Expression>> badParams = definition._test_typeFailure(r, newExpressionOfDifferentType);
        return new CallExpression(functionName, badParams.getFirst(), badParams.getSecond());
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        CallExpression that = (CallExpression) o;

        if (!functionName.equals(that.functionName)) return false;
        return params.equals(that.params);
    }

    @Override
    public int hashCode()
    {
        int result = functionName.hashCode();
        result = 31 * result + params.hashCode();
        return result;
    }
}
