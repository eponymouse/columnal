package records.gui.lexeditor;

import annotation.recorded.qual.Recorded;
import annotation.units.CanonicalLocation;
import annotation.units.DisplayLocation;
import annotation.units.RawInputLocation;
import com.google.common.collect.ImmutableList;
import log.Log;
import records.gui.lexeditor.EditorLocationAndErrorRecorder.CanonicalSpan;
import records.gui.lexeditor.EditorLocationAndErrorRecorder.DisplaySpan;
import records.gui.lexeditor.EditorLocationAndErrorRecorder.ErrorDetails;
import records.gui.lexeditor.LexAutoComplete.LexCompletion;
import records.gui.lexeditor.Lexer.LexerResult.CaretPos;
import styled.StyledShowable;
import styled.StyledString;
import utility.Utility;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;

public abstract class Lexer<EXPRESSION extends StyledShowable, CODE_COMPLETION_CONTEXT extends CodeCompletionContext>
{
    static class LexerResult<EXPRESSION extends styled.StyledShowable, CODE_COMPLETION_CONTEXT extends CodeCompletionContext>
    {
        public static class CaretPos
        {
            public final @CanonicalLocation int positionInternal;
            public final @DisplayLocation int positionDisplay;

            public CaretPos(@CanonicalLocation int positionInternal, @DisplayLocation int positionDisplay)
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
        public final RemovedCharacters removedChars;
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
        public LexerResult(@Recorded EXPRESSION result, String adjustedContent, RemovedCharacters removedChars, boolean reLexOnCaretMove, ImmutableList<CaretPos> caretPositions, StyledString display, ImmutableList<ErrorDetails> errors, ImmutableList<AutoCompleteDetails<CODE_COMPLETION_CONTEXT>> completeDetails, BitSet suppressBracketMatching, boolean bracketsBalanced)
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
        
        public ImmutableList<LexCompletion> getCompletionsFor(@CanonicalLocation int pos)
        {
            return autoCompleteDetails.stream().filter(a -> a.location.touches(pos)).flatMap(a -> a.codeCompletionContext.getCompletionsFor(pos).stream()).collect(ImmutableList.<LexCompletion>toImmutableList());
        }

        public @DisplayLocation int mapContentToDisplay(@CanonicalLocation int contentPos)
        {
            // We assume that the position is a caret position when mapping from content
            for (CaretPos caretPosition : caretPositions)
            {
                if (caretPosition.positionInternal == contentPos)
                    return caretPosition.positionDisplay;
            }
            // Uh-oh!  Inaccurate, but will do as a fallback:
            Log.logStackTrace("Trying to find non-caret content position " + contentPos + " in {{{" + adjustedContent + "}}} positions are: " + Utility.listToString(caretPositions));
            @SuppressWarnings("units")
            @DisplayLocation int fallback = contentPos;
            return fallback;
        }

        public @CanonicalLocation int mapDisplayToContent(@DisplayLocation int clickedCaret, boolean biasEarlier)
        {
            // Shouldn't happen, but try to stay sane:
            if (caretPositions.isEmpty())
            {
                @SuppressWarnings("units")
                @CanonicalLocation int zero = 0;
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
    
    public static class AutoCompleteDetails<CODE_COMPLETION_CONTEXT extends CodeCompletionContext>
    {
        public final CanonicalSpan location;
        public final CODE_COMPLETION_CONTEXT codeCompletionContext;

        public AutoCompleteDetails(CanonicalSpan location, CODE_COMPLETION_CONTEXT codeCompletionContext)
        {
            this.location = location;
            this.codeCompletionContext = codeCompletionContext;
        }
    }

    // Helper class for implementations
    static class RemovedCharacters
    {
        // Indexes are in RawInputLocation
        private final BitSet removedChars = new BitSet();

        @SuppressWarnings("units")
        public @CanonicalLocation int map(@RawInputLocation int location)
        {
            return location - removedChars.get(0, location).cardinality();
        }

        public CanonicalSpan map(@RawInputLocation int startIncl, @RawInputLocation int endExcl)
        {
            return new CanonicalSpan(map(startIncl), map(endExcl));
        }

        public CanonicalSpan map(@RawInputLocation int startIncl, String skipString)
        {
            @SuppressWarnings("units")
            @RawInputLocation int length = skipString.length();
            return new CanonicalSpan(map(startIncl), map(startIncl + length));
        }
        
        public void set(@RawInputLocation int index)
        {
            removedChars.set(index);
        }

        // Effectively does: this = this | (src << shiftBy)
        public void orShift(RemovedCharacters src, int shiftBy)
        {
            for (int srcBit = src.removedChars.nextSetBit(0); srcBit != -1; srcBit = src.removedChars.nextSetBit(srcBit + 1))
            {
                removedChars.set(srcBit + shiftBy);
            }
        }
    }

    @SuppressWarnings("units")
    protected static @RawInputLocation int rawLength(String content)
    {
        return content.length();
    }
    
    // Takes latest content, lexes it, returns result
    public abstract LexerResult<EXPRESSION, CODE_COMPLETION_CONTEXT> process(String content, int caretPos);

    protected static StyledString padZeroWidthErrors(StyledString display, ArrayList<CaretPos> caretPos, ImmutableList<ErrorDetails> errors)
    {

        // Important to go through in order so that later errors can be
        // adjusted correctly according to earlier errors.

        for (ErrorDetails error : Utility.iterableStream(errors.stream().sorted(Comparator.comparing(e -> e.location.start))))
        {
            // If an error only occupies one caret position, add an extra char there:
            if (error.location.start == error.location.end)
            {
                // Find caret pos:
                @DisplayLocation int displayOffset = DisplayLocation.ZERO;
                for (int i = 0; i < caretPos.size(); i++)
                {
                    if (displayOffset != 0)
                    {
                        caretPos.set(i, new CaretPos(caretPos.get(i).positionInternal, caretPos.get(i).positionDisplay + displayOffset));
                    }
                    else
                    {
                        CaretPos p = caretPos.get(i);

                        if (p.positionInternal == error.location.start)
                        {
                            error.displayLocation = new DisplaySpan(p.positionDisplay, p.positionDisplay + DisplayLocation.ONE);
                            // Add space to display:
                            display = StyledString.concat(display.substring(0, p.positionDisplay), StyledString.s(" "), display.substring(p.positionDisplay, display.getLength()));
                            // And offset future caret pos display by one:
                            displayOffset += DisplayLocation.ONE;
                        }
                    }
                }
            }
        }
        return display;
    }
}
