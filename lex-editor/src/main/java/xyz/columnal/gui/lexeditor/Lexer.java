/*
 * Columnal: Safer, smoother data table processing.
 * Copyright (c) Neil Brown, 2016-2020, 2022.
 *
 * This file is part of Columnal.
 *
 * Columnal is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 *
 * Columnal is distributed in the hope that it will be useful, but WITHOUT 
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or 
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for 
 * more details.
 *
 * You should have received a copy of the GNU General Public License along 
 * with Columnal. If not, see <https://www.gnu.org/licenses/>.
 */

package xyz.columnal.gui.lexeditor;

import annotation.recorded.qual.Recorded;
import annotation.units.CanonicalLocation;
import annotation.units.DisplayLocation;
import annotation.units.RawInputLocation;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import javafx.scene.Node;
import xyz.columnal.log.Log;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.gui.lexeditor.TopLevelEditor.DisplayType;
import xyz.columnal.transformations.expression.CanonicalSpan;
import xyz.columnal.gui.lexeditor.EditorLocationAndErrorRecorder.DisplaySpan;
import xyz.columnal.gui.lexeditor.EditorLocationAndErrorRecorder.ErrorDetails;
import xyz.columnal.gui.lexeditor.Lexer.LexerResult.CaretPos;
import xyz.columnal.gui.lexeditor.completion.InsertListener;
import xyz.columnal.gui.lexeditor.completion.LexCompletionGroup;
import xyz.columnal.styled.StyledCSS;
import xyz.columnal.styled.StyledShowable;
import xyz.columnal.styled.StyledString;
import xyz.columnal.utility.function.fx.FXPlatformBiFunction;
import xyz.columnal.utility.IdentifierUtility.Consumed;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.Utility;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

public abstract class Lexer<EXPRESSION extends StyledShowable, CODE_COMPLETION_CONTEXT extends CodeCompletionContext>
{
    // First is all caret pos, second is word boundary
    @SuppressWarnings("units")
    protected static Pair<ArrayList<CaretPos>, ImmutableList<@CanonicalLocation Integer>> calculateCaretPos(ArrayList<ContentChunk> chunks)
    {
        ArrayList<CaretPos> caretPos = new ArrayList<>();
        ArrayList<@CanonicalLocation Integer> wordBoundaryCaretPos = new ArrayList<>();
        @CanonicalLocation int internalLenSoFar = 0;
        @DisplayLocation int displayLenSoFar = 0;
        for (ContentChunk chunk : chunks)
        {
            for (CaretPos caretPosition : chunk.caretPositions)
            {
                addCaretPos(caretPos, new CaretPos(caretPosition.positionInternal + internalLenSoFar, caretPosition.positionDisplay + displayLenSoFar));
            }
            for (@CanonicalLocation int caretPosition : chunk.wordBoundaryCaretPositions)
            {
                if (!wordBoundaryCaretPos.contains(caretPosition + internalLenSoFar))
                    wordBoundaryCaretPos.add(caretPosition + internalLenSoFar);
            }
            internalLenSoFar += chunk.internalContent.length();
            displayLenSoFar += chunk.displayContent.getLength();
        }
        // Empty strings should still have a caret pos:
        if (chunks.isEmpty())
        {
            chunks.add(new ContentChunk("", StyledString.s(" "), ChunkType.IDENT));
            caretPos.add(new CaretPos(0, 0));
            wordBoundaryCaretPos.add(0);
        }
        return new Pair<>(caretPos, ImmutableList.copyOf(wordBoundaryCaretPos));
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
    
    protected static interface MakeCompletions<CCC extends CodeCompletionContext>
    {
        public CCC makeCompletions(String chunk, @CanonicalLocation int canonIndex, ChunkType curChunk, ChunkType precedingChunk);
    }
    
    protected static <CCC extends CodeCompletionContext> ImmutableList<AutoCompleteDetails<CCC>> makeCompletions(List<ContentChunk> chunks, MakeCompletions<CCC> makeCompletions)
    {
        ImmutableList.Builder<AutoCompleteDetails<CCC>> acd = ImmutableList.builderWithExpectedSize(chunks.size());

        // An expression can be viewed as a list of chunks of different types:
        //   - Ident
        //   - Keyword (i.e. something beginning with @, or a bracket)
        //   - Operator 
        //   - Nested literal
        // There can never be two adjacent ident chunks, because they would be treated as one ident chunk.
        // There can be issues with overlapping or missing completions.  For example, if you have:
        //   abs((1+2)/3)
        // We don't want to suddenly show the completion "sqrt" between abs and the first opening bracket, because it's not
        // a match for the stem abs.  However, we do want to show the sqrt completion between the two opening brackets.
        // Another issue is that we don't want to display operators immediately at the beginning, nor just after
        // an opening bracket.  We do want to show them after the 'a', 'b' and 's' in abs.  But after the +,
        // we only want to show the +- completion, because it's the only one prefixed by the earlier operator.
        // So here's the plan:
        //   - When calculating completions, we need to know if we were preceded by an ident.
        //     - If yes, do not show any completions at all in first spot (ident will handle that).
        //     - If false, do we follow an opening bracket/keyword, operator or closing bracket/keyword?
        //       - If opening or operator, do not show any operators in first spot (we either follow a bracket, or we follow an operator which will sort itself out)
        //       - If closing, do show operators in first spot
        // So, we need to categorise previous chunk into:
        //   - Ident
        //   - Opening keyword/bracket (including partial keywords and operators)
        //   - Closing keyword/bracket
        //   - Nested
        @CanonicalLocation int curPos = CanonicalLocation.ZERO;
        ChunkType prevChunkType = ChunkType.OPENING;
        @Nullable String prevChunk = null;
        for (ContentChunk chunk : chunks)
        {
            //Log.debug("Chunk: {" + chunk.internalContent + "} type: " + chunk.chunkType);
            
            @SuppressWarnings("units")
            @CanonicalLocation int nextPos = curPos + chunk.internalContent.length();
            if (chunk.chunkType != ChunkType.NESTED)
            {
                @SuppressWarnings("units")
                @CanonicalLocation int start = curPos;
                CanonicalSpan location = new CanonicalSpan(start, chunk.chunkType == ChunkType.NESTED_START ? start : nextPos);
                CCC context = makeCompletions.makeCompletions(chunk.internalContent, curPos, chunk.chunkType, prevChunkType);
                acd.add(new AutoCompleteDetails<>(location, context));
            }
            
            curPos = nextPos;
            prevChunkType = chunk.chunkType;
            prevChunk = chunk.internalContent;
        }
        if (prevChunkType != ChunkType.IDENT)
        {
            // Add completions beyond last item:
            CanonicalSpan location = new CanonicalSpan(curPos, curPos);
            CCC context = makeCompletions.makeCompletions("", curPos, ChunkType.IDENT, prevChunkType);
            acd.add(new AutoCompleteDetails<>(location, context));
        }
        
        return acd.build();
    }

    static class LexerResult<EXPRESSION extends xyz.columnal.styled.StyledShowable, CODE_COMPLETION_CONTEXT extends CodeCompletionContext>
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
        public final ImmutableList<@CanonicalLocation Integer> wordBoundaryCaretPositions;
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
        public final FXPlatformBiFunction<@CanonicalLocation Integer, Node, ImmutableMap<DisplayType, Pair<StyledString, ImmutableList<TextQuickFix>>>> infoAndPromptForPosition;

        @SuppressWarnings("units")
        public LexerResult(@Recorded EXPRESSION result, String adjustedContent, RemovedCharacters removedChars, boolean reLexOnCaretMove, ImmutableList<CaretPos> caretPositions, ImmutableList<@CanonicalLocation Integer> wordBoundaryCaretPositions, StyledString display, ImmutableList<ErrorDetails> errors, EditorLocationAndErrorRecorder locationRecorder, ImmutableList<AutoCompleteDetails<CODE_COMPLETION_CONTEXT>> completeDetails, BitSet suppressBracketMatching, boolean bracketsBalanced, FXPlatformBiFunction<@CanonicalLocation Integer, Node,  ImmutableMap<DisplayType, Pair<StyledString, ImmutableList<TextQuickFix>>>> infoAndPromptForPosition)
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
            this.infoAndPromptForPosition = infoAndPromptForPosition;
            if (!errors.isEmpty())
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

        public void setAll(Consumed<?> consumed)
        {
            consumed.removedCharacters.forEach(this::set);
        }
    }

    @SuppressWarnings("units")
    protected static @RawInputLocation int rawLength(String content)
    {
        return content.length();
    }
    
    // Takes latest content, lexes it, returns result
    public abstract LexerResult<EXPRESSION, CODE_COMPLETION_CONTEXT> process(String content, @Nullable @RawInputLocation Integer caretPos, InsertListener insertListener);

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
        IDENT, OPENING, CLOSING, NESTED, NESTED_START;
    }

    protected static class ContentChunk
    {
        protected final String internalContent;
        // Positions are relative to this chunk:
        protected final ImmutableList<CaretPos> caretPositions;
        protected final ImmutableList<@CanonicalLocation Integer> wordBoundaryCaretPositions;
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
            wordBoundaryCaretPositions = ImmutableList.of(0, simpleContent.length());
            this.chunkType = chunkType;
        }
        
        // Special keyword/operator that has no valid caret positions except at ends, and optionally pads with spaces
        @SuppressWarnings("units")
        public ContentChunk(boolean addLeadingSpace, ExpressionToken specialContent, ChunkType chunkType, boolean addTrailingSpace)
        {
            internalContent = specialContent.getContent();
            displayContent = StyledString.concat(StyledString.s(addLeadingSpace ? " " : ""), specialContent.toStyledString(), StyledString.s(addTrailingSpace ? " " : ""));
            caretPositions = ImmutableList.of(new CaretPos(0, 0), new CaretPos(specialContent.getContent().length(), displayContent.getLength()));
            wordBoundaryCaretPositions = Utility.mapListI(caretPositions, p -> p.positionInternal);
            this.chunkType = chunkType;
        }

        public ContentChunk(String internalContent, StyledString displayContent, ImmutableList<CaretPos> caretPositions, ImmutableList<@CanonicalLocation Integer> wordBoundaryCaretPositions, ChunkType chunkType)
        {
            this.internalContent = internalContent;
            this.caretPositions = caretPositions;
            this.wordBoundaryCaretPositions = wordBoundaryCaretPositions;
            this.displayContent = displayContent;
            this.chunkType = chunkType;
        }
    }
}
