package records.transformations.function;

import annotation.qual.Value;
import com.google.common.base.CharMatcher;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.TypeManager;
import records.data.unit.Unit;
import records.error.InternalException;
import records.error.UserException;
import utility.Either;
import utility.SimulationFunction;
import utility.Utility;
import records.data.ValueFunction;

public class StringTrim extends FunctionDefinition
{
    public StringTrim() throws InternalException
    {
        super("text:trim");
    }

    @Override
    public ValueFunction getInstance(TypeManager typeManager, SimulationFunction<String, Either<Unit, DataType>> paramTypes) throws InternalException
    {
        return new Instance();
    }

    private static class Instance extends ValueFunction
    {

        @Override
        public @Value Object call() throws UserException, InternalException
        {
            String src = arg(0, String.class);
            String trimmed = CharMatcher.whitespace().trimFrom(src);
            return DataTypeUtility.value(trimmed);
        }
    }
}
