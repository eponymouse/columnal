package records.gui.lexeditor;

import annotation.recorded.qual.Recorded;
import annotation.units.SourceLocation;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import log.Log;
import records.gui.lexeditor.EditorLocationAndErrorRecorder.ErrorDetails;
import records.gui.lexeditor.EditorLocationAndErrorRecorder.Span;
import records.gui.lexeditor.LexAutoComplete.LexCompletion;
import styled.StyledShowable;
import styled.StyledString;
import utility.Utility;

import java.util.BitSet;

public interface Lexer<EXPRESSION extends StyledShowable, CODE_COMPLETION_CONTEXT extends CodeCompletionContext>
{
    static class LexerResult<EXPRESSION extends StyledShowable, CODE_COMPLETION_CONTEXT extends CodeCompletionContext>
    {
        public static class CaretPos
        {
            public final @SourceLocation int positionInternal;
            public final int positionDisplay;

            @SuppressWarnings("units")
            public CaretPos(int positionInternal, int positionDisplay)
            {
                this.positionInternal = positionInternal;
                this.positionDisplay = positionDisplay;
            }

            @Override
            public String toString()
            {
                return "(internal=" + positionInternal +
                        ", display=" + positionDisplay +
                        ')';
            }
        }
        
        // Save result of parsing the content.  Never null because we save invalid items:
        public final @Recorded EXPRESSION result;
        // The internal canonical content after parsing:
        public final String adjustedContent;
        // The removed characters (indexes in original string to process):
        public final BitSet removedChars;
        public final boolean reLexOnCaretMove;
        // Valid caret positions
        public final ImmutableList<CaretPos> caretPositions;
        // The content to display in the TextFlow
        public final StyledString display;
        
        public final ImmutableList<ErrorDetails> errors;
        public final ImmutableList<Lexer.AutoCompleteDetails<CODE_COMPLETION_CONTEXT>> autoCompleteDetails;
        // If a bit is set at a particular caret position, when the user
        // types a ({[ opening bracket then do not insert the closing bracket.
        public final BitSet suppressBracketMatching;
        public final boolean bracketsAreBalanced;

        @SuppressWarnings("units")
        public LexerResult(@Recorded EXPRESSION result, String adjustedContent, BitSet removedChars, boolean reLexOnCaretMove, ImmutableList<CaretPos> caretPositions, StyledString display, ImmutableList<ErrorDetails> errors, ImmutableList<AutoCompleteDetails<CODE_COMPLETION_CONTEXT>> completeDetails, BitSet suppressBracketMatching, boolean bracketsBalanced)
        {
            this.result = result;
            this.adjustedContent = adjustedContent;
            this.reLexOnCaretMove = reLexOnCaretMove;
            this.removedChars = removedChars;
            this.caretPositions = caretPositions;
            this.display = display;
            this.errors = errors;
            this.autoCompleteDetails = completeDetails;
            this.suppressBracketMatching = suppressBracketMatching;
            this.bracketsAreBalanced = bracketsBalanced;
            Log.debug("Showing errors: " + Utility.listToString(errors));
        }
        
        public ImmutableList<LexCompletion> getCompletionsFor(@SourceLocation int pos)
        {
            return autoCompleteDetails.stream().filter(a -> a.location.contains(pos)).flatMap(a -> a.codeCompletionContext.getCompletionsFor(pos).stream()).collect(ImmutableList.<LexCompletion>toImmutableList());
        }
        
        @SuppressWarnings("units")
        public @SourceLocation int mapOldCaretPos(@SourceLocation int pos)
        {
            int mapped = pos - removedChars.get(0, pos).cardinality();
            for (ErrorDetails error : errors)
            {
                error.caretHasLeftSinceEdit = !error.location.contains(mapped);
            }
            return mapped;
        }

        public int mapContentToDisplay(@SourceLocation int contentPos)
        {
            // We assume that the position is a caret position when mapping from content
            for (CaretPos caretPosition : caretPositions)
            {
                if (caretPosition.positionInternal == contentPos)
                    return caretPosition.positionDisplay;
            }
            // Uh-oh!  Inaccurate, but will do as a fallback:
            Log.logStackTrace("Trying to find non-caret content position " + contentPos + " in {{{" + adjustedContent + "}}} positions are: " + Utility.listToString(caretPositions));
            return contentPos;
        }

        // Finds the Nth (N = targetPos) clear index in the given bitset.
        @SuppressWarnings("units")
        public static Span findNthClearIndex(BitSet addedDisplayChars, int targetPos)
        {
            // We look for the ith empty spot in addedDisplayChars
            int start = 0;
            for (int j = 0; j < targetPos; j++)
            {
                if (addedDisplayChars.get(j))
                {
                    start += 1;
                }
                start += 1;
            }
            // It ends at the next empty spot:
            return new Span(start, addedDisplayChars.nextClearBit(targetPos) - targetPos + start);
        }

        public @SourceLocation int mapDisplayToContent(int clickedCaret, boolean biasEarlier)
        {
            // Shouldn't happen, but try to stay sane:
            if (caretPositions.isEmpty())
            {
                @SuppressWarnings("units")
                @SourceLocation int zero = 0;
                return zero;
            }
            // We need to find the nearest display caret position
            // If it's a draw, use biasEarlier to pick
            int prevDist = Math.abs(caretPositions.get(0).positionDisplay - clickedCaret);
            for (int i = 1; i < caretPositions.size(); i++)
            {
                int curDist = Math.abs(caretPositions.get(i).positionDisplay - clickedCaret);
                if (curDist > prevDist)
                    return caretPositions.get(i - 1).positionInternal;
                else if (curDist == prevDist)
                    return caretPositions.get(biasEarlier ? i - 1 : i).positionInternal; 
                    
                prevDist = curDist;
            }
            // Must be last one then:
            return caretPositions.get(caretPositions.size() - 1).positionInternal;
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
