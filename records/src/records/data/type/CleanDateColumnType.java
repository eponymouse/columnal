package records.data.type;

import org.checkerframework.checker.nullness.qual.Nullable;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by neil on 30/10/2016.
 */
public class CleanDateColumnType extends ColumnType
{
    public static List<String> DATE_FORMATS = new ArrayList<>();
    @FunctionalInterface
    private interface DateAssembler
    {
        public String assemble(String y, String d, String m, String sep);
    }
    static {
        List<String> years = Arrays.asList("yyyy", "yy");
        List<String> months = Arrays.asList("MMM", "MM", "M");
        List<String> days = Arrays.asList("dd", "d");
        List<String> seps = Arrays.asList("/", "-", " ", ".");
        List<DateAssembler> assemblers = Arrays.asList(
            (y,d,m,x) -> y + x + m + x + d, // YMD; ISO style
            (y,d,m,x) -> d + x + m + x + y, // DMY; most countries
            (y,d,m,x) -> m + x + d + x + y // MDY; US style.
        );

        //I make this a lot of different formats!
        for (String y : years)
            for (String d : days)
                for (String m : months)
                    for (String x : seps)
                        for (DateAssembler asm : assemblers)
                            DATE_FORMATS.add(asm.assemble(y, d, m, x));
        // We allow no dividers, but only in the case where four digit years, and two digit days and months are used:
        for (DateAssembler asm : assemblers)
            DATE_FORMATS.add(asm.assemble("yyyy", "MM", "dd", ""));
    }

    // As passed to DateTimeFormatter.ofPattern
    private final String formatString;

    public CleanDateColumnType(String formatString)
    {
        this.formatString = formatString;
    }

    @Override
    public boolean isDate()
    {
        return true;
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        CleanDateColumnType that = (CleanDateColumnType) o;

        return formatString.equals(that.formatString);

    }

    @Override
    public int hashCode()
    {
        return formatString.hashCode();
    }

    public DateTimeFormatter getDateTimeFormatter()
    {
        return DateTimeFormatter.ofPattern(formatString);
    }
}
