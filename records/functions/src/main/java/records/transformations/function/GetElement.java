package records.transformations.function;

import annotation.userindex.qual.UserIndex;
import annotation.qual.Value;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeUtility;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.function.FunctionType.AnyType;
import records.transformations.function.FunctionType.ArrayType;
import records.transformations.function.FunctionType.ExactType;
import records.transformations.function.FunctionType.TupleType;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Utility;

import java.util.Collections;
import java.util.List;

/**
 * Created by neil on 17/01/2017.
 */
public class GetElement extends FunctionDefinition
{
    public GetElement()
    {
        super("element", "element.short");
    }

    // Takes parameters: column/array, index
    @Override
    public List<FunctionType> getOverloads(UnitManager mgr) throws InternalException
    {
        return Collections.singletonList(new FunctionType(Instance::new, new TupleType(0, new ArrayType(new AnyType()), new ExactType(DataType.NUMBER)), null));
    }

    private static class Instance extends FunctionInstance
    {
        @Override
        @OnThread(Tag.Simulation)
        public @Value Object getValue(int rowIndex, @Value Object params) throws UserException, InternalException
        {
            @Value Object[] paramList = Utility.valueTuple(params, 2);
            @UserIndex int userIndex = DataTypeUtility.userIndex(paramList[1]);
            return Utility.getAtIndex(Utility.valueList(paramList[0]), userIndex);
        }
    }
}
