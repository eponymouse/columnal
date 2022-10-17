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
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.datatype.DataType.TagType;
import xyz.columnal.data.datatype.DataTypeUtility;
import xyz.columnal.gui.dtf.Recogniser;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.ParseProgress;
import xyz.columnal.utility.TaggedValue;
import xyz.columnal.utility.Utility;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class TaggedRecogniser extends Recogniser<@ImmediateValue TaggedValue>
{
    private final ImmutableList<TagType<Recogniser<? extends @ImmediateValue @NonNull Object>>> tags;

    public TaggedRecogniser(ImmutableList<TagType<Recogniser<? extends @ImmediateValue @NonNull Object>>> tags)
    {
        this.tags = tags;
    }

    @Override
    public Either<ErrorDetails, SuccessDetails<@ImmediateValue TaggedValue>> process(ParseProgress parseProgress, boolean immediatelySurroundedByRoundBrackets)
    {
        ParseProgress pp = parseProgress.skipSpaces();
        
        List<Pair<Integer, TagType<Recogniser<? extends @ImmediateValue @NonNull Object>>>> tagsLargestFirst = new ArrayList<>(Utility.streamIndexed(tags).collect(Collectors.<Pair<Integer, TagType<Recogniser<? extends @ImmediateValue @NonNull Object>>>>toList()));
        // Longest names first:
        Collections.<Pair<Integer, TagType<Recogniser<? extends @ImmediateValue @NonNull Object>>>>sort(tagsLargestFirst, Comparator.<Pair<Integer, TagType<Recogniser<? extends @ImmediateValue @NonNull Object>>>, Integer>comparing(p -> -p.getSecond().getName().length()));

        StringBuilder replText = new StringBuilder();
        
        for (Pair<Integer, TagType<Recogniser<? extends @ImmediateValue @NonNull Object>>> tagInfo : tagsLargestFirst)
        {
            TagType<Recogniser<? extends @ImmediateValue @NonNull Object>> tag = tagInfo.getSecond();
            ParseProgress afterTag = pp.consumeNext(tag.getName());
            if (afterTag != null)
            {
                replText.append(tag.getName());
                @Nullable Recogniser<? extends @ImmediateValue @NonNull Object> inner = tag.getInner();
                if (inner == null)
                    return success(TaggedValue.immediate(tagInfo.getFirst(), null, DataTypeUtility.fromTags(tags)), replText.toString(), afterTag);
                
                pp = afterTag.consumeNext("(");
                replText.append("(");
                if (pp == null)
                    return error("Expected '(' around an inner value", afterTag.curCharIndex);
                return inner.process(pp, true).<SuccessDetails<@ImmediateValue TaggedValue>>flatMap((SuccessDetails<? extends @ImmediateValue @NonNull Object>  succ) -> {
                    replText.append(succ.immediateReplacementText);
                    ParseProgress afterBracket = succ.parseProgress.consumeNext(")");
                    if (afterBracket == null)
                        return error("Expected closing ')' after an inner value", succ.parseProgress.curCharIndex);
                    replText.append(")");
                    return success(TaggedValue.immediate(tagInfo.getFirst(), succ.value, DataTypeUtility.fromTags(tags)), replText.toString(), afterBracket);
                });
                
            }
        }
        
        return error("Did not recognise tag name", parseProgress.curCharIndex);
    }
}
