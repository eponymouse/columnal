package records.gui.dtf;

import annotation.qual.ImmediateValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.styled.StyledString;
import xyz.columnal.utility.Either;
import xyz.columnal.utility.Pair;
import xyz.columnal.utility.ParseProgress;

public abstract class Recogniser<T>
{
    protected  @Nullable Pair<String, ParseProgress> consumeDigits(ParseProgress parseProgress)
    {
        int i;
        for (i = parseProgress.curCharIndex; i < parseProgress.src.length(); i++)
        {
            char c = parseProgress.src.charAt(i);
            if (c < '0' || c > '9')
            {
                break;
            }
        }
        
        if (i == parseProgress.curCharIndex)
            return null;
        else
            return new Pair<>(parseProgress.src.substring(parseProgress.curCharIndex, i), parseProgress.skip(i - parseProgress.curCharIndex));
    }

    public static class ErrorDetails
    {
        public final StyledString error;
        public final int errorPosition;
        //public final ImmutableList<Fix> fixes;

        public ErrorDetails(StyledString error, int errorPosition)
        {
            this.error = error;
            this.errorPosition = errorPosition;
        }
    }
    
    public static class SuccessDetails<T>
    {
        public final @NonNull @ImmediateValue T value;
        public final @Nullable String immediateReplacementText;
        public final ImmutableList<StyleSpanInfo> styles;
        public final ParseProgress parseProgress;

        private SuccessDetails(@NonNull @ImmediateValue T value, @Nullable String immediateReplacementText, ImmutableList<StyleSpanInfo> styles, ParseProgress parseProgress)
        {
            this.value = value;
            this.immediateReplacementText = immediateReplacementText;
            this.styles = styles;
            this.parseProgress = parseProgress;
        }

        public SuccessDetails<@ImmediateValue Object> asObject()
        {
            return new SuccessDetails<>(value, immediateReplacementText, styles, parseProgress);
        }

        // Makes sure there are no non-spaces left to be processed
        public Either<ErrorDetails, SuccessDetails<T>> requireEnd()
        {
            ParseProgress pp = parseProgress.skipSpaces();
            if (pp.curCharIndex == pp.src.length())
                return Either.right(this);
            else
                return Either.left(new ErrorDetails(StyledString.s("Unexpected additional content: " + pp.src.substring(pp.curCharIndex)), pp.curCharIndex));
        }
    }
    
    public static class StyleSpanInfo
    {
        public final int startIndex;
        public final int endIndex;
        public final ImmutableSet<String> styleClasses;

        public StyleSpanInfo(int startIndex, int endIndex, ImmutableSet<String> styleClasses)
        {
            this.startIndex = startIndex;
            this.endIndex = endIndex;
            this.styleClasses = styleClasses;
        }
    }
    
    public abstract Either<ErrorDetails, SuccessDetails<T>> process(ParseProgress parseProgress, boolean immediatelySurroundedByRoundBrackets);

    protected Either<ErrorDetails, SuccessDetails<T>> error(String msg, int errorPosition)
    {
        return Either.left(new ErrorDetails(StyledString.s(msg), errorPosition));
    }

    protected Either<ErrorDetails, SuccessDetails<T>> success(@NonNull @ImmediateValue T value, ParseProgress parseProgress)
    {
        return Either.right(new SuccessDetails<T>(value, null, ImmutableList.of(), parseProgress));
    }

    protected Either<ErrorDetails, SuccessDetails<T>> success(@NonNull @ImmediateValue T value, @Nullable String replacementText, ParseProgress parseProgress)
    {
        return Either.right(new SuccessDetails<T>(value, replacementText, ImmutableList.of(), parseProgress));
    }

    protected Either<ErrorDetails, SuccessDetails<T>> success(@NonNull @ImmediateValue T value, ImmutableList<StyleSpanInfo> styleSpanInfos, ParseProgress parseProgress)
    {
        return Either.right(new SuccessDetails<T>(value, null, styleSpanInfos, parseProgress));
    }
}
