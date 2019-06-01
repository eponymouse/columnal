package utility;

import annotation.identifier.qual.ExpressionIdentifier;
import annotation.identifier.qual.UnitIdentifier;
import annotation.units.RawInputLocation;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CodePointCharStream;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.Token;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.grammar.ExpressionLexer;
import records.grammar.ExpressionParser.IdentContext;
import records.grammar.UnitLexer;
import records.grammar.UnitParser.SingleUnitContext;
import utility.Utility.DescriptiveErrorListener;

public class IdentifierUtility
{
    @SuppressWarnings("identifier")
    public static @Nullable @UnitIdentifier String asUnitIdentifier(String src)
    {
        if (Utility.lexesAs(src, UnitLexer::new, UnitLexer.IDENT))
            return src;
        else
            return null;
    }

    @SuppressWarnings("identifier")
    public static @Nullable @ExpressionIdentifier String asExpressionIdentifier(String src)
    {
        if (Utility.lexesAs(src, ExpressionLexer::new, ExpressionLexer.IDENT))
            return src;
        else
            return null;
    }

    @SuppressWarnings("identifier")
    public static @ExpressionIdentifier String fromParsed(records.grammar.ExpressionParser.IdentContext parsedIdent)
    {
        return parsedIdent.getText();
    }

    @SuppressWarnings("identifier")
    public static @ExpressionIdentifier String fromParsed(records.grammar.FormatParser.IdentContext parsedIdent)
    {
        return parsedIdent.getText();
    }

    @SuppressWarnings("identifier")
    public static @ExpressionIdentifier String fromParsed(records.grammar.FormatParser.ColumnNameContext parsedIdent)
    {
        return parsedIdent.getText();
    }

    @SuppressWarnings("identifier")
    public static @ExpressionIdentifier String fromParsed(records.grammar.DataParser.LabelContext parsedIdent)
    {
        return parsedIdent.UNQUOTED_IDENT().getText();
    }

    @SuppressWarnings("identifier")
    public static @UnitIdentifier String fromParsed(SingleUnitContext parsedIdent)
    {
        return parsedIdent.IDENT().getText();
    }

    @SuppressWarnings("identifier")
    public static @Nullable Pair<@ExpressionIdentifier String, @RawInputLocation Integer> consumeExpressionIdentifier(String content, @RawInputLocation int startFrom, @RawInputLocation int includeTrailingSpaceIfEndsAt)
    {
        CodePointCharStream inputStream = CharStreams.fromString(content.substring(startFrom));
        Lexer lexer = new ExpressionLexer(inputStream);
        DescriptiveErrorListener errorListener = new DescriptiveErrorListener();
        lexer.addErrorListener(errorListener);
        Token token = lexer.nextToken();
        // If there any errors, abort:
        if (!errorListener.errors.isEmpty())
            return null;
        else if (token.getType() == ExpressionLexer.IDENT)
        {
            @SuppressWarnings("units")
            @RawInputLocation int end = startFrom + token.getStopIndex() + 1;
            if (end + 1 == includeTrailingSpaceIfEndsAt && end < content.length() && content.charAt(end) == ' ')
                return new Pair<>(token.getText() + " ", end + RawInputLocation.ONE);
            else
                return new Pair<>(token.getText(), end);
        }
        else
            return null;
    }

    public static @Nullable Pair<String, @RawInputLocation Integer> consumePossiblyScopedExpressionIdentifier(String content, @RawInputLocation int startFrom, @RawInputLocation int includeTrailingSpaceIfEndsAt)
    {
        @Nullable Pair<@ExpressionIdentifier String, @RawInputLocation Integer> before = consumeExpressionIdentifier(content, startFrom, includeTrailingSpaceIfEndsAt);
        if (before != null)
        {
            if (before.getSecond() < content.length() && content.charAt(before.getSecond()) == ':')
            {
                @Nullable Pair<@ExpressionIdentifier String, @RawInputLocation Integer> after = consumeExpressionIdentifier(content, before.getSecond() + RawInputLocation.ONE, includeTrailingSpaceIfEndsAt);
                if (after != null)
                {
                    return new Pair<>(before.getFirst() + ":" + after.getFirst(), after.getSecond());
                }
            }
            return new Pair<>(before.getFirst(), before.getSecond());
        }
        return null;
    }

    @SuppressWarnings({"identifier", "units"})
    public static @Nullable Pair<@UnitIdentifier String, @RawInputLocation Integer> consumeUnitIdentifier(String content, int startFrom)
    {
        CodePointCharStream inputStream = CharStreams.fromString(content.substring(startFrom));
        Lexer lexer = new UnitLexer(inputStream);
        DescriptiveErrorListener errorListener = new DescriptiveErrorListener();
        lexer.addErrorListener(errorListener);
        Token token = lexer.nextToken();
        // If there any errors, abort:
        if (!errorListener.errors.isEmpty())
            return null;
        else if (token.getType() == UnitLexer.IDENT)
            return new Pair<>(token.getText(), startFrom + token.getStopIndex() + 1);
        else
            return null;
    }
    
    private static enum Last {START, SPACE, VALID}
    
    // Finds closest valid expression identifier
    @SuppressWarnings("identifier")
    public static @ExpressionIdentifier String fixExpressionIdentifier(String original, @ExpressionIdentifier String hint)
    {
        StringBuilder processed = new StringBuilder();
        Last last = Last.START;
        for (int c : original.codePoints().toArray())
        {
            if (last == Last.START && Character.isAlphabetic(c))
            {
                processed.appendCodePoint(c);
                last = Last.VALID;
            }
            else if (last != Last.START && (Character.isAlphabetic(c) || Character.getType(c) == Character.OTHER_LETTER || Character.isDigit(c)))
            {
                processed.appendCodePoint(c);
                last = Last.VALID;
            }
            else if (last != Last.SPACE && (c == '_' || Character.isWhitespace(c)))
            {
                processed.appendCodePoint(c == '_' ? c : ' ');
                last = Last.SPACE;
            }
            // Swap others to space:
            else if (last != Last.SPACE)
            {
                processed.appendCodePoint(' ');
                last = Last.SPACE;
            }
        }
        
        @ExpressionIdentifier String r = asExpressionIdentifier(processed.toString().trim());
        if (r != null)
            return r;
        else
            return hint;
    }
    
    public static @ExpressionIdentifier String identNum(@ExpressionIdentifier String stem, int number)
    {
        return fixExpressionIdentifier(stem + " " + number, stem);
    }
}
