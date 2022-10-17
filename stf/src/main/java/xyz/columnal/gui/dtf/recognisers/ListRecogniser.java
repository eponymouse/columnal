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
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.NonNull;
import xyz.columnal.gui.dtf.Recogniser;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.ParseProgress;
import xyz.columnal.utility.Utility.ListEx;
import xyz.columnal.utility.Utility.ListExList;

public class ListRecogniser extends Recogniser<@ImmediateValue ListEx>
{
    private final Recogniser<? extends @ImmediateValue @NonNull Object> inner;

    public ListRecogniser(Recogniser<? extends @ImmediateValue @NonNull Object> inner)
    {
        this.inner = inner;
    }

    @Override
    public Either<ErrorDetails, SuccessDetails<@ImmediateValue ListEx>> process(ParseProgress parseProgress, boolean immediatelySurroundedByRoundBrackets)
    {
        try
        {
            ImmutableList.Builder<@ImmediateValue Object> list = ImmutableList.builder();
            StringBuilder replText = new StringBuilder();
            ParseProgress pp = parseProgress;
            pp = pp.consumeNext("[");
            replText.append("[");
            if (pp == null)
                return error("Expected '[' to begin list", parseProgress.curCharIndex);
            pp = pp.skipSpaces();

            boolean first = true;
            while (pp.curCharIndex < pp.src.length() && pp.src.charAt(pp.curCharIndex) != ']' && (first || pp.src.charAt(pp.curCharIndex) == ','))
            {
                if (!first)
                {
                    // Skip comma:
                    pp = pp.skip(1);
                    replText.append(", ");
                }

                pp = addToList(list, replText, inner.process(pp, false));

                pp = pp.skipSpaces();
                first = false;
            }

            ParseProgress beforeBracket = pp;
            pp = pp.consumeNext("]");
            if (pp == null)
                return error("Expected ']' to end list", beforeBracket.curCharIndex);
            replText.append("]");
            
            return success(ListExList.immediate(list.build()), replText.toString(), pp);
        }
        catch (ListException e)
        {
            return Either.left(e.errorDetails);
        }
    }
    
    private static class ListException extends RuntimeException
    {
        private final ErrorDetails errorDetails;

        private ListException(ErrorDetails errorDetails)
        {
            this.errorDetails = errorDetails;
        }
    }

    private static <T extends @ImmediateValue Object> ParseProgress addToList(ImmutableList.Builder<@ImmediateValue Object> list, StringBuilder replText, Either<ErrorDetails, SuccessDetails<T>> process) throws ListException
    {
        return process.either(err -> {throw new ListException(err);}, 
            succ -> { list.add(succ.value); replText.append(succ.immediateReplacementText); return succ.parseProgress; });
    }
}
