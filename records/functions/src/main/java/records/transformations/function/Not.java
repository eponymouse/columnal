package records.transformations.function;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeUtility;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Utility;

import java.util.List;

public class Not extends FunctionDefinition
{
    public Not()
    {
        super("not", Instance::new, DataType.BOOLEAN, DataType.BOOLEAN);
    }
    
    public static FunctionGroup group()
    {
        return new FunctionGroup("not.short", new Not());
    }

    private static class Instance extends FunctionInstance
    {

        @Override
        public @OnThread(Tag.Simulation) @Value Object getValue(int rowIndex, @Value Object param) throws UserException, InternalException
        {
            return DataTypeUtility.value(!Utility.cast(param, Boolean.class));
        }
    }
}
