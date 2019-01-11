package records.gui.flex.recognisers;

import annotation.qual.Value;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.error.UserException;
import records.gui.flex.Recogniser;
import utility.Either;
import utility.Pair;
import utility.Utility;

public class NumberRecogniser extends Recogniser<@Value Number>
{
    @Override
    public Either<ErrorDetails, SuccessDetails<@Value Number>> process(ParseProgress orig, boolean immediatelySurroundedByRoundBrackets)
    {
        ParseProgress pp = orig.skipSpaces();
        String sign = "";
        if (pp.src.startsWith("+", pp.curCharIndex))
        {
            pp = pp.skip(1);
        }
        else if (pp.src.startsWith("-", pp.curCharIndex))
        {
            pp = pp.skip(1);
            sign = "-";
        }
        
        Pair<String, ParseProgress> beforeDot = consumeDigits(pp);
        
        try
        {
            if (beforeDot == null)
            {
                return error("Expected digits to start a number", pp.curCharIndex);
            }
            else if (beforeDot.getSecond().src.startsWith(".", beforeDot.getSecond().curCharIndex))
            {
                Pair<String, ParseProgress> afterDot = consumeDigits(beforeDot.getSecond().skip(1));

                if (afterDot == null)
                {
                    return error("Expected digits after decimal point", beforeDot.getSecond().curCharIndex + 1);
                }
                
                return success(Utility.parseNumber(sign + beforeDot.getFirst() + "." + afterDot.getFirst()), afterDot.getSecond());
            }
            else
            {
                return success(Utility.parseNumber(sign + beforeDot.getFirst()), beforeDot.getSecond());
            }
        }
        catch (UserException e)
        {
            return Either.left(new ErrorDetails(e.getStyledMessage(), orig.curCharIndex));
        }
    }

}
