package records.gui.flex.recognisers;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import records.data.datatype.DataTypeUtility;
import records.gui.flex.Recogniser;
import utility.Either;

import java.util.Iterator;

public class TupleRecogniser extends Recogniser<@Value Object @Value []>
{
    private final ImmutableList<Recogniser<@Value ?>> members;

    public TupleRecogniser(ImmutableList<Recogniser<@Value ?>> members)
    {
        this.members = members;
    }

    @Override
    public Either<ErrorDetails, SuccessDetails<@Value Object @Value[]>> process(ParseProgress orig, boolean immediatelySurroundedByRoundBrackets)
    {
        ParseProgress pp = orig;
        if (!immediatelySurroundedByRoundBrackets)
            pp = pp.consumeNext("(");
        if (pp == null)
            return error("Expected '(' to begin a tuple", orig.curCharIndex);

        return next(false, !immediatelySurroundedByRoundBrackets, members.iterator(), pp, ImmutableList.builderWithExpectedSize(members.size()));
    }
    
    // Recurse down the list of members, processing one and if no error, process next, until no more members and then look for closing bracket:
    private Either<ErrorDetails, SuccessDetails<@Value Object @Value[]>> next(boolean expectComma, boolean expectClosingBracket, Iterator<Recogniser<?>> nextMember, ParseProgress orig, ImmutableList.Builder<SuccessDetails<Object>> soFar)
    {
        ParseProgress pp = orig;
        // If no more members, look for closing bracket:
        if (!nextMember.hasNext())
        {
            if (expectClosingBracket)
                pp = pp.consumeNext(")");
            if (pp == null)
                return error("Expected ')' to end tuple", orig.curCharIndex);
            ImmutableList<SuccessDetails<Object>> all = soFar.build();
            return success(DataTypeUtility.value(all.stream().map(s -> s.value).<@Value Object>toArray(n -> new @Value Object[n])), all.stream().flatMap(s -> s.styles.stream()).collect(ImmutableList.<StyleSpanInfo>toImmutableList()), pp);
        }
        
        if (expectComma)
        {
            ParseProgress beforeComma = pp;
            pp = pp.consumeNext(",");
            if (pp == null)
                return error("Expected ',' to separate tuple items", beforeComma.curCharIndex);
        }
        
        return nextMember.next().process(pp, false).<SuccessDetails<Object>>map(s -> s.asObject())
            .<SuccessDetails<@Value Object @Value[]>>flatMap((SuccessDetails<Object> succ) -> {
                soFar.add(succ);
                return next(true, expectClosingBracket, nextMember, succ.parseProgress, soFar);
            });
    }
}
