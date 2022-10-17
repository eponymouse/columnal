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
import xyz.columnal.grammar.GrammarUtility;
import xyz.columnal.gui.dtf.Recogniser;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.ParseProgress;

public class StringRecogniser extends Recogniser<@ImmediateValue String>
{
    private final boolean soloRecogniser;

    public StringRecogniser(boolean soloRecogniser)
    {
        this.soloRecogniser = soloRecogniser;
    }


    @Override
    public Either<ErrorDetails, SuccessDetails<@ImmediateValue String>> process(ParseProgress orig, boolean immediatelySurroundedByRoundBrackets)
    {
        ParseProgress pp = orig.consumeNext("\"");
        Pair<String, ParseProgress> content;
        @Nullable String replacement;
        if (pp == null)
        {
            if (!soloRecogniser)
                return error("Looking for \" to begin text", orig.curCharIndex);
            // Try without:
            content = new Pair<>(orig.src.substring(orig.curCharIndex), ParseProgress.fromStart(orig.src).skip(orig.src.length()));
            replacement = "\"" + orig.src + "\"";
        }
        else
        {
            content = pp.consumeUpToAndIncluding("\"");
            if (content == null)
                return error("Could not find closing \" for text", orig.src.length() - 1);
            replacement = "\"" + content.getFirst() + "\"";
        }
        return success(DataTypeUtility.value(GrammarUtility.processEscapes(content.getFirst(), false)), replacement, content.getSecond());
    }
}
