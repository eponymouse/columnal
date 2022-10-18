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

package xyz.columnal.gui.dtf;

import annotation.qual.ImmediateValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.styled.StyledString;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.ParseProgress;

public abstract class Recogniser<T>
{
    protected  @Nullable Pair<String, ParseProgress> consumeDigits(ParseProgress parseProgress)
    {
        int i;
        for (i = parseProgress.curCharIndex; i < parseProgress.src.length(); i++)
        {
            char c = parseProgress.src.charAt(i);
            if (c < '0' || c > '9')
            {
                break;
            }
        }
        
        if (i == parseProgress.curCharIndex)
            return null;
        else
            return new Pair<>(parseProgress.src.substring(parseProgress.curCharIndex, i), parseProgress.skip(i - parseProgress.curCharIndex));
    }

    public static class ErrorDetails
    {
        public final StyledString error;
        public final int errorPosition;
        //public final ImmutableList<Fix> fixes;

        public ErrorDetails(StyledString error, int errorPosition)
        {
            this.error = error;
            this.errorPosition = errorPosition;
        }
    }
    
    public static class SuccessDetails<T>
    {
        public final @NonNull @ImmediateValue T value;
        public final String immediateReplacementText;
        public final ImmutableList<StyleSpanInfo> styles;
        public final ParseProgress parseProgress;

        private SuccessDetails(@NonNull @ImmediateValue T value, String immediateReplacementText, ImmutableList<StyleSpanInfo> styles, ParseProgress parseProgress)
        {
            this.value = value;
            this.immediateReplacementText = immediateReplacementText;
            this.styles = styles;
            this.parseProgress = parseProgress;
        }

        public SuccessDetails<@ImmediateValue Object> asObject()
        {
            return new SuccessDetails<>(value, immediateReplacementText, styles, parseProgress);
        }

        // Makes sure there are no non-spaces left to be processed
        public Either<ErrorDetails, SuccessDetails<T>> requireEnd()
        {
            ParseProgress pp = parseProgress.skipSpaces();
            if (pp.curCharIndex == pp.src.length())
                return Either.right(this);
            else
                return Either.left(new ErrorDetails(StyledString.s("Unexpected additional content: " + pp.src.substring(pp.curCharIndex)), pp.curCharIndex));
        }
    }
    
    public static class StyleSpanInfo
    {
        public final int startIndex;
        public final int endIndex;
        public final ImmutableSet<String> styleClasses;

        public StyleSpanInfo(int startIndex, int endIndex, ImmutableSet<String> styleClasses)
        {
            this.startIndex = startIndex;
            this.endIndex = endIndex;
            this.styleClasses = styleClasses;
        }
    }
    
    public abstract Either<ErrorDetails, SuccessDetails<T>> process(ParseProgress parseProgress, boolean immediatelySurroundedByRoundBrackets);

    protected Either<ErrorDetails, SuccessDetails<T>> error(String msg, int errorPosition)
    {
        return Either.left(new ErrorDetails(StyledString.s(msg), errorPosition));
    }

    protected Either<ErrorDetails, SuccessDetails<T>> success(@NonNull @ImmediateValue T value, String replacementText, ParseProgress parseProgress)
    {
        return Either.right(new SuccessDetails<T>(value, replacementText, ImmutableList.of(), parseProgress));
    }

    protected Either<ErrorDetails, SuccessDetails<T>> success(@NonNull @ImmediateValue T value, String replacementText, ImmutableList<StyleSpanInfo> styleSpanInfos, ParseProgress parseProgress)
    {
        return Either.right(new SuccessDetails<T>(value, replacementText, styleSpanInfos, parseProgress));
    }
}
