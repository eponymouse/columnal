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
import xyz.columnal.data.datatype.DataTypeUtility;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.gui.dtf.Recogniser;
import xyz.columnal.log.Log;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.ParseProgress;
import xyz.columnal.utility.Utility;

public class NumberRecogniser extends Recogniser<@ImmediateValue Number>
{
    @Override
    public Either<ErrorDetails, SuccessDetails<@ImmediateValue Number>> process(ParseProgress orig, boolean immediatelySurroundedByRoundBrackets)
    {
        ParseProgress pp = orig.skipSpaces();
        String sign = "";
        if (pp.src.startsWith("+", pp.curCharIndex))
        {
            pp = pp.skip(1);
        }
        else if (pp.src.startsWith("-", pp.curCharIndex))
        {
            pp = pp.skip(1);
            sign = "-";
        }
        
        Pair<String, ParseProgress> beforeDot = consumeDigits(pp);
        
        try
        {
            if (beforeDot == null)
            {
                return error("Expected digits to start a number", pp.curCharIndex);
            }
            else if (beforeDot.getSecond().src.startsWith(".", beforeDot.getSecond().curCharIndex))
            {
                Pair<String, ParseProgress> afterDot = consumeDigits(beforeDot.getSecond().skip(1));

                if (afterDot == null)
                {
                    return error("Expected digits after decimal point", beforeDot.getSecond().curCharIndex + 1);
                }
                
                return success(sign + beforeDot.getFirst() + "." + afterDot.getFirst(), afterDot.getSecond());
            }
            else
            {
                return success(sign + beforeDot.getFirst(), beforeDot.getSecond());
            }
        }
        catch (InternalException | UserException e)
        {
            return Either.left(new ErrorDetails(e.getStyledMessage(), orig.curCharIndex));
        }
    }

    private Either<ErrorDetails, SuccessDetails<@ImmediateValue Number>> success(String src, ParseProgress pp) throws UserException, InternalException
    {
        @ImmediateValue Number number = Utility.parseNumber(src);
        String repl = src;
        try
        {
            repl = DataTypeUtility.numberToString(number, null, null);
        }
        catch (InternalException | UserException e)
        {
            // Shouldn't happen:
            Log.log(e);
        }
        return success(number, repl, pp);
    }

}
