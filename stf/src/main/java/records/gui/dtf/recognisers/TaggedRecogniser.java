package records.gui.dtf.recognisers;

import annotation.qual.ImmediateValue;
import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.datatype.DataType.TagType;
import xyz.columnal.data.datatype.DataTypeUtility;
import records.gui.dtf.Recogniser;
import xyz.columnal.utility.Either;
import xyz.columnal.utility.Pair;
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

        for (Pair<Integer, TagType<Recogniser<? extends @ImmediateValue @NonNull Object>>> tagInfo : tagsLargestFirst)
        {
            TagType<Recogniser<? extends @ImmediateValue @NonNull Object>> tag = tagInfo.getSecond();
            ParseProgress afterTag = pp.consumeNext(tag.getName());
            if (afterTag != null)
            {
                @Nullable Recogniser<? extends @ImmediateValue @NonNull Object> inner = tag.getInner();
                if (inner == null)
                    return success(TaggedValue.immediate(tagInfo.getFirst(), null, DataTypeUtility.fromTags(tags)), afterTag);
                
                pp = afterTag.consumeNext("(");
                if (pp == null)
                    return error("Expected '(' around an inner value", afterTag.curCharIndex);
                return inner.process(pp, true).<SuccessDetails<@ImmediateValue TaggedValue>>flatMap((SuccessDetails<? extends @ImmediateValue @NonNull Object>  succ) -> {
                    ParseProgress afterBracket = succ.parseProgress.consumeNext(")");
                    if (afterBracket == null)
                        return error("Expected closing ')' after an inner value", succ.parseProgress.curCharIndex);
                    return success(TaggedValue.immediate(tagInfo.getFirst(), succ.value, DataTypeUtility.fromTags(tags)), afterBracket);
                });
                
            }
        }
        
        return error("Did not recognise tag name", parseProgress.curCharIndex);
    }
}
