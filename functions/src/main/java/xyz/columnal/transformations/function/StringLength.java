package xyz.columnal.transformations.function;

import annotation.qual.Value;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.DataTypeUtility;
import xyz.columnal.data.datatype.TypeManager;
import xyz.columnal.data.unit.Unit;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.utility.Either;
import xyz.columnal.utility.SimulationFunction;
import xyz.columnal.transformations.expression.function.ValueFunction;

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
