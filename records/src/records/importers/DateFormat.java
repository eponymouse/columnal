package records.importers;

import java.time.format.DateTimeFormatter;

/**
 * Created by neil on 31/10/2016.
 */
class DateFormat
{
    public final String formatString;
    public final DateTimeFormatter formatter;

    public DateFormat(String formatString)
    {
        this.formatString = formatString;
        this.formatter = DateTimeFormatter.ofPattern(formatString);
    }
}
