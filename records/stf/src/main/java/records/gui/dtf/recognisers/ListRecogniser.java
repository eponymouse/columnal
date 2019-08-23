package records.gui.dtf.recognisers;

import annotation.qual.ImmediateValue;
import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import records.gui.dtf.Recogniser;
import utility.Either;
import utility.ParseProgress;
import utility.Utility.ListEx;
import utility.Utility.ListExList;

public class ListRecogniser extends Recogniser<@ImmediateValue ListEx>
{
    private final Recogniser<@ImmediateValue ?> inner;

    public ListRecogniser(Recogniser<@ImmediateValue ?> inner)
    {
        this.inner = inner;
    }

    @Override
    public Either<ErrorDetails, SuccessDetails<@ImmediateValue ListEx>> process(ParseProgress parseProgress, boolean immediatelySurroundedByRoundBrackets)
    {
        try
        {
            ImmutableList.Builder<@ImmediateValue Object> list = ImmutableList.builder();
            ParseProgress pp = parseProgress;
            pp = pp.consumeNext("[");
            if (pp == null)
                return error("Expected '[' to begin list", parseProgress.curCharIndex);
            pp = pp.skipSpaces();

            boolean first = true;
            while (pp.curCharIndex < pp.src.length() && pp.src.charAt(pp.curCharIndex) != ']' && (first || pp.src.charAt(pp.curCharIndex) == ','))
            {
                if (!first)
                {
                    // Skip comma:
                    pp = pp.skip(1);
                }

                pp = addToList(list, inner.process(pp, false));

                pp = pp.skipSpaces();
                first = false;
            }

            ParseProgress beforeBracket = pp;
            pp = pp.consumeNext("]");
            if (pp == null)
                return error("Expected ']' to end list", beforeBracket.curCharIndex);

            return success(ListExList.immediate(list.build()), pp);
        }
        catch (ListException e)
        {
            return Either.left(e.errorDetails);
        }
    }
    
    private static class ListException extends RuntimeException
    {
        private final ErrorDetails errorDetails;

        private ListException(ErrorDetails errorDetails)
        {
            this.errorDetails = errorDetails;
        }
    }

    private <T> ParseProgress addToList(ImmutableList.Builder<@ImmediateValue Object> list, Either<ErrorDetails, SuccessDetails<@ImmediateValue T>> process) throws ListException
    {
        return process.either(err -> {throw new ListException(err);}, 
            succ -> { list.add(succ.value); return succ.parseProgress; });
    }
}
