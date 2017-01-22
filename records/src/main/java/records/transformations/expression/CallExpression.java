package records.transformations.expression;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.units.qual.C;
import org.sosy_lab.java_smt.api.Formula;
import org.sosy_lab.java_smt.api.FormulaManager;
import records.data.ColumnId;
import records.data.RecordSet;
import records.data.TableId;
import records.data.datatype.DataType;
import records.data.unit.Unit;
import records.data.unit.UnitManager;
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
    private final Expression param;
    private final List<Unit> units;
    @MonotonicNonNull
    private FunctionDefinition definition;
    @MonotonicNonNull
    private FunctionInstance instance;

    public CallExpression(String functionName, List<Unit> units, Expression arg)
    {
        this.functionName = functionName;
        this.units = new ArrayList<>(units);
        this.param = arg;
    }

    public CallExpression(String functionName, Expression... args)
    {
        this(functionName, Collections.emptyList(), args.length == 1 ? args[0] : new TupleExpression(ImmutableList.copyOf(args)));
    }

    @Override
    public @Nullable DataType check(RecordSet data, TypeState state, ExBiConsumer<Expression, String> onError) throws UserException, InternalException
    {
        @Nullable FunctionDefinition def = state.findFunction(functionName).orElse(null);
        if (def == null)
            throw new UserException("Unknown function: " + functionName);
        this.definition = def;
        @Nullable DataType paramType = param.check(data, state, onError);
        if (paramType == null)
            return null;
        @Nullable Pair<FunctionInstance, DataType> checked = definition.typeCheck(units, paramType, s -> onError.accept(this, s), state.getUnitManager());
        if (checked == null)
            return null;
        this.instance = checked.getFirst();
        return checked.getSecond();
    }

    @Override
    public @OnThread(Tag.Simulation) @Value Object getValue(int rowIndex, EvaluateState state) throws UserException, InternalException
    {
        if (instance == null)
            throw new InternalException("Calling function which didn't typecheck");

        return instance.getValue(rowIndex, param.getValue(rowIndex, state));
    }

    @Override
    public Stream<ColumnId> allColumnNames()
    {
        return param.allColumnNames();
    }

    @Override
    public String save(boolean topLevel)
    {
        if (param instanceof TupleExpression)
            return functionName + param.save(false);
        else
            return functionName + "(" + param.save(true) + ")";
    }

    @Override
    public Formula toSolver(FormulaManager formulaManager, RecordSet src, Map<Pair<@Nullable TableId, ColumnId>, Formula> columnVariables) throws InternalException, UserException
    {
        throw new UnimplementedException();
    }

    @Override
    public Stream<Pair<Expression, Function<Expression, Expression>>> _test_childMutationPoints()
    {
        return param._test_allMutationPoints().map(p -> new Pair<>(p.getFirst(), newParam -> new CallExpression(functionName, units, newParam)));
    }

    @Override
    public Expression _test_typeFailure(Random r, _test_TypeVary newExpressionOfDifferentType, UnitManager unitManager) throws InternalException, UserException
    {
        if (definition == null)
            throw new InternalException("Calling _test_typeFailure after type check failure");
        Pair<List<Unit>, Expression> badParams = definition._test_typeFailure(r, newExpressionOfDifferentType, unitManager);
        return new CallExpression(functionName, badParams.getFirst(), badParams.getSecond());
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        CallExpression that = (CallExpression) o;

        if (!functionName.equals(that.functionName)) return false;
        return param.equals(that.param);
    }

    @Override
    public int hashCode()
    {
        int result = functionName.hashCode();
        result = 31 * result + param.hashCode();
        return result;
    }
}
