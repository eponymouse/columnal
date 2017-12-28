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

public class FromString extends FunctionDefinition
{
    public FromString()
    {
        super("from.string", Instance::new, () -> new FunctionTypes(
            new MutVar(null),
            TypeExp.fromConcrete(null, DataType.TEXT)
        ));
    }

    private static class Instance extends FunctionInstance
    {
        @Override
        @OnThread(Tag.Simulation)
        public @Value Object getValue(int rowIndex, @Value Object param) throws UserException, InternalException
        {
            return DataTypeUtility.value(true);
        }
    }
}
