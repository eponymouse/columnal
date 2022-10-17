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
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.datatype.DataTypeUtility;
import xyz.columnal.gui.dtf.Recogniser;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.ParseProgress;

public class BooleanRecogniser extends Recogniser<@ImmediateValue Boolean>
{
    @Override
    public Either<ErrorDetails, SuccessDetails<@ImmediateValue Boolean>> process(ParseProgress orig, boolean immediatelySurroundedByRoundBrackets)
    {
        @Nullable ParseProgress pp = orig.consumeNextIC("true");
        if (pp != null)
            return success(DataTypeUtility.value(true), "true", pp);
        pp = orig.consumeNextIC("false");
        if (pp != null)
            return success(DataTypeUtility.value(false), "false", pp);
        
        return error("Expected true or false", orig.curCharIndex);
    }

}
