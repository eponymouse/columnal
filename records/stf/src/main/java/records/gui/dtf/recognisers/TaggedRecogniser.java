package records.gui.dtf.recognisers;

import annotation.qual.ImmediateValue;
import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType.TagType;
import records.gui.dtf.Recogniser;
import utility.Either;
import utility.Pair;
import utility.ParseProgress;
import utility.TaggedValue;
import utility.Utility;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class TaggedRecogniser extends Recogniser<@ImmediateValue TaggedValue>
{
    private final ImmutableList<TagType<Recogniser<@ImmediateValue ?>>> tags;

    public TaggedRecogniser(ImmutableList<TagType<Recogniser<@ImmediateValue ?>>> tags)
    {
        this.tags = tags;
    }

    @Override
    public Either<ErrorDetails, SuccessDetails<@ImmediateValue TaggedValue>> process(ParseProgress parseProgress, boolean immediatelySurroundedByRoundBrackets)
    {
        ParseProgress pp = parseProgress.skipSpaces();
        
        List<Pair<Integer, TagType<Recogniser<@ImmediateValue ?>>>> tagsLargestFirst = new ArrayList<>(Utility.streamIndexed(tags).collect(Collectors.<Pair<Integer, TagType<Recogniser<@ImmediateValue ?>>>>toList()));
        // Longest names first:
        Collections.<Pair<Integer, TagType<Recogniser<@ImmediateValue ?>>>>sort(tagsLargestFirst, Comparator.<Pair<Integer, TagType<Recogniser<@ImmediateValue ?>>>, Integer>comparing(p -> -p.getSecond().getName().length()));

        for (Pair<Integer, TagType<Recogniser<@ImmediateValue ?>>> tagInfo : tagsLargestFirst)
        {
            TagType<Recogniser<@ImmediateValue ?>> tag = tagInfo.getSecond();
            ParseProgress afterTag = pp.consumeNext(tag.getName());
            if (afterTag != null)
            {
                @Nullable Recogniser<@ImmediateValue ?> inner = tag.getInner();
                if (inner == null)
                    return success(TaggedValue.immediate(tagInfo.getFirst(), null), afterTag);
                
                pp = afterTag.consumeNext("(");
                if (pp == null)
                    return error("Expected '(' around an inner value", afterTag.curCharIndex);
                return inner.process(pp, true).<SuccessDetails<@ImmediateValue TaggedValue>>flatMap((SuccessDetails<@ImmediateValue ?>  succ) -> {
                    ParseProgress afterBracket = succ.parseProgress.consumeNext(")");
                    if (afterBracket == null)
                        return error("Expected closing ')' after an inner value", succ.parseProgress.curCharIndex);
                    return success(TaggedValue.immediate(tagInfo.getFirst(), succ.value), afterBracket);
                });
                
            }
        }
        
        return error("Did not recognise tag name", parseProgress.curCharIndex);
    }
}
