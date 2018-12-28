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
    public static class ParseProgress
    {
        public final String src;
        public final int curCharIndex;

        private ParseProgress(String src, int curCharIndex)
        {
            this.src = src;
            this.curCharIndex = curCharIndex;
        }
        
        public @Nullable ParseProgress consumeNext(String match)
        {
            int next = skipSpaces();
            if (src.startsWith(match, next))
                return new ParseProgress(src, next + match.length());
            else
                return null;
        }
        
        // Consumes all text until the terminator, and returns it, and consumes the terminator.
        public @Nullable Pair<String, ParseProgress> consumeUntil(String terminator)
        {
            int index = src.indexOf(terminator, curCharIndex);
            if (index == -1)
                return null;
            return new Pair<>(src.substring(curCharIndex, index), new ParseProgress(src, index + terminator.length()));
        }

        // Ignore case
        public @Nullable ParseProgress consumeNextIC(String match)
        {
            int next = skipSpaces();
            if (src.regionMatches(true, next, match, 0, match.length()))
                return new ParseProgress(src, next + match.length());
            else
                return null;
        }
        
        public ParseProgress skip(int chars)
        {
            return new ParseProgress(src, curCharIndex + chars);
        }
        
        private int skipSpaces()
        {
            int i = curCharIndex;
            while (Character.isWhitespace(src.charAt(i)))
                i += 1;
            return i;
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

        public SuccessDetails(@NonNull T value, ImmutableList<StyleSpanInfo> styles, ParseProgress parseProgress)
        {
            this.value = value;
            this.styles = styles;
            this.parseProgress = parseProgress;
        }

        public SuccessDetails(@NonNull T value, ParseProgress parseProgress)
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
    
    public abstract Either<ErrorDetails, SuccessDetails<T>> process(ParseProgress parseProgress);

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
