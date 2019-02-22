package records.transformations.function.list;

import annotation.qual.Value;
import annotation.userindex.qual.UserIndex;
import records.data.ValueFunction;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.TypeManager;
import records.data.unit.Unit;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.function.FunctionDefinition;
import utility.Either;
import utility.SimulationFunction;
import utility.Utility;
import utility.Utility.ListEx;

/**
 * Created by neil on 17/01/2017.
 */
public class GetElement extends FunctionDefinition
{
    // Takes parameters: column/array, index
    public GetElement() throws InternalException
    {
        super("list:element");
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
            @Value int zeroBasedIndex = intArg(1);
            @UserIndex int userIndex = DataTypeUtility.userIndex(zeroBasedIndex);
            if (recordExplanation)
                explanation = withArgLoc(0, loc -> loc.getListElementLocation(zeroBasedIndex));
            return Utility.getAtIndex(arg(0, ListEx.class), userIndex);
        }
    }
}
