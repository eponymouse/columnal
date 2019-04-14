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
    public static @UnitIdentifier String fromParsed(SingleUnitContext parsedIdent)
    {
        return parsedIdent.getText();
    }

    @SuppressWarnings("identifier")
    public static @Nullable Pair<@ExpressionIdentifier String, @RawInputLocation Integer> consumeExpressionIdentifier(String content, @RawInputLocation int startFrom)
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
            return new Pair<>(token.getText(), end);
        }
        else
            return null;
    }

    public static @Nullable Pair<String, @RawInputLocation Integer> consumePossiblyScopedExpressionIdentifier(String content, @RawInputLocation int startFrom)
    {
        @Nullable Pair<@ExpressionIdentifier String, @RawInputLocation Integer> before = consumeExpressionIdentifier(content, startFrom);
        if (before != null)
        {
            if (before.getSecond() < content.length() && content.charAt(before.getSecond()) == ':')
            {
                @Nullable Pair<@ExpressionIdentifier String, @RawInputLocation Integer> after = consumeExpressionIdentifier(content, before.getSecond() + RawInputLocation.ONE);
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
        Token token = lexer.nextToken();
        if (token.getType() == UnitLexer.IDENT)
            return new Pair<>(token.getText(), startFrom + token.getStopIndex() + 1);
        else
            return null;
    }
}
