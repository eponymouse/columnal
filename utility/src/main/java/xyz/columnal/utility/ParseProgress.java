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

package xyz.columnal.utility;

import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.utility.adt.Pair;

public class ParseProgress
{
    public final String src;
    public final int curCharIndex;

    protected ParseProgress(String src, int curCharIndex)
    {
        this.src = src;
        this.curCharIndex = curCharIndex;
    }

    public static ParseProgress fromStart(String text)
    {
        return new ParseProgress(text, 0);
    }

    public @Nullable ParseProgress consumeNext(String match)
    {
        int next = match.codePoints().anyMatch(Character::isWhitespace) ? curCharIndex : skipSpaces().curCharIndex;
        if (src.startsWith(match, next))
            return new ParseProgress(src, next + match.length());
        else
            return null;
    }
    
    // Consumes all text until the terminator, and returns it, and consumes the terminator.
    public @Nullable Pair<String, ParseProgress> consumeUpToAndIncluding(String terminator)
    {
        int index = src.indexOf(terminator, curCharIndex);
        if (index == -1)
            return null;
        return new Pair<>(src.substring(curCharIndex, index), new ParseProgress(src, index + terminator.length()));
    }

    // Consumes all text until the soonest terminator, and returns it, and does NOT consume the terminator.
    // If none are found, consumes whole string
    public Pair<String, ParseProgress> consumeUpToAndExcluding(ImmutableList<String> terminators)
    {
        int earliest = src.length();
        for (String terminator : terminators)
        {
            int index = src.indexOf(terminator, curCharIndex);
            if (index != -1 && index < earliest)
                earliest = index;
        }
        return new Pair<>(src.substring(curCharIndex, earliest), new ParseProgress(src, earliest));
    }

    // Ignore case
    public @Nullable ParseProgress consumeNextIC(String match)
    {
        int next = skipSpaces().curCharIndex;
        if (src.regionMatches(true, next, match, 0, match.length()))
            return new ParseProgress(src, next + match.length());
        else
            return null;
    }
    
    public ParseProgress skip(int chars)
    {
        return new ParseProgress(src, curCharIndex + chars);
    }
    
    public ParseProgress skipSpaces()
    {
        int i = curCharIndex;
        while (i < src.length() && Character.isWhitespace(src.charAt(i)))
            i += 1;
        return new ParseProgress(src, i);
    }

    // Doesn't skip spaces!
    public Pair<String, ParseProgress> consumeNumbers()
    {
        int start = curCharIndex;
        while (start < src.length() && Character.isDigit(src.charAt(start)))
            start += 1;
        return new Pair<>(src.substring(curCharIndex, start), new ParseProgress(src, start));
    }
}
