package records.transformations.function;

import annotation.qual.Value;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeUtility;
import records.error.InternalException;
import records.error.UserException;
import records.types.MutVar;
import records.types.TypeExp;
import threadchecker.OnThread;
import threadchecker.Tag;

public class ToString extends FunctionDefinition
{
    public ToString()
    {
        super("to.string", Instance::new, () -> new FunctionTypes(
            TypeExp.fromConcrete(null, DataType.TEXT),
            new MutVar(null)
        ));
    }
    
    private static class Instance extends FunctionInstance
    {
        @Override
        @OnThread(Tag.Simulation)
        public @Value Object getValue(int rowIndex, @Value Object param) throws UserException, InternalException
        {
            return DataTypeUtility.value("TODO");
        }
    }
}
