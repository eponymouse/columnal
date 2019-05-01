package records.gui.lexeditor;

import annotation.identifier.qual.ExpressionIdentifier;
import annotation.qual.Value;
import annotation.recorded.qual.Recorded;
import annotation.units.CanonicalLocation;
import annotation.units.DisplayLocation;
import annotation.units.RawInputLocation;
import com.google.common.base.CharMatcher;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import javafx.beans.value.ObservableObjectValue;
import log.Log;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.TableId;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DateTimeInfo.DateTimeType;
import records.data.datatype.DataType.TagType;
import records.data.datatype.TaggedTypeDefinition;
import records.data.datatype.TypeId;
import records.data.datatype.TypeManager;
import records.error.ExceptionWithStyle;
import records.error.InternalException;
import records.error.UserException;
import records.grammar.GrammarUtility;
import records.gui.lexeditor.EditorLocationAndErrorRecorder.CanonicalSpan;
import records.gui.lexeditor.EditorLocationAndErrorRecorder.ErrorDetails;
import records.gui.lexeditor.completion.LexCompletion;
import records.gui.lexeditor.completion.LexAutoComplete.LexSelectionBehaviour;
import records.gui.lexeditor.Lexer.LexerResult.CaretPos;
import records.gui.lexeditor.completion.LexCompletionGroup;
import records.jellytype.JellyType;
import records.transformations.expression.*;
import records.transformations.expression.ColumnReference.ColumnReferenceType;
import records.transformations.expression.Expression.ColumnLookup;
import records.transformations.expression.function.StandardFunctionDefinition;
import records.transformations.expression.type.TypeExpression;
import records.typeExp.TypeExp;
import styled.StyledCSS;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformSupplierInt;
import utility.IdentifierUtility;
import utility.Pair;
import utility.Utility;
import utility.gui.TranslationUtility;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ExpressionLexer extends Lexer<Expression, ExpressionCompletionContext>
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
    private final @Nullable DataType expectedType;

    public ExpressionLexer(ObservableObjectValue<ColumnLookup> columnLookup, TypeManager typeManager, ImmutableList<StandardFunctionDefinition> functions, FXPlatformSupplierInt<TypeState> makeTypeState, @Nullable DataType expectedType)
    {
        this.columnLookup = columnLookup;
        this.typeManager = typeManager;
        this.allFunctions = functions;
        this.makeTypeState = makeTypeState;
        this.expectedType = expectedType;
    }

    @Override
    public LexerResult<Expression, ExpressionCompletionContext> process(String content, int curCaretPos)
    {
        ExpressionSaver saver = new ExpressionSaver();
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
                boolean spaceThenCaret = prevWasIdent && curIndex + 1 == curCaretPos;
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
                    saver.saveKeyword(keyword, removedChars.map(curIndex, keyword.getContent()), c -> {});
                    if (keyword.getContent().startsWith("@"))
                    {
                        boolean addLeadingSpace = chunks.stream().mapToInt(c -> c.internalContent.length()).sum() > 0;
                        chunks.add(new ContentChunk(addLeadingSpace, keyword, ChunkType.NON_IDENT, true));
                    }
                    else
                        chunks.add(new ContentChunk(keyword.getContent(), ChunkType.NON_IDENT));
                    curIndex += rawLength(keyword.getContent());
                    continue nextToken;
                }
            }
            // Need to go through longest first:
            for (Op op : Utility.iterableStream(Arrays.stream(Op.values()).sorted(Comparator.comparing(o -> -o.getContent().length()))))
            {
                if (content.startsWith(op.getContent(), curIndex))
                {
                    saver.saveOperator(op, removedChars.map(curIndex, op.getContent()), c -> {});
                    boolean addLeadingSpace = !op.getContent().equals(",");
                    chunks.add(new ContentChunk(addLeadingSpace, op, ChunkType.NON_IDENT, true));
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
                    saver.saveOperand(new StringLiteral(GrammarUtility.processEscapes(content.substring(curIndex + 1, endQuote), false)), removedChars.map(curIndex, endQuote + RawInputLocation.ONE), c -> {});
                    chunks.add(new ContentChunk(content.substring(curIndex, endQuote + 1), ChunkType.NON_IDENT, "expression-string-literal"));
                    suppressBracketMatching.set(curIndex + 1, endQuote);
                    curIndex = endQuote + RawInputLocation.ONE;
                    continue nextToken;
                }
                else
                {
                    // Unterminated string:
                    saver.locationRecorder.addErrorAndFixes(removedChars.map(curIndex, content), StyledString.s("Missing closing quote around text"), ImmutableList.of());
                    saver.saveOperand(new StringLiteral(GrammarUtility.processEscapes(content.substring(curIndex + 1, content.length()), false)), removedChars.map(curIndex, content), c -> {});
                    chunks.add(new ContentChunk(content.substring(curIndex), ChunkType.NON_IDENT));
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
                    saver.saveOperand(new NumericLiteral(number.get(), null), removedChars.map(numberStart, curIndex), c -> {});
                    chunks.add(new ContentChunk(content.substring(numberStart, curIndex), ChunkType.IDENT));
                    continue nextToken;
                }
            }

            for (Pair<String, Function<NestedLiteralSource, LiteralOutcome>> nestedLiteral : getNestedLiterals())
            {
                @Nullable NestedLiteralSource nestedOutcome = tryNestedLiteral(nestedLiteral.getFirst(), content, curIndex, removedChars, saver.locationRecorder);
                if (nestedOutcome != null)
                {
                    LiteralOutcome outcome = nestedLiteral.getSecond().apply(nestedOutcome);
                    saver.saveOperand(outcome.expression, removedChars.map(curIndex, nestedOutcome.positionAfter), c -> {});
                    @SuppressWarnings("units")
                    @DisplayLocation int displayOffset = curIndex + chunks.stream().mapToInt(c -> c.displayContent.getLength()).sum();
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
                @Nullable Pair<@ExpressionIdentifier String, @RawInputLocation Integer> varName = IdentifierUtility.consumeExpressionIdentifier(content, curIndex + RawInputLocation.ONE);
                if (varName == null)
                {
                    saver.saveOperand(new MatchAnythingExpression(), removedChars.map(curIndex, curIndex + RawInputLocation.ONE), c -> {
                    });
                    chunks.add(new ContentChunk("_", ChunkType.IDENT));
                    curIndex += RawInputLocation.ONE;
                    continue nextToken;
                }
                else
                {
                    saver.saveOperand(new VarDeclExpression(varName.getFirst()), removedChars.map(curIndex, varName.getSecond()), c -> {});
                    chunks.add(new ContentChunk("_" + varName.getFirst(), ChunkType.IDENT));
                    curIndex = varName.getSecond();
                    continue nextToken;
                }
            }
            
            if (content.startsWith("@entire", curIndex))
            {
                @Nullable Pair<String, @RawInputLocation Integer> parsed = IdentifierUtility.consumePossiblyScopedExpressionIdentifier(content, curIndex + rawLength("@entire"));
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
                            saver.saveOperand(new ColumnReference(availableColumn), removedChars.map(curIndex, parsed.getSecond()), c -> {
                            });
                            chunks.add(new ContentChunk("@entire " + parsed.getFirst(), StyledString.s("@entire " + parsed.getFirst()).withStyle(new StyledCSS("expression-column")).withStyle(new EditorDisplay.TokenBackground(ImmutableList.of("expression-column-background"))), ChunkType.IDENT));
                            curIndex = parsed.getSecond();
                            continue nextToken;
                        }
                    }
                    
                }
            }

            @Nullable Pair<String, @RawInputLocation Integer> parsed = IdentifierUtility.consumePossiblyScopedExpressionIdentifier(content, curIndex);
            final @CanonicalLocation int canonIndex = removedChars.map(curIndex);
            if (parsed != null && parsed.getSecond() > curIndex)
            {
                prevWasIdent = true;
                String text = parsed.getFirst();
                CanonicalSpan location = removedChars.map(curIndex, ((parsed.getSecond() < content.length() && content.charAt(parsed.getSecond()) == ' ') ? parsed.getSecond() + RawInputLocation.ONE : parsed.getSecond()));

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
                    chunks.add(new ContentChunk(text, StyledString.s(text).withStyle(new StyledCSS("expression-column")).withStyle(new EditorDisplay.TokenBackground(ImmutableList.of("expression-column-background"))), ChunkType.IDENT));
                }
                else
                {
                    chunks.add(new ContentChunk(text, ChunkType.IDENT));
                }
                curIndex = parsed.getSecond();
                continue nextToken;
            }
            
            if (content.startsWith("@", curIndex))
            {
                @SuppressWarnings("units")
                @RawInputLocation int nonLetter = CharMatcher.inRange('a', 'z').or(CharMatcher.inRange('A', 'Z')).negate().indexIn(content, curIndex + 1);
                if (nonLetter == -1)
                    nonLetter = rawLength(content);
                String stem = content.substring(curIndex, nonLetter);

                if (Utility.startsWithIgnoreCase("@entire",stem))
                {
                    for (ColumnReference columnReference : Utility.iterableStream(columnLookup.get().getAvailableColumnReferences().sorted(Comparator.comparing((ColumnReference c) -> c.getTableId() == null ? "" : c.getTableId().getRaw()).thenComparing((ColumnReference c) -> c.getColumnId().getRaw()))))
                    {
                        if (columnReference.getReferenceType() == ColumnReferenceType.WHOLE_COLUMN)
                        {
                            String fullAfterEntire = (columnReference.getTableId() == null ? "" : columnReference.getTableId().getRaw() + ":") + columnReference.getColumnId().getRaw();
                        }
                    }
                    
                }

                // We skip to next non-letter to prevent trying to complete the keyword as a function:
                String attemptedKeyword = content.substring(curIndex, nonLetter);
                saver.saveOperand(new InvalidIdentExpression(attemptedKeyword), removedChars.map(curIndex, nonLetter), c -> {});
                saver.locationRecorder.addErrorAndFixes(removedChars.map(curIndex, nonLetter), StyledString.s("Unknown keyword: " + attemptedKeyword), ImmutableList.of());
                chunks.add(new ContentChunk(attemptedKeyword, ChunkType.NON_IDENT));
                curIndex = nonLetter;
                preserveNextSpace = true;
                continue nextToken;
            }

            boolean nextTrue = content.startsWith("true", curIndex);
            boolean nextFalse = content.startsWith("false", curIndex);
            if (nextTrue || nextFalse)
            {
                saver.saveOperand(new BooleanLiteral(nextTrue), removedChars.map(curIndex, nextTrue ? "true" : "false"), c -> {});
                chunks.add(new ContentChunk(nextTrue ? "true" : "false", ChunkType.IDENT));
                curIndex += rawLength(nextTrue ? "true" : "false");
                continue nextToken;
            }
            
            CanonicalSpan invalidCharLocation = removedChars.map(curIndex, curIndex + RawInputLocation.ONE);
            saver.saveOperand(new InvalidIdentExpression(content.substring(curIndex, curIndex + 1)), invalidCharLocation, c -> {});
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
                    TypeExp.unifyTypes(typeExp, TypeExp.fromDataType(null, expectedType));
                @RawInputLocation int lastIndex = curIndex;
                typeExp.toConcreteType(typeManager, false).ifLeft(err -> {
                    saver.locationRecorder.addErrorAndFixes(new CanonicalSpan(CanonicalLocation.ZERO, removedChars.map(lastIndex)), err.getErrorText(), ImmutableList.of(/*asType fix?  Only if ambig?*/));
                });
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
        ArrayList<CaretPos> caretPos = calculateCaretPos(chunks);

        ImmutableList<ErrorDetails> errors = saver.getErrors();
        display = Lexer.padZeroWidthErrors(display, caretPos, errors);

        return new LexerResult<Expression, ExpressionCompletionContext>(saved, internalContent, removedChars, lexOnMove, ImmutableList.copyOf(caretPos), display, errors, saver.locationRecorder, Utility.concatI(Lexer.<ExpressionCompletionContext>makeCompletions(chunks, this::makeCompletions), nestedCompletions.build()), suppressBracketMatching, !saver.hasUnmatchedBrackets());
    }

    private AutoCompleteDetails<ExpressionCompletionContext> offsetBy(AutoCompleteDetails<CodeCompletionContext> acd, @CanonicalLocation int caretPosOffset)
    {
        return new AutoCompleteDetails<>(acd.location.offsetBy(caretPosOffset), new ExpressionCompletionContext(acd.codeCompletionContext, caretPosOffset));
    }

    private ExpressionCompletionContext makeCompletions(String stem, @CanonicalLocation int canonIndex)
    {
        // Large size to avoid reallocations:
        Builder<Pair<CompletionStatus, ExpressionCompletion>> completions = ImmutableList.builderWithExpectedSize(1000);

        // Add completions even if one is already spotted:
        addFunctionCompletions(completions, stem, canonIndex);
        addColumnCompletions(completions, stem, canonIndex);
        addTagCompletions(completions, stem, canonIndex);
        addVariableCompletions(completions, stem, canonIndex);
        addNestedLiteralCompletions(completions, stem, canonIndex);

        addKeywordCompletions(completions, stem, canonIndex);

        return new ExpressionCompletionContext(sort(completions.build()));
    }

    private void addKeywordCompletions(Builder<Pair<CompletionStatus, ExpressionCompletion>> completions, String stem, @CanonicalLocation int canonIndex)
    {

        if (Utility.startsWithIgnoreCase("@i", stem))
        {
            completions.add(new Pair<>(CompletionStatus.DIRECT, new ExpressionCompletion(new LexCompletion(canonIndex, stem.length(), "@if@then@else@endif").withDisplay(StyledString.s("@if \u2026 @then \u2026 @else \u2026 @endif")).withFurtherDetailsURL("syntax-if.html").withCaretPosAfterCompletion(3).withSelectionBehaviour(LexSelectionBehaviour.SELECT_IF_TOP), CompletionType.KEYWORD_CHAIN)));
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
    private void addNestedLiteralCompletions(Builder<Pair<CompletionStatus, ExpressionCompletion>> identCompletions, @Nullable String stem, @CanonicalLocation int canonIndex)
    {
        for (Pair<String, Function<NestedLiteralSource, LiteralOutcome>> nestedLiteral : getNestedLiterals())
        {
            matchWordStart(stem, canonIndex, nestedLiteral.getFirst(), WordPosition.FIRST_WORD).map(p -> p.mapSecond(c -> c.withReplacement(nestedLiteral.getFirst() + "}", StyledString.s(nestedLiteral.getFirst() + "\u2026}"))
                    .withCaretPosAfterCompletion(nestedLiteral.getFirst().length())
                    .withFurtherDetailsURL("literal-" + nestedLiteral.getFirst().replace("{", "") + ".html"))).ifPresent(c -> {
                identCompletions.add(new Pair<>(CompletionStatus.DIRECT, new ExpressionCompletion(c.getSecond(), CompletionType.NESTED_LITERAL)));
            });
        }
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
            matchWordStart(stem, canonIndex, bool, WordPosition.FIRST_WORD).ifPresent(p -> {
                identCompletions.add(new Pair<>(CompletionStatus.DIRECT, new ExpressionCompletion(p.getSecond(), CompletionType.VARIABLE)));
            });
        }
        
        try
        {
            for (String availableVariable : makeTypeState.get().getAvailableVariables())
            {
                matchWordStart(stem, canonIndex, availableVariable, WordPosition.FIRST_WORD, WordPosition.LATER_WORD).map(p -> p.mapSecond(c -> {
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
                })).ifPresent(c -> identCompletions.add(new Pair<>(c.getFirst() == WordPosition.FIRST_WORD ? CompletionStatus.DIRECT : CompletionStatus.RELATED, new ExpressionCompletion(c.getSecond(), CompletionType.VARIABLE))));
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
    private void addColumnCompletions(Builder<Pair<CompletionStatus, ExpressionCompletion>> identCompletions, @Nullable String stem, @CanonicalLocation int canonIndex)
    {
        for (ColumnReference availableColumn : Utility.iterableStream(columnLookup.get().getAvailableColumnReferences()))
        {
            if (availableColumn.getReferenceType() == ColumnReferenceType.CORRESPONDING_ROW && availableColumn.getTableId() == null)
            {
                matchWordStart(stem, canonIndex, availableColumn.getColumnId().getRaw(), WordPosition.FIRST_WORD, WordPosition.LATER_WORD).ifPresent(c -> identCompletions.add(new Pair<>(c.getFirst() == WordPosition.FIRST_WORD ? CompletionStatus.DIRECT : CompletionStatus.RELATED, new ExpressionCompletion(c.getSecond(), CompletionType.COLUMN))));
            }
            
            if (availableColumn.getReferenceType() == ColumnReferenceType.WHOLE_COLUMN)
            {
                String withoutEntire = (availableColumn.getTableId() == null ? "" : availableColumn.getTableId().getRaw() + ":") + availableColumn.getColumnId().getRaw();
                Optional<Pair<CompletionStatus, LexCompletion>> entire = matchWordStart(stem, canonIndex, "@entire " + withoutEntire, WordPosition.FIRST_WORD).map(c -> c.mapFirst(w -> CompletionStatus.DIRECT));
                if (!entire.isPresent())
                {
                    // Add a related item if matches without the entire
                    entire = matchWordStart(stem, canonIndex, withoutEntire, WordPosition.FIRST_WORD).map(p -> p.map(w -> CompletionStatus.RELATED, c -> c.withReplacement("@entire " + withoutEntire)));
                }
                entire.ifPresent(p -> identCompletions.add(p.mapSecond(c -> new ExpressionCompletion(c, CompletionType.COLUMN))));
            }
        }
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
            Optional<Pair<CompletionStatus, LexCompletion>> lexCompletion = matchWordStart(stem, canonIndex, function.getName(), WordPosition.FIRST_WORD, WordPosition.LATER_WORD).map(p -> p.mapFirst(word -> word == WordPosition.FIRST_WORD ? CompletionStatus.DIRECT : CompletionStatus.RELATED));
            
            if (!lexCompletion.isPresent() && stem != null && stem.length() >= 3)
            {
                for (String synonym : function.getSynonyms())
                {
                    lexCompletion = matchWordStart(stem, canonIndex, synonym, WordPosition.FIRST_WORD).map(c -> {
                        return new Pair<>(CompletionStatus.RELATED, c.getSecond().withReplacement(function.getName()).withSideText("\u2248 " + synonym));
                    });
                    if (lexCompletion.isPresent())
                        break;
                }
            }
            
            lexCompletion.ifPresent(c -> identCompletions.add(new Pair<>(c.getFirst(), new ExpressionCompletion(c.getSecond().withReplacement(function.getName() + "()", StyledString.s(function.getName() + "(\u2026)")).withFurtherDetailsURL("function-" + function.getDocKey().replace(":", "-") + ".html").withCaretPosAfterCompletion(function.getName().length() + 1), CompletionType.FUNCTION))));
        }
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
            String fullName = (tag.typeName != null ? (tag.typeName + ":") : "") + tag.tagName;
            if (stem == null || Utility.startsWithIgnoreCase(tag.tagName, stem) || (tag.typeName != null && Utility.startsWithIgnoreCase(fullName, stem)))
            {
                identCompletions.add(new Pair<>(CompletionStatus.DIRECT, new ExpressionCompletion(new LexCompletion(canonIndex, stem == null ? 0 : stem.length(), fullName + (tag.hasInner ? "()" : "")).withCaretPosAfterCompletion(fullName.length() + (tag.hasInner ? 1 : 0)), CompletionType.CONSTRUCTOR)));
            }
        }
    }

    private @Nullable String getDocURLFor(Keyword keyword)
    {
        switch (keyword)
        {
            case IF:
            case THEN:
            case ELSE:
            case ENDIF:
                return "syntax-if.html";
            case MATCH:
            case CASE:
            case ORCASE:
            case GIVEN:
            case ENDMATCH:
                return "syntax-match.html";
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
        
        public LiteralOutcome(NestedLiteralSource source, Expression expression)
        {
            this.chunk = new ContentChunk(source.prefix + source.innerContent + (source.terminatedProperly ? "}" : ""), ChunkType.NESTED_START);
            this.expression = expression;
            this.removedChars = new RemovedCharacters();
            this.nestedErrors = ImmutableList.of();
            this.completions = ImmutableList.of();
            this.locationRecorder = null;
        }

        @SuppressWarnings("units")
        public LiteralOutcome(String prefix, String internalContent, StyledString displayContent, Expression expression, String suffix, RemovedCharacters removedChars, ImmutableList<CaretPos> caretPos, ImmutableList<ErrorDetails> errors, ImmutableList<AutoCompleteDetails<CodeCompletionContext>> completions,                             EditorLocationAndErrorRecorder locationRecorder)
        {
            ImmutableList.Builder<CaretPos> caretPosIncludingPrefixSuffix = ImmutableList.builder();
            CaretPos initialPos = new CaretPos(0, 0);
            caretPosIncludingPrefixSuffix.add(initialPos);
            for (CaretPos p : caretPos)
            {
                caretPosIncludingPrefixSuffix.add(new CaretPos(p.positionInternal + prefix.length(), p.positionDisplay + prefix.length()));
            }
            caretPosIncludingPrefixSuffix.add(new CaretPos(prefix.length() + internalContent.length() + suffix.length(), prefix.length() + displayContent.getLength() + suffix.length()));
            this.chunk = new ContentChunk(
                prefix + internalContent + suffix,
                StyledString.concat(StyledString.s(prefix), displayContent, StyledString.s(suffix)),
                caretPosIncludingPrefixSuffix.build(), ChunkType.NESTED_START);
            this.expression = expression;
            this.removedChars = removedChars;
            this.nestedErrors = errors;
            this.completions = completions;
            this.locationRecorder = locationRecorder;
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
                TypeLexer typeLexer = new TypeLexer(typeManager, true, false);
                LexerResult<TypeExpression, CodeCompletionContext> processed = typeLexer.process(c.innerContent, 0);
                return new LiteralOutcome(c.prefix, processed.adjustedContent, processed.display, new TypeLiteralExpression(processed.result), c.terminatedProperly ? "}" : "", processed.removedChars, processed.caretPositions, processed.errors, processed.autoCompleteDetails, processed.locationRecorder);
            }),
            new Pair<>("{", c -> {
                UnitLexer unitLexer = new UnitLexer(typeManager.getUnitManager(), false);
                LexerResult<UnitExpression, CodeCompletionContext> processed = unitLexer.process(c.innerContent, 0);
                return new LiteralOutcome(c.prefix, processed.adjustedContent, processed.display, new UnitLiteralExpression(processed.result), c.terminatedProperly ? "}" : "", processed.removedChars, processed.caretPositions, processed.errors, processed.autoCompleteDetails, processed.locationRecorder);
            })
        );
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
        COLUMN, VARIABLE, FUNCTION, CONSTRUCTOR, NESTED_LITERAL, KEYWORD_CHAIN, KEYWORD; 
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
    
    private static ImmutableList<LexCompletionGroup> sort(ImmutableList<Pair<CompletionStatus, ExpressionCompletion>> completions)
    {
        ImmutableList.Builder<LexCompletionGroup> groups = ImmutableList.builder();

        ImmutableList<LexCompletion> direct = sort(completions.stream().filter(p -> p.getFirst().equals(CompletionStatus.DIRECT)).map(p -> p.getSecond()));
        if (!direct.isEmpty())
        {
            groups.add(new LexCompletionGroup(direct));
        }
        ImmutableList<LexCompletion> related = sort(completions.stream().filter(p -> p.getFirst().equals(CompletionStatus.RELATED)).map(p -> p.getSecond()));
        if (!related.isEmpty())
        {
            groups.add(new LexCompletionGroup(related, StyledString.s("Related"), 2));
        }
        
        return groups.build();
    }
    private static ImmutableList<LexCompletion> sort(Stream<ExpressionCompletion> completions)
    {
        return completions.sorted(Comparator.<ExpressionCompletion, Integer>comparing(c -> c.completionType.ordinal()).thenComparing((c1, c2) -> c1.completion.content.compareToIgnoreCase(c2.completion.content))).map(c -> c.completion).collect(ImmutableList.<LexCompletion>toImmutableList());
    }
    
    private static enum WordPosition
    {
        FIRST_WORD,
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
    private static Optional<Pair<WordPosition, LexCompletion>> matchWordStart(@Nullable Pair<String, Integer> src, @CanonicalLocation int startPos, String completionText, WordPosition... possiblePositions)
    {
        if (src == null)
            return Optional.of(new Pair<>(WordPosition.FIRST_WORD, new LexCompletion(startPos, 0, completionText)));

        boolean firstWord = Arrays.asList(possiblePositions).contains(WordPosition.FIRST_WORD);
        boolean laterWord = Arrays.asList(possiblePositions).contains(WordPosition.LATER_WORD);
        int curCompletionStart = 0;
        do
        {
            int len = Utility.longestCommonStartIgnoringCase(completionText, curCompletionStart, src.getFirst(), src.getSecond());
            if (len > 0 || (firstWord && len == 0 && curCompletionStart == 0))
            {
                return Optional.of(new Pair<>(curCompletionStart == 0 ? WordPosition.FIRST_WORD : WordPosition.LATER_WORD, new LexCompletion(startPos, len, completionText)));
            }
            curCompletionStart = completionText.indexOf(' ', curCompletionStart);
            // If not -1, it's one char past the space, otherwise leave as -1:
            if (curCompletionStart >= 0)
                curCompletionStart += 1;
        }
        while (laterWord && curCompletionStart >= 0);

        return Optional.empty();
    }

    // Helper for above that uses zero as the src start position
    private static Optional<Pair<WordPosition, LexCompletion>> matchWordStart(@Nullable String src, @CanonicalLocation int startPos, String completionText, WordPosition... possiblePositions)
    {
        return matchWordStart(src == null ? null : new Pair<>(src, 0), startPos, completionText, possiblePositions);
    }
}
