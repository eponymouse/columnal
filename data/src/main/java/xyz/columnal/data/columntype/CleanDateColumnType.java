/*
 * Columnal: Safer, smoother data table processing.
 * Copyright (c) Neil Brown, 2016-2020, 2022.
 *
 * This file is part of Columnal.
 *
 * Columnal is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 *
 * Columnal is distributed in the hope that it will be useful, but WITHOUT 
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or 
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for 
 * more details.
 *
 * You should have received a copy of the GNU General Public License along 
 * with Columnal. If not, see <https://www.gnu.org/licenses/>.
 */

package xyz.columnal.data.columntype;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.datatype.DataType.DateTimeInfo;
import xyz.columnal.data.datatype.DataType.DateTimeInfo.DateTimeType;
import xyz.columnal.error.InternalException;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.Utility;

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
