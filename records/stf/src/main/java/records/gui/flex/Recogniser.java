package records.gui.flex;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import styled.StyledString;
import utility.Either;
import utility.Pair;

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

    public static class ParseProgress
    {
        public final String src;
        public final int curCharIndex;

        private ParseProgress(String src, int curCharIndex)
        {
            this.src = src;
            this.curCharIndex = curCharIndex;
        }

        public static ParseProgress fromStart(String text)
        {
            return new ParseProgress(text, 0);
        }

        public @Nullable ParseProgress consumeNext(String match)
        {
            int next = match.codePoints().anyMatch(Character::isWhitespace) ? curCharIndex : skipSpaces().curCharIndex;
            if (src.startsWith(match, next))
                return new ParseProgress(src, next + match.length());
            else
                return null;
        }
        
        // Consumes all text until the terminator, and returns it, and consumes the terminator.
        public @Nullable Pair<String, ParseProgress> consumeUpToAndIncluding(String terminator)
        {
            int index = src.indexOf(terminator, curCharIndex);
            if (index == -1)
                return null;
            return new Pair<>(src.substring(curCharIndex, index), new ParseProgress(src, index + terminator.length()));
        }

        // Consumes all text until the soonest terminator, and returns it, and does NOT consume the terminator.
        // If none are found, consumes whole string
        public Pair<String, ParseProgress> consumeUpToAndExcluding(ImmutableList<String> terminators)
        {
            int earliest = src.length();
            for (String terminator : terminators)
            {
                int index = src.indexOf(terminator, curCharIndex);
                if (index != -1 && index < earliest)
                    earliest = index;
            }
            return new Pair<>(src.substring(curCharIndex, earliest), new ParseProgress(src, earliest));
        }

        // Ignore case
        public @Nullable ParseProgress consumeNextIC(String match)
        {
            int next = skipSpaces().curCharIndex;
            if (src.regionMatches(true, next, match, 0, match.length()))
                return new ParseProgress(src, next + match.length());
            else
                return null;
        }
        
        public ParseProgress skip(int chars)
        {
            return new ParseProgress(src, curCharIndex + chars);
        }
        
        public ParseProgress skipSpaces()
        {
            int i = curCharIndex;
            while (i < src.length() && Character.isWhitespace(src.charAt(i)))
                i += 1;
            return new ParseProgress(src, i);
        }
    }
    
    public static class ErrorDetails
    {
        public final StyledString error;
        //public final ImmutableList<Fix> fixes;

        public ErrorDetails(StyledString error)
        {
            this.error = error;
        }
    }
    
    public static class SuccessDetails<T>
    {
        public final @NonNull T value;
        public final ImmutableList<StyleSpanInfo> styles;
        public final ParseProgress parseProgress;

        private SuccessDetails(@NonNull T value, ImmutableList<StyleSpanInfo> styles, ParseProgress parseProgress)
        {
            this.value = value;
            this.styles = styles;
            this.parseProgress = parseProgress;
        }

        private SuccessDetails(@NonNull T value, ParseProgress parseProgress)
        {
            this(value, ImmutableList.of(), parseProgress);
        }
        
        public SuccessDetails<Object> asObject()
        {
            return new SuccessDetails<>(value, styles, parseProgress);
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

    protected Either<ErrorDetails, SuccessDetails<T>> error(String msg)
    {
        return Either.left(new ErrorDetails(StyledString.s(msg)));
    }

    protected Either<ErrorDetails, SuccessDetails<T>> success(@NonNull T value, ParseProgress parseProgress)
    {
        return Either.right(new SuccessDetails<T>(value, parseProgress));
    }

    protected Either<ErrorDetails, SuccessDetails<T>> success(@NonNull T value, ImmutableList<StyleSpanInfo> styleSpanInfos, ParseProgress parseProgress)
    {
        return Either.right(new SuccessDetails<T>(value, styleSpanInfos, parseProgress));
    }
}
