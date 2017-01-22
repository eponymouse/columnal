package records.transformations.function;

import java.util.Arrays;
import java.util.List;

/**
 * Created by neil on 13/12/2016.
 */
public class FunctionList
{
    public static List<FunctionDefinition> FUNCTIONS = Arrays.asList(
        new Absolute(),
        new AsUnit(),
        new Count(),
        new GetElement(),
        new Mean(),
        new Round(),
        new Sum(),
        new ToDate(),
        new ToDateTime(),
        new ToDateTimeZone(),
        new ToTime(),
        new ToTimeAndZone(),
        new ToYearMonth()
    );
}
