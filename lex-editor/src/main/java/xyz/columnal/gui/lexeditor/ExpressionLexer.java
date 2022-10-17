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

import annotation.identifier.qual.ExpressionIdentifier;
import annotation.qual.Value;
import annotation.recorded.qual.Recorded;
import annotation.units.CanonicalLocation;
import annotation.units.DisplayLocation;
import annotation.units.RawInputLocation;
import com.google.common.base.CharMatcher;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.collect.ImmutableMap;
import javafx.beans.value.ObservableObjectValue;
import xyz.columnal.log.Log;
import org.checkerframework.checker.i18n.qual.LocalizableKey;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.id.ColumnId;
import xyz.columnal.id.TableId;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.DataType.DateTimeInfo.DateTimeType;
import xyz.columnal.data.datatype.DataType.TagType;
import xyz.columnal.data.datatype.TaggedTypeDefinition;
import xyz.columnal.data.datatype.TypeId;
import xyz.columnal.data.datatype.TypeManager;
import xyz.columnal.error.ExceptionWithStyle;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.grammar.GrammarUtility;
import xyz.columnal.transformations.expression.BooleanLiteral;
import xyz.columnal.transformations.expression.CanonicalSpan;
import xyz.columnal.gui.lexeditor.EditorLocationAndErrorRecorder.ErrorDetails;
import xyz.columnal.gui.lexeditor.completion.InsertListener;
import xyz.columnal.gui.lexeditor.completion.LexCompletion;
import xyz.columnal.gui.lexeditor.completion.LexAutoComplete.LexSelectionBehaviour;
import xyz.columnal.gui.lexeditor.Lexer.LexerResult.CaretPos;
import xyz.columnal.gui.lexeditor.completion.LexCompletionGroup;
import xyz.columnal.jellytype.JellyType;
import xyz.columnal.transformations.expression.DefineExpression;
import xyz.columnal.transformations.expression.DefineExpression.DefineItem;
import xyz.columnal.transformations.expression.DefineExpression.Definition;
import xyz.columnal.transformations.expression.Expression;
import xyz.columnal.transformations.expression.Expression.ColumnLookup;
import xyz.columnal.transformations.expression.HasTypeExpression;
import xyz.columnal.transformations.expression.IdentExpression;
import xyz.columnal.transformations.expression.IfThenElseExpression;
import xyz.columnal.transformations.expression.InvalidIdentExpression;
import xyz.columnal.transformations.expression.MatchAnythingExpression;
import xyz.columnal.transformations.expression.MatchExpression;
import xyz.columnal.transformations.expression.MatchExpression.MatchClause;
import xyz.columnal.transformations.expression.NumericLiteral;
import xyz.columnal.transformations.expression.StringLiteral;
import xyz.columnal.transformations.expression.TemporalLiteral;
import xyz.columnal.transformations.expression.TypeLiteralExpression;
import xyz.columnal.transformations.expression.TypeState;
import xyz.columnal.transformations.expression.UnitExpression;
import xyz.columnal.transformations.expression.UnitLiteralExpression;
import xyz.columnal.transformations.expression.function.FunctionLookup;
import xyz.columnal.transformations.expression.function.StandardFunctionDefinition;
import xyz.columnal.transformations.expression.type.TypeExpression;
import xyz.columnal.transformations.expression.visitor.ExpressionVisitorStream;
import xyz.columnal.typeExp.TypeExp;
import xyz.columnal.styled.StyledCSS;
import xyz.columnal.styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.function.fx.FXPlatformSupplierInt;
import xyz.columnal.utility.IdentifierUtility;
import xyz.columnal.utility.IdentifierUtility.Consumed;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.Utility;
import xyz.columnal.utility.gui.FXUtility;
import xyz.columnal.utility.TranslationUtility;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class ExpressionLexer extends Lexer<Expression, ExpressionCompletionContext>
{
    private static ImmutableList<StandardFunctionDefinition> getAllFunctions(FunctionLookup functionLookup)
    {
        try
        {
            return functionLookup.getAllFunctions();
        }
        catch (InternalException e)
        {
            Log.log(e);
            return ImmutableList.of();
        }
    }

    /**
     * The difference between a Keyword and Op is that a Keyword is never a prefix of a longer
     * item, and thus always completes immediately when directly matched.
     */
    public static enum Keyword implements ExpressionToken
    {
        OPEN_SQUARE("["), CLOSE_SQUARE("]"), OPEN_ROUND("("), CLOSE_ROUND(")"), QUEST("?"),
        IF(xyz.columnal.grammar.ExpressionLexer.IF), THEN(xyz.columnal.grammar.ExpressionLexer.THEN), ELSE(xyz.columnal.grammar.ExpressionLexer.ELSE), ENDIF(xyz.columnal.grammar.ExpressionLexer.ENDIF),
        MATCH(xyz.columnal.grammar.ExpressionLexer.MATCH),
        CASE(xyz.columnal.grammar.ExpressionLexer.CASE),
        ORCASE(xyz.columnal.grammar.ExpressionLexer.ORCASE),
        GIVEN(xyz.columnal.grammar.ExpressionLexer.CASEGUARD),
        ENDMATCH(xyz.columnal.grammar.ExpressionLexer.ENDMATCH),
        DEFINE(xyz.columnal.grammar.ExpressionLexer.DEFINE),
        ENDDEFINE(xyz.columnal.grammar.ExpressionLexer.ENDDEFINE),
        FUNCTION(xyz.columnal.grammar.ExpressionLexer.FUNCTION),
        ENDFUNCTION(xyz.columnal.grammar.ExpressionLexer.ENDFUNCTION);

        private final String keyword;

        private Keyword(String keyword)
        {
            this.keyword = keyword;
        }

        private Keyword(int token)
        {
            this.keyword = Utility.literal(xyz.columnal.grammar.ExpressionLexer.VOCABULARY, token);
        }

        @Override
        @OnThread(Tag.Any)
        public String getContent()
        {
            return keyword;
        }

        @Override
        @OnThread(Tag.Any)
        public StyledString toStyledString()
        {
            String content = getContent();
            if (content.startsWith("@"))
            {
                return StyledString.concat(StyledString.s("@").withStyle(new StyledCSS("expression-" + this.name().toLowerCase(), "expression-at")), StyledString.s(content.substring(1)).withStyle(new StyledCSS("expression-" + this.name().toLowerCase())));
            }
            else
                return StyledString.s(content).withStyle(new StyledCSS("expression-" + this.name().toLowerCase()));
        }


        public boolean isClosing()
        {
            switch (this)
            {
                case CLOSE_ROUND:
                case CLOSE_SQUARE:
                case ENDIF:
                case ENDDEFINE:
                case ENDMATCH:
                case ENDFUNCTION:
                    return true;
            }
            return false;
        }
    }

    /**
     * An Op, unlike a Keyword, may have a longer alternative available, so should not
     * complete on direct match (unless it is the only possible direct match).
     */
    public static enum Op implements ExpressionToken
    {
        MULTIPLY("*", "op.times"), DIVIDE("/", "op.divide"), 
        ADD("+", "op.plus"), SUBTRACT("-", "op.minus"), 
        STRING_CONCAT(";", "op.stringConcat"), 
        EQUALS("=", "op.equal"), EQUALS_PATTERN("=~", "op.equalPattern"), NOT_EQUAL("<>", "op.notEqual"),
        LESS_THAN("<", "op.lessThan"), LESS_THAN_OR_EQUAL("<=", "op.lessThanOrEqual"), GREATER_THAN(">", "op.greaterThan"), GREATER_THAN_OR_EQUAL(">=", "op.greaterThanOrEqual"),
        AND("&", "op.and"), OR("|", "op.or"),
        PLUS_MINUS("\u00B1", "op.plusminus") {
            @Override
            public String getASCIIContent()
            {
                return "+-";
            }
        }, RAISE("^", "op.raise"),
        HAS_TYPE("::", "op.hasType"),
        COLON(":", "op.colon"),
        FIELD_ACCESS("#", "op.fieldAccess"),
        COMMA(",", "op.separator");

        private final String op;
        private final @LocalizableKey String localNameKey;

        private Op(String op, @LocalizableKey String localNameKey)
        {
            this.op = op;
            this.localNameKey = localNameKey;
        }

        @Override
        @OnThread(Tag.Any)
        public String getContent()
        {
            return op;
        }
        
        public String getASCIIContent()
        {
            return op;
        }
    }
    
    private final ObservableObjectValue<ColumnLookup> columnLookup;
    private final TypeManager typeManager;
    private final FunctionLookup functionLookup;
    private final ImmutableList<StandardFunctionDefinition> allFunctions;
    private final FXPlatformSupplierInt<TypeState> makeTypeState;
    private final @Nullable DataType expectedType;

    public ExpressionLexer(ObservableObjectValue<ColumnLookup> columnLookup, TypeManager typeManager, FunctionLookup functionLookup, FXPlatformSupplierInt<TypeState> makeTypeState, @Nullable DataType expectedType)
    {
        this.columnLookup = columnLookup;
        this.typeManager = typeManager;
        this.functionLookup = functionLookup;
        // Get functions once rather than every time we need them:
        this.allFunctions = getAllFunctions(functionLookup);
        this.makeTypeState = makeTypeState;
        this.expectedType = expectedType;
    }

    @Override
    public LexerResult<Expression, ExpressionCompletionContext> process(String content, @Nullable @RawInputLocation Integer curCaretPos, InsertListener insertListener)
    {
        ExpressionSaver saver = new ExpressionSaver(typeManager, functionLookup, insertListener);
        @RawInputLocation int curIndex = RawInputLocation.ZERO;
        // Index is in original parameter "content":
        RemovedCharacters removedChars = new RemovedCharacters();
        BitSet suppressBracketMatching = new BitSet();
        ArrayList<ContentChunk> chunks = new ArrayList<>();
        ImmutableList.Builder<AutoCompleteDetails<ExpressionCompletionContext>> nestedCompletions = ImmutableList.builder();
        boolean prevWasIdent = false;
        boolean preserveNextSpace = false;
        boolean lexOnMove = false;
        nextToken: while (curIndex < content.length())
        {
            // Skip any extra spaces at the start of tokens:
            if (content.startsWith(" ", curIndex))
            {
                // Keep single space after ident as it may continue ident:
                boolean spaceThenCaret = prevWasIdent && curCaretPos != null && curIndex + 1 == curCaretPos;
                if (spaceThenCaret || preserveNextSpace)
                {
                    chunks.add(new ContentChunk(" ", ChunkType.IDENT));
                    lexOnMove = spaceThenCaret;
                }
                else
                {
                    removedChars.set(curIndex);
                }
                prevWasIdent = false;
                preserveNextSpace = false;
                curIndex += RawInputLocation.ONE;
                continue nextToken;
            }
            prevWasIdent = false;
            preserveNextSpace = false;
            
            for (Keyword keyword : Keyword.values())
            {
                if (content.startsWith(keyword.getContent(), curIndex))
                {
                    saver.saveKeyword(keyword, removedChars.map(curIndex, keyword.getContent()));
                    if (keyword.getContent().startsWith("@"))
                    {
                        boolean addLeadingSpace = chunks.stream().mapToInt(c -> c.internalContent.length()).sum() > 0;
                        chunks.add(new ContentChunk(addLeadingSpace, keyword, keyword.isClosing() ? ChunkType.CLOSING : ChunkType.OPENING, true));
                    }
                    else
                        chunks.add(new ContentChunk(keyword.getContent(), keyword.isClosing() ? ChunkType.CLOSING : ChunkType.OPENING));
                    curIndex += rawLength(keyword.getContent());
                    continue nextToken;
                }
            }
            // Need to go through longest first:
            for (Op op : Utility.iterableStream(Arrays.stream(Op.values()).sorted(Comparator.comparing(o -> -o.getContent().length()))))
            {
                if (content.startsWith(op.getContent(), curIndex))
                {
                    saver.saveOperator(op, removedChars.map(curIndex, op.getContent()));
                    boolean addLeadingSpace = !op.getContent().equals(",");
                    chunks.add(new ContentChunk(addLeadingSpace, op, ChunkType.OPENING, true));
                    curIndex += rawLength(op.getContent());
                    continue nextToken;
                }
            }
            
            if (content.startsWith("\"", curIndex))
            {
                // Consume string until next quote:
                @SuppressWarnings("units")
                @RawInputLocation int endQuote = content.indexOf("\"", curIndex + 1);
                if (endQuote != -1)
                {
                    saver.saveOperand(new StringLiteral(content.substring(curIndex + 1, endQuote)), removedChars.map(curIndex, endQuote + RawInputLocation.ONE));
                    String stringLit = content.substring(curIndex, endQuote + 1);
                    @SuppressWarnings("units")
                    ImmutableList<CaretPos> caretPositions = IntStream.range(0, stringLit.length() + 1).mapToObj(i -> new CaretPos(i, i)).collect(ImmutableList.<CaretPos>toImmutableList());
                    @SuppressWarnings("units")
                    ImmutableList<@CanonicalLocation Integer> wordCaretPos = Stream.<Integer>of(0, 1, stringLit.length() - 1, stringLit.length()).distinct().collect(ImmutableList.<@CanonicalLocation Integer>toImmutableList());
                    chunks.add(new ContentChunk(stringLit, StyledString.s(stringLit).withStyle(new StyledCSS("expression-string-literal")), caretPositions, wordCaretPos, ChunkType.CLOSING));
                    suppressBracketMatching.set(curIndex + 1, endQuote);
                    curIndex = endQuote + RawInputLocation.ONE;
                    continue nextToken;
                }
                else
                {
                    // Unterminated string:
                    saver.locationRecorder.addErrorAndFixes(removedChars.map(curIndex, content), StyledString.s("Missing closing quote around text"), ImmutableList.of());
                    saver.saveOperand(new StringLiteral(GrammarUtility.processEscapes(content.substring(curIndex + 1, content.length()), false)), removedChars.map(curIndex, content));
                    chunks.add(new ContentChunk(content.substring(curIndex), ChunkType.NESTED_START));
                    suppressBracketMatching.set(curIndex + 1, content.length() + 1);
                    curIndex = rawLength(content);
                    continue nextToken;
                }
            }
            
            if (content.charAt(curIndex) >= '0' && content.charAt(curIndex) <= '9')
            {
                @RawInputLocation int numberStart = curIndex;
                // Before dot:
                do
                {
                    curIndex += RawInputLocation.ONE;
                }
                while (curIndex < content.length() && content.charAt(curIndex) >= '0' && content.charAt(curIndex) <= '9');
                
                if (curIndex < content.length() && content.charAt(curIndex) == '.')
                {
                    do
                    {
                        curIndex += RawInputLocation.ONE;
                    }
                    while (curIndex < content.length() && content.charAt(curIndex) >= '0' && content.charAt(curIndex) <= '9');
                }
                Optional<@Value Number> number = Utility.parseNumberOpt(content.substring(numberStart, curIndex));
                if (number.isPresent())
                {
                    saver.saveOperand(new NumericLiteral(number.get(), null), removedChars.map(numberStart, curIndex));
                    chunks.add(new ContentChunk(content.substring(numberStart, curIndex), ChunkType.IDENT));
                    continue nextToken;
                }
            }

            for (Pair<String, Function<NestedLiteralSource, LiteralOutcome>> nestedLiteral : getNestedLiterals(saver.lastWasNumber(), insertListener))
            {
                @Nullable NestedLiteralSource nestedOutcome = tryNestedLiteral(nestedLiteral.getFirst(), content, curIndex, removedChars, saver.locationRecorder);
                if (nestedOutcome != null)
                {
                    LiteralOutcome outcome = nestedLiteral.getSecond().apply(nestedOutcome);
                    saver.saveOperand(outcome.expression, removedChars.map(curIndex, nestedOutcome.positionAfter));
                    @SuppressWarnings("units")
                    @DisplayLocation int displayOffset = nestedLiteral.getFirst().length() + chunks.stream().mapToInt(c -> c.displayContent.getLength()).sum();
                    @CanonicalLocation int caretPosOffset = removedChars.map(curIndex + rawLength(nestedLiteral.getFirst()));
                    if (outcome.locationRecorder != null)
                        saver.addNestedLocations(outcome.locationRecorder, caretPosOffset);
                    saver.addNestedErrors(outcome.nestedErrors, caretPosOffset, displayOffset);
                    chunks.add(outcome.chunk);
                    nestedCompletions.addAll(outcome.completions.stream().map(acd -> offsetBy(acd, caretPosOffset)).collect(ImmutableList.<AutoCompleteDetails<ExpressionCompletionContext>>toImmutableList()));
                    removedChars.orShift(outcome.removedChars, curIndex + nestedLiteral.getFirst().length());
                    curIndex = nestedOutcome.positionAfter;
                    continue nextToken;
                }
            }

            if (content.startsWith("_", curIndex))
            {
                saver.saveOperand(new MatchAnythingExpression(), removedChars.map(curIndex, curIndex + RawInputLocation.ONE));
                chunks.add(new ContentChunk("_", ChunkType.IDENT));
                curIndex += RawInputLocation.ONE;
                continue nextToken;
            }

            @Nullable Consumed<Pair<@Nullable @ExpressionIdentifier String, ImmutableList<@ExpressionIdentifier String>>> parsed = IdentifierUtility.consumePossiblyScopedExpressionIdentifier(content, curIndex, curCaretPos != null ? curCaretPos : -1 * RawInputLocation.ONE);
            final @CanonicalLocation int canonIndex = removedChars.map(curIndex);
            if (parsed != null && parsed.positionAfter > curIndex)
            {
                prevWasIdent = true;
                removedChars.setAll(parsed);
                CanonicalSpan location = removedChars.map(curIndex, ((parsed.positionAfter < content.length() && content.charAt(parsed.positionAfter) == ' ') ? parsed.positionAfter + RawInputLocation.ONE : parsed.positionAfter));
                saver.saveOperand(IdentExpression.load(parsed.item.getFirst(), parsed.item.getSecond()), location);
                chunks.add(new ContentChunk((parsed.item.getFirst() == null ? "" : parsed.item.getFirst() + "\\\\") + parsed.item.getSecond().stream().collect(Collectors.joining("\\")), ChunkType.IDENT));
                curIndex = parsed.positionAfter;
                continue nextToken;
            }
            
            if (content.startsWith("@", curIndex))
            {
                @SuppressWarnings("units")
                @RawInputLocation int nonLetter = CharMatcher.inRange('a', 'z').or(CharMatcher.inRange('A', 'Z')).negate().indexIn(content, curIndex + 1);
                if (nonLetter == -1)
                    nonLetter = rawLength(content);
                String stem = content.substring(curIndex, nonLetter);

                // We skip to next non-letter to prevent trying to complete the keyword as a function:
                String attemptedKeyword = content.substring(curIndex, nonLetter);
                saver.saveOperand(new InvalidIdentExpression(attemptedKeyword), removedChars.map(curIndex, nonLetter));
                saver.locationRecorder.addErrorAndFixes(removedChars.map(curIndex, nonLetter), StyledString.s("Unknown keyword: " + attemptedKeyword), ImmutableList.of());
                chunks.add(new ContentChunk(attemptedKeyword, ChunkType.OPENING));
                curIndex = nonLetter;
                preserveNextSpace = true;
                continue nextToken;
            }

            boolean nextTrue = content.startsWith("true", curIndex);
            boolean nextFalse = content.startsWith("false", curIndex);
            if (nextTrue || nextFalse)
            {
                saver.saveOperand(new BooleanLiteral(nextTrue), removedChars.map(curIndex, nextTrue ? "true" : "false"));
                chunks.add(new ContentChunk(nextTrue ? "true" : "false", ChunkType.IDENT));
                curIndex += rawLength(nextTrue ? "true" : "false");
                continue nextToken;
            }
            
            CanonicalSpan invalidCharLocation = removedChars.map(curIndex, curIndex + RawInputLocation.ONE);
            saver.saveOperand(new InvalidIdentExpression(content.substring(curIndex, curIndex + 1)), invalidCharLocation);
            saver.locationRecorder.addErrorAndFixes(invalidCharLocation, StyledString.concat(TranslationUtility.getStyledString("error.illegalCharacter", Utility.codePointToString(content.charAt(curIndex))), StyledString.s("\n  "), StyledString.s("Character code: \\u" + Integer.toHexString(content.charAt(curIndex))).withStyle(new StyledCSS("errorable-sub-explanation"))), ImmutableList.of(new TextQuickFix("error.illegalCharacter.remove", invalidCharLocation, () -> new Pair<>("", StyledString.s("<remove>")))));
            chunks.add(new ContentChunk(content.substring(curIndex, curIndex + 1), ChunkType.IDENT));
            curIndex += RawInputLocation.ONE;
        }
        @Recorded Expression saved = saver.finish(removedChars.map(curIndex, curIndex));
        try
        {
            TypeExp typeExp = saved.checkExpression(columnLookup.get(), makeTypeState.get(), saver.locationRecorder.getRecorder());
            
            if (typeExp != null)
            {
                // Must be concrete:
                if (expectedType != null)
                    TypeExp.unifyTypes(typeExp, TypeExp.fromDataType(null, expectedType)).ifLeft(err -> saver.locationRecorder.addErrorAndFixes(saver.recorderFor(saved), err.getMessage(), ImmutableList.of()));
                @RawInputLocation int lastIndex = curIndex;
                saver.locationRecorder.getRecorder().recordLeftError(typeManager, functionLookup, saved, typeExp.toConcreteType(typeManager, false));
            }
        }
        catch (InternalException | UserException e)
        {
            if (e instanceof InternalException)
                Log.log(e);
            saver.locationRecorder.addErrorAndFixes(removedChars.map(RawInputLocation.ZERO, curIndex), ((ExceptionWithStyle) e).getStyledMessage(), ImmutableList.of());
        }
        
        if (chunks.isEmpty())
        {
            chunks.add(new ContentChunk("", ChunkType.IDENT));
        }
        
        String internalContent = chunks.stream().map(c -> c.internalContent).collect(Collectors.joining());
        StyledString display = chunks.stream().map(c -> c.displayContent).filter(d -> d.getLength() > 0).collect(StyledString.joining(""));
        Pair<ArrayList<CaretPos>, ImmutableList<@CanonicalLocation Integer>> caretPos = calculateCaretPos(chunks);

        ImmutableList<ErrorDetails> errors = saver.getErrors();
        try
        {
            display = addIndents(display, caretPos.getFirst(), saved, saver.locationRecorder);
        }
        catch (InternalException e)
        {
            // Just abandon adding the indents, user will live without
            Log.log(e);
        }
        display = Lexer.padZeroWidthErrors(display, caretPos.getFirst(), errors);
        

        return new LexerResult<Expression, ExpressionCompletionContext>(saved, internalContent, removedChars, lexOnMove, ImmutableList.copyOf(caretPos.getFirst()), ImmutableList.copyOf(caretPos.getSecond()), display, errors, saver.locationRecorder, Utility.concatI(Lexer.<ExpressionCompletionContext>makeCompletions(chunks, new MakeCompletions<ExpressionCompletionContext>()
        {
            // Must be anon inner class (rather than lambda) to avoid lambda annotation bug in checker framework
            // https://github.com/typetools/checker-framework/issues/2173
            // Can be swapped back in Java 9

            @Override
            public ExpressionCompletionContext makeCompletions(String chunk, @CanonicalLocation int canonIndex, ChunkType curChunk, ChunkType precedingChunk)
            {
                return ExpressionLexer.this.makeCompletions(chunk, canonIndex, curChunk, precedingChunk, insertListener);
            }
        }), nestedCompletions.build()), suppressBracketMatching, !saver.hasUnmatchedBrackets(), saver::getDisplayFor);
    }

    private StyledString addIndents(StyledString display, ArrayList<CaretPos> caretPos, @Recorded Expression expression, EditorLocationAndErrorRecorder locations) throws InternalException
    {
        ImmutableList<AddedSpace> addedSpaces = expression.visit(new AddedSpaceCalculator(locations)).sorted(Comparator.<AddedSpace, Integer>comparing(a -> a.addedAtInternalPos)).collect(ImmutableList.<AddedSpace>toImmutableList());

        for (AddedSpace addedSpace : addedSpaces)
        {
            display = addDisplayAfter(display, caretPos, addedSpace.addedAtInternalPos, StyledString.s(addedSpace.added).withStyle(new StyledCSS("expression-indent")));
        }
        
        return display;
    }
    
    private class AddedSpace
    {
        private final @CanonicalLocation int addedAtInternalPos;
        private final String added;

        public AddedSpace(@CanonicalLocation int addedAtInternalPos, String added)
        {
            this.addedAtInternalPos = addedAtInternalPos;
            this.added = added;
        }
    }
    
    private StyledString addDisplayAfter(StyledString display, ArrayList<CaretPos> caretPos, @CanonicalLocation int after, StyledString displayContentToAdd) throws InternalException
    {
        // We find the caret pos at the end of the span:
        CaretPos posAfter = caretPos.stream().filter(p -> p.positionInternal == after).findFirst().orElse(null);
        if (posAfter == null)
            throw new InternalException("Could not find caret pos: " + after);
        boolean removingExistingSpace = display.toPlain().startsWith(" ", posAfter.positionDisplay);
        StyledString displayBefore = display.substring(0, posAfter.positionDisplay);
        StyledString displayAfter = display.substring(posAfter.positionDisplay + (removingExistingSpace ? 1 : 0), display.getLength());

        for (int i = 0; i < caretPos.size(); i++)
        {
            CaretPos p = caretPos.get(i);
            if (p.positionInternal > posAfter.positionInternal)
            {
                // Don't change internal position, only display position:
                @SuppressWarnings("units")
                CaretPos adjusted = new CaretPos(p.positionInternal, p.positionDisplay + displayContentToAdd.toPlain().length() - (removingExistingSpace ? 1 : 0));
                caretPos.set(i, adjusted);
            }
        }
        
        return StyledString.concat(displayBefore, displayContentToAdd, displayAfter);
    }

    private AutoCompleteDetails<ExpressionCompletionContext> offsetBy(AutoCompleteDetails<CodeCompletionContext> acd, @CanonicalLocation int caretPosOffset)
    {
        return new AutoCompleteDetails<>(acd.location.offsetBy(caretPosOffset), new ExpressionCompletionContext(acd.codeCompletionContext, caretPosOffset));
    }

    private ExpressionCompletionContext makeCompletions(String stem, @CanonicalLocation int canonIndex, ChunkType curChunk, ChunkType precedingChunk, InsertListener insertListener)
    {
        // Large size to avoid reallocations:
        Builder<Pair<CompletionStatus, ExpressionCompletion>> completions = ImmutableList.builderWithExpectedSize(1000);

        // Add completions even if one is already spotted:
        addFunctionCompletions(completions, stem, canonIndex);
        addColumnAndTableCompletions(completions, stem, canonIndex);
        addTagCompletions(completions, stem, canonIndex);
        addVariableCompletions(completions, stem, canonIndex);
        addNestedLiteralCompletions(completions, stem, canonIndex, insertListener);

        addKeywordCompletions(completions, stem, canonIndex);

        ImmutableList<Pair<CompletionStatus, ExpressionCompletion>> directAndRelated = precedingChunk == ChunkType.IDENT ? Utility.mapListI(completions.build(), c -> excludeFirstPos(c)) : completions.build();
        ArrayList<LexCompletion> guides = new ArrayList<>();
        matchWordStart(stem, canonIndex, "conversion", null, WordPosition.FIRST_WORD_NON_EMPTY).forEach((k, v) -> {
            guides.add(guideCompletion("Conversion", "conversion", v.startPos, v.lastShowPosIncl).withSideText("\u2248 conversion"));
        });
        matchWordStart(stem, canonIndex, "units", null, WordPosition.FIRST_WORD_NON_EMPTY).forEach((k, v) -> {
            guides.add(guideCompletion("Units", "units", v.startPos, v.lastShowPosIncl).withSideText("\u2248 units"));
        });
        matchWordStart(stem, canonIndex, "optional", null, WordPosition.FIRST_WORD_NON_EMPTY).forEach((k, v) -> {
            guides.add(guideCompletion("Optional Type", "optional", v.startPos, v.lastShowPosIncl).withSideText("\u2248 optional"));
        });
        
        // Only add expression guide at positions where there are already completions:
        if (!directAndRelated.isEmpty())
        {
            @SuppressWarnings("units") // Due to IntStream use
            @CanonicalLocation int start = directAndRelated.stream().mapToInt(p -> p.getSecond().completion.startPos).min().orElse(canonIndex);
            @SuppressWarnings("units") // Due to IntStream use
            @CanonicalLocation int end = directAndRelated.stream().mapToInt(p -> p.getSecond().completion.lastShowPosIncl).max().orElse(canonIndex);
            
            if (guides.isEmpty())
            {
                guides.add(guideCompletion("Expressions", "expressions", start, end));
                guides.add(guideCompletion("Conversion", "conversion", start, end));
                guides.add(guideCompletion("Units", "units", start, end));
                guides.add(guideCompletion("Optional Type", "optional", start, end));
            }
        }
        
        ImmutableList<LexCompletion> operators = getOperatorCompletions(canonIndex, stem, curChunk, precedingChunk);
        
        return new ExpressionCompletionContext(sort(directAndRelated, operators, ImmutableList.copyOf(guides)));
    }

    private Pair<CompletionStatus, ExpressionCompletion> excludeFirstPos(Pair<CompletionStatus, ExpressionCompletion> original)
    {
        return original.mapSecond(c -> new ExpressionCompletion(c.completion.copyNoShowAtFirstPos(), c.completionType));
    }

    private ImmutableList<LexCompletion> getOperatorCompletions(@CanonicalLocation int canonIndex, String stem, ChunkType curChunk, ChunkType precedingChunkType)
    {
        // We need to get the operator completions.  Consider an expression like:
        // ab<cd
        // There are three chunks there: "ab", "<", "cd", and 6 positions (0-5 inclusive)
        // In terms of operator completions, we want to see all operators at 5 because the user may want
        // to add any operator after the ident.  Ditto for 4 because they may want an operator between c and d
        // But at position 3 we only want to show those which can complete the "<" operator (so "<", "<=", "<>")
        // and not the rest.  At 1 and 2 we show all, but at 0 we show none because it makes no sense to have an operator at the very beginning.
        // So, the rules are:
        //  - In a non-operator chunk we show operators in the first position if not preceded by an operator, and always in all later positions
        //  - In an operator chunk (only the first and last position will be caret-visitable anyway) we show further operators in the last position

        boolean showOpsAtStart = precedingChunkType == ChunkType.CLOSING || precedingChunkType == ChunkType.NESTED;
        boolean anyIsOp = Arrays.stream(Op.values()).anyMatch(op -> getOpCommon(stem, op) > 0);
        return Arrays.stream(Op.values()).<LexCompletion>flatMap(op -> {
            int common = getOpCommon(stem, op);
            //Log.debug("Showing " + op.getContent() + " at " + canonIndex + "+" + common + " stem: {" + stem + "} " + " prev: " + precedingChunkType);
            if (anyIsOp)
            {
                return Stream.of(new LexCompletion(canonIndex, common, op.getContent())
                {
                    @Override
                    public boolean showFor(@CanonicalLocation int caretPos)
                    {
                        if (caretPos == canonIndex)
                            return showOpsAtStart;
                        else
                            return super.showFor(caretPos);
                    }
                }.withSideText(TranslationUtility.getString(op.localNameKey))
                    .withFurtherDetailsURL("operator-" + op.getContent().codePoints().mapToObj(n -> Integer.toString(n)).collect(Collectors.joining("-")) + ".html"));
            }
            else
            {
                int maxLength = curChunk == ChunkType.IDENT ? stem.length() : 0;
                return IntStream.range(0, maxLength + 1).mapToObj(offset -> {
                    return new LexCompletion(canonIndex + offset * CanonicalLocation.ONE, 0, op.getContent())
                    {
                        @Override
                        public boolean showFor(@CanonicalLocation int caretPos)
                        {
                            if (caretPos == canonIndex && offset == 0)
                                return showOpsAtStart;
                            else
                                return super.showFor(caretPos);
                        }
                    }.withSideText(TranslationUtility.getString(op.localNameKey))
                        .withFurtherDetailsURL("operator-" + op.getContent().codePoints().mapToObj(n -> Integer.toString(n)).collect(Collectors.joining("-")) + ".html"); 
                });
            }
        }).collect(ImmutableList.<LexCompletion>toImmutableList());
    }

    private int getOpCommon(String stem, Op op)
    {
        return Math.max(
            Utility.longestCommonStart(stem, 0, op.getContent(), 0),
            Utility.longestCommonStart(stem, 0, op.getASCIIContent(), 0));
    }

    private LexCompletion guideCompletion(String name, String guideFileName, @CanonicalLocation int start, @CanonicalLocation int end)
    {
        return new LexCompletion(start, end, StyledString.s(name), "guide-" + guideFileName + ".html");
    }

    private void addKeywordCompletions(Builder<Pair<CompletionStatus, ExpressionCompletion>> completions, String stem, @CanonicalLocation int canonIndex)
    {
        if (Utility.startsWithIgnoreCase("@i", stem) || Utility.startsWithIgnoreCase("if", stem))
        {
            completions.add(new Pair<>(CompletionStatus.DIRECT, new ExpressionCompletion(new LexCompletion(canonIndex, stem.length(), "@if@then@else@endif").withDisplay(StyledString.s("@if \u2026 @then \u2026 @else \u2026 @endif")).withFurtherDetailsURL("syntax-if.html").withCaretPosAfterCompletion(3).withSelectionBehaviour(LexSelectionBehaviour.SELECT_IF_TOP), CompletionType.KEYWORD_CHAIN)));
        }

        if (Utility.startsWithIgnoreCase("@matc", stem))
        {
            completions.add(new Pair<>(CompletionStatus.DIRECT, new ExpressionCompletion(new LexCompletion(canonIndex, stem.length(), "@match@case@then@case@then@endmatch").withDisplay(StyledString.s("@match \u2026 @case \u2026 @then \u2026 @case \u2026 @then \u2026 @endmatch")).withFurtherDetailsURL("syntax-match.html").withCaretPosAfterCompletion(6).withSelectionBehaviour(LexSelectionBehaviour.SELECT_IF_TOP), CompletionType.KEYWORD_CHAIN)));
        }

        if (Utility.startsWithIgnoreCase("@defin", stem))
        {
            completions.add(new Pair<>(CompletionStatus.DIRECT, new ExpressionCompletion(new LexCompletion(canonIndex, stem.length(), "@define@then@enddefine").withDisplay(StyledString.s("@define \u2026 @then \u2026 @enddefine")).withFurtherDetailsURL("syntax-define.html").withCaretPosAfterCompletion(7).withSelectionBehaviour(LexSelectionBehaviour.SELECT_IF_TOP), CompletionType.KEYWORD_CHAIN)));
        }
        
        for (Keyword keyword : Keyword.values())
        {
            boolean added = false;
            // Add keywords without @
            if (keyword.getContent().startsWith("@") && !stem.startsWith("@"))
            {
                @SuppressWarnings("units")
                @RawInputLocation int common = Utility.longestCommonStart(keyword.getContent(), 1, stem, 0);
                // We only show as related if we actually match at least one character:
                if (common > 0)
                {
                    completions.add(new Pair<>(CompletionStatus.RELATED, new ExpressionCompletion(new LexCompletion(canonIndex, common, keyword.getContent()).withFurtherDetailsURL(getDocURLFor(keyword)), CompletionType.KEYWORD)));
                    added = true;
                }
            }
            
            if (!added)
            {
                // Add keywords as-is:
                int rawMatchLen = Utility.longestCommonStartIgnoringCase(keyword.getContent(), 0, stem, 0);
                // Once complete, don't show:
                int len = Math.min(rawMatchLen, keyword.getContent().length() - 1);
                completions.add(new Pair<>(CompletionStatus.DIRECT, new ExpressionCompletion(new LexCompletion(canonIndex, len, keyword.getContent()).withFurtherDetailsURL(getDocURLFor(keyword)).withSelectionBehaviour(LexSelectionBehaviour.SELECT_IF_ONLY), CompletionType.KEYWORD)));
            }
        }
    }
    
    /**
     * Adds all the available variable completions to the given builder
     * @param identCompletions The builder to add to
     * @param stem The stem to narrow down the options, if non-null.  If null, add all functions
     * @param canonIndex The position to pass to the completion
     */
    private void addNestedLiteralCompletions(Builder<Pair<CompletionStatus, ExpressionCompletion>> identCompletions, @Nullable String stem, @CanonicalLocation int canonIndex, InsertListener insertListener)
    {
        for (Pair<String, Function<NestedLiteralSource, LiteralOutcome>> nestedLiteral : getNestedLiterals(false, insertListener))
        {
            map(matchWordStart(stem, canonIndex, nestedLiteral.getFirst(), "Value", WordPosition.FIRST_WORD), c -> c.withReplacement(nestedLiteral.getFirst() + "}", StyledString.s(nestedLiteral.getFirst() + "\u2026}"))
                    .withCaretPosAfterCompletion(nestedLiteral.getFirst().length())
                    .withFurtherDetailsURL("literal-" + nestedLiteral.getFirst().replace("{", "") + ".html")).forEach((k, v) -> {
                identCompletions.add(new Pair<>(CompletionStatus.DIRECT, new ExpressionCompletion(v, nestedLiteral.getFirst().equals("{") ? CompletionType.UNIT_LITERAL : CompletionType.NESTED_LITERAL)));
            });
        }
    }
    
    private static ImmutableMap<WordPosition, LexCompletion> map(ImmutableMap<WordPosition, LexCompletion> items, UnaryOperator<LexCompletion> withEach)
    {
        ImmutableMap.Builder<WordPosition, LexCompletion> r = ImmutableMap.builder();
        items.forEach((k, v) -> r.put(k, withEach.apply(v)));
        return r.build();
    }

    /**
     * Adds all the available variable completions to the given builder
     * @param identCompletions The builder to add to
     * @param stem The stem to narrow down the options, if non-null.  If null, add all functions
     * @param canonIndex The position to pass to the completion
     */
    private void addVariableCompletions(Builder<Pair<CompletionStatus, ExpressionCompletion>> identCompletions, @Nullable String stem, @CanonicalLocation int canonIndex)
    {
        for (String bool : ImmutableList.of("true", "false"))
        {
            matchWordStart(stem, canonIndex, bool, "Value", WordPosition.FIRST_WORD).forEach((k, v) -> {
                identCompletions.add(new Pair<>(CompletionStatus.DIRECT, new ExpressionCompletion(v, CompletionType.CONSTANT)));
            });
        }
        
        try
        {
            for (String availableVariable : makeTypeState.get().getAvailableVariables())
            {
                map(matchWordStart(stem, canonIndex, availableVariable, "Variable", WordPosition.FIRST_WORD, WordPosition.LATER_WORD), c -> {
                    // Special cases for in-built variables with attached documentation:
                    if (availableVariable.equals(TypeState.GROUP_COUNT))
                    {
                        return c.withFurtherDetailsURL("variable-" + TypeState.GROUP_COUNT.replace(" ", "-") + ".html");
                    }
                    else if (availableVariable.equals(TypeState.ROW_NUMBER))
                    {
                        return c.withFurtherDetailsURL("variable-" + TypeState.ROW_NUMBER.replace(" ", "-") + ".html");
                    }
                    else
                        return c;
                }).forEach((k, v) -> identCompletions.add(new Pair<>(k == WordPosition.FIRST_WORD ? CompletionStatus.DIRECT : CompletionStatus.RELATED, new ExpressionCompletion(v, CompletionType.VARIABLE))));
            }
        }
        catch (InternalException e)
        {
            Log.log(e);
        }
    }

    /**
     * Adds all the available column completions to the given builder
     * @param identCompletions The builder to add to
     * @param stem The stem to narrow down the options, if non-null.  If null, add all functions
     * @param canonIndex The position to pass to the completion
     */
    private void addColumnAndTableCompletions(Builder<Pair<CompletionStatus, ExpressionCompletion>> identCompletions, @Nullable String stem, @CanonicalLocation int canonIndex)
    {
        for (Pair<@Nullable TableId, ColumnId> availableColumn : Utility.iterableStream(columnLookup.get().getAvailableColumnReferences()))
        {
            if (availableColumn.getFirst() == null)
            {
                matchWordStart(stem, canonIndex, availableColumn.getSecond().getRaw(), "Column", WordPosition.FIRST_WORD, WordPosition.LATER_WORD).forEach((k, v) -> identCompletions.add(new Pair<>(k == WordPosition.FIRST_WORD ? CompletionStatus.DIRECT : CompletionStatus.RELATED, new ExpressionCompletion(v.withFurtherDetailsHTMLContent(htmlForColumn(availableColumn.getFirst(), availableColumn.getSecond())), CompletionType.COLUMN))));
            }
        }

        for (TableId tableReference : Utility.iterableStream(columnLookup.get().getAvailableTableReferences()))
        {
            ArrayList<Pair<CompletionStatus, LexCompletion>> tableCompletions = new ArrayList<>();
            matchWordStart(stem, canonIndex, "table\\\\" + tableReference.getRaw(), "Table", WordPosition.FIRST_WORD).values().forEach(c -> tableCompletions.add(new Pair<>(CompletionStatus.DIRECT, c)));

            // Add a related item if matches without the entire
            matchWordStart(stem, canonIndex, tableReference.getRaw(), "Table", WordPosition.FIRST_WORD_NON_EMPTY, WordPosition.LATER_WORD).forEach((k, v) -> tableCompletions.add(new Pair<>(CompletionStatus.RELATED, v.withReplacement("table\\" + tableReference.getRaw()))));

            for (Pair<CompletionStatus, LexCompletion> p : tableCompletions)
            {
                identCompletions.add(p.mapSecond(c -> new ExpressionCompletion(c.withFurtherDetailsHTMLContent(htmlForTable(tableReference)), CompletionType.TABLE)));
            }
        }
    }

    private String htmlForColumn(@Nullable TableId t, ColumnId c)
    {
        String funcdocURL = FXUtility.getStylesheet("funcdoc.css");
        String webURL = FXUtility.getStylesheet("web.css");
        
        return "<html>\n" +
                "   <head>\n" +
                "      <meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\">\n" +
                "      <link rel=\"stylesheet\" href=\"" + funcdocURL + "\">\n" +
                "      <link rel=\"stylesheet\" href=\"" + webURL + "\">\n" +
                "   </head>\n" +
                "   <body class=\"indiv\">\n" +
                "      <div class=\"column-item\"><span class=\"column-header\"/>" + (t != null ? t.getRaw() + ":<wbr>" : "") + c.getRaw() + "</span>" +
                "<p>This uses the value of the column in the current row.</p>" +
                "</div></body></html>";
    }

    private String htmlForTable(TableId t)
    {
        String funcdocURL = FXUtility.getStylesheet("funcdoc.css");
        String webURL = FXUtility.getStylesheet("web.css");

        return "<html>\n" +
            "   <head>\n" +
            "      <meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\">\n" +
            "      <link rel=\"stylesheet\" href=\"" + funcdocURL + "\">\n" +
            "      <link rel=\"stylesheet\" href=\"" + webURL + "\">\n" +
            "   </head>\n" +
            "   <body class=\"indiv\">\n" +
            "      <div class=\"table-item\"><span class=\"table-header\"/>" + t.getRaw() + "</span>" +
            "<p>This uses the table.  You can access individual columns by appending # followed by the column name.</p>" +
            "</div></body></html>";
    }

    /**
     * Adds all the available function completions to the given builder
     * @param identCompletions The builder to add to
     * @param stem The stem to narrow down the options, if non-null.  If null, add all functions
     * @param canonIndex The position to pass to the completion
     */
    private void addFunctionCompletions(Builder<Pair<CompletionStatus, ExpressionCompletion>> identCompletions, @Nullable String stem, @CanonicalLocation int canonIndex)
    {
        for (StandardFunctionDefinition function : allFunctions)
        {
            ArrayList<Pair<CompletionStatus, LexCompletion>> lexCompletions = new ArrayList<>(); 
                    
            matchWordStart(stem, canonIndex, function.getName(), "Function", WordPosition.FIRST_WORD, WordPosition.LATER_WORD).forEach((k, v) -> lexCompletions.add(new Pair<>(k == WordPosition.FIRST_WORD ? CompletionStatus.DIRECT : CompletionStatus.RELATED, v)));

            
            if (stem != null && stem.length() >= 2)
            {
                for (String synonym : function.getSynonyms())
                {
                    boolean[] added = new boolean[] {false};
                    matchWordStart(stem, canonIndex, synonym, "Function", WordPosition.FIRST_WORD_NON_EMPTY).values().forEach(c -> {
                        lexCompletions.add(new Pair<>(CompletionStatus.RELATED, c.withReplacement(function.getName()).withSideText("\u2248 " + synonym)));
                        added[0] = true;
                    });
                    if (added[0])
                        break;
                }
            }

            for (Pair<CompletionStatus, LexCompletion> c : lexCompletions)
            {
                identCompletions.add(new Pair<>(c.getFirst(), new ExpressionCompletion(c.getSecond().withReplacement(function.getName() + "()", StyledString.s(function.getName() + "(\u2026)")).withFurtherDetailsURL(makeFuncDocURL(function)).withCaretPosAfterCompletion(function.getName().length() + 1), CompletionType.FUNCTION)));
            }
        }
    }

    public static String makeFuncDocURL(StandardFunctionDefinition function)
    {
        return "function-" + function.getDocKey().replace(":", "-") + ".html";
    }

    /**
     * Adds all the available tag completions to the given builder
     * @param identCompletions The builder to add to
     * @param stem The stem to narrow down the options, if non-null.  If null, add all functions
     * @param canonIndex The position to pass to the completion
     */
    private void addTagCompletions(Builder<Pair<CompletionStatus, ExpressionCompletion>> identCompletions, @Nullable String stem, @CanonicalLocation int canonIndex)
    {
        for (TagCompletion tag : getTagCompletions(typeManager.getKnownTaggedTypes()))
        {
            String fullName = (tag.typeName != null ? (tag.typeName + "\\") : "") + tag.tagName;
            if (stem == null || Utility.startsWithIgnoreCase(tag.tagName, stem) || (tag.typeName != null && Utility.startsWithIgnoreCase(fullName, stem)))
            {
                identCompletions.add(new Pair<>(CompletionStatus.DIRECT, new ExpressionCompletion(new LexCompletion(canonIndex, stem == null ? 0 : stem.length(), fullName + (tag.hasInner ? "()" : "")).withSideText("Tag").withCaretPosAfterCompletion(fullName.length() + (tag.hasInner ? 1 : 0)), CompletionType.CONSTRUCTOR)));
            }
        }
    }

    private @Nullable String getDocURLFor(Keyword keyword)
    {
        switch (keyword)
        {
            case IF:
            //case THEN:  // Overloaded!
            case ELSE:
            case ENDIF:
                return "syntax-if.html";
            case MATCH:
            case CASE:
            case ORCASE:
            case GIVEN:
            case ENDMATCH:
                return "syntax-match.html";
            case DEFINE:
            case ENDDEFINE:
                return "syntax-define.html";
            case FUNCTION:
            case ENDFUNCTION:
                return "syntax-function.html";
            case QUEST:
                return "syntax-quest.html";
                
        }
        return null;
    }

    // Inserts a bit at the location, and shifts all higher bits up by one
    private void insertBit(BitSet dest, int index)
    {
        // Move all higher bits up by one.  Important to go backwards:
        for (int i = dest.length(); i >= index; i--)
        {
            dest.set(i + 1, dest.get(i));
        }
        dest.set(index);
    }

    class TagCompletion implements Comparable<TagCompletion>
    {
        // Non-null if needed to disambiguate, null if type name is unique
        private final @Nullable TypeId typeName;
        private final String tagName;
        private final boolean hasInner;

        public TagCompletion(@Nullable TypeId typeName, String tagName, boolean hasInner)
        {
            this.typeName = typeName;
            this.tagName = tagName;
            this.hasInner = hasInner;
        }

        @Override
        public boolean equals(@Nullable Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            TagCompletion that = (TagCompletion) o;
            return Objects.equals(typeName, that.typeName) &&
                    tagName.equals(that.tagName);
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(typeName, tagName);
        }

        @Override
        public int compareTo(TagCompletion o)
        {
            if (typeName == null && o.typeName != null)
                return -1;
            else if (typeName != null && o.typeName == null)
                return 1;
            else if (typeName != null && o.typeName != null)
            {
                int c = typeName.compareTo(o.typeName);
                if (c != 0)
                    return c;
            }
            return tagName.compareTo(o.tagName);
        }
    }

    private Collection<TagCompletion> getTagCompletions(Map<TypeId, TaggedTypeDefinition> knownTaggedTypes)
    {
        // Optional.empty indicates a name clash for a simple key
        TreeMap<TagCompletion, Optional<TaggedTypeDefinition>> completions = new TreeMap<>();

        for (TaggedTypeDefinition value : knownTaggedTypes.values())
        {
            // Bit of a hack to hide internal types;
            if (value.getTaggedTypeName().equals(new TypeId("Type")) || value.getTaggedTypeName().equals(new TypeId("Unit")))
                continue;
            
            for (TagType<JellyType> tag : value.getTags())
            {
                // Try first without type:
                TagCompletion simpleKey = new TagCompletion(null, tag.getName(), tag.getInner() != null);
                TagCompletion fullKey = new TagCompletion(value.getTaggedTypeName(), tag.getName(), tag.getInner() != null);
                completions.put(fullKey, Optional.of(value));
                @Nullable Optional<TaggedTypeDefinition> prevSimple = completions.remove(simpleKey);
                if (prevSimple == null)
                {
                    completions.put(simpleKey, Optional.of(value));
                }
                else
                {
                    if (prevSimple.isPresent())
                    {
                        // Note clash
                        completions.put(simpleKey, Optional.empty());
                    }
                }
            }

        }
        return new ArrayList<TagCompletion>(completions.keySet());
    }
    
    private static class LiteralOutcome
    {
        public final Expression expression;
        public final RemovedCharacters removedChars;
        public final ContentChunk chunk;
        public final ImmutableList<ErrorDetails> nestedErrors;
        public final ImmutableList<AutoCompleteDetails<CodeCompletionContext>> completions;
        public final @Nullable EditorLocationAndErrorRecorder locationRecorder;
        
        @SuppressWarnings("units")
        public LiteralOutcome(NestedLiteralSource source, Expression expression)
        {
            String content = source.prefix + source.innerContent + (source.terminatedProperly ? "}" : "");
            this.chunk = new ContentChunk(content, StyledString.s(content), IntStream.concat(IntStream.concat(IntStream.of(0), IntStream.range(0, source.innerContent.length() + 1).map(n -> n + source.prefix.length())), IntStream.range(source.terminatedProperly ? content.length() - 1 : content.length(), content.length() + 1)).mapToObj(i -> new CaretPos(i, i)).collect(ImmutableList.<CaretPos>toImmutableList()), 
                    Stream.<Integer>of(0, source.prefix.length(), source.terminatedProperly ? content.length() - 1 : content.length(), content.length()).distinct().collect(ImmutableList.<@CanonicalLocation Integer>toImmutableList()),
                    ChunkType.NESTED_START);
            this.expression = expression;
            this.removedChars = new RemovedCharacters();
            this.nestedErrors = ImmutableList.of();
            this.completions = ImmutableList.of();
            this.locationRecorder = null;
        }

        @SuppressWarnings("units")
        public LiteralOutcome(String prefix, String internalContent, StyledString displayContent, Expression expression, String suffix, RemovedCharacters removedChars, ImmutableList<CaretPos> caretPos, ImmutableList<@CanonicalLocation Integer> wordBoundaryCaretPos, ImmutableList<ErrorDetails> errors, ImmutableList<AutoCompleteDetails<CodeCompletionContext>> completions,                             EditorLocationAndErrorRecorder locationRecorder)
        {
            ImmutableList.Builder<CaretPos> caretPosIncludingPrefixSuffix = ImmutableList.builder();
            CaretPos initialPos = new CaretPos(0, 0);
            caretPosIncludingPrefixSuffix.add(initialPos);
            for (CaretPos p : caretPos)
            {
                caretPosIncludingPrefixSuffix.add(new CaretPos(p.positionInternal + prefix.length(), p.positionDisplay + prefix.length()));
            }
            caretPosIncludingPrefixSuffix.add(new CaretPos(prefix.length() + internalContent.length() + suffix.length(), prefix.length() + displayContent.getLength() + suffix.length()));
            ImmutableList.Builder<@CanonicalLocation Integer> wordPosIncludingPrefixSuffix = ImmutableList.builder();
            wordPosIncludingPrefixSuffix.add(initialPos.positionInternal);
            for (@CanonicalLocation Integer p : wordBoundaryCaretPos)
            {
                wordPosIncludingPrefixSuffix.add(p + prefix.length());
            }
            wordPosIncludingPrefixSuffix.add(prefix.length() + internalContent.length() + suffix.length());
            this.chunk = new ContentChunk(
                prefix + internalContent + suffix,
                StyledString.concat(StyledString.s(prefix), displayContent, StyledString.s(suffix)),
                caretPosIncludingPrefixSuffix.build(), wordPosIncludingPrefixSuffix.build(), ChunkType.NESTED_START);
            this.expression = expression;
            this.removedChars = removedChars;
            this.nestedErrors = errors;
            this.completions = completions;
            this.locationRecorder = locationRecorder;
        }
    }

    private ImmutableList<Pair<String, Function<NestedLiteralSource, LiteralOutcome>>> getNestedLiterals(boolean lastWasNumber, InsertListener insertListener)
    {
        Function<NestedLiteralSource, LiteralOutcome> unitLit = c -> {
            UnitLexer unitLexer = new UnitLexer(typeManager, false);
            LexerResult<UnitExpression, CodeCompletionContext> processed = unitLexer.process(c.innerContent, 0, insertListener);
            return new LiteralOutcome(c.prefix, processed.adjustedContent, processed.display, new UnitLiteralExpression(processed.result), c.terminatedProperly ? "}" : "", processed.removedChars, processed.caretPositions, processed.wordBoundaryCaretPositions, processed.errors, processed.autoCompleteDetails, processed.locationRecorder);
        };
        ImmutableList<Pair<String, Function<NestedLiteralSource, LiteralOutcome>>> anywhere = ImmutableList.of(
            new Pair<String, Function<NestedLiteralSource, LiteralOutcome>>("date{", c -> new LiteralOutcome(c, new TemporalLiteral(DateTimeType.YEARMONTHDAY, c.innerContent))),
            new Pair<String, Function<NestedLiteralSource, LiteralOutcome>>("datetime{", c -> new LiteralOutcome(c, new TemporalLiteral(DateTimeType.DATETIME, c.innerContent))),
            new Pair<String, Function<NestedLiteralSource, LiteralOutcome>>("datetimezoned{", c -> new LiteralOutcome(c, new TemporalLiteral(DateTimeType.DATETIMEZONED, c.innerContent))),
            new Pair<String, Function<NestedLiteralSource, LiteralOutcome>>("dateym{", c -> new LiteralOutcome(c, new TemporalLiteral(DateTimeType.YEARMONTH, c.innerContent))),
            new Pair<String, Function<NestedLiteralSource, LiteralOutcome>>("time{", c -> new LiteralOutcome(c, new TemporalLiteral(DateTimeType.TIMEOFDAY, c.innerContent))),
            new Pair<String, Function<NestedLiteralSource, LiteralOutcome>>("type{", c -> {
                TypeLexer typeLexer = new TypeLexer(typeManager, true, false);
                LexerResult<TypeExpression, CodeCompletionContext> processed = typeLexer.process(c.innerContent, 0, insertListener);
                return new LiteralOutcome(c.prefix, processed.adjustedContent, processed.display, new TypeLiteralExpression(processed.result), c.terminatedProperly ? "}" : "", processed.removedChars, processed.caretPositions, processed.wordBoundaryCaretPositions, processed.errors, processed.autoCompleteDetails, processed.locationRecorder);
            }),
            new Pair<String, Function<NestedLiteralSource, LiteralOutcome>>("unit{", unitLit)
        );
        if (lastWasNumber)
            return Utility.appendToList(anywhere, new Pair<>("{", unitLit));
        else
            return anywhere;
    }
    
    private static class NestedLiteralSource
    {
        private final String prefix;
        private final String innerContent;
        private final @RawInputLocation int positionAfter;
        private final boolean terminatedProperly;

        public NestedLiteralSource(String prefix, String innerContent, @RawInputLocation int positionAfter, boolean terminatedProperly)
        {
            this.prefix = prefix;
            this.innerContent = innerContent;
            this.positionAfter = positionAfter;
            this.terminatedProperly = terminatedProperly;
        }
    }
    
    @SuppressWarnings("units")
    private @Nullable NestedLiteralSource tryNestedLiteral(String prefixInclCurly, String content, @RawInputLocation int curIndex, RemovedCharacters removedChars, EditorLocationAndErrorRecorder locationRecorder)
    {
        if (content.startsWith(prefixInclCurly, curIndex))
        {
            curIndex += prefixInclCurly.length();
            @RawInputLocation int startIndex = curIndex;
            int openCount = 1;
            while (curIndex < content.length() && openCount > 0)
            {
                if (content.charAt(curIndex) == '{')
                    openCount += 1;
                else if (content.charAt(curIndex) == '}')
                    openCount -= 1;
                curIndex += 1;
            }
            if (openCount == 0)
                return new NestedLiteralSource(prefixInclCurly, content.substring(startIndex, curIndex - 1), curIndex, true);
            else
            {
                locationRecorder.addErrorAndFixes(removedChars.map(startIndex, content.length()), StyledString.s("Started " + prefixInclCurly + " but no matching closing }"), ImmutableList.of());
                return new NestedLiteralSource(prefixInclCurly, content.substring(startIndex), content.length(), false);
            }
        }
        return null;
    }
    
    // Used for ordering, so the order here is significant
    private static enum CompletionType
    {
        COLUMN, VARIABLE, NESTED_LITERAL, FUNCTION, CONSTRUCTOR, UNIT_LITERAL, CONSTANT, TABLE, KEYWORD_CHAIN, KEYWORD; 
    }
    
    private static enum CompletionStatus
    {
        DIRECT, RELATED;
    }
    
    // LexCompletion plus extra info for sorting
    private static class ExpressionCompletion
    {
        private final LexCompletion completion;
        private final CompletionType completionType;

        public ExpressionCompletion(LexCompletion completion, CompletionType completionType)
        {
            this.completion = completion;
            this.completionType = completionType;
        }
    }
    
    private static ImmutableList<LexCompletionGroup> sort(ImmutableList<Pair<CompletionStatus, ExpressionCompletion>> completions, ImmutableList<LexCompletion> operators, ImmutableList<LexCompletion> guides)
    {
        ImmutableList.Builder<LexCompletionGroup> groups = ImmutableList.builder();

        ImmutableList<LexCompletion> direct = sort(completions.stream().filter(p -> p.getFirst().equals(CompletionStatus.DIRECT)).map(p -> p.getSecond()));
        if (!direct.isEmpty())
        {
            groups.add(new LexCompletionGroup(direct, null, 2));
        }
        ImmutableList<LexCompletion> related = sort(completions.stream().filter(p -> p.getFirst().equals(CompletionStatus.RELATED)).map(p -> p.getSecond()));
        if (!related.isEmpty())
        {
            groups.add(new LexCompletionGroup(related, StyledString.s("Related"), 2));
        }
        if (!operators.isEmpty())
        {
            groups.add(new LexCompletionGroup(operators, StyledString.s("Operators"), 1));
        }
        if (!guides.isEmpty())
        {
            groups.add(new LexCompletionGroup(guides, StyledString.s("Help"), 1));
        }
        
        return groups.build();
    }
    private static ImmutableList<LexCompletion> sort(Stream<ExpressionCompletion> completions)
    {
        return completions.sorted(Comparator.<ExpressionCompletion, Integer>comparing(c -> c.completionType.ordinal()).thenComparing((c1, c2) -> {
            if (c1.completion.content == null)
                return -1;
            else if (c2.completion.content == null)
                return 1;
            else
                return c1.completion.content.compareToIgnoreCase(c2.completion.content);
        })).map(c -> c.completion).collect(ImmutableList.<LexCompletion>toImmutableList());
    }
    
    private static enum WordPosition
    {
        FIRST_WORD,
        // Match first word, but only if there is a non-empty match:
        FIRST_WORD_NON_EMPTY,
        LATER_WORD
    }

    /**
     * Check if the source snippet is a stem of any (space-separated) word in the completion text.  If so, return a
     * corresponding completion, else return empty.
     *
     * e.g. matchWordStart(new Pair("te", 0), 34, "from text to", FIRST_WORD, LATER_WORD)
     * would return Optional.of(new Pair(LATER_WORD, new LexCompletion(34, 2, "from text to")))
     * because te matches text.
     * 
     * e.g. matchWordStart(new Pair("deepfreeze", 4), 67, "from text to", FIRST_WORD)
     * would return Optional.of(new Pair(FIRST_WORD, new LexCompletion(67, 2, "from text to")))
     * because fr ("deepfreeze".substring(4)) matches from for the first two characters.
     * 
     *
     * @param src The source content from the user and position to start the search at.  If null, automatically match as FIRST_WORD (a convenience to avoid null checks for each caller). 
     * @param startPos the start position to feed to the completion constructor
     * @param completionText The completion text to search for.
     * @return True, completion if at very start; False, completion if it maps a later word.
     */
    private static ImmutableMap<WordPosition, LexCompletion> matchWordStart(@Nullable Pair<String, Integer> src, @CanonicalLocation int startPos, String completionText, @Nullable String sideText, WordPosition... possiblePositions)
    {
        if (src == null)
            return ImmutableMap.of(WordPosition.FIRST_WORD, addSideText(new LexCompletion(startPos, 0, completionText), sideText));

        boolean firstWord = Arrays.asList(possiblePositions).contains(WordPosition.FIRST_WORD);
        boolean firstWordNonEmpty = Arrays.asList(possiblePositions).contains(WordPosition.FIRST_WORD_NON_EMPTY);
        boolean laterWord = Arrays.asList(possiblePositions).contains(WordPosition.LATER_WORD);
        int prevCompletionLength = 0;
        int curCompletionStart = 0;
        // Need HashMap not builder because we may overwrite if multiple later words match:
        HashMap<WordPosition, LexCompletion> r = new HashMap<>();
        // We loop, looking at the start of each word in the completionText ident for a match against src
        // If we find a match and it's valid given the WordPosition items, we store it.  Because of 
        do
        {
            int len = Utility.longestCommonStartIgnoringCase(completionText, curCompletionStart, src.getFirst(), src.getSecond());
            if ((firstWord && len == 0 && curCompletionStart == 0) || (len > 0 && len > prevCompletionLength))
            {
                @SuppressWarnings("units")
                @CanonicalLocation int adjStartPos = startPos + prevCompletionLength;
                r.put(curCompletionStart == 0 ? WordPosition.FIRST_WORD : WordPosition.LATER_WORD, addSideText(new LexCompletion(startPos, len, completionText) {
                    @Override
                    public boolean showFor(@CanonicalLocation int caretPos)
                    {
                        if (firstWordNonEmpty)
                            return adjStartPos < caretPos && caretPos <= adjStartPos + (len * CanonicalLocation.ONE);
                        else
                            return adjStartPos <= caretPos && caretPos <= adjStartPos + (len * CanonicalLocation.ONE);
                    }
                }, sideText));
                // No need to add related word again later:
                if (curCompletionStart != 0)
                    break;
                else
                    prevCompletionLength = len + 1;
            }
            curCompletionStart = completionText.indexOf(' ', curCompletionStart);
            // If not -1, it's one char past the space, otherwise leave as -1:
            if (curCompletionStart >= 0)
                curCompletionStart += 1;
        }
        while (laterWord && curCompletionStart >= 0);

        return ImmutableMap.copyOf(r);
    }
    
    private static LexCompletion addSideText(LexCompletion completion, @Nullable String sideText)
    {
        if (sideText != null)
            return completion.withSideText(sideText);
        else
            return completion;
    }

    // Helper for above that uses zero as the src start position
    private static ImmutableMap<WordPosition, LexCompletion> matchWordStart(@Nullable String src, @CanonicalLocation int startPos, String completionText, @Nullable String sideText, WordPosition... possiblePositions)
    {
        return matchWordStart(src == null ? null : new Pair<>(src, 0), startPos, completionText, sideText, possiblePositions);
    }

    @OnThread(value = Tag.FXPlatform, ignoreParent = true)
    class AddedSpaceCalculator extends ExpressionVisitorStream<AddedSpace>
    {
        private final EditorLocationAndErrorRecorder locations;
        private final String INDENT = "  ";

        public AddedSpaceCalculator(EditorLocationAndErrorRecorder locations)
        {
            this.locations = locations;
        }

        @Override
        public Stream<AddedSpace> ifThenElse(IfThenElseExpression self, @Recorded Expression condition, @Recorded Expression thenExpression, @Recorded Expression elseExpression)
        {
            return Stream.<AddedSpace>concat(Stream.<AddedSpace>of(
                new AddedSpace(self.getThenLocation().start, "\n" + INDENT),
                new AddedSpace(self.getElseLocation().start, "\n" + INDENT),
                new AddedSpace(self.getEndIfLocation().start, "\n")
            ), increaseIndent(super.ifThenElse(self, condition, thenExpression, elseExpression)));
        }

        @Override
        public Stream<AddedSpace> match(MatchExpression self, @Recorded Expression expression, ImmutableList<MatchClause> clauses)
        {
            Stream.Builder<AddedSpace> r = Stream.builder();

            for (int i = 0; i < clauses.size(); i++)
            {
                r.add(new AddedSpace(clauses.get(i).getCaseLocation().start, "\n" + INDENT));
            }
            r.add(new AddedSpace(self.getEndLocation().start, "\n"));
            return Stream.<AddedSpace>concat(r.build(), increaseIndent(super.match(self, expression, clauses)));
        }

        @Override
        public Stream<AddedSpace> define(DefineExpression self, ImmutableList<DefineItem> defines, @Recorded Expression body)
        {
            Stream.Builder<AddedSpace> r = Stream.builder();

            for (int i = 0; i < defines.size(); i++)
            {
                Either<@Recorded HasTypeExpression, Definition> item = defines.get(i).typeOrDefinition;
                @Recorded Expression expression = item.<@Recorded Expression>either(t -> t, d -> d.rhsValue);
                boolean followedByComma = defines.size() > 1 && i < defines.size() - 1;
                r.add(new AddedSpace(defines.get(i).trailingCommaOrThenLocation.start + (followedByComma ? CanonicalLocation.ONE : CanonicalLocation.ZERO), followedByComma ? "\n       " : "\n  " ));
            }
            r.add(new AddedSpace(self.getEndLocation().start, "\n"));
            
            return Stream.<AddedSpace>concat(r.build(), increaseIndent(super.define(self, defines, body)));
        }

        private Stream<AddedSpace> increaseIndent(Stream<AddedSpace> indents)
        {
            return indents.map(a -> new AddedSpace(a.addedAtInternalPos, a.added + INDENT));
        }
    }
}
