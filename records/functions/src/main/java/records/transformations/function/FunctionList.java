package records.transformations.function;

import com.google.common.collect.ImmutableList;
import log.Log;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.transformations.function.core.AsType;
import records.transformations.function.core.TypeOf;
import records.transformations.function.list.AnyAllNone;
import records.transformations.function.list.Combine;
import records.transformations.function.list.Count;
import records.transformations.function.list.GetElement;
import records.transformations.function.list.JoinLists;
import records.transformations.function.list.Single;
import records.transformations.function.lookup.LookupFunctions;
import utility.Pair;
import utility.Utility;

import java.util.Arrays;
import java.util.stream.Stream;

/**
 * Created by neil on 13/12/2016.
 */
public class FunctionList
{
    public static ImmutableList<FunctionDefinition> getAllFunctions(UnitManager unitManager) throws InternalException
    {
        return Utility.concatStreams(Arrays.asList(
            new Absolute(),
            new AnyAllNone.Any(),
            new AnyAllNone.All(),
            new AnyAllNone.None(),
            new AsType(),
            //new AsUnit(),
            new Combine(),
            new Count(),
            new FromString(),
            new GetElement(),
            new JoinLists(),
            new Max(),
            new Mean(),
            new Min(),
            new Not(),
            new Round(),
            new Single(),
            // TODO document and put back all these string functions:
            //new StringLeft(),
            new StringLength(),
            //new StringMid(),
            //new StringReplaceAll(),
            //new StringRight(),
            //new StringTrim(),
            //new StringWithin(),
            //new StringWithinIndex(),
            new Sum(),
            new ToString(),
            new TypeOf(),
            new Xor()
        ).stream(),
            LookupFunctions.getLookupFunctions().stream(),
            Stream.<ToTemporalFunction>of(
                // TODO document and put back all these date conversion functions:
                new ToDate()
                /*
                new ToDateTime(),
                new ToDateTimeZone(),
                new ToTime(),
                new ToTimeAndZone(),
                new ToYearMonth()
                */
        ).flatMap(t -> {
            try
            {
                return t.getTemporalFunctions(unitManager).stream();
            }
            catch (InternalException e)
            {
                Log.log(e);
                return Stream.empty();
            }
        })).collect(ImmutableList.toImmutableList());
    }

    public static @Nullable FunctionDefinition lookup(UnitManager mgr, String functionName) throws InternalException
    {
        for (FunctionDefinition functionDefinition : getAllFunctions(mgr))
        {
            if (functionDefinition.getName().equals(functionName))
                return functionDefinition;
        }
        return null;
    }
}
