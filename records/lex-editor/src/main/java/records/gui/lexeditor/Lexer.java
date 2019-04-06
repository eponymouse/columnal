package records.gui.lexeditor;

import annotation.recorded.qual.Recorded;
import annotation.units.SourceLocation;
import com.google.common.collect.ImmutableList;
import records.gui.lexeditor.EditorLocationAndErrorRecorder.ErrorDetails;
import records.gui.lexeditor.EditorLocationAndErrorRecorder.Span;
import records.gui.lexeditor.LexAutoComplete.LexCompletion;
import styled.StyledShowable;
import styled.StyledString;

import java.util.BitSet;

public interface Lexer<EXPRESSION extends StyledShowable, CODE_COMPLETION_CONTEXT extends CodeCompletionContext>
{
    public static class LexerResult<EXPRESSION extends StyledShowable, CODE_COMPLETION_CONTEXT extends CodeCompletionContext>
    {
        public final @Recorded EXPRESSION result;
        public final String adjustedContent;
        public final BitSet removedChars;
        public final BitSet addedDisplayChars;
        public final boolean reLexOnCaretMove;
        // Valid caret positions in adjustedContent
        public final @SourceLocation int[] caretPositions;
        // The content to display in the TextFlow
        public final StyledString display;
        
        public final ImmutableList<ErrorDetails> errors;
        public final ImmutableList<Lexer.AutoCompleteDetails<CODE_COMPLETION_CONTEXT>> autoCompleteDetails;
        // If a bit is set at a particular caret position, when the user
        // types a ({[ opening bracket then do not insert the closing bracket.
        public final BitSet suppressBracketMatching;
        public final boolean bracketsAreBalanced;

        @SuppressWarnings("units")
        public LexerResult(@Recorded EXPRESSION result, String adjustedContent, BitSet removedChars, boolean reLexOnCaretMove, int[] caretPositions, StyledString display, BitSet addedDisplayChars, ImmutableList<ErrorDetails> errors, ImmutableList<AutoCompleteDetails<CODE_COMPLETION_CONTEXT>> completeDetails, BitSet suppressBracketMatching, boolean bracketsBalanced)
        {
            this.result = result;
            this.adjustedContent = adjustedContent;
            this.reLexOnCaretMove = reLexOnCaretMove;
            this.removedChars = removedChars;
            this.caretPositions = caretPositions;
            this.display = display;
            this.addedDisplayChars = addedDisplayChars;
            this.errors = errors;
            this.autoCompleteDetails = completeDetails;
            this.suppressBracketMatching = suppressBracketMatching;
            this.bracketsAreBalanced = bracketsBalanced;
        }
        
        public ImmutableList<LexCompletion> getCompletionsFor(@SourceLocation int pos)
        {
            return autoCompleteDetails.stream().filter(a -> a.location.contains(pos)).flatMap(a -> a.codeCompletionContext.getCompletionsFor(pos).stream()).collect(ImmutableList.<LexCompletion>toImmutableList());
        }
        
        @SuppressWarnings("units")
        public @SourceLocation int mapOldCaretPos(@SourceLocation int pos)
        {
            return pos - removedChars.get(0, pos).cardinality();
        }

        @SuppressWarnings("units")
        public @SourceLocation int mapContentToDisplay(@SourceLocation int contentPos)
        {
            // We look for the ith empty spot in addedDisplayChars
            int r = 0;
            for (int j = 0; j < contentPos; j++)
            {
                while (addedDisplayChars.get(r))
                {
                    r += 1;
                }
                r += 1;
            }
            return r;
        }

        @SuppressWarnings("units")
        public @SourceLocation int mapDisplayToContent(@SourceLocation int displayPos)
        {
            displayPos = Math.max(0, displayPos);
            displayPos = Math.min(displayPos, display.toPlain().length());
            return displayPos - addedDisplayChars.get(0, displayPos).cardinality();
        }
    }
    
    public interface CaretPosMapper
    {
        public @SourceLocation int mapCaretPos(@SourceLocation int pos);
    }
    
    public static class AutoCompleteDetails<CODE_COMPLETION_CONTEXT extends CodeCompletionContext>
    {
        public final Span location;
        public final CODE_COMPLETION_CONTEXT codeCompletionContext;

        public AutoCompleteDetails(Span location, CODE_COMPLETION_CONTEXT codeCompletionContext)
        {
            this.location = location;
            this.codeCompletionContext = codeCompletionContext;
        }
    }
    
    // Takes latest content, lexes it, returns result
    public LexerResult<EXPRESSION, CODE_COMPLETION_CONTEXT> process(String content, int caretPos);
}
