package records.importers;

import java.time.format.DateTimeFormatter;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalQuery;

/**
 * Created by neil on 31/10/2016.
 */
class DateFormat
{
    public final String formatString;
    public final DateTimeFormatter formatter;
    public final TemporalQuery<? extends Temporal> destQuery;

    public DateFormat(String formatString, TemporalQuery<? extends Temporal> destQuery)
    {
        this.formatString = formatString;
        this.formatter = DateTimeFormatter.ofPattern(formatString);
        this.destQuery = destQuery;
    }
}
