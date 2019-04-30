package records.gui.lexeditor;

import annotation.identifier.qual.ExpressionIdentifier;
import annotation.recorded.qual.Recorded;
import annotation.units.CanonicalLocation;
import annotation.units.DisplayLocation;
import annotation.units.RawInputLocation;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import log.Log;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CodePointCharStream;
import org.antlr.v4.runtime.Token;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.TableAndColumnRenames;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataType.DateTimeInfo.DateTimeType;
import records.data.datatype.TypeManager;
import records.error.ExceptionWithStyle;
import records.error.InternalException;
import records.error.UserException;
import records.grammar.FormatLexer;
import records.gui.lexeditor.EditorLocationAndErrorRecorder.CanonicalSpan;
import records.gui.lexeditor.EditorLocationAndErrorRecorder.ErrorDetails;
import records.gui.lexeditor.completion.LexCompletion;
import records.gui.lexeditor.Lexer.LexerResult.CaretPos;
import records.gui.lexeditor.completion.LexCompletionGroup;
import records.jellytype.JellyType;
import records.jellytype.JellyType.UnknownTypeException;
import records.transformations.expression.UnitExpression;
import records.transformations.expression.type.IdentTypeExpression;
import records.transformations.expression.type.InvalidIdentTypeExpression;
import records.transformations.expression.type.NumberTypeExpression;
import records.transformations.expression.type.TypeExpression;
import records.transformations.expression.type.TypeExpression.JellyRecorder;
import records.transformations.expression.type.TypeExpression.UnJellyableTypeExpression;
import records.transformations.expression.type.TypePrimitiveLiteral;
import records.transformations.expression.type.UnitLiteralTypeExpression;
import styled.StyledCSS;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.Pair;
import utility.Utility;
import utility.Utility.DescriptiveErrorListener;
import utility.gui.TranslationUtility;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.IdentityHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TypeLexer extends Lexer<TypeExpression, CodeCompletionContext>
{
    public static enum Keyword implements ExpressionToken
    {
        OPEN_ROUND("("), CLOSE_ROUND(")"), OPEN_SQUARE("["), CLOSE_SQUARE("]");

        private final String keyword;

        private Keyword(String keyword)
        {
            this.keyword = keyword;
        }

        @Override
        @OnThread(Tag.Any)
        public String getContent()
        {
            return keyword;
        }
    }

    public static enum Operator implements ExpressionToken
    {
        COMMA(",");

        private final String op;

        private Operator(String op)
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

    private final TypeManager typeManager;
    // Should we turn to DataType as part of save?
    private final boolean requireConcrete;
    // Is it fine to be empty?  i.e. hide errors when empty
    private final boolean emptyAllowed;

    public TypeLexer(TypeManager typeManager, boolean requireConcrete, boolean emptyAllowed)
    {
        this.typeManager = typeManager;
        this.requireConcrete = requireConcrete;
        this.emptyAllowed = emptyAllowed;
    }

    @Override
    public LexerResult<TypeExpression, CodeCompletionContext> process(String content, int curCaretPos)
    {
        TypeSaver saver = new TypeSaver();
        boolean prevWasIdent = false;
        @RawInputLocation int curIndex = RawInputLocation.ZERO;
        ImmutableList.Builder<AutoCompleteDetails<CodeCompletionContext>> autoCompletes = ImmutableList.builder();
        ArrayList<ContentChunk> chunks = new ArrayList<>();
        RemovedCharacters removedCharacters = new RemovedCharacters();
        nextToken: while (curIndex < content.length())
        {
            // Skip any extra spaces at the start of tokens:
            if (content.startsWith(" ", curIndex))
            {
                // Keep single space after ident as it may continue ident:
                if (prevWasIdent)
                {
                    chunks.add(new ContentChunk(" ", ChunkType.IDENT));
                }
                else
                {
                    removedCharacters.set(curIndex);
                }
                prevWasIdent = false;
                curIndex += RawInputLocation.ONE;
                continue nextToken;
            }
            prevWasIdent = false;
            
            for (Keyword bracket : Keyword.values())
            {
                if (content.startsWith(bracket.getContent(), curIndex))
                {
                    saver.saveKeyword(bracket, removedCharacters.map(curIndex, bracket.getContent()), c -> {});
                    curIndex += rawLength(bracket.getContent());
                    chunks.add(new ContentChunk(bracket.getContent(), ChunkType.NON_IDENT));
                    continue nextToken;
                }
            }
            for (Operator op : Operator.values())
            {
                if (content.startsWith(op.getContent(), curIndex))
                {
                    saver.saveOperator(op, removedCharacters.map(curIndex, op.getContent()), c -> {});
                    curIndex += rawLength(op.getContent());
                    chunks.add(new ContentChunk(op.getContent(), StyledString.s(op.getContent() + " "), ChunkType.NON_IDENT));
                    continue nextToken;
                }
            }
            
            if (content.charAt(curIndex) == '{')
            {
                @SuppressWarnings("units")
                @RawInputLocation int end = content.indexOf('}', curIndex + 1);
                if (end != -1)
                {
                    // We don't require concrete as we do that bit so don't want do it twice:
                    UnitLexer unitLexer = new UnitLexer( typeManager.getUnitManager(), false);
                    LexerResult<UnitExpression, CodeCompletionContext> lexerResult = unitLexer.process(content.substring(curIndex + 1, end), 0);
                    saver.saveOperand(new UnitLiteralTypeExpression(lexerResult.result), removedCharacters.map(curIndex, end + RawInputLocation.ONE), c -> {});
                    chunks.add(new ContentChunk("{", ChunkType.NESTED_START));
                    @SuppressWarnings("units")
                    @DisplayLocation int displayOffset = chunks.stream().mapToInt(c -> c.displayContent.getLength()).sum();
                    @CanonicalLocation int caretPosOffset = removedCharacters.map(curIndex + RawInputLocation.ONE);
                    saver.addNestedLocations(lexerResult.locationRecorder, caretPosOffset);
                    saver.addNestedErrors(lexerResult.errors, caretPosOffset, displayOffset);
                    removedCharacters.orShift(lexerResult.removedChars, curIndex + lexerResult.adjustedContent.length());
                    chunks.add(new ContentChunk(lexerResult.adjustedContent, lexerResult.display, ChunkType.NESTED));
                    chunks.add(new ContentChunk("}", ChunkType.NESTED));
                    curIndex = end + RawInputLocation.ONE;
                    continue nextToken;
                }
                else
                {
                    saver.locationRecorder.addErrorAndFixes(removedCharacters.map(curIndex, content.substring(curIndex)), StyledString.s("Missing closing }"), ImmutableList.of());
                    UnitLexer unitLexer = new UnitLexer(typeManager.getUnitManager(), false);
                    LexerResult<UnitExpression, CodeCompletionContext> lexerResult = unitLexer.process(content.substring(curIndex + 1, content.length()), 0);
                    saver.addNestedLocations(lexerResult.locationRecorder, removedCharacters.map(curIndex + RawInputLocation.ONE));
                    saver.saveOperand(new UnitLiteralTypeExpression(lexerResult.result), removedCharacters.map(curIndex, content), c -> {});
                    chunks.add(new ContentChunk(content.substring(curIndex), ChunkType.NON_IDENT));
                    curIndex = rawLength(content);
                }
                continue nextToken;
            }

            @Nullable Pair<Either<DataType, @ExpressionIdentifier String>, @RawInputLocation Integer> parsed = consumeTypeIdentifier(content, curIndex);
            if (parsed != null && parsed.getSecond() > curIndex)
            {
                prevWasIdent = true;
                
                @CanonicalLocation int startOfType = removedCharacters.map(curIndex);
                String match = parsed.getFirst().either(dt -> dt.toString(), s -> s);
                
                // Add any relevant autocompletes:
                for (DataType dataType : Utility.<DataType>iterableStream(streamDataTypes()))
                {
                    @SuppressWarnings("units")
                    @CanonicalLocation int common = Utility.longestCommonStart(match, 0, dataType.toString(), 0);
                    if (common > 0)
                    {
                        autoCompletes.add(new AutoCompleteDetails<>(new CanonicalSpan(startOfType, startOfType + common), (@CanonicalLocation int caretPos) -> ImmutableList.of(new LexCompletionGroup(ImmutableList.of(typeCompletion(dataType, startOfType))))));
                    }
                }
                
                final @RawInputLocation int curIndexFinal = curIndex;
                parsed.getFirst().either_(dataType -> {
                    saver.saveOperand(dataType.equals(DataType.NUMBER) ? new NumberTypeExpression(null) : new TypePrimitiveLiteral(dataType), removedCharacters.map(curIndexFinal, parsed.getSecond()), c -> {});
                    
                }, ident -> {
                    saver.saveOperand(new IdentTypeExpression(ident), removedCharacters.map(curIndexFinal, parsed.getSecond()), c -> {
                    });
                });
                curIndex = parsed.getSecond();
                chunks.add(new ContentChunk(match, ChunkType.IDENT));
                continue nextToken;
            }

            CanonicalSpan invalidCharLocation = removedCharacters.map(curIndex, curIndex + RawInputLocation.ONE);
            saver.saveOperand(new InvalidIdentTypeExpression(content.substring(curIndex, curIndex + 1)), invalidCharLocation, c -> {});
            saver.locationRecorder.addErrorAndFixes(invalidCharLocation, StyledString.concat(TranslationUtility.getStyledString("error.illegalCharacter", Utility.codePointToString(content.charAt(curIndex))), StyledString.s("\n  "), StyledString.s("Character code: \\u" + Integer.toHexString(content.charAt(curIndex))).withStyle(new StyledCSS("errorable-sub-explanation"))), ImmutableList.of(new TextQuickFix("error.illegalCharacter.remove", invalidCharLocation, () -> new Pair<>("", StyledString.s("<remove>")))));
            chunks.add(new ContentChunk("" + content.charAt(curIndex), ChunkType.NON_IDENT));
            
            curIndex += RawInputLocation.ONE;
        }
        @Recorded TypeExpression saved = saver.finish(removedCharacters.map(curIndex, curIndex));
        
        // We still add auto-complete even when empty:
        if (content.isEmpty())
        {
            ImmutableList.Builder<LexCompletion> emptyCompletions = ImmutableList.builder();
            for (DataType dataType : Utility.<DataType>iterableStream(streamDataTypes()))
            {
                emptyCompletions.add(typeCompletion(dataType, CanonicalLocation.ZERO));
            }
            ImmutableList<LexCompletion> built = emptyCompletions.build();
            autoCompletes.add(new AutoCompleteDetails<>(new CanonicalSpan(CanonicalLocation.ZERO, CanonicalLocation.ZERO), (caretPos) -> ImmutableList.of(new LexCompletionGroup(built))));
        }
        
        ArrayList<CaretPos> caretPositions = calculateCaretPos(chunks);
        StyledString built = chunks.stream().map(c -> c.displayContent).collect(StyledString.joining(""));
        if (built.getLength() == 0)
            built = StyledString.s(" ");
        ImmutableList<ErrorDetails> errors = saver.getErrors();
        built = padZeroWidthErrors(built, caretPositions, errors);
        
        if (errors.isEmpty() && requireConcrete)
        {
            class LocalJellyRecorder implements JellyRecorder
            {
                private final IdentityHashMap<@Recorded JellyType, CanonicalSpan> jellyTypeLocations = new IdentityHashMap<>();
                
                @SuppressWarnings("recorded")
                @Override
                @OnThread(value = Tag.FXPlatform, ignoreParent = true)
                public @Recorded JellyType record(JellyType jellyType, @Recorded TypeExpression source)
                {
                    jellyTypeLocations.put(jellyType, saver.locationRecorder.recorderFor(source));
                    return jellyType;
                }
                
                @SuppressWarnings("nullness")
                public CanonicalSpan locationFor(@Recorded JellyType jellyType)
                {
                    return jellyTypeLocations.get(jellyType);
                }
            }
            LocalJellyRecorder jellyRecorder = new LocalJellyRecorder();
            
            try
            {
                // If toJellyType throws, there will usually have been an error
                // in the lexing stage, except for errors such as
                // units in the wrong place.
                saved.toJellyType(typeManager, jellyRecorder).makeDataType(ImmutableMap.of(), typeManager);
            }
            catch (InternalException | UserException e)
            {
                if (e instanceof InternalException)
                    Log.log(e);
                final CanonicalSpan location;
                final ImmutableList<TextQuickFix> fixes;
                if (e instanceof UnJellyableTypeExpression)
                {
                    UnJellyableTypeExpression unjelly = (UnJellyableTypeExpression) e;
                    location = unjelly.getSource().either(u -> saver.locationRecorder.recorderFor(u), t -> saver.locationRecorder.recorderFor(t));
                    fixes = Utility.mapListI(unjelly.getFixes(), fixed -> new TextQuickFix(saver.locationRecorder.recorderFor(fixed.getReplacementTarget()), u -> u.toStyledString().toPlain(), fixed));
                }
                else
                {
                    if (e instanceof UnknownTypeException)
                    {
                        UnknownTypeException ute = (UnknownTypeException) e;
                        location = jellyRecorder.locationFor(ute.getReplacementTarget());
                        fixes = Utility.mapListI(ute.getSuggestedFixes(), fixed -> new TextQuickFix(StyledString.s("Correct"), ImmutableList.of(), location, () -> {
                            try
                            {
                                TypeExpression fixedExpression = TypeExpression.fromJellyType(fixed, typeManager);
                                String str = fixedExpression.save(false, new TableAndColumnRenames(ImmutableMap.of()));
                                return new Pair<>(str, fixedExpression.toStyledString());
                            }
                            catch (UserException ex)
                            {
                                // We shouldn't have suggested something which can't be loaded!
                                throw new InternalException("Invalid suggested fix: " + fixed, ex);
                            }
                        }));
                    }
                    else
                    {
                        fixes = ImmutableList.of();
                        location = new CanonicalSpan(CanonicalLocation.ZERO, removedCharacters.map(curIndex));
                    }
                }
                errors = Utility.appendToList(errors, new ErrorDetails(location, ((ExceptionWithStyle) e).getStyledMessage(),fixes));
            }
        }
        
        if (saved.isEmpty() && emptyAllowed)
            errors = ImmutableList.of();
        
        return new LexerResult<>(saved, chunks.stream().map(c -> c.internalContent).collect(Collectors.joining()), removedCharacters, false, ImmutableList.copyOf(caretPositions), built, errors, saver.locationRecorder, autoCompletes.build(), new BitSet(), !saver.hasUnmatchedBrackets());
    }

    protected LexCompletion typeCompletion(DataType dataType, @CanonicalLocation int start)
    {
        return new LexCompletion(start, dataType.toString().length(), dataType.toString()).withFurtherDetailsURL("type-" + dataType.toString() + ".html");
    }

    private Stream<DataType> streamDataTypes()
    {
        return Stream.<DataType>concat(Stream.<DataType>of(DataType.NUMBER, DataType.TEXT, DataType.BOOLEAN), Arrays.stream(DateTimeType.values()).<DataType>map(t -> DataType.date(new DateTimeInfo(t))));
    }

    @SuppressWarnings({"identifier", "units"})
    public static @Nullable Pair<Either<DataType, @ExpressionIdentifier String>, @RawInputLocation Integer> consumeTypeIdentifier(String content, int startFrom)
    {
        CodePointCharStream inputStream = CharStreams.fromString(content.substring(startFrom));
        org.antlr.v4.runtime.Lexer lexer = new FormatLexer(inputStream);
        DescriptiveErrorListener errorListener = new DescriptiveErrorListener();
        lexer.addErrorListener(errorListener);
        Token token = lexer.nextToken();
        if (!errorListener.errors.isEmpty())
            return null;
        final Either<DataType, @ExpressionIdentifier String> r;
        switch (token.getType())
        {
            case FormatLexer.BOOLEAN:
                r = Either.left(DataType.BOOLEAN);
                break;
            case FormatLexer.DATETIME:
                r = Either.left(DataType.date(new DateTimeInfo(DateTimeType.DATETIME)));
                break;
            case FormatLexer.DATETIMEZONED:
                r = Either.left(DataType.date(new DateTimeInfo(DateTimeType.DATETIMEZONED)));
                break;
            case FormatLexer.NUMBER:
                r = Either.left(DataType.NUMBER);
                break;
            case FormatLexer.TEXT:
                r = Either.left(DataType.TEXT);
                break;
            case FormatLexer.TIMEOFDAY:
                r = Either.left(DataType.date(new DateTimeInfo(DateTimeType.TIMEOFDAY)));
                break;
            case FormatLexer.YEARMONTH:
                r = Either.left(DataType.date(new DateTimeInfo(DateTimeType.YEARMONTH)));
                break;
            case FormatLexer.YEARMONTHDAY:
                r = Either.left(DataType.date(new DateTimeInfo(DateTimeType.YEARMONTHDAY)));
                break;
            case FormatLexer.UNQUOTED_NAME:
                r = Either.right(token.getText());
                break;
            default:
                return null;
        }
        return new Pair<>(r, startFrom + token.getStopIndex() + 1);
    }
}
