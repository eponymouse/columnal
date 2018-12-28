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
    public Either<ErrorDetails, SuccessDetails<@Value Object @Value[]>> process(ParseProgress orig)
    {
        ParseProgress pp = orig;
        pp = pp.consumeNext("(");
        if (pp == null)
            return error("Expected '(' to begin a tuple");

        return next(false, members.iterator(), pp, ImmutableList.builderWithExpectedSize(members.size()));
    }
    
    // Recurse down the list of members, processing one and if no error, process next, until no more members and then look for closing bracket:
    private Either<ErrorDetails, SuccessDetails<@Value Object @Value[]>> next(boolean expectComma, Iterator<Recogniser<?>> nextMember, ParseProgress orig, ImmutableList.Builder<SuccessDetails<Object>> soFar)
    {
        ParseProgress pp = orig;
        // If no more members, look for closing bracket:
        if (!nextMember.hasNext())
        {
            pp = pp.consumeNext(")");
            if (pp == null)
                return error("Expected ')' to end tuple");
            ImmutableList<SuccessDetails<Object>> all = soFar.build();
            return success(DataTypeUtility.value(all.stream().map(s -> s.value).<@Value Object>toArray(n -> new @Value Object[n])), all.stream().flatMap(s -> s.styles.stream()).collect(ImmutableList.toImmutableList()), pp);
        }
        
        if (expectComma)
        {
            pp = pp.consumeNext(",");
            if (pp == null)
                return error("Expected ',' to separate tuple items");
        }
        
        return nextMember.next().process(pp).map(s -> s.asObject())
            .flatMap(succ -> {
                soFar.add(succ);
                return next(true, nextMember, succ.parseProgress, soFar);
            });
    }
}
