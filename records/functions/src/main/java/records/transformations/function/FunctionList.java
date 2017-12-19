package records.transformations.function;

import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.Arrays;
import java.util.List;

/**
 * Created by neil on 13/12/2016.
 */
public class FunctionList
{
    public static List<FunctionGroup> FUNCTIONS = Arrays.asList(
        Absolute.group(),
        AsUnit.group(),
        Count.group(),
        GetElement.group(),
        Max.group(),
        Mean.group(),
        Min.group(),
        Not.group(),
        Round.group(),
        StringLeft.group(),
        StringLength.group(),
        StringMid.group(),
        StringReplaceAll.group(),
        StringRight.group(),
        StringTrim.group(),
        StringWithin.group(),
        StringWithinIndex.group(),
        Sum.group(),
        ToDate.group(),
        ToDateTime.group(),
        ToDateTimeZone.group(),
        ToTime.group(),
        ToTimeAndZone.group(),
        ToYearMonth.group()
    );

    public static @Nullable FunctionGroup lookup(String functionName)
    {
        return FUNCTIONS.stream().filter(f -> f.getName().equals(functionName)).findFirst().orElse(null);
    }
}
