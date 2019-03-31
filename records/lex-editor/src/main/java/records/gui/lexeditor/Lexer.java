package records.gui.lexeditor;

import annotation.recorded.qual.Recorded;
import annotation.units.SourceLocation;
import com.google.common.collect.ImmutableList;
import records.gui.lexeditor.EditorLocationAndErrorRecorder.ErrorDetails;
import records.gui.lexeditor.EditorLocationAndErrorRecorder.Span;
import records.gui.lexeditor.LexAutoComplete.LexCompletion;
import styled.StyledShowable;

import java.util.BitSet;

public interface Lexer<EXPRESSION extends StyledShowable, CODE_COMPLETION_CONTEXT extends CodeCompletionContext>
{
    public static class LexerResult<EXPRESSION extends StyledShowable, CODE_COMPLETION_CONTEXT extends CodeCompletionContext>
    {
        public final @Recorded EXPRESSION result;
        public final String adjustedContent;
        public final CaretPosMapper mapperToAdjusted;
        public final @SourceLocation int[] caretPositions;
        public final ImmutableList<ErrorDetails> errors;
        public final ImmutableList<Lexer.AutoCompleteDetails<CODE_COMPLETION_CONTEXT>> autoCompleteDetails;
        public final BitSet suppressBracketMatching;

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
        public LexerResult(@Recorded EXPRESSION result, String adjustedContent, CaretPosMapper mapperToAdjusted, ImmutableList<ErrorDetails> errors, ImmutableList<AutoCompleteDetails<CODE_COMPLETION_CONTEXT>> completeDetails, BitSet suppressBracketMatching)
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
            this.autoCompleteDetails = completeDetails;
            this.suppressBracketMatching = suppressBracketMatching;
        }
        
        public ImmutableList<LexCompletion> getCompletionsFor(@SourceLocation int pos)
        {
            return autoCompleteDetails.stream().filter(a -> a.location.contains(pos)).findFirst().map(a -> a.codeCompletionContext.getCompletionsFor(pos)).orElse(ImmutableList.of());
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
    public LexerResult<EXPRESSION, CODE_COMPLETION_CONTEXT> process(String content);
}
