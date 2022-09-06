package records.gui.dtf.recognisers;

import annotation.identifier.qual.ExpressionIdentifier;
import annotation.qual.ImmediateValue;
import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataTypeUtility;
import records.gui.dtf.Recogniser;
import xyz.columnal.utility.Either;
import xyz.columnal.utility.IdentifierUtility;
import xyz.columnal.utility.Pair;
import xyz.columnal.utility.ParseProgress;
import xyz.columnal.utility.Utility;
import xyz.columnal.utility.Utility.Record;
import xyz.columnal.utility.Utility.RecordMap;

import java.util.HashMap;

public class RecordRecogniser extends Recogniser<@ImmediateValue Record>
{
    private final ImmutableMap<@ExpressionIdentifier String, Recogniser<? extends @ImmediateValue @NonNull Object>> members;

    public RecordRecogniser(ImmutableMap<@ExpressionIdentifier String, Recogniser<? extends @ImmediateValue @NonNull Object>> members)
    {
        this.members = members;
    }

    @Override
    public Either<ErrorDetails, SuccessDetails<@ImmediateValue Record>> process(ParseProgress orig, boolean immediatelySurroundedByRoundBrackets)
    {
        ParseProgress pp = orig;
        if (!immediatelySurroundedByRoundBrackets)
            pp = pp.consumeNext("(");
        if (pp == null)
            return error("Expected '(' to begin a record", orig.curCharIndex);

        HashMap<@ExpressionIdentifier String, Recogniser<? extends @ImmediateValue @NonNull Object>> remainingFields = new HashMap<@ExpressionIdentifier String, Recogniser<? extends @ImmediateValue @NonNull Object>>(members);
        
        return next(false, !immediatelySurroundedByRoundBrackets, remainingFields, pp, ImmutableMap.builderWithExpectedSize(members.size()));
    }

    // Recurse down the list of members, processing one and if no error, process next, until no more members and then look for closing bracket:
    private Either<ErrorDetails, SuccessDetails<@ImmediateValue Record>> next(boolean expectComma, boolean expectClosingBracket, HashMap<@ExpressionIdentifier String, Recogniser<? extends @ImmediateValue @NonNull Object>> remainingMembers, ParseProgress orig, ImmutableMap.Builder<@ExpressionIdentifier String, SuccessDetails<@ImmediateValue Object>> soFar)
    {
        ParseProgress pp = orig;
        // If no more members, look for closing bracket:
        if (remainingMembers.isEmpty())
        {
            if (expectClosingBracket)
                pp = pp.consumeNext(")");
            if (pp == null)
                return error("Expected ')' to end record", orig.curCharIndex);
            ImmutableMap<@ExpressionIdentifier String, SuccessDetails<@ImmediateValue Object>> all = soFar.build();
            return success(RecordMap.immediate(Utility.<@ExpressionIdentifier String, SuccessDetails<@ImmediateValue Object>, @ImmediateValue Object>mapValues(all, s -> s.value)), all.values().stream().flatMap(s -> s.styles.stream()).collect(ImmutableList.<StyleSpanInfo>toImmutableList()), pp);
        }

        if (expectComma)
        {
            ParseProgress beforeComma = pp;
            pp = pp.consumeNext(",");
            if (pp == null)
                return error("Expected ',' to separate record fields", beforeComma.curCharIndex);
        }
        
        // Look for field name:
        @Nullable Pair<String, ParseProgress> fieldName = pp.consumeUpToAndIncluding(":");
        if (fieldName == null)
            return error("Expected field name followed by colon", pp.curCharIndex);
        @ExpressionIdentifier String name = IdentifierUtility.asExpressionIdentifier(fieldName.getFirst().trim());
        if (name == null)
            return error("Expected field name followed by colon", pp.curCharIndex);
        Recogniser<?> recogniser = remainingMembers.remove(name);
        if (recogniser == null)
            return error("Unknown field name: \"" + name + "\"", pp.curCharIndex);
        pp = fieldName.getSecond();
        
        return recogniser.process(pp, false).<SuccessDetails<@ImmediateValue Object>>map(s -> s.asObject())
            .<SuccessDetails<@ImmediateValue Record>>flatMap((SuccessDetails<@ImmediateValue Object> succ) -> {
                soFar.put(name, succ);
                return next(true, expectClosingBracket, remainingMembers, succ.parseProgress, soFar);
            });
    }
}
