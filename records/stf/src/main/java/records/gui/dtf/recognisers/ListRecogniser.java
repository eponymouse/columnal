package records.gui.dtf.recognisers;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import records.gui.dtf.Recogniser;
import utility.Either;
import utility.Utility.ListEx;
import utility.Utility.ListExList;

public class ListRecogniser extends Recogniser<@Value ListEx>
{
    private final Recogniser<@Value ?> inner;

    public ListRecogniser(Recogniser<@Value ?> inner)
    {
        this.inner = inner;
    }

    @Override
    public Either<ErrorDetails, SuccessDetails<@Value ListEx>> process(ParseProgress parseProgress, boolean immediatelySurroundedByRoundBrackets)
    {
        try
        {
            ImmutableList.Builder<@Value Object> list = ImmutableList.builder();
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

            return success(new ListExList(list.build()), pp);
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

    private <T> ParseProgress addToList(ImmutableList.Builder<@Value Object> list, Either<ErrorDetails, SuccessDetails<@Value T>> process) throws ListException
    {
        return process.either(err -> {throw new ListException(err);}, 
            succ -> { list.add(succ.value); return succ.parseProgress; });
    }
}
