package records.gui.flex.recognisers;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import log.Log;
import org.checkerframework.checker.nullness.qual.NonNull;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataType.DateTimeInfo.DateTimeType;
import records.error.InternalException;
import records.error.UserException;
import records.gui.flex.Recogniser;
import utility.Either;
import utility.Pair;

import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalField;
import java.time.temporal.TemporalQueries;
import java.time.temporal.TemporalQuery;
import java.time.temporal.UnsupportedTemporalTypeException;
import java.util.regex.Pattern;

public class TemporalRecogniser extends Recogniser<@Value TemporalAccessor>
{
    private final DateTimeType dateTimeType;

    public TemporalRecogniser(DateTimeType dateTimeType)
    {
        this.dateTimeType = dateTimeType;
    }

    @Override
    public Either<ErrorDetails, SuccessDetails<@Value TemporalAccessor>> process(ParseProgress orig)
    {
        final int year, month, day, hour, minute, second;
        final long nano;
        final String zone;
        try
        {
            ParseProgress pp = orig.skipSpaces();
            if (dateTimeType.hasYearMonth())
            {
                Pair<Integer, ParseProgress> yearPair = consumeInt(pp);
                year = yearPair.getFirst();
                pp = consumeNext("-", yearPair.getSecond());
                Pair<Integer, ParseProgress> monthPair = consumeInt(pp);
                month = monthPair.getFirst();
                if (dateTimeType.hasDay())
                {
                    pp = consumeNext("-", monthPair.getSecond());
                    Pair<Integer, ParseProgress> dayPair = consumeInt(pp);
                    day = dayPair.getFirst();
                    pp = dateTimeType.hasTime() ? consumeNext(" ", dayPair.getSecond()).skipSpaces() : dayPair.getSecond();
                }
                else
                    day = 0;
            }
            else
            {
                year = month = day = 0;
            }
            
            if (dateTimeType.hasTime())
            {
                Pair<Integer, ParseProgress> hourPair = consumeInt(pp);
                hour = hourPair.getFirst();
                pp = consumeNext(":", hourPair.getSecond());
                Pair<Integer, ParseProgress> minutePair = consumeInt(pp);
                minute = minutePair.getFirst();
                pp = consumeNext(":", minutePair.getSecond());
                Pair<Integer, ParseProgress> secondPair = consumeInt(pp);
                second = secondPair.getFirst();
                pp = secondPair.getSecond();
                
                if (pp.src.startsWith(".", pp.curCharIndex))
                {
                    Pair<String, ParseProgress> nanoPair = consumeDigits(pp.skip(1));
                    if (nanoPair == null)
                        throw new UserException("Expected digits after \".\"");
                    StringBuilder b = new StringBuilder(nanoPair.getFirst().substring(0, Math.min(9, nanoPair.getFirst().length())));
                    while (b.length() < 9)
                        b.append('0');
                    nano = Long.parseLong(b.toString());
                    pp = nanoPair.getSecond();
                }
                else
                    nano = 0;
                
                if (dateTimeType.hasZoneId())
                {
                    pp = pp.skipSpaces();
                    int end = Pattern.compile("^[A-Za-z][A-Za-z_/+-]+").matcher(pp.src.substring(pp.curCharIndex)).end();
                    zone = pp.src.substring(pp.curCharIndex, end);
                    pp = pp.skip(end - pp.curCharIndex);
                }
                else
                    zone = "";
            }
            else
            {
                hour = minute = second = 0;
                nano = 0;
                zone = "";
            }

            return success(new DateTimeInfo(dateTimeType).fromParsed(new TemporalAccessor()
            {
                @Override
                public boolean isSupported(TemporalField field)
                {
                    if (field instanceof ChronoField)
                    {
                        switch (((ChronoField) field))
                        {
                            case YEAR:
                            case MONTH_OF_YEAR:
                                return dateTimeType.hasYearMonth();
                            case DAY_OF_MONTH:
                                return dateTimeType.hasDay();
                            case HOUR_OF_DAY:
                            case MINUTE_OF_HOUR:
                            case SECOND_OF_MINUTE:
                            case NANO_OF_SECOND:
                                return dateTimeType.hasTime();
                        }
                    }
                    return false;
                }

                @Override
                public long getLong(TemporalField field)
                {
                    if (field instanceof ChronoField)
                    {
                        switch (((ChronoField) field))
                        {
                            case YEAR: return year;
                            case MONTH_OF_YEAR: return month;
                            case DAY_OF_MONTH: return day;
                            case HOUR_OF_DAY: return hour;
                            case MINUTE_OF_HOUR: return minute;
                            case SECOND_OF_MINUTE: return second;
                            case NANO_OF_SECOND: return nano;
                        }
                    }
                    throw new UnsupportedTemporalTypeException("Unsupported: " + field);
                }

                @Override
                @SuppressWarnings("unchecked")
                public <R> R query(TemporalQuery<R> query)
                {
                    if (query.equals(TemporalQueries.zone()) || query.equals(TemporalQueries.zoneId()))
                    {
                        return (R)zone;
                    }
                    return query.queryFrom(this);
                }
            }), pp);
        }
        catch (InternalException e)
        {
            Log.log(e);
            return Either.left(new ErrorDetails(e.getStyledMessage()));
        }
        catch (UserException e)
        {
            return Either.left(new ErrorDetails(e.getStyledMessage()));
        }
        catch (NumberFormatException e)
        {
            return error(e.getLocalizedMessage());
        }
    }

    private ParseProgress consumeNext(String s, ParseProgress pp) throws UserException
    {
        ParseProgress after = pp.consumeNext(s);
        if (after == null)
            throw new UserException("Expected \"" + s + "\"");
        return after;
    }

    public Pair<Integer, ParseProgress> consumeInt(ParseProgress pp) throws UserException
    {
        Pair<String, ParseProgress> pair = consumeDigits(pp);
        if (pair == null)
            throw new UserException("Expected number");
        return pair.mapFirst(Integer::parseInt);
    }
}
