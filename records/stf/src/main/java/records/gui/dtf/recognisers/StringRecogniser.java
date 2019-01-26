package records.gui.dtf.recognisers;

import annotation.qual.Value;
import records.data.datatype.DataTypeUtility;
import records.grammar.GrammarUtility;
import records.gui.dtf.Recogniser;
import utility.Either;
import utility.Pair;

public class StringRecogniser extends Recogniser<@Value String>
{
    @Override
    public Either<ErrorDetails, SuccessDetails<@Value String>> process(ParseProgress orig, boolean immediatelySurroundedByRoundBrackets)
    {
        ParseProgress pp = orig.consumeNext("\"");
        if (pp == null)
            return error("Looking for \" to begin text", orig.curCharIndex);
        Pair<String, ParseProgress> content = pp.consumeUpToAndIncluding("\"");
        if (content == null)
            return error("Could not find closing \" for text", orig.src.length() - 1);
        return success(DataTypeUtility.value(GrammarUtility.processEscapes(content.getFirst(), false)), content.getSecond());
    }
}
