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
        // Maps position in parameter to process to adjustedContent
        public final CaretPosMapper mapperToAdjusted;
        public final boolean reLexOnCaretMove;
        // Valid caret positions in adjustedContent
        public final @SourceLocation int[] caretPositions;
        // The content to display in the TextFlow
        public final StyledString display;
        public final CaretPosMapper mapContentToDisplay;
        public final CaretPosMapper mapDisplayToContent;
        
        public final ImmutableList<ErrorDetails> errors;
        public final ImmutableList<Lexer.AutoCompleteDetails<CODE_COMPLETION_CONTEXT>> autoCompleteDetails;
        // If a bit is set at a particular caret position, when the user
        // types a ({[ opening bracket then do not insert the closing bracket.
        public final BitSet suppressBracketMatching;
        public final boolean bracketsAreBalanced;

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
        public LexerResult(@Recorded EXPRESSION result, String adjustedContent, CaretPosMapper mapperToAdjusted, boolean reLexOnCaretMove, int[] caretPositions, StyledString display, CaretPosMapper mapContentToDisplay, CaretPosMapper mapDisplayToContent, ImmutableList<ErrorDetails> errors, ImmutableList<AutoCompleteDetails<CODE_COMPLETION_CONTEXT>> completeDetails, BitSet suppressBracketMatching, boolean bracketsBalanced)
        {
            this.result = result;
            this.adjustedContent = adjustedContent;
            this.reLexOnCaretMove = reLexOnCaretMove;
            this.mapperToAdjusted = mapperToAdjusted;
            this.caretPositions = caretPositions;
            this.display = display;
            this.mapContentToDisplay = mapContentToDisplay;
            this.mapDisplayToContent = mapDisplayToContent;
            this.errors = errors;
            this.autoCompleteDetails = completeDetails;
            this.suppressBracketMatching = suppressBracketMatching;
            this.bracketsAreBalanced = bracketsBalanced;
        }
        
        public ImmutableList<LexCompletion> getCompletionsFor(@SourceLocation int pos)
        {
            return autoCompleteDetails.stream().filter(a -> a.location.contains(pos)).flatMap(a -> a.codeCompletionContext.getCompletionsFor(pos).stream()).collect(ImmutableList.<LexCompletion>toImmutableList());
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
