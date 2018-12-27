package records.gui.flex;

import styled.StyledString;
import utility.Either;
import utility.Pair;

public abstract class Recogniser<T>
{
    public static class ParseProgress
    {
        public final int[] codepoints;
        public final int curIndex;

        public ParseProgress(int[] codepoints, int curIndex)
        {
            this.codepoints = codepoints;
            this.curIndex = curIndex;
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
    
    public abstract Either<ErrorDetails, Pair<T, ParseProgress>> process(ParseProgress src);
}
