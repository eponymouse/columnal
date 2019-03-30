package records.gui.lexeditor;

import annotation.identifier.qual.ExpressionIdentifier;
import annotation.qual.Value;
import annotation.recorded.qual.Recorded;
import annotation.units.SourceLocation;
import com.google.common.collect.ImmutableList;
import javafx.beans.value.ObservableObjectValue;
import log.Log;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.TableId;
import records.data.datatype.DataType.DateTimeInfo.DateTimeType;
import records.data.datatype.DataType.TagType;
import records.data.datatype.TaggedTypeDefinition;
import records.data.datatype.TypeManager;
import records.error.ExceptionWithStyle;
import records.error.InternalException;
import records.error.UserException;
import records.grammar.GrammarUtility;
import records.gui.expressioneditor.GeneralExpressionEntry.Keyword;
import records.gui.expressioneditor.GeneralExpressionEntry.Op;
import records.gui.lexeditor.EditorLocationAndErrorRecorder.Span;
import records.gui.lexeditor.LexAutoComplete.LexCompletion;
import records.jellytype.JellyType;
import records.transformations.expression.*;
import records.transformations.expression.ColumnReference.ColumnReferenceType;
import records.transformations.expression.Expression.ColumnLookup;
import records.transformations.expression.function.StandardFunctionDefinition;
import styled.StyledCSS;
import styled.StyledString;
import utility.IdentifierUtility;
import utility.Pair;
import utility.Utility;
import utility.Utility.TransparentBuilder;
import utility.gui.TranslationUtility;

import java.util.BitSet;
import java.util.Optional;
import java.util.function.Function;

public class ExpressionLexer implements Lexer<Expression, ExpressionCompletionContext>
{
    private final ObservableObjectValue<ColumnLookup> columnLookup;
    private final TypeManager typeManager;
    private ImmutableList<StandardFunctionDefinition> allFunctions;

    public ExpressionLexer(ObservableObjectValue<ColumnLookup> columnLookup, TypeManager typeManager,  ImmutableList<StandardFunctionDefinition> functions)
    {
        this.columnLookup = columnLookup;
        this.typeManager = typeManager;
        this.allFunctions = functions;
    }

    @SuppressWarnings("units")
    @Override
    public LexerResult<Expression, ExpressionCompletionContext> process(String content)
    {
        ImmutableList.Builder<AutoCompleteDetails<ExpressionCompletionContext>> completions = ImmutableList.builder();
        ExpressionSaver saver = new ExpressionSaver();
        @SourceLocation int curIndex = 0;
        BitSet missingSpots = new BitSet();
        StringBuilder s = new StringBuilder();
        boolean prevWasIdent = false;
        nextToken: while (curIndex < content.length())
        {
            // Skip any extra spaces at the start of tokens:
            if (content.startsWith(" ", curIndex))
            {
                // Keep single space after ident as it may continue ident:
                if (prevWasIdent)
                {
                    s.append(" ");
                }
                else
                {
                    missingSpots.set(curIndex);
                }
                prevWasIdent = false;
                curIndex += 1;
                continue nextToken;
            }
            prevWasIdent = false;
            
            for (Keyword keyword : Keyword.values())
            {
                if (content.startsWith(keyword.getContent(), curIndex))
                {
                    saver.saveKeyword(keyword, new Span(curIndex, curIndex + keyword.getContent().length()), c -> {});
                    s.append(keyword.getContent());
                    curIndex += keyword.getContent().length();
                    continue nextToken;
                }
            }
            for (Op op : Op.values())
            {
                if (content.startsWith(op.getContent(), curIndex))
                {
                    saver.saveOperator(op, new Span(curIndex, curIndex + op.getContent().length()), c -> {});
                    s.append(op.getContent());
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
                    s.append(content.substring(curIndex, endQuote + 1));
                    curIndex = endQuote + 1;
                    continue nextToken;
                }
                else
                {
                    // Unterminated string:
                    saver.locationRecorder.addErrorAndFixes(new Span(curIndex, content.length()), StyledString.s("Missing closing quote around text"), ImmutableList.of());
                    saver.saveOperand(new StringLiteral(GrammarUtility.processEscapes(content.substring(curIndex + 1, content.length()), false)), new Span(curIndex, content.length()), c -> {});
                    s.append(content.substring(curIndex, content.length()));
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
                    s.append(content.substring(numberStart, curIndex));
                    continue nextToken;
                }
            }

            for (Pair<String, Function<String, Expression>> nestedLiteral : getNestedLiterals())
            {
                @Nullable Pair<String, Integer> nestedOutcome = tryNestedLiteral(nestedLiteral.getFirst(), content, curIndex, saver.locationRecorder);
                if (nestedOutcome != null)
                {
                    saver.saveOperand(nestedLiteral.getSecond().apply(nestedOutcome.getFirst()), new Span(curIndex, nestedOutcome.getSecond()), c -> {});
                    s.append(content.substring(curIndex, nestedOutcome.getSecond()));
                    curIndex = nestedOutcome.getSecond();
                    continue nextToken;
                }
            }
            
            @Nullable Pair<@ExpressionIdentifier String, Integer> parsed = IdentifierUtility.consumeExpressionIdentifier(content, curIndex);
            if (parsed != null && parsed.getSecond() > curIndex)
            {
                prevWasIdent = true;
                @ExpressionIdentifier String text = parsed.getFirst();
                Span location = new Span(curIndex, parsed.getSecond());
                
                ImmutableList.Builder<LexCompletion> identCompletions = ImmutableList.builder();
                
                // Add completions even if one is already spotted:
                for (StandardFunctionDefinition function : allFunctions)
                {
                    identCompletions.add(new LexCompletion(function.getName() + "()"));
                }
                
                completions.add(new AutoCompleteDetails<>(location, new ExpressionCompletionContext(identCompletions.build())));
                
                /*
                if (text.startsWith("_"))
                {
                    if (text.equals("_"))
                        saver.saveOperand(new MatchAnythingExpression(), this, this, this::afterSave);
                    else
                    {
                        @SuppressWarnings("identifier")
                        @ExpressionIdentifier String minusLeadingUnderscore = text.substring(1);
                        saver.saveOperand(new VarDeclExpression(minusLeadingUnderscore), this, this, this::afterSave);
                    }
                }
                else*/
                if (text.equals("true") || text.equals("false"))
                {
                    saver.saveOperand(new BooleanLiteral(text.equals("true")), location, c -> {});
                }
                else
                {
                    boolean wasColumn = false;
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
                                saver.saveOperand(new IdentExpression(parsed.getFirst()), location, c -> {
                                });
                        }
                    }
                }
                s.append(content.substring(curIndex, parsed.getSecond()));
                curIndex = parsed.getSecond();
                continue nextToken;
            }
            
            Span invalidCharLocation = new Span(curIndex, curIndex + 1);
            saver.saveOperand(new InvalidIdentExpression(content.substring(curIndex, curIndex + 1)), invalidCharLocation, c -> {});
            saver.locationRecorder.addErrorAndFixes(invalidCharLocation, StyledString.concat(TranslationUtility.getStyledString("error.illegalCharacter.start", Utility.codePointToString(content.charAt(curIndex))), StyledString.s("\n  "), StyledString.s("Character code: \\u" + Integer.toHexString(content.charAt(curIndex))).withStyle(new StyledCSS("errorable-sub-explanation"))), ImmutableList.of(new TextQuickFix("error.illegalCharacter.remove", invalidCharLocation, () -> new Pair<>("", StyledString.s("<remove>")))));
            s.append(content.charAt(curIndex));
            curIndex += 1;
        }
        Expression saved = saver.finish(new Span(curIndex, curIndex));
        try
        {
            saved.checkExpression(columnLookup.get(), new TypeState(typeManager.getUnitManager(), typeManager), saver.locationRecorder.getRecorder());
        }
        catch (InternalException | UserException e)
        {
            if (e instanceof InternalException)
                Log.log(e);
            saver.locationRecorder.addErrorAndFixes(new Span(0, curIndex), ((ExceptionWithStyle) e).getStyledMessage(), ImmutableList.of());
        }
        
        return new LexerResult<>(saved, s.toString(), i -> {
            return i - missingSpots.get(0, i).cardinality();
        }, saver.getErrors(), completions.build());
    }

    private ImmutableList<Pair<String, Function<String, Expression>>> getNestedLiterals()
    {
        return ImmutableList.of(
            new Pair<>("date{", c -> new TemporalLiteral(DateTimeType.YEARMONTHDAY, c)),
            new Pair<>("datetime{", c -> new TemporalLiteral(DateTimeType.DATETIME, c)),
            new Pair<>("datetimezoned{", c -> new TemporalLiteral(DateTimeType.DATETIMEZONED, c)),
            new Pair<>("dateym{", c -> new TemporalLiteral(DateTimeType.YEARMONTH, c)),
            new Pair<>("time{", c -> new TemporalLiteral(DateTimeType.TIMEOFDAY, c)),
            new Pair<>("type{", c -> {
                TypeLexer typeLexer = new TypeLexer();
                // TODO also save positions, content, etc
                return new TypeLiteralExpression(typeLexer.process(c).result);
            }),
            new Pair<>("{", c -> {
                UnitLexer unitLexer = new UnitLexer();
                // TODO also save positions, content, etc
                return new UnitLiteralExpression(unitLexer.process(c).result);
            })
        );
    }
    
    @SuppressWarnings("units")
    private @Nullable Pair<String, Integer> tryNestedLiteral(String prefixInclCurly, String content, @SourceLocation int curIndex, EditorLocationAndErrorRecorder locationRecorder)
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
                return new Pair<>(content.substring(startIndex, curIndex - 1), curIndex);
            else
            {
                locationRecorder.addErrorAndFixes(new Span(startIndex, content.length()), StyledString.s("Started " + prefixInclCurly + " but no matching closing }"), ImmutableList.of());
                return new Pair<>(content.substring(startIndex), content.length());
            }        
        }
        return null;
    }    
}
