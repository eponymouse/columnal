package records.transformations.function.list;

import annotation.qual.Value;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeUtility;
import records.data.unit.Unit;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.function.FunctionDefinition;
import records.transformations.function.FunctionGroup;
import records.types.MutVar;
import records.types.TypeCons;
import records.types.TypeExp;
import utility.Pair;
import utility.Utility;
import utility.ValueFunction;

import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Created by neil on 14/01/2017.
 */
public class Count extends FunctionDefinition
{
    public Count()
    {
        super("list length", "list.length.mini", Instance::new, DataType.NUMBER, DataType.array());
    }

    public static FunctionGroup group()
    {
        return new FunctionGroup("count.short", new Count());
    }
    
    @Override
    public <E> Pair<List<Unit>, E> _test_typeFailure(Random r, _test_TypeVary<E> newExpressionOfDifferentType, UnitManager unitManager) throws UserException, InternalException
    {
        return new Pair<>(Collections.emptyList(), newExpressionOfDifferentType.getDifferentType(TypeExp.list(null, new MutVar(null))));
    }

    private static class Instance extends ValueFunction
    {
        @Override
        public @Value Object call(@Value Object param) throws UserException, InternalException
        {
            return DataTypeUtility.value(Utility.valueList(param).size());
        }
    }
}
