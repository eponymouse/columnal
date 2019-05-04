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
import records.gui.lexeditor.Lexer.LexerResult.CaretPos;
import records.gui.lexeditor.completion.LexCompletionGroup;
import styled.StyledCSS;
import styled.StyledShowable;
import styled.StyledString;
import utility.Pair;
import utility.Utility;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiFunction;
import java.util.stream.IntStream;

public abstract class Lexer<EXPRESSION extends StyledShowable, CODE_COMPLETION_CONTEXT extends CodeCompletionContext>
{
    // First is all caret pos, second is word boundary
    @SuppressWarnings("units")
    protected static Pair<ArrayList<CaretPos>, ArrayList<CaretPos>> calculateCaretPos(ArrayList<ContentChunk> chunks)
    {
        ArrayList<CaretPos> caretPos = new ArrayList<>();
        ArrayList<CaretPos> wordBoundaryCaretPos = new ArrayList<>();
        @CanonicalLocation int internalLenSoFar = 0;
        @DisplayLocation int displayLenSoFar = 0;
        for (ContentChunk chunk : chunks)
        {
            for (CaretPos caretPosition : chunk.caretPositions)
            {
                addCaretPos(caretPos, new CaretPos(caretPosition.positionInternal + internalLenSoFar, caretPosition.positionDisplay + displayLenSoFar));
            }
            for (CaretPos caretPosition : chunk.wordBoundaryCaretPositions)
            {
                addCaretPos(wordBoundaryCaretPos, new CaretPos(caretPosition.positionInternal + internalLenSoFar, caretPosition.positionDisplay + displayLenSoFar));
            }
            internalLenSoFar += chunk.internalContent.length();
            displayLenSoFar += chunk.displayContent.getLength();
        }
        // Empty strings should still have a caret pos:
        if (chunks.isEmpty())
        {
            chunks.add(new ContentChunk("", StyledString.s(" "), ChunkType.IDENT));
            caretPos.add(new CaretPos(0, 0));
            wordBoundaryCaretPos.add(new CaretPos(0, 0));
        }
        return new Pair<>(caretPos, wordBoundaryCaretPos);
    }

    private static void addCaretPos(ArrayList<CaretPos> caretPos, CaretPos newPos)
    {
        if (!caretPos.isEmpty())
        {
            CaretPos last = caretPos.get(caretPos.size() - 1);
            if (newPos.positionInternal == last.positionInternal && newPos.positionDisplay == last.positionDisplay)
            {
                // Complete duplicate, ignore
                return;
            }
            else if (newPos.positionInternal == last.positionInternal || newPos.positionDisplay == last.positionDisplay)
            {
                // Partial duplicate, so gives conflict.  Favour first by ignoring
                return;
            }
        }
        caretPos.add(newPos);
    }

    protected static <CCC extends CodeCompletionContext> ImmutableList<AutoCompleteDetails<CCC>> makeCompletions(List<ContentChunk> chunks, BiFunction<String, @CanonicalLocation Integer, CCC> makeCompletions)
    {
        ImmutableList.Builder<AutoCompleteDetails<CCC>> acd = ImmutableList.builderWithExpectedSize(chunks.size());

        @CanonicalLocation int curPos = CanonicalLocation.ZERO;
        ChunkType prevChunkType = ChunkType.NON_IDENT;
        for (ContentChunk chunk : chunks)
        {
            @SuppressWarnings("units")
            @CanonicalLocation int nextPos = curPos + chunk.internalContent.length();
            if (chunk.chunkType != ChunkType.NESTED)
            {
                @SuppressWarnings("units")
                @CanonicalLocation int start = prevChunkType == ChunkType.IDENT ? curPos + 1 : curPos;
                CanonicalSpan location = new CanonicalSpan(start, chunk.chunkType == ChunkType.NESTED_START ? start : nextPos);
                acd.add(new AutoCompleteDetails<>(location, makeCompletions.apply(chunk.internalContent, curPos)));
            }
            
            curPos = nextPos;
            prevChunkType = chunk.chunkType;
        }
        
        return acd.build();
    }

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
        public final ImmutableList<CaretPos> wordBoundaryCaretPositions;
        // The content to display in the TextFlow
        public final StyledString display;
        
        public final ImmutableList<ErrorDetails> errors;
        // For added nested recorded locations to the outer recorder
        public final EditorLocationAndErrorRecorder locationRecorder;
        public final ImmutableList<Lexer.AutoCompleteDetails<CODE_COMPLETION_CONTEXT>> autoCompleteDetails;
        // If a bit is set at a particular caret position, when the user
        // types a ({[ opening bracket then do not insert the closing bracket.
        public final BitSet suppressBracketMatching;
        public final boolean bracketsAreBalanced;

        @SuppressWarnings("units")
        public LexerResult(@Recorded EXPRESSION result, String adjustedContent, RemovedCharacters removedChars, boolean reLexOnCaretMove, ImmutableList<CaretPos> caretPositions, ImmutableList<CaretPos> wordBoundaryCaretPositions, StyledString display, ImmutableList<ErrorDetails> errors, EditorLocationAndErrorRecorder locationRecorder, ImmutableList<AutoCompleteDetails<CODE_COMPLETION_CONTEXT>> completeDetails, BitSet suppressBracketMatching, boolean bracketsBalanced)
        {
            this.result = result;
            this.adjustedContent = adjustedContent;
            this.reLexOnCaretMove = reLexOnCaretMove;
            this.removedChars = removedChars;
            this.caretPositions = caretPositions;
            this.wordBoundaryCaretPositions = wordBoundaryCaretPositions;
            this.display = display;
            this.errors = errors;
            this.locationRecorder = locationRecorder;
            this.autoCompleteDetails = completeDetails;
            this.suppressBracketMatching = suppressBracketMatching;
            this.bracketsAreBalanced = bracketsBalanced;
            Log.debug("Showing errors: " + Utility.listToString(errors));
        }
        
        public ImmutableList<LexCompletionGroup> getCompletionsFor(@CanonicalLocation int pos)
        {
            return autoCompleteDetails.stream().filter(a -> a.location.touches(pos)).flatMap(a -> a.codeCompletionContext.getCompletionsFor(pos).stream()).collect(ImmutableList.<LexCompletionGroup>toImmutableList());
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
    public abstract LexerResult<EXPRESSION, CODE_COMPLETION_CONTEXT> process(String content, @RawInputLocation int caretPos);

    protected static StyledString padZeroWidthErrors(StyledString display, ArrayList<CaretPos> caretPos, ImmutableList<ErrorDetails> errors)
    {

        // Important to go through in order so that later errors can be
        // adjusted correctly according to earlier errors.

        for (ErrorDetails error : Utility.iterableStream(errors.stream().sorted(Comparator.comparing(e -> e.location.start))))
        {
            // If an error only occupies one caret position, add an extra char there:
            if (error.location.start == error.location.end && (error.displayLocation == null || error.displayLocation.start == error.displayLocation.end))
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
    
    protected static enum ChunkType
    {
        IDENT, NON_IDENT, NESTED, NESTED_START;
    }

    protected static class ContentChunk
    {
        protected final String internalContent;
        // Positions are relative to this chunk:
        protected final ImmutableList<CaretPos> caretPositions;
        protected final ImmutableList<CaretPos> wordBoundaryCaretPositions;
        protected final StyledString displayContent;
        protected final ChunkType chunkType;

        public ContentChunk(String simpleContent, ChunkType chunkType, String... styleClasses)
        {
            this(simpleContent, StyledString.s(simpleContent).withStyle(new StyledCSS(styleClasses)), chunkType);
        }
        
        @SuppressWarnings("units")
        public ContentChunk(String simpleContent, StyledString styledString, ChunkType chunkType)
        {
            internalContent = simpleContent;
            displayContent = styledString;
            caretPositions = IntStream.range(0, simpleContent.length() + 1).mapToObj(i -> new CaretPos(i, i)).collect(ImmutableList.<CaretPos>toImmutableList());
            wordBoundaryCaretPositions = ImmutableList.of(new CaretPos(0, 0), new CaretPos(simpleContent.length(), simpleContent.length()));
            this.chunkType = chunkType;
        }
        
        // Special keyword/operator that has no valid caret positions except at ends, and optionally pads with spaces
        @SuppressWarnings("units")
        public ContentChunk(boolean addLeadingSpace, ExpressionToken specialContent, ChunkType chunkType, boolean addTrailingSpace)
        {
            internalContent = specialContent.getContent();
            displayContent = StyledString.concat(StyledString.s(addLeadingSpace ? " " : ""), specialContent.toStyledString(), StyledString.s(addTrailingSpace ? " " : ""));
            caretPositions = ImmutableList.of(new CaretPos(0, 0), new CaretPos(specialContent.getContent().length(), displayContent.getLength()));
            wordBoundaryCaretPositions = caretPositions;
            this.chunkType = chunkType;
        }

        public ContentChunk(String internalContent, StyledString displayContent, ImmutableList<CaretPos> caretPositions, ImmutableList<CaretPos> wordBoundaryCaretPositions, ChunkType chunkType)
        {
            this.internalContent = internalContent;
            this.caretPositions = caretPositions;
            this.wordBoundaryCaretPositions = wordBoundaryCaretPositions;
            this.displayContent = displayContent;
            this.chunkType = chunkType;
        }
    }
}
