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

package test.gen.backwards;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.DataType.DateTimeInfo;
import xyz.columnal.data.datatype.DataType.DateTimeInfo.DateTimeType;
import xyz.columnal.data.datatype.DataType.FlatDataTypeVisitor;
import xyz.columnal.data.datatype.NumberInfo;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.transformations.expression.DivideExpression;
import xyz.columnal.transformations.expression.NumericLiteral;
import xyz.columnal.utility.adt.Pair;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoField;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalField;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

@SuppressWarnings("recorded")
public class BackwardsTemporal extends BackwardsProvider
{
    public BackwardsTemporal(SourceOfRandomness r, RequestBackwardsExpression parent)
    {
        super(r, parent);
    }

    @Override
    public List<ExpressionMaker> terminals(DataType targetType, @Value Object targetValue) throws InternalException, UserException
    {
        return ImmutableList.of();
    }

    @Override
    public List<ExpressionMaker> deep(int maxLevels, DataType targetType, @Value Object targetValue) throws InternalException, UserException
    {
        DateTimeInfo dateTimeInfo = targetType.apply(new FlatDataTypeVisitor<@Nullable DateTimeInfo>(null) {
            @Override
            public @Nullable DateTimeInfo date(DateTimeInfo dateTimeInfo) throws InternalException, InternalException
            {
                return dateTimeInfo;
            }
        });
        
        if (dateTimeInfo == null)
            return ImmutableList.of();

        ImmutableList.Builder<ExpressionMaker> deep = ImmutableList.builder();
        
        switch (dateTimeInfo.getType())
        {
            case YEARMONTHDAY:
            {
                Pair<String, DateTimeType> convertAndType = r.choose(Arrays.asList(
                        new Pair<>("date from datetime", DateTimeType.DATETIME),
                        new Pair<>("date from datetimezoned", DateTimeType.DATETIMEZONED)));
                DataType t = DataType.date(new DateTimeInfo(convertAndType.getSecond()));
                deep.add(() -> call(convertAndType.getFirst(), parent.make(t, makeTemporalToMatch(convertAndType.getSecond(), (TemporalAccessor) targetValue), maxLevels - 1)));
                LocalDate target = (LocalDate) targetValue;
                deep.add(() -> call("date from ymd",
                        parent.make(DataType.number(new NumberInfo(getUnit("year"))), target.getYear(), maxLevels - 1),
                        parent.make(DataType.number(new NumberInfo(getUnit("month"))), target.getMonthValue(), maxLevels - 1),
                        parent.make(DataType.number(new NumberInfo(getUnit("day"))), target.getDayOfMonth(), maxLevels - 1)
                ));
                        /*
                        deep.add(() -> call("date.from.ym.day",
                            make(DataType.date(new DateTimeInfo(DateTimeType.YEARMONTH)), YearMonth.of(target.getYear(), target.getMonth()), maxLevels - 1),
                            make(DataType.number(new NumberInfo(getUnit("day"))), target.getDayOfMonth(), maxLevels - 1)
                        ));
                        */
            }
            break;
            case YEARMONTH:
            {
                Pair<String, DateTimeType> convertAndType = r.choose(Arrays.asList(
                        new Pair<>("dateym from date", DateTimeType.YEARMONTHDAY)));
                DataType t = DataType.date(new DateTimeInfo(convertAndType.getSecond()));
                deep.add(() -> call(convertAndType.getFirst(), parent.make(t, makeTemporalToMatch(convertAndType.getSecond(), (TemporalAccessor) targetValue), maxLevels - 1)));
                YearMonth target = (YearMonth) targetValue;
                deep.add(() -> call("dateym from ym",
                        parent.make(DataType.number(new NumberInfo(getUnit("year"))), target.getYear(), maxLevels - 1),
                        parent.make(DataType.number(new NumberInfo(getUnit("month"))), target.getMonthValue(), maxLevels - 1)
                ));
            }
            break;
            case TIMEOFDAY:
            {
                Pair<String, DateTimeType> convertAndType = r.choose(Arrays.asList(
                        //new Pair<>("time.from.timezoned", DateTimeType.TIMEOFDAYZONED),
                        new Pair<>("time from datetime", DateTimeType.DATETIME),
                        new Pair<>("time from datetimezoned", DateTimeType.DATETIMEZONED)));
                DataType t = DataType.date(new DateTimeInfo(convertAndType.getSecond()));
                deep.add(() -> call(convertAndType.getFirst(), parent.make(t, makeTemporalToMatch(convertAndType.getSecond(), (TemporalAccessor) targetValue), maxLevels - 1)));
                LocalTime target = (LocalTime) targetValue;
                deep.add(() -> call("time from hms",
                        parent.make(DataType.number(new NumberInfo(getUnit("hour"))), target.getHour(), maxLevels - 1),
                        parent.make(DataType.number(new NumberInfo(getUnit("minute"))), target.getMinute(), maxLevels - 1),
                        // We only generate integers in this class, so generate nanos then divide:
                        new DivideExpression(parent.make(DataType.number(new NumberInfo(getUnit("s"))), (long)target.getSecond() * 1_000_000_000L + target.getNano(), maxLevels - 1), new NumericLiteral(1_000_000_000L, null))
                ));
            }
            break;
                    /*
                    case TIMEOFDAYZONED:
                    {
                        DateTimeType dateTimeType = r.<@NonNull DateTimeType>choose(Arrays.asList(DateTimeType.DATETIMEZONED));
                        DataType t = DataType.date(new DateTimeInfo(dateTimeType));
                        deep.add(() -> call("timezoned.from.datetimezoned", make(t, makeTemporalToMatch(dateTimeType, (TemporalAccessor) targetValue), maxLevels - 1)));
                        OffsetTime target = (OffsetTime) targetValue;
                        deep.add(() -> call("timezoned",
                            make(DataType.date(new DateTimeInfo(DateTimeType.TIMEOFDAY)), target.toLocalTime(), maxLevels - 1),
                            make(DataType.TEXT, target.getOffset().toString(), maxLevels - 1)
                        ));
                    }
                        break;
                    */
            case DATETIME:
            {
                LocalDateTime target = (LocalDateTime) targetValue;
                //date+time+zone:
                deep.add(() -> call("datetime from dt",
                        parent.make(DataType.date(new DateTimeInfo(DateTimeType.YEARMONTHDAY)), target.toLocalDate(), maxLevels - 1),
                        parent.make(DataType.date(new DateTimeInfo(DateTimeType.TIMEOFDAY)), target.toLocalTime(), maxLevels - 1)
                ));
            }
            break;
            case DATETIMEZONED:
            {
                ZonedDateTime target = (ZonedDateTime) targetValue;
                //datetime+zone
                        /*
                        deep.add(() -> call("datetimezoned.from.datetime.zone",
                            make(DataType.date(new DateTimeInfo(DateTimeType.DATETIME)), target.toLocalDateTime(), maxLevels - 1),
                            make(DataType.TEXT, target.getZone().toString(), maxLevels - 1)
                        ));
                        */
                        /*
                        //date + time&zone
                        // only if using offset, not a zone:
                        if (target.getZone().equals(target.getOffset()))
                        {
                            deep.add(() -> call("datetimezoned.from.date.timezoned",
                                make(DataType.date(new DateTimeInfo(DateTimeType.YEARMONTHDAY)), target.toLocalDate(), maxLevels - 1),
                                make(DataType.date(new DateTimeInfo(DateTimeType.TIMEOFDAYZONED)), target.toOffsetDateTime().toOffsetTime(), maxLevels - 1)
                            ));
                        }*/
                //date+time+zone:
                deep.add(() -> call("datetimezoned from dtz",
                        parent.make(DataType.date(new DateTimeInfo(DateTimeType.YEARMONTHDAY)), target.toLocalDate(), maxLevels - 1),
                        parent.make(DataType.date(new DateTimeInfo(DateTimeType.TIMEOFDAY)), target.toLocalTime(), maxLevels - 1),
                        parent.make(DataType.TEXT, target.getZone().toString(), maxLevels - 1)
                ));
            }
            break;
        }


        return deep.build();
    }

    // Makes a value which, when the right fields are extracted, will give the value target
    // That is, you pass a type which is "bigger" than the intended (e.g. datetimezone)
    // and a target value (e.g. a timezoned), and it will make a datetimezone
    // value which you can downcast to your target value.
    private Temporal makeTemporalToMatch(DateTimeType type, TemporalAccessor target)
    {
        Function<TemporalField, Integer> tf = field -> {
            if (target.isSupported(field))
                return target.get(field);
            if (field.equals(ChronoField.YEAR))
                return r.nextInt(1, 9999);
            if (field.equals(ChronoField.MONTH_OF_YEAR))
                return r.nextInt(1, 12);
            if (field.equals(ChronoField.DAY_OF_MONTH))
                return r.nextInt(1, 28);
            if (field.equals(ChronoField.HOUR_OF_DAY))
                return r.nextInt(0, 23);
            if (field.equals(ChronoField.MINUTE_OF_HOUR))
                return r.nextInt(0, 59);
            if (field.equals(ChronoField.SECOND_OF_MINUTE))
                return r.nextInt(0, 59);
            if (field.equals(ChronoField.NANO_OF_SECOND))
                return r.nextInt(0, 999999999);
            if (field.equals(ChronoField.OFFSET_SECONDS))
                return r.nextInt(-12*60, 12*60) * 60;

            throw new RuntimeException("Unknown temporal field: " + field + " on type " + target);
        };

        switch (type)
        {
            case YEARMONTHDAY:
                return LocalDate.of(tf.apply(ChronoField.YEAR), tf.apply(ChronoField.MONTH_OF_YEAR), tf.apply(ChronoField.DAY_OF_MONTH));
            case YEARMONTH:
                return YearMonth.of(tf.apply(ChronoField.YEAR), tf.apply(ChronoField.MONTH_OF_YEAR));
            case TIMEOFDAY:
                return LocalTime.of(tf.apply(ChronoField.HOUR_OF_DAY), tf.apply(ChronoField.MINUTE_OF_HOUR), tf.apply(ChronoField.SECOND_OF_MINUTE), tf.apply(ChronoField.NANO_OF_SECOND));
            /*
            case TIMEOFDAYZONED:
                return OffsetTime.of(tf.apply(ChronoField.HOUR_OF_DAY), tf.apply(ChronoField.MINUTE_OF_HOUR), tf.apply(ChronoField.SECOND_OF_MINUTE), tf.apply(ChronoField.NANO_OF_SECOND), ZoneOffset.ofTotalSeconds(tf.apply(ChronoField.OFFSET_SECONDS)));*/
            case DATETIME:
                return LocalDateTime.of(tf.apply(ChronoField.YEAR), tf.apply(ChronoField.MONTH_OF_YEAR), tf.apply(ChronoField.DAY_OF_MONTH), tf.apply(ChronoField.HOUR_OF_DAY), tf.apply(ChronoField.MINUTE_OF_HOUR), tf.apply(ChronoField.SECOND_OF_MINUTE), tf.apply(ChronoField.NANO_OF_SECOND));
            case DATETIMEZONED:
                if (target instanceof ZonedDateTime)
                    return (ZonedDateTime)target; //Preserves zone name properly; this is the only type that can have a named zone
                else
                    return ZonedDateTime.of(tf.apply(ChronoField.YEAR), tf.apply(ChronoField.MONTH_OF_YEAR), tf.apply(ChronoField.DAY_OF_MONTH), tf.apply(ChronoField.HOUR_OF_DAY), tf.apply(ChronoField.MINUTE_OF_HOUR), tf.apply(ChronoField.SECOND_OF_MINUTE), tf.apply(ChronoField.NANO_OF_SECOND), ZoneId.ofOffset("", ZoneOffset.ofTotalSeconds(tf.apply(ChronoField.OFFSET_SECONDS)))).withFixedOffsetZone();
        }
        throw new RuntimeException("Cannot match " + type);
    }
}
