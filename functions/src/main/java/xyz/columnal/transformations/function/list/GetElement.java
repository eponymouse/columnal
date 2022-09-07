package records.transformations.function.list;

import annotation.qual.Value;
import annotation.userindex.qual.UserIndex;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.transformations.expression.explanation.Explanation;
import records.transformations.expression.explanation.ExplanationLocation;
import records.transformations.expression.function.ValueFunction;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.DataTypeUtility;
import xyz.columnal.data.datatype.TypeManager;
import xyz.columnal.data.unit.Unit;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import records.transformations.function.FunctionDefinition;
import threadchecker.OnThread;
import xyz.columnal.utility.Either;
import xyz.columnal.utility.Pair;
import xyz.columnal.utility.SimulationFunction;
import xyz.columnal.utility.Utility;
import xyz.columnal.utility.Utility.ListEx;

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
