package records.gui.dtf.recognisers;

import annotation.qual.ImmediateValue;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataTypeUtility;
import records.grammar.GrammarUtility;
import records.gui.dtf.Recogniser;
import utility.Either;
import utility.Pair;
import utility.ParseProgress;

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
            replacement = null;
        }
        if (content == null)
            return error("Could not find closing \" for text", orig.src.length() - 1);
        return success(DataTypeUtility.value(GrammarUtility.processEscapes(content.getFirst(), false)), replacement, content.getSecond());
    }
}
