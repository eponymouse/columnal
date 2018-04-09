package records.transformations.function;

import annotation.qual.Value;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeUtility;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Utility;

public class Xor extends FunctionDefinition
{
    public Xor()
    {
        super("xor", "xor.mini", Instance::new, DataType.BOOLEAN, DataType.tuple(DataType.BOOLEAN, DataType.BOOLEAN));
    }
    public static FunctionGroup group()
    {
        return new FunctionGroup("xor.short", new Xor());
    }

    private static class Instance extends FunctionInstance
    {

        @Override
        public @OnThread(Tag.Simulation) @Value Object getValue(int rowIndex, @Value Object param) throws UserException, InternalException
        {
            @Value Object[] params = Utility.castTuple(param, 2);
            return DataTypeUtility.value(Utility.cast(params[0], Boolean.class) ^ Utility.cast(params[1], Boolean.class));
        }
    }
}
