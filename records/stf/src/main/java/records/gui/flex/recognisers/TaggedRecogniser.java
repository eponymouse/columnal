package records.gui.flex.recognisers;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType.TagType;
import records.gui.flex.Recogniser;
import utility.Either;
import utility.Pair;
import utility.TaggedValue;
import utility.Utility;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class TaggedRecogniser extends Recogniser<@Value TaggedValue>
{
    private final ImmutableList<TagType<Recogniser<@Value ?>>> tags;

    public TaggedRecogniser(ImmutableList<TagType<Recogniser<@Value ?>>> tags)
    {
        this.tags = tags;
    }

    @Override
    public Either<ErrorDetails, SuccessDetails<TaggedValue>> process(ParseProgress parseProgress, boolean immediatelySurroundedByRoundBrackets)
    {
        ParseProgress pp = parseProgress.skipSpaces();
        
        List<Pair<Integer, TagType<Recogniser<@Value ?>>>> tagsLargestFirst = new ArrayList<>(Utility.streamIndexed(tags).collect(Collectors.<Pair<Integer, TagType<Recogniser<@Value ?>>>>toList()));
        // Longest names first:
        Collections.<Pair<Integer, TagType<Recogniser<@Value ?>>>>sort(tagsLargestFirst, Comparator.<Pair<Integer, TagType<Recogniser<@Value ?>>>, Integer>comparing(p -> -p.getSecond().getName().length()));

        for (Pair<Integer, TagType<Recogniser<@Value ?>>> tagInfo : tagsLargestFirst)
        {
            TagType<Recogniser<@Value ?>> tag = tagInfo.getSecond();
            ParseProgress afterTag = pp.consumeNext(tag.getName());
            if (afterTag != null)
            {
                @Nullable Recogniser<@Value ?> inner = tag.getInner();
                if (inner == null)
                    return success(new TaggedValue(tagInfo.getFirst(), null), afterTag);
                
                pp = afterTag.consumeNext("(");
                if (pp == null)
                    return error("Expected '(' around an inner value", afterTag.curCharIndex);
                return inner.process(pp, true).flatMap((SuccessDetails<@Value ?>  succ) -> {
                    ParseProgress afterBracket = succ.parseProgress.consumeNext(")");
                    if (afterBracket == null)
                        return error("Expected closing ')' after an inner value", succ.parseProgress.curCharIndex);
                    return success(new TaggedValue(tagInfo.getFirst(), succ.value), afterBracket);
                });
                
            }
        }
        
        return error("Did not recognise tag name", parseProgress.curCharIndex);
    }
}
