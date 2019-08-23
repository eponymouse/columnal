package records.gui.dtf.recognisers;

import annotation.qual.ImmediateValue;
import annotation.qual.Value;
import records.data.datatype.DataTypeUtility;
import records.grammar.GrammarUtility;
import records.gui.dtf.Recogniser;
import utility.Either;
import utility.Pair;
import utility.ParseProgress;

public class StringRecogniser extends Recogniser<@ImmediateValue String>
{
    @Override
    public Either<ErrorDetails, SuccessDetails<@ImmediateValue String>> process(ParseProgress orig, boolean immediatelySurroundedByRoundBrackets)
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
