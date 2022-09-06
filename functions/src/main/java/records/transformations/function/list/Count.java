package records.transformations.function.list;

import annotation.qual.Value;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.TypeManager;
import records.data.unit.Unit;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import records.transformations.function.FunctionDefinition;
import xyz.columnal.utility.Either;
import xyz.columnal.utility.SimulationFunction;
import records.transformations.expression.function.ValueFunction;
import xyz.columnal.utility.Utility.ListEx;

/**
 * Created by neil on 14/01/2017.
 */
public class Count extends FunctionDefinition
{
    public Count() throws InternalException
    {
        super("list:list length");
    }

    @Override
    public ValueFunction getInstance(TypeManager typeManager, SimulationFunction<String, Either<Unit, DataType>> paramTypes) throws InternalException
    {
        return new Instance();
    }

    /*
    @Override
    public <E> Pair<List<Unit>, E> _test_typeFailure(Random r, _test_TypeVary<E> newExpressionOfDifferentType, UnitManager unitManager) throws UserException, InternalException
    {
        return new Pair<>(Collections.emptyList(), newExpressionOfDifferentType.getDifferentType(TypeExp.list(null, new MutVar(null))));
    }
    */

    private static class Instance extends ValueFunction
    {
        @Override
        public @Value Object _call() throws UserException, InternalException
        {
            return DataTypeUtility.value(arg(0, ListEx.class).size());
        }
    }
}
