package records.gui.dtf.recognisers;

import annotation.qual.ImmediateValue;
import annotation.qual.Value;
import xyz.columnal.error.UserException;
import records.gui.dtf.Recogniser;
import xyz.columnal.utility.Either;
import xyz.columnal.utility.Pair;
import xyz.columnal.utility.ParseProgress;
import xyz.columnal.utility.Utility;

public class NumberRecogniser extends Recogniser<@ImmediateValue Number>
{
    @Override
    public Either<ErrorDetails, SuccessDetails<@ImmediateValue Number>> process(ParseProgress orig, boolean immediatelySurroundedByRoundBrackets)
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
