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

package xyz.columnal.gui.dtf.recognisers;

import annotation.qual.ImmediateValue;
import xyz.columnal.log.Log;
import xyz.columnal.data.datatype.DataType.DateTimeInfo;
import xyz.columnal.data.datatype.DataType.DateTimeInfo.DateTimeType;
import xyz.columnal.data.datatype.DataTypeUtility;
import xyz.columnal.data.datatype.DataTypeUtility.PositionedUserException;
import xyz.columnal.data.datatype.DataTypeUtility.StringView;
import xyz.columnal.error.InternalException;
import xyz.columnal.gui.dtf.Recogniser;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.ParseProgress;

import java.time.temporal.TemporalAccessor;

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
            int start = orig.curCharIndex;
            StringView stringView = new StringView(orig);
            DateTimeInfo dateTimeInfo = new DateTimeInfo(dateTimeType);
            @ImmediateValue TemporalAccessor temporal = DataTypeUtility.parseTemporalFlexible(dateTimeInfo, stringView);
            String replacementText = orig.src.substring(start, stringView.getPosition());
            try
            {
                replacementText = DataTypeUtility.temporalToString(temporal, null);
            }
            catch (InternalException e)
            {
                Log.log(e);
            }
            return success(temporal, replacementText, stringView.getParseProgress());
        }
        catch (PositionedUserException e)
        {
            return Either.left(new ErrorDetails(e.getStyledMessage(), e.getPosition()));
        }
    }
}
