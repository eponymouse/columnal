package records.data.columntype;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataType.DateTimeInfo.DateTimeType;
import records.error.InternalException;
import utility.Either;
import utility.Utility;

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
    private final boolean preprocessDate;
    private final DateTimeType dateTimeType;

    public CleanDateColumnType(DateTimeType dateTimeType, boolean preprocessDate, DateTimeFormatter formatter, TemporalQuery<? extends Temporal> query)
    {
        this.dateTimeType = dateTimeType;
        this.preprocessDate = preprocessDate;
        this.formatter = formatter;
        this.query = query;
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        CleanDateColumnType that = (CleanDateColumnType) o;

        return formatter.toString().equals(that.formatter.toString()) && dateTimeType.equals(that.dateTimeType) && preprocessDate == that.preprocessDate;

    }

    @Override
    public int hashCode()
    {
        return 31 * formatter.hashCode() + dateTimeType.hashCode() + (preprocessDate ? 1 : 0);
    }

    // Is the year two digits?
    public boolean isShortYear()
    {
        return formatter.parse(formatter.format(LocalDate.of(1850, 1, 1))).get(ChronoField.YEAR) != 1850;
    }

    @Override
    public String toString()
    {
        return "CleanDate \"" + formatter + "\" " + dateTimeType + " " + preprocessDate;
    }

    public DateTimeFormatter getDateTimeFormatter()
    {
        return formatter;
    }

    public Either<String, TemporalAccessor> parse(@NonNull String s) throws InternalException
    {
        s = s.trim();
        try
        {
            return Either.right(getDateTimeInfo().fromParsed(getDateTimeFormatter().parse(preprocessDate ? Utility.preprocessDate(s) : s, query)));
        }
        catch (RuntimeException e)
        {
            return Either.left(s);
        }
    }

    public DateTimeInfo getDateTimeInfo()
    {
        return new DateTimeInfo(dateTimeType);
    }

    public TemporalQuery<? extends Temporal> getQuery()
    {
        return query;
    }
}
