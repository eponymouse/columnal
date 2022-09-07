package records.gui.dtf.recognisers;

import annotation.qual.ImmediateValue;
import annotation.qual.Value;
import xyz.columnal.log.Log;
import xyz.columnal.data.datatype.DataType.DateTimeInfo;
import xyz.columnal.data.datatype.DataType.DateTimeInfo.DateTimeType;
import xyz.columnal.data.datatype.DataTypeUtility;
import xyz.columnal.data.datatype.DataTypeUtility.PositionedUserException;
import xyz.columnal.data.datatype.DataTypeUtility.StringView;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import records.gui.dtf.Recogniser;
import xyz.columnal.utility.Either;
import xyz.columnal.utility.Pair;
import xyz.columnal.utility.ParseProgress;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAccessor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TemporalRecogniser extends Recogniser<@ImmediateValue TemporalAccessor>
{
    private final DateTimeType dateTimeType;

    public TemporalRecogniser(DateTimeType dateTimeType)
    {
        this.dateTimeType = dateTimeType;
    }

    @Override
    public Either<ErrorDetails, SuccessDetails<@ImmediateValue TemporalAccessor>> process(ParseProgress orig, boolean immediatelySurroundedByRoundBrackets)
    {
        try
        {
            StringView stringView = new StringView(orig);
            @ImmediateValue TemporalAccessor temporal = DataTypeUtility.parseTemporalFlexible(new DateTimeInfo(dateTimeType), stringView);
            return success(temporal, stringView.getParseProgress());
        }
        catch (PositionedUserException e)
        {
            return Either.left(new ErrorDetails(e.getStyledMessage(), e.getPosition()));
        }
    }
}
