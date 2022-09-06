package records.transformations.function.list;

import annotation.qual.Value;
import annotation.userindex.qual.UserIndex;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.transformations.expression.explanation.Explanation;
import records.transformations.expression.explanation.ExplanationLocation;
import records.transformations.expression.function.ValueFunction;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.TypeManager;
import records.data.unit.Unit;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.function.FunctionDefinition;
import threadchecker.OnThread;
import utility.Either;
import utility.Pair;
import utility.SimulationFunction;
import utility.Utility;
import utility.Utility.ListEx;

import java.util.stream.Collectors;

/**
 * Created by neil on 17/01/2017.
 */
public class GetElement extends FunctionDefinition
{
    public static final String NAME = "element";
    
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
        public @Value Object _call() throws UserException, InternalException
        {
            @Value int oneBasedIndex = intArg(1);
            @UserIndex int userIndex = DataTypeUtility.userIndex(oneBasedIndex);
            addUsedLocations(locs -> {
                ExplanationLocation resultLoc = locs.get(0).getListElementLocation(oneBasedIndex - 1);
                if (resultLoc != null)
                    setResultIsLocation(resultLoc);
                return Utility.streamNullable(resultLoc);
            });
            return Utility.getAtIndex(arg(0, ListEx.class), userIndex);
        }
    }
}
