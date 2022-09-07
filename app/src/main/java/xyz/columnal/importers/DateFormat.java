package xyz.columnal.importers;

import xyz.columnal.data.datatype.DataType.DateTimeInfo.DateTimeType;

import java.time.format.DateTimeFormatter;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalQuery;

/**
 * Created by neil on 31/10/2016.
 */
class DateFormat
{
    public final boolean preprocessDate;
    public final DateTimeType dateTimeType;
    public final DateTimeFormatter formatter;
    public final TemporalQuery<? extends Temporal> destQuery;

    public DateFormat(DateTimeType dateTimeType, boolean preprocessDate, DateTimeFormatter dateTimeFormatter, TemporalQuery<? extends Temporal> destQuery)
    {
        this.dateTimeType = dateTimeType;
        this.preprocessDate = preprocessDate;
        this.formatter = dateTimeFormatter;
        this.destQuery = destQuery;
    }
}
