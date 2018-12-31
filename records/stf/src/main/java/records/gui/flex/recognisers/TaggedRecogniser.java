package records.gui.flex.recognisers;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType.TagType;
import records.gui.flex.Recogniser;
import utility.Either;
import utility.TaggedValue;

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
        for (int i = 0; i < tags.size(); i++)
        {
            int iFinal = i;
            TagType<Recogniser<@Value ?>> tag = tags.get(i);
            ParseProgress afterTag = pp.consumeNext(tag.getName());
            if (afterTag != null)
            {
                @Nullable Recogniser<@Value ?> inner = tag.getInner();
                if (inner == null)
                    return success(new TaggedValue(i, null), afterTag);
                
                pp = afterTag.consumeNext("(");
                if (pp == null)
                    return error("Expected '(' around an inner value");
                return inner.process(pp, true).flatMap((SuccessDetails<@Value ?>  succ) -> {
                    ParseProgress afterBracket = succ.parseProgress.consumeNext(")");
                    if (afterBracket == null)
                        return error("Expected closing ')' after an inner value");
                    return success(new TaggedValue(iFinal, succ.value), afterBracket);
                });
                
            }
        }
        
        return error("Did not recognise tag name");
    }
}
