package records.gui.lexeditor;

import annotation.identifier.qual.ExpressionIdentifier;
import annotation.qual.Value;
import annotation.recorded.qual.Recorded;
import annotation.units.SourceLocation;
import com.google.common.base.CharMatcher;
import com.google.common.collect.ImmutableList;
import javafx.beans.value.ObservableObjectValue;
import log.Log;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.TableId;
import records.data.datatype.DataType.DateTimeInfo.DateTimeType;
import records.data.datatype.DataType.TagType;
import records.data.datatype.TaggedTypeDefinition;
import records.data.datatype.TypeId;
import records.data.datatype.TypeManager;
import records.error.ExceptionWithStyle;
import records.error.InternalException;
import records.error.UserException;
import records.grammar.GrammarUtility;
import records.gui.lexeditor.EditorLocationAndErrorRecorder.ErrorDetails;
import records.gui.lexeditor.EditorLocationAndErrorRecorder.Span;
import records.gui.lexeditor.LexAutoComplete.LexCompletion;
import records.gui.lexeditor.LexAutoComplete.LexSelectionBehaviour;
import records.gui.lexeditor.Lexer.LexerResult.CaretPos;
import records.jellytype.JellyType;
import records.transformations.expression.*;
import records.transformations.expression.ColumnReference.ColumnReferenceType;
import records.transformations.expression.Expression.ColumnLookup;
import records.transformations.expression.function.StandardFunctionDefinition;
import records.transformations.expression.type.TypeExpression;
import styled.StyledCSS;
import styled.StyledString;
import styled.StyledString.Style;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformSupplier;
import utility.FXPlatformSupplierInt;
import utility.IdentifierUtility;
import utility.Pair;
import utility.Utility;
import utility.gui.TranslationUtility;

import java.net.URL;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class ExpressionLexer implements Lexer<Expression, ExpressionCompletionContext>
{
    /**
     * The difference between a Keyword and Op is that a Keyword is never a prefix of a longer
     * item, and thus always completes immediately when directly matched.
     */
    public static enum Keyword implements ExpressionToken
    {
        OPEN_SQUARE("["), CLOSE_SQUARE("]"), OPEN_ROUND("("), CLOSE_ROUND(")"), QUEST("?"),
        IF(records.grammar.ExpressionLexer.IF), THEN(records.grammar.ExpressionLexer.THEN), ELSE(records.grammar.ExpressionLexer.ELSE), ENDIF(records.grammar.ExpressionLexer.ENDIF),
        MATCH(records.grammar.ExpressionLexer.MATCH),
        CASE(records.grammar.ExpressionLexer.CASE),
        ORCASE(records.grammar.ExpressionLexer.ORCASE),
        GIVEN(records.grammar.ExpressionLexer.CASEGUARD),
        ENDMATCH(records.grammar.ExpressionLexer.ENDMATCH);

        private final String keyword;

        private Keyword(String keyword)
        {
            this.keyword = keyword;
        }

        private Keyword(int token)
        {
            this.keyword = Utility.literal(records.grammar.ExpressionLexer.VOCABULARY, token);
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
            return StyledString.s(getContent()).withStyle(new StyledCSS("expression-" + this.name().toLowerCase()));
        }


    }

    /**
     * An Op, unlike a Keyword, may have a longer alternative available, so should not
     * complete on direct match (unless it is the only possible direct match).
     */
    public static enum Op implements ExpressionToken
    {
        AND("&"), OR("|"), MULTIPLY("*"), ADD("+"), SUBTRACT("-"), DIVIDE("/"), STRING_CONCAT(";"), EQUALS("="), NOT_EQUAL("<>"), PLUS_MINUS("\u00B1"), RAISE("^"),
        COMMA(","),
        LESS_THAN("<"), LESS_THAN_OR_EQUAL("<="), GREATER_THAN(">"), GREATER_THAN_OR_EQUAL(">=");

        private final String op;

        private Op(String op)
        {
            this.op = op;
        }

        @Override
        @OnThread(Tag.Any)
        public String getContent()
        {
            return op;
        }
    }
    
    private final ObservableObjectValue<ColumnLookup> columnLookup;
    private final TypeManager typeManager;
    private ImmutableList<StandardFunctionDefinition> allFunctions;
    private final FXPlatformSupplierInt<TypeState> makeTypeState;

    public ExpressionLexer(ObservableObjectValue<ColumnLookup> columnLookup, TypeManager typeManager, ImmutableList<StandardFunctionDefinition> functions, FXPlatformSupplierInt<TypeState> makeTypeState)
    {
        this.columnLookup = columnLookup;
        this.typeManager = typeManager;
        this.allFunctions = functions;
        this.makeTypeState = makeTypeState;
    }

    public ExpressionLexer(ObservableObjectValue<ColumnLookup> columnLookup, TypeManager typeManager, ImmutableList<StandardFunctionDefinition> functions)
    {
        this(columnLookup, typeManager, functions, () -> new TypeState(typeManager.getUnitManager(), typeManager));
    }
    
    private static class ContentChunk
    {
        private final String internalContent;
        // Positions are relative to this chunk:
        private final ImmutableList<CaretPos> caretPositions;
        private final StyledString displayContent;

        public ContentChunk(String simpleContent, String... styleClasses)
        {
            this(simpleContent, StyledString.s(simpleContent).withStyle(new StyledCSS(styleClasses)));
        }
        
        public ContentChunk(String simpleContent, StyledString styledString)
        {
            internalContent = simpleContent;
            displayContent = styledString;
            caretPositions = IntStream.range(0, simpleContent.length() + 1).mapToObj(i -> new CaretPos(i, i)).collect(ImmutableList.<CaretPos>toImmutableList());
        }
        
        // Special keyword/operator that has no valid caret positions except at ends, and optionally pads with spaces
        public ContentChunk(boolean addLeadingSpace, ExpressionToken specialContent, boolean addTrailingSpace)
        {
            internalContent = specialContent.getContent();
            displayContent = StyledString.concat(StyledString.s(addLeadingSpace ? " " : ""), specialContent.toStyledString(), StyledString.s(addTrailingSpace ? " " : ""));
            caretPositions = ImmutableList.of(new CaretPos(0, 0), new CaretPos(specialContent.getContent().length(), displayContent.getLength()));
        }

        public ContentChunk(String internalContent, StyledString displayContent, ImmutableList<CaretPos> caretPositions)
        {
            this.internalContent = internalContent;
            this.caretPositions = caretPositions;
            this.displayContent = displayContent;
        }
    }

    @SuppressWarnings("units")
    @Override
    public LexerResult<Expression, ExpressionCompletionContext> process(String content, int curCaretPos)
    {
        ImmutableList.Builder<AutoCompleteDetails<ExpressionCompletionContext>> completions = ImmutableList.builder();
        ExpressionSaver saver = new ExpressionSaver();
        @SourceLocation int curIndex = 0;
        // Index is in original parameter "content":
        BitSet removedChars = new BitSet();
        BitSet suppressBracketMatching = new BitSet();
        ArrayList<ContentChunk> chunks = new ArrayList<>();
        boolean prevWasIdent = false;
        boolean preserveNextSpace = false;
        boolean lexOnMove = false;
        nextToken: while (curIndex < content.length())
        {
            // Skip any extra spaces at the start of tokens:
            if (content.startsWith(" ", curIndex))
            {
                // Keep single space after ident as it may continue ident:
                boolean spaceThenCaret = prevWasIdent && curIndex + 1 == curCaretPos;
                if (spaceThenCaret || preserveNextSpace)
                {
                    chunks.add(new ContentChunk(" "));
                    lexOnMove = spaceThenCaret;
                }
                else
                {
                    removedChars.set(curIndex);
                }
                prevWasIdent = false;
                preserveNextSpace = false;
                curIndex += 1;
                continue nextToken;
            }
            prevWasIdent = false;
            preserveNextSpace = false;
            
            for (Keyword keyword : Keyword.values())
            {
                if (content.startsWith(keyword.getContent(), curIndex))
                {
                    saver.saveKeyword(keyword, new Span(curIndex, curIndex + keyword.getContent().length()), c -> {});
                    if (keyword.getContent().startsWith("@"))
                    {
                        boolean addLeadingSpace = chunks.stream().mapToInt(c -> c.internalContent.length()).sum() > 0;
                        chunks.add(new ContentChunk(addLeadingSpace, keyword, true));
                    }
                    else
                        chunks.add(new ContentChunk(keyword.getContent()));
                    curIndex += keyword.getContent().length();
                    continue nextToken;
                }
            }
            // Need to go through longest first:
            for (Op op : Utility.iterableStream(Arrays.stream(Op.values()).sorted(Comparator.comparing(o -> -o.getContent().length()))))
            {
                if (content.startsWith(op.getContent(), curIndex))
                {
                    saver.saveOperator(op, new Span(curIndex, curIndex + op.getContent().length()), c -> {});
                    boolean addLeadingSpace = !op.getContent().equals(",");
                    chunks.add(new ContentChunk(addLeadingSpace, op, true));
                    curIndex += op.getContent().length();
                    continue nextToken;
                }
            }
            
            if (content.startsWith("\"", curIndex))
            {
                // Consume string until next quote:
                int endQuote = content.indexOf("\"", curIndex + 1);
                if (endQuote != -1)
                {
                    saver.saveOperand(new StringLiteral(GrammarUtility.processEscapes(content.substring(curIndex + 1, endQuote), false)), new Span(curIndex, endQuote + 1), c -> {});
                    chunks.add(new ContentChunk(content.substring(curIndex, endQuote + 1), "expression-string-literal"));
                    suppressBracketMatching.set(curIndex + 1, endQuote);
                    curIndex = endQuote + 1;
                    continue nextToken;
                }
                else
                {
                    // Unterminated string:
                    saver.locationRecorder.addErrorAndFixes(new Span(curIndex, content.length()), StyledString.s("Missing closing quote around text"), ImmutableList.of());
                    saver.saveOperand(new StringLiteral(GrammarUtility.processEscapes(content.substring(curIndex + 1, content.length()), false)), new Span(curIndex, content.length()), c -> {});
                    chunks.add(new ContentChunk(content.substring(curIndex)));
                    suppressBracketMatching.set(curIndex + 1, content.length() + 1);
                    curIndex = content.length();
                    continue nextToken;
                }
            }
            
            if (content.charAt(curIndex) >= '0' && content.charAt(curIndex) <= '9')
            {
                @SourceLocation int numberStart = curIndex;
                // Before dot:
                do
                {
                    curIndex += 1;
                }
                while (curIndex < content.length() && content.charAt(curIndex) >= '0' && content.charAt(curIndex) <= '9');
                
                if (curIndex < content.length() && content.charAt(curIndex) == '.')
                {
                    do
                    {
                        curIndex += 1;
                    }
                    while (curIndex < content.length() && content.charAt(curIndex) >= '0' && content.charAt(curIndex) <= '9');
                }
                Optional<@Value Number> number = Utility.parseNumberOpt(content.substring(numberStart, curIndex));
                if (number.isPresent())
                {
                    saver.saveOperand(new NumericLiteral(number.get(), null), new Span(numberStart, curIndex), c -> {});
                    chunks.add(new ContentChunk(content.substring(numberStart, curIndex)));
                    continue nextToken;
                }
            }

            for (Pair<String, Function<NestedLiteralSource, LiteralOutcome>> nestedLiteral : getNestedLiterals())
            {
                @Nullable NestedLiteralSource nestedOutcome = tryNestedLiteral(nestedLiteral.getFirst(), content, curIndex, saver.locationRecorder);
                if (nestedOutcome != null)
                {
                    LiteralOutcome outcome = nestedLiteral.getSecond().apply(nestedOutcome);
                    saver.saveOperand(outcome.expression, new Span(curIndex, nestedOutcome.positionAfter), c -> {});
                    saver.addNestedErrors(outcome.nestedErrors, curIndex + nestedLiteral.getFirst().length(), curIndex + chunks.stream().mapToInt(c -> c.displayContent.getLength()).sum());
                    chunks.add(outcome.chunk);
                    orShift(removedChars, outcome.removedChars, curIndex + nestedLiteral.getFirst().length());
                    curIndex = nestedOutcome.positionAfter;
                    continue nextToken;
                }
            }

            if (content.startsWith("_", curIndex))
            {
                @Nullable Pair<@ExpressionIdentifier String, Integer> varName = IdentifierUtility.consumeExpressionIdentifier(content, curIndex + 1);
                if (varName == null)
                {
                    saver.saveOperand(new MatchAnythingExpression(), new Span(curIndex, curIndex + 1), c -> {
                    });
                    chunks.add(new ContentChunk("_"));
                    curIndex += 1;
                    continue nextToken;
                }
                else
                {
                    saver.saveOperand(new VarDeclExpression(varName.getFirst()), new Span(curIndex, varName.getSecond()), c -> {});
                    chunks.add(new ContentChunk("_" + varName.getFirst()));
                    curIndex = varName.getSecond();
                    continue nextToken;
                }
            }
            
            if (content.startsWith("@entire", curIndex))
            {
                @Nullable Pair<String, Integer> parsed = IdentifierUtility.consumePossiblyScopedExpressionIdentifier(content, curIndex + "@entire".length());
                if (parsed != null)
                {
                    for (ColumnReference availableColumn : Utility.iterableStream(columnLookup.get().getAvailableColumnReferences()))
                    {
                        TableId tableId = availableColumn.getTableId();
                        String columnIdRaw = availableColumn.getColumnId().getRaw();
                        if (availableColumn.getReferenceType() == ColumnReferenceType.WHOLE_COLUMN &&
                                ((tableId == null && columnIdRaw.equals(parsed.getFirst()))
                                || (tableId != null && (tableId.getRaw() + ":" + columnIdRaw).equals(parsed.getFirst()))))
                        {
                            saver.saveOperand(new ColumnReference(availableColumn), new Span(curIndex, parsed.getSecond()), c -> {
                            });
                            chunks.add(new ContentChunk("@entire " + parsed.getFirst(), "expression-column"));
                            curIndex = parsed.getSecond();
                            continue nextToken;
                        }
                    }
                    
                }
            }

            @Nullable Pair<String, Integer> parsed = IdentifierUtility.consumePossiblyScopedExpressionIdentifier(content, curIndex);
            if (parsed != null && parsed.getSecond() > curIndex)
            {
                prevWasIdent = true;
                String text = parsed.getFirst();
                Span location = new Span(curIndex, parsed.getSecond() + ((parsed.getSecond() < content.length() && content.charAt(parsed.getSecond()) == ' ') ? 1 : 0));
                
                ImmutableList.Builder<LexCompletion> identCompletions = ImmutableList.builder();
                
                // Add completions even if one is already spotted:
                for (StandardFunctionDefinition function : allFunctions)
                {
                    if (function.getName().startsWith(parsed.getFirst()))
                        identCompletions.add(new LexCompletion(curIndex, function.getName() + "()", function.getName().length() + 1) {
                            @Override
                            public @Nullable Pair<String, @Nullable String> getFurtherDetailsURL()
                            {
                                return new Pair<>("function-" + function.getDocKey().replace(":", "-") + ".html", null);
                            }
                        });
                }
                for (ColumnReference availableColumn : Utility.iterableStream(columnLookup.get().getAvailableColumnReferences()))
                {
                    if (availableColumn.getReferenceType() == ColumnReferenceType.CORRESPONDING_ROW && availableColumn.getTableId() == null && availableColumn.getColumnId().getRaw().startsWith(parsed.getFirst()))
                        identCompletions.add(new LexCompletion(curIndex, availableColumn.getColumnId().getRaw()));
                }
                for (TagCompletion tag : getTagCompletions(typeManager.getKnownTaggedTypes()))
                {
                    String fullName = (tag.typeName != null ? (tag.typeName + ":") : "") + tag.tagName;
                    if (tag.tagName.startsWith(parsed.getFirst()) || (tag.typeName != null && fullName.startsWith(parsed.getFirst())))
                    {
                        identCompletions.add(new LexCompletion(curIndex, fullName + (tag.hasInner ? "()" : ""), fullName.length() + (tag.hasInner ? 1 : 0)));
                    }
                }
                try
                {
                    for (String availableVariable : makeTypeState.get().getAvailableVariables())
                    {
                        if (availableVariable.startsWith(parsed.getFirst()))
                            identCompletions.add(new LexCompletion(curIndex, availableVariable));
                    }
                }
                catch (InternalException e)
                {
                    Log.log(e);
                }
                for (Keyword keyword : Keyword.values())
                {
                    if (keyword.getContent().startsWith("@"))
                    {
                        int common = Utility.longestCommonStart(keyword.getContent().substring(1), text);
                        if (common > 0)
                        {
                            completions.add(new AutoCompleteDetails<>(new Span(curIndex, curIndex + common), new ExpressionCompletionContext(ImmutableList.of(new LexCompletion(curIndex, keyword.getContent(), LexSelectionBehaviour.SELECT_IF_ONLY) {
                                @Override
                                public @Nullable Pair<String, @Nullable String> getFurtherDetailsURL()
                                {
                                    return getDocURLFor(keyword);
                                }
                            }))));
                        }
                    }
                }
                
                completions.add(new AutoCompleteDetails<>(location, new ExpressionCompletionContext(identCompletions.build())));

                boolean wasColumn = false;
                {
                    for (ColumnReference availableColumn : Utility.iterableStream(columnLookup.get().getAvailableColumnReferences()))
                    {
                        TableId tableId = availableColumn.getTableId();
                        String columnIdRaw = availableColumn.getColumnId().getRaw();
                        if (availableColumn.getReferenceType() == ColumnReferenceType.CORRESPONDING_ROW &&
                                (
                                        (tableId == null && columnIdRaw.equals(text))
                                                || (tableId != null && (tableId.getRaw() + ":" + columnIdRaw).equals(text))
                                ))
                        {
                            saver.saveOperand(new ColumnReference(availableColumn), location, c -> {
                            });
                            wasColumn = true;
                            break;
                        }
                        else if (availableColumn.getReferenceType() == ColumnReferenceType.WHOLE_COLUMN &&
                                text.startsWith("@entire ") &&
                                ((tableId == null && ("@entire " + columnIdRaw).equals(text))
                                        || (tableId != null && ("@entire " + tableId.getRaw() + ":" + columnIdRaw).equals(text))))
                        {
                            saver.saveOperand(new ColumnReference(availableColumn), location, c -> {
                            });
                            wasColumn = true;
                            break;
                        }
                    }

                    if (!wasColumn)
                    {
                        boolean wasFunction = false;
                        for (StandardFunctionDefinition function : allFunctions)
                        {
                            if (function.getName().equals(text))
                            {
                                saver.saveOperand(new StandardFunction(function), location, c -> {});
                                wasFunction = true;
                                break;
                            }
                        }

                        if (!wasFunction)
                        {
                            boolean wasTagged = false;
                            for (TaggedTypeDefinition taggedType : typeManager.getKnownTaggedTypes().values())
                            {
                                for (TagType<JellyType> tag : taggedType.getTags())
                                {
                                    if (tag.getName().equals(text) || text.equals(taggedType.getTaggedTypeName().getRaw() + ":" + tag.getName()))
                                    {
                                        saver.saveOperand(new ConstructorExpression(typeManager, taggedType.getTaggedTypeName().getRaw(), tag.getName()), location, c -> {});
                                        wasTagged = true;
                                        break;
                                    }
                                }
                            }

                            if (!wasTagged)
                                saver.saveOperand(InvalidIdentExpression.identOrUnfinished(text), location, c -> {
                                });
                        }
                    }
                }
                if (wasColumn)
                {
                    chunks.add(new ContentChunk(text, StyledString.s(text).withStyle(new StyledCSS("expression-column")).withStyle(new EditorDisplay.TokenBackground(ImmutableList.of("expression-column-background")))));
                }
                else
                {
                    chunks.add(new ContentChunk(text));
                }
                curIndex = parsed.getSecond();
                continue nextToken;
            }
            
            if (content.startsWith("@", curIndex))
            {
                int nonLetter = CharMatcher.inRange('a', 'z').or(CharMatcher.inRange('A', 'Z')).negate().indexIn(content, curIndex + 1);
                nonLetter = nonLetter == -1 ? content.length() : nonLetter;
                String stem = content.substring(curIndex, nonLetter);

                ImmutableList.Builder<LexCompletion> validKeywordCompletions = ImmutableList.builder();
                if ("@i".startsWith(stem))
                {
                    validKeywordCompletions.add(new LexCompletion(curIndex, "@if@then@else@endif", 2, LexSelectionBehaviour.SELECT_IF_TOP) {
                        @Override
                        public String toString()
                        {
                            return "@if \u2026 @then \u2026 @else \u2026 @endif";
                        }

                        @Override
                        public @Nullable Pair<String, @Nullable String> getFurtherDetailsURL()
                        {
                            return new Pair<>("syntax-if.html", null);
                        }
                    });
                }

                for (Keyword keyword : Keyword.values())
                {
                    if (keyword.getContent().startsWith(stem))
                    {
                        validKeywordCompletions.add(new LexCompletion(curIndex, keyword.getContent(), LexSelectionBehaviour.SELECT_IF_ONLY) {
                            @Override
                            public @Nullable Pair<String, @Nullable String> getFurtherDetailsURL()
                            {
                                return getDocURLFor(keyword);
                            }
                        });
                    }
                }
                completions.add(new AutoCompleteDetails<>(new Span(curIndex, nonLetter), new ExpressionCompletionContext(validKeywordCompletions.build())));
                
                // We skip to next non-letter to prevent trying to complete the keyword as a function:
                String attemptedKeyword = content.substring(curIndex, nonLetter);
                saver.saveOperand(new InvalidIdentExpression(attemptedKeyword), new Span(curIndex, nonLetter), c -> {});
                saver.locationRecorder.addErrorAndFixes(new Span(curIndex, nonLetter), StyledString.s("Unknown keyword: " + attemptedKeyword), ImmutableList.of());
                chunks.add(new ContentChunk(attemptedKeyword));
                curIndex = nonLetter;
                preserveNextSpace = true;
                continue nextToken;
            }

            boolean nextTrue = content.startsWith("true", curIndex);
            boolean nextFalse = content.startsWith("false", curIndex);
            if (nextTrue || nextFalse)
            {
                saver.saveOperand(new BooleanLiteral(nextTrue), new Span(curIndex, curIndex + (nextTrue ? 4 : 5)), c -> {});
                chunks.add(new ContentChunk(nextTrue ? "true" : "false"));
                curIndex += nextTrue ? 4 : 5;
                continue nextToken;
            }
            
            Span invalidCharLocation = new Span(curIndex, curIndex + 1);
            saver.saveOperand(new InvalidIdentExpression(content.substring(curIndex, curIndex + 1)), invalidCharLocation, c -> {});
            saver.locationRecorder.addErrorAndFixes(invalidCharLocation, StyledString.concat(TranslationUtility.getStyledString("error.illegalCharacter", Utility.codePointToString(content.charAt(curIndex))), StyledString.s("\n  "), StyledString.s("Character code: \\u" + Integer.toHexString(content.charAt(curIndex))).withStyle(new StyledCSS("errorable-sub-explanation"))), ImmutableList.of(new TextQuickFix("error.illegalCharacter.remove", invalidCharLocation, () -> new Pair<>("", StyledString.s("<remove>")))));
            chunks.add(new ContentChunk(content.substring(curIndex, curIndex + 1)));
            curIndex += 1;
        }
        @Recorded Expression saved = saver.finish(new Span(curIndex, curIndex));
        try
        {
            saved.checkExpression(columnLookup.get(), makeTypeState.get(), saver.locationRecorder.getRecorder());
        }
        catch (InternalException | UserException e)
        {
            if (e instanceof InternalException)
                Log.log(e);
            saver.locationRecorder.addErrorAndFixes(new Span(0, curIndex), ((ExceptionWithStyle) e).getStyledMessage(), ImmutableList.of());
        }
        
        String internalContent = chunks.stream().map(c -> c.internalContent).collect(Collectors.joining());
        StyledString display = chunks.stream().map(c -> c.displayContent).filter(d -> d.getLength() > 0).collect(StyledString.joining(""));
        ArrayList<CaretPos> caretPos = new ArrayList<>();
        int internalLenSoFar = 0;
        int displayLenSoFar = 0;
        for (ContentChunk chunk : chunks)
        {
            for (CaretPos caretPosition : chunk.caretPositions)
            {
                addCaretPos(caretPos, new CaretPos(caretPosition.positionInternal + internalLenSoFar, caretPosition.positionDisplay + displayLenSoFar));
            }
            internalLenSoFar += chunk.internalContent.length();
            displayLenSoFar += chunk.displayContent.getLength();
        }
        // Empty strings should still have a caret pos:
        if (chunks.isEmpty())
        {
            chunks.add(new ContentChunk("", StyledString.s(" ")));
            caretPos.add(new CaretPos(0, 0));
        }

        // Important to go through in order so that later errors can be
        // adjusted correctly according to earlier errors.
        ImmutableList<ErrorDetails> errors = saver.getErrors();
        for (ErrorDetails error : Utility.iterableStream(errors.stream().sorted(Comparator.comparing(e -> e.location.start))))
        {
            error.location = new Span(error.location.start - removedChars.get(0, error.location.start).cardinality(), error.location.end - removedChars.get(0, error.location.end).cardinality());
            
            // If an error only occupies one caret position, add an extra char there:
            if (error.location.start == error.location.end)
            {                
                // Find caret pos:
                int displayOffset = 0;
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
                            error.displayLocation = new Span(p.positionDisplay, p.positionDisplay + 1);
                            // Add space to display:
                            display = StyledString.concat(display.substring(0, p.positionDisplay), StyledString.s(" "), display.substring(p.positionDisplay, display.getLength()));
                            // And offset future caret pos display by one:
                            displayOffset += 1;
                        }
                    }
                }
            }
        }
        
        return new LexerResult<>(saved, internalContent, removedChars, lexOnMove, ImmutableList.copyOf(caretPos), display, errors, completions.build(), suppressBracketMatching, !saver.hasUnmatchedBrackets());
    }

    private @Nullable Pair<String, @Nullable String> getDocURLFor(Keyword keyword)
    {
        switch (keyword)
        {
            case IF:
            case THEN:
            case ELSE:
            case ENDIF:
                return new Pair<>("syntax-if.html", null);
            case MATCH:
            case CASE:
            case ORCASE:
            case GIVEN:
            case ENDMATCH:
                return new Pair<>("syntax-match.html", null);
        }
        return null;
    }

    private void addCaretPos(ArrayList<CaretPos> caretPos, CaretPos newPos)
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

    // Effectively does: dest = dest | (src << shiftBy)
    private void orShift(BitSet dest, BitSet src, int shiftBy)
    {
        for (int srcBit = src.nextSetBit(0); srcBit != -1; srcBit = src.nextSetBit(srcBit + 1))
        {
            dest.set(srcBit + shiftBy);
        }
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
        public final BitSet removedChars;
        public final ContentChunk chunk;
        public final ImmutableList<ErrorDetails> nestedErrors;
        
        public LiteralOutcome(NestedLiteralSource source, Expression expression)
        {
            this.chunk = new ContentChunk(source.prefix + source.innerContent + (source.terminatedProperly ? "}" : ""));
            this.expression = expression;
            this.removedChars = new BitSet();
            this.nestedErrors = ImmutableList.of();
        }

        public LiteralOutcome(String prefix, String internalContent, StyledString displayContent, Expression expression, String suffix, BitSet removedChars, ImmutableList<CaretPos> caretPos, ImmutableList<ErrorDetails> errors)
        {
            ImmutableList.Builder<CaretPos> caretPosIncludingPrefixSuffix = ImmutableList.builder();
            caretPosIncludingPrefixSuffix.add(new CaretPos(0, 0));
            for (CaretPos p : caretPos)
            {
                caretPosIncludingPrefixSuffix.add(new CaretPos(p.positionInternal + prefix.length(), p.positionDisplay + prefix.length()));
            }
            caretPosIncludingPrefixSuffix.add(new CaretPos(prefix.length() + internalContent.length() + suffix.length(), prefix.length() + displayContent.getLength() + suffix.length()));
            this.chunk = new ContentChunk(
                prefix + internalContent + suffix,
                StyledString.concat(StyledString.s(prefix), displayContent, StyledString.s(suffix)),
                caretPosIncludingPrefixSuffix.build());
            this.expression = expression;
            this.removedChars = removedChars;
            this.nestedErrors = errors;
        }
    }

    private ImmutableList<Pair<String, Function<NestedLiteralSource, LiteralOutcome>>> getNestedLiterals()
    {
        return ImmutableList.of(
            new Pair<>("date{", c -> new LiteralOutcome(c, new TemporalLiteral(DateTimeType.YEARMONTHDAY, c.innerContent))),
            new Pair<>("datetime{", c -> new LiteralOutcome(c, new TemporalLiteral(DateTimeType.DATETIME, c.innerContent))),
            new Pair<>("datetimezoned{", c -> new LiteralOutcome(c, new TemporalLiteral(DateTimeType.DATETIMEZONED, c.innerContent))),
            new Pair<>("dateym{", c -> new LiteralOutcome(c, new TemporalLiteral(DateTimeType.YEARMONTH, c.innerContent))),
            new Pair<>("time{", c -> new LiteralOutcome(c, new TemporalLiteral(DateTimeType.TIMEOFDAY, c.innerContent))),
            new Pair<>("type{", c -> {
                TypeLexer typeLexer = new TypeLexer();
                LexerResult<TypeExpression, CodeCompletionContext> processed = typeLexer.process(c.innerContent, 0);
                return new LiteralOutcome(c.prefix, processed.adjustedContent, processed.display, new TypeLiteralExpression(processed.result), c.terminatedProperly ? "}" : "", processed.removedChars, processed.caretPositions, processed.errors);
            }),
            new Pair<>("{", c -> {
                UnitLexer unitLexer = new UnitLexer();
                LexerResult<UnitExpression, CodeCompletionContext> processed = unitLexer.process(c.innerContent, 0);
                return new LiteralOutcome(c.prefix, processed.adjustedContent, processed.display, new UnitLiteralExpression(processed.result), c.terminatedProperly ? "}" : "", processed.removedChars, processed.caretPositions, processed.errors);
            })
        );
    }
    
    private static class NestedLiteralSource
    {
        private final String prefix;
        private final String innerContent;
        private final int positionAfter;
        private final boolean terminatedProperly;

        public NestedLiteralSource(String prefix, String innerContent, int positionAfter, boolean terminatedProperly)
        {
            this.prefix = prefix;
            this.innerContent = innerContent;
            this.positionAfter = positionAfter;
            this.terminatedProperly = terminatedProperly;
        }
    }
    
    @SuppressWarnings("units")
    private @Nullable NestedLiteralSource tryNestedLiteral(String prefixInclCurly, String content, @SourceLocation int curIndex, EditorLocationAndErrorRecorder locationRecorder)
    {
        if (content.startsWith(prefixInclCurly, curIndex))
        {
            curIndex += prefixInclCurly.length();
            @SourceLocation int startIndex = curIndex;
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
                locationRecorder.addErrorAndFixes(new Span(startIndex, content.length()), StyledString.s("Started " + prefixInclCurly + " but no matching closing }"), ImmutableList.of());
                return new NestedLiteralSource(prefixInclCurly, content.substring(startIndex), content.length(), false);
            }
        }
        return null;
    }    
}
