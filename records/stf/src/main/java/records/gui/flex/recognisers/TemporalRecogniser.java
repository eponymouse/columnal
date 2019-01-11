package records.gui.flex.recognisers;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import log.Log;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataType.DateTimeInfo.DateTimeType;
import records.error.InternalException;
import records.error.UserException;
import records.gui.flex.Recogniser;
import utility.Either;
import utility.Pair;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalField;
import java.time.temporal.TemporalQueries;
import java.time.temporal.TemporalQuery;
import java.time.temporal.UnsupportedTemporalTypeException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TemporalRecogniser extends Recogniser<@Value TemporalAccessor>
{
    private final DateTimeType dateTimeType;

    public TemporalRecogniser(DateTimeType dateTimeType)
    {
        this.dateTimeType = dateTimeType;
    }

    @Override
    public Either<ErrorDetails, SuccessDetails<@Value TemporalAccessor>> process(ParseProgress orig, boolean immediatelySurroundedByRoundBrackets)
    {
        final int year, month, day, hour, minute, second, nano;
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
                pp = monthPair.getSecond();
                if (dateTimeType.hasDay())
                {
                    pp = consumeNext("-", pp);
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
                    nano = Integer.parseInt(b.toString());
                    pp = nanoPair.getSecond();
                }
                else
                    nano = 0;
                
                if (dateTimeType.hasZoneId())
                {
                    pp = pp.skipSpaces();
                    Matcher matcher = Pattern.compile("^([A-Za-z+\\-][0-9:A-Za-z_/+\\-]+).*").matcher(pp.src.substring(pp.curCharIndex));
                    if (!matcher.matches())
                        throw new UserException("Expected time zone");
                    int end = matcher.end(1);
                    zone = pp.src.substring(pp.curCharIndex, end + pp.curCharIndex);
                    pp = pp.skip(end);
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

            final TemporalAccessor t;
            switch (dateTimeType)
            {
                case YEARMONTHDAY:
                    t = LocalDate.of(year, month, day);
                    break;
                case YEARMONTH:
                    t = YearMonth.of(year, month);
                    break;
                case TIMEOFDAY:
                    t = LocalTime.of(hour, minute, second, nano);
                    break;
                case DATETIME:
                    t = LocalDateTime.of(year, month, day, hour, minute, second, nano);
                    break;
                case DATETIMEZONED:
                    t = ZonedDateTime.of(year, month, day, hour, minute, second, nano, ZoneId.of(zone));
                    break;
                default:
                    throw new InternalException("Unhandled case: " + dateTimeType);
            }
            return success(new DateTimeInfo(dateTimeType).fromParsed(t), pp);
        }
        catch (InternalException e)
        {
            Log.log(e);
            return Either.left(new ErrorDetails(e.getStyledMessage(), orig.curCharIndex));
        }
        catch (UserException e)
        {
            return Either.left(new ErrorDetails(e.getStyledMessage(), orig.curCharIndex));
        }
        catch (NumberFormatException e)
        {
            return error(e.getLocalizedMessage(), orig.curCharIndex);
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
