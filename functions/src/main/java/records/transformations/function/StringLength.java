package records.transformations.function;

import annotation.qual.Value;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.TypeManager;
import records.data.unit.Unit;
import records.error.InternalException;
import records.error.UserException;
import utility.Either;
import utility.SimulationFunction;
import records.transformations.expression.function.ValueFunction;

public class StringLength extends FunctionDefinition
{
    public StringLength() throws InternalException
    {
        super("text:text length");
    }

    @Override
    public ValueFunction getInstance(TypeManager typeManager, SimulationFunction<String, Either<Unit, DataType>> paramTypes) throws InternalException
    {
        return new Instance();
    }

    private static class Instance extends ValueFunction
    {

        @Override
        public @Value Object _call() throws UserException, InternalException
        {
            @Value String str = arg(0, String.class);
            return DataTypeUtility.value(str.codePointCount(0, str.length()));
        }
    }
}
