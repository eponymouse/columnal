package records.gui.lexeditor;

import annotation.recorded.qual.Recorded;
import annotation.units.SourceLocation;
import com.google.common.collect.ImmutableList;
import records.gui.lexeditor.EditorLocationAndErrorRecorder.ErrorDetails;
import styled.StyledShowable;
import utility.Pair;

public interface Lexer<EXPRESSION extends StyledShowable>
{
    public static class LexerResult<EXPRESSION extends StyledShowable>
    {
        public final @Recorded EXPRESSION result;
        public final String adjustedContent;
        public final CaretPosMapper mapperToAdjusted;
        public final @SourceLocation int[] caretPositions;
        public final ImmutableList<ErrorDetails> errors;

        /*
        public LexerResult(@Recorded EXPRESSION result, String adjustedContent, CaretPosMapper mapperToAdjusted, int[] caretPositions, ImmutableList<ErrorDetails> errors)
        {
            this.result = result;
            this.adjustedContent = adjustedContent;
            this.mapperToAdjusted = mapperToAdjusted;
            this.caretPositions = caretPositions;
            this.errors = errors;
        }
        */

        // Temporary constructor to auto-fill caret positions
        // Remove once caret positions done properly
        @SuppressWarnings("units")
        public LexerResult(@Recorded EXPRESSION result, String adjustedContent, CaretPosMapper mapperToAdjusted, ImmutableList<ErrorDetails> errors)
        {
            this.result = result;
            this.adjustedContent = adjustedContent;
            this.mapperToAdjusted = mapperToAdjusted;
            this.caretPositions = new int[adjustedContent.length() + 1];
            for (int i = 0; i < caretPositions.length; i++)
            {
                caretPositions[i] = i;
            }
            this.errors = errors;
        }
    }
    
    public interface CaretPosMapper
    {
        public @SourceLocation int mapCaretPos(@SourceLocation int pos);
    }
    
    // Takes latest content, lexes it, returns result
    public LexerResult<EXPRESSION> process(String content);
}
