package records.data.columntype;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataType.DateTimeInfo.DateTimeType;
import records.error.InternalException;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoField;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalQuery;

/**
 * Created by neil on 30/10/2016.
 */
public class CleanDateColumnType extends ColumnType
{
    // As passed to DateTimeFormatter.ofPattern
    private final DateTimeFormatter formatter;
    private final TemporalQuery<? extends Temporal> query;

    public CleanDateColumnType(DateTimeFormatter formatter, TemporalQuery<? extends Temporal> query)
    {
        this.formatter = formatter;
        this.query = query;
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        CleanDateColumnType that = (CleanDateColumnType) o;

        return formatter.equals(that.formatter);

    }

    @Override
    public int hashCode()
    {
        return formatter.hashCode();
    }

    // Is the year two digits?
    public boolean isShortYear()
    {
        return formatter.parse(formatter.format(LocalDate.of(1850, 1, 1))).get(ChronoField.YEAR) != 1850;
    }

    @Override
    public String toString()
    {
        return "CleanDate \"" + formatter + "\"";
    }

    public DateTimeFormatter getDateTimeFormatter()
    {
        return formatter;
    }

    public TemporalAccessor parse(@NonNull String s) throws InternalException
    {
        return getDateTimeInfo().fromParsed(getDateTimeFormatter().parse(s, query));
    }

    public DateTimeInfo getDateTimeInfo()
    {
        return new DateTimeInfo(DateTimeType.YEARMONTHDAY);
    }

    public TemporalQuery<? extends Temporal> getQuery()
    {
        return query;
    }
}
