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
import annotation.recorded.qual.Recorded;
import annotation.units.CanonicalLocation;
import annotation.units.DisplayLocation;
import annotation.units.RawInputLocation;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import xyz.columnal.log.Log;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CodePointCharStream;
import org.antlr.v4.runtime.Token;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.id.TableAndColumnRenames;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.DataType.DateTimeInfo;
import xyz.columnal.data.datatype.DataType.DateTimeInfo.DateTimeType;
import xyz.columnal.data.datatype.TaggedTypeDefinition;
import xyz.columnal.data.datatype.TypeId;
import xyz.columnal.data.datatype.TypeManager;
import xyz.columnal.error.ExceptionWithStyle;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.grammar.FormatLexer;
import xyz.columnal.gui.lexeditor.TopLevelEditor.DisplayType;
import xyz.columnal.transformations.expression.CanonicalSpan;
import xyz.columnal.gui.lexeditor.EditorLocationAndErrorRecorder.ErrorDetails;
import xyz.columnal.gui.lexeditor.completion.InsertListener;
import xyz.columnal.gui.lexeditor.completion.LexCompletion;
import xyz.columnal.gui.lexeditor.Lexer.LexerResult.CaretPos;
import xyz.columnal.gui.lexeditor.completion.LexCompletionGroup;
import xyz.columnal.jellytype.JellyType;
import xyz.columnal.jellytype.JellyType.UnknownTypeException;
import xyz.columnal.transformations.expression.Expression.SaveDestination;
import xyz.columnal.transformations.expression.UnitExpression;
import xyz.columnal.transformations.expression.type.IdentTypeExpression;
import xyz.columnal.transformations.expression.type.InvalidIdentTypeExpression;
import xyz.columnal.transformations.expression.type.NumberTypeExpression;
import xyz.columnal.transformations.expression.type.TypeExpression;
import xyz.columnal.transformations.expression.type.TypeExpression.JellyRecorder;
import xyz.columnal.transformations.expression.type.TypeExpression.UnJellyableTypeExpression;
import xyz.columnal.transformations.expression.type.TypePrimitiveLiteral;
import xyz.columnal.transformations.expression.type.UnitLiteralTypeExpression;
import xyz.columnal.styled.CommonStyles;
import xyz.columnal.styled.StyledCSS;
import xyz.columnal.styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.Utility;
import xyz.columnal.utility.Utility.DescriptiveErrorListener;
import xyz.columnal.utility.TranslationUtility;

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

        public boolean isClosing()
        {
            return this == CLOSE_ROUND || this == CLOSE_SQUARE;
        }
    }

    public static enum Operator implements ExpressionToken
    {
        COMMA(","), COLON(":");

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
    public LexerResult<TypeExpression, CodeCompletionContext> process(String content, @Nullable Integer curCaretPos, InsertListener insertListener)
    {
        TypeSaver saver = new TypeSaver(typeManager, insertListener);
        boolean prevWasIdent = false;
        @RawInputLocation int curIndex = RawInputLocation.ZERO;
        ArrayList<ContentChunk> chunks = new ArrayList<>();
        ImmutableList.Builder<AutoCompleteDetails<CodeCompletionContext>> nestedCompletions = ImmutableList.builder();
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
                    saver.saveKeyword(bracket, removedCharacters.map(curIndex, bracket.getContent()));
                    curIndex += rawLength(bracket.getContent());
                    chunks.add(new ContentChunk(bracket.getContent(), bracket.isClosing() ? ChunkType.CLOSING : ChunkType.OPENING));
                    continue nextToken;
                }
            }
            for (Operator op : Operator.values())
            {
                if (content.startsWith(op.getContent(), curIndex))
                {
                    saver.saveOperator(op, removedCharacters.map(curIndex, op.getContent()));
                    curIndex += rawLength(op.getContent());
                    chunks.add(new ContentChunk(op.getContent(), StyledString.s(op.getContent() + " "), ChunkType.OPENING));
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
                    UnitLexer unitLexer = new UnitLexer( typeManager, false);
                    LexerResult<UnitExpression, CodeCompletionContext> lexerResult = unitLexer.process(content.substring(curIndex + 1, end), 0, insertListener);
                    saver.saveOperand(new UnitLiteralTypeExpression(lexerResult.result), removedCharacters.map(curIndex, end + RawInputLocation.ONE));
                    chunks.add(new ContentChunk("{", ChunkType.NESTED_START));
                    @SuppressWarnings("units")
                    @DisplayLocation int displayOffset = chunks.stream().mapToInt(c -> c.displayContent.getLength()).sum();
                    @CanonicalLocation int caretPosOffset = removedCharacters.map(curIndex + RawInputLocation.ONE);
                    saver.addNestedLocations(lexerResult.locationRecorder, caretPosOffset);
                    saver.addNestedErrors(lexerResult.errors, caretPosOffset, displayOffset);
                    removedCharacters.orShift(lexerResult.removedChars, curIndex + lexerResult.adjustedContent.length());
                    chunks.add(new ContentChunk(lexerResult.adjustedContent, lexerResult.display, ChunkType.NESTED));
                    chunks.add(new ContentChunk("}", ChunkType.NESTED));
                    nestedCompletions.addAll(Utility.mapListI(lexerResult.autoCompleteDetails, acd -> offsetBy(acd, caretPosOffset)));
                    curIndex = end + RawInputLocation.ONE;
                    continue nextToken;
                }
                else
                {
                    saver.locationRecorder.addErrorAndFixes(removedCharacters.map(curIndex, content.substring(curIndex)), StyledString.s("Missing closing }"), ImmutableList.of());
                    UnitLexer unitLexer = new UnitLexer(typeManager, false);
                    LexerResult<UnitExpression, CodeCompletionContext> lexerResult = unitLexer.process(content.substring(curIndex + 1, content.length()), 0, insertListener);
                    saver.addNestedLocations(lexerResult.locationRecorder, removedCharacters.map(curIndex + RawInputLocation.ONE));
                    saver.saveOperand(new UnitLiteralTypeExpression(lexerResult.result), removedCharacters.map(curIndex, content));
                    chunks.add(new ContentChunk(content.substring(curIndex), ChunkType.NESTED));
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
                
                final @RawInputLocation int curIndexFinal = curIndex;
                parsed.getFirst().either_(dataType -> {
                    saver.saveOperand(dataType.equals(DataType.NUMBER) ? new NumberTypeExpression(null) : new TypePrimitiveLiteral(dataType), removedCharacters.map(curIndexFinal, parsed.getSecond()));
                    
                }, ident -> {
                    saver.saveOperand(new IdentTypeExpression(ident), removedCharacters.map(curIndexFinal, parsed.getSecond()));
                });
                curIndex = parsed.getSecond();
                chunks.add(new ContentChunk(match, ChunkType.IDENT));
                continue nextToken;
            }

            CanonicalSpan invalidCharLocation = removedCharacters.map(curIndex, curIndex + RawInputLocation.ONE);
            saver.saveOperand(new InvalidIdentTypeExpression(content.substring(curIndex, curIndex + 1)), invalidCharLocation);
            saver.locationRecorder.addErrorAndFixes(invalidCharLocation, StyledString.concat(TranslationUtility.getStyledString("error.illegalCharacter", Utility.codePointToString(content.charAt(curIndex))), StyledString.s("\n  "), StyledString.s("Character code: \\u" + Integer.toHexString(content.charAt(curIndex))).withStyle(new StyledCSS("errorable-sub-explanation"))), ImmutableList.of(new TextQuickFix("error.illegalCharacter.remove", invalidCharLocation, () -> new Pair<>("", StyledString.s("<remove>")))));
            chunks.add(new ContentChunk("" + content.charAt(curIndex), ChunkType.OPENING));
            
            curIndex += RawInputLocation.ONE;
        }
        @Recorded TypeExpression saved = saver.finish(removedCharacters.map(curIndex, curIndex));
        
        Pair<ArrayList<CaretPos>, ImmutableList<@CanonicalLocation Integer>> caretPositions = calculateCaretPos(chunks);
        StyledString built = chunks.stream().map(c -> c.displayContent).collect(StyledString.joining(""));
        if (built.getLength() == 0)
            built = StyledString.s(" ");
        ImmutableList<ErrorDetails> errors = saver.getErrors();
        built = padZeroWidthErrors(built, caretPositions.getFirst(), errors);
        
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
                                String str = fixedExpression.save(SaveDestination.TO_EDITOR_FULL_NAME, new TableAndColumnRenames(ImmutableMap.of()));
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
        
        return new LexerResult<>(saved, chunks.stream().map(c -> c.internalContent).collect(Collectors.joining()), removedCharacters, false, ImmutableList.copyOf(caretPositions.getFirst()), ImmutableList.copyOf(caretPositions.getSecond()), built, errors, saver.locationRecorder, Utility.<AutoCompleteDetails<CodeCompletionContext>>concatI(Lexer.<CodeCompletionContext>makeCompletions(chunks, this::makeCompletions), nestedCompletions.build()), new BitSet(), !saver.hasUnmatchedBrackets(), (i, n) -> ImmutableMap.<DisplayType, Pair<StyledString, ImmutableList<TextQuickFix>>>of());
    }
    
    private CodeCompletionContext makeCompletions(String stem, @CanonicalLocation int canonIndex, ChunkType curType, ChunkType preceding)
    {
        return new CodeCompletionContext(curType != ChunkType.IDENT ? ImmutableList.of() : ImmutableList.of(new LexCompletionGroup(
            Stream.<LexCompletion>concat(
                Stream.<LexCompletion>concat(
                    streamConcreteDataTypes().<LexCompletion>map(t -> {
                        int len = Utility.longestCommonStartIgnoringCase(t.toString(), 0, stem, 0);
                        return typeCompletionConcrete(t, canonIndex, len);
                    }),
                    Stream.<LexCompletion>of(typeCompletionConcrete(DataType.NUMBER, canonIndex, Utility.longestCommonStartIgnoringCase("Number{}", 0, stem, 0)).withReplacement("Number{}", StyledString.concat(StyledString.s("Number{"), StyledString.styled("unit", CommonStyles.ITALIC), StyledString.s("}"))).withCaretPosAfterCompletion("Number{".length())))
                , typeManager.getKnownTaggedTypes().values().stream().filter(ttd -> !ttd.getTags().isEmpty() && !ttd.getTaggedTypeName().equals(new TypeId("Type")) && !ttd.getTaggedTypeName().equals(new TypeId("Unit"))).<LexCompletion>map(ttd -> {
                        int len = Utility.longestCommonStartIgnoringCase(ttd.getTaggedTypeName().getRaw(), 0, stem, 0);
                       return typeCompletionTagged(ttd, canonIndex, len).withCaretPosAfterCompletion(ttd.getTaggedTypeName().getRaw().length() + (ttd.getTypeArguments().isEmpty() ? 0 : 1));
                    })
            ).collect(ImmutableList.<LexCompletion>toImmutableList())
        , null, 2)));
    }

    protected LexCompletion typeCompletionConcrete(DataType dataType, @CanonicalLocation int start, int lengthToShowFor)
    {
        return new LexCompletion(start, lengthToShowFor, dataType.toString()).withFurtherDetailsURL("type-" + dataType.toString() + ".html");
    }
    
    protected LexCompletion typeCompletionTagged(TaggedTypeDefinition taggedTypeDefinition, @CanonicalLocation int start, int lengthToShowFor)
    {
        return new LexCompletion(start, lengthToShowFor, taggedTypeDefinition.getTaggedTypeName().getRaw() + Utility.replicate(taggedTypeDefinition.getTypeArguments().size(), "()").stream().collect(Collectors.joining()));
    }

    private static Stream<DataType> streamConcreteDataTypes()
    {
        return Stream.<DataType>concat(Stream.<DataType>of(DataType.NUMBER, DataType.TEXT, DataType.BOOLEAN), Arrays.stream(DateTimeType.values()).<DataType>map(t -> DataType.date(new DateTimeInfo(t))));
    }

    private AutoCompleteDetails<CodeCompletionContext> offsetBy(AutoCompleteDetails<CodeCompletionContext> acd, @CanonicalLocation int caretPosOffset)
    {
        return new AutoCompleteDetails<>(acd.location.offsetBy(caretPosOffset), new CodeCompletionContext(acd.codeCompletionContext, caretPosOffset));
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
