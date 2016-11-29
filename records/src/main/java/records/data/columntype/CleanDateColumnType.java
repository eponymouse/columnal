package records.data.columntype;

import org.checkerframework.checker.nullness.qual.NonNull;
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
    public static List<@NonNull String> DATE_FORMATS = new ArrayList<>();
    @FunctionalInterface
    private interface DateAssembler
    {
        public @Nullable String assemble(String y, String d, String m, String sep);
    }
    static {
        List<String> years = Arrays.asList("yyyy", "yy");
        List<String> months = Arrays.asList("MMM", "MM", "M");
        List<String> days = Arrays.asList("dd", "d");
        List<String> seps = Arrays.asList("/", "-", " ", ".");
        List<DateAssembler> assemblers = Arrays.asList(
            (y,d,m,x) -> y.equals("yyyy") ? y + x + m + x + d : null, // YMD; ISO style; only allowed if year is four digits
            (y,d,m,x) -> d + x + m + x + y, // DMY; most countries
            (y,d,m,x) -> m + x + d + x + y // MDY; US style.
        );

        //I make this a lot of different formats!
        for (String y : years)
            for (String d : days)
                for (String m : months)
                    for (String x : seps)
                        for (DateAssembler asm : assemblers)
                        {
                            String fmt = asm.assemble(y, d, m, x);
                            if (fmt != null)
                                DATE_FORMATS.add(fmt);
                        }
        // We allow no dividers, but only in the case where four digit years, and two digit days and months are used:
        for (DateAssembler asm : assemblers)
        {
            String fmt = asm.assemble("yyyy", "MM", "dd", "");
            if (fmt != null)
                DATE_FORMATS.add(fmt);
        }
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

    @Override
    public String toString()
    {
        return "CleanDate \"" + formatString + "\"";
    }

    public DateTimeFormatter getDateTimeFormatter()
    {
        return DateTimeFormatter.ofPattern(formatString);
    }
}
