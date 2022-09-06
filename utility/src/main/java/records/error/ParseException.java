package records.error;

import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.misc.ParseCancellationException;
import org.apache.commons.lang3.StringUtils;
import utility.Utility;

/**
 * Created by neil on 14/01/2017.
 */
public class ParseException extends UserException
{
    public ParseException(ParserRuleContext problemItem, String explanation)
    {
        super(formatLocation(problemItem) + " " + explanation + ": \"" + problemItem.getText() + "\"");
    }

    @SuppressWarnings("deprecation")
    public ParseException(String expectedItem, Parser p)
    {
        super("Expected " + expectedItem + " found: {" + p.getCurrentToken().getText() + "} at " + p.getCurrentToken().getLine() + ":" + p.getCurrentToken().getCharPositionInLine() + " " +  (p.getCurrentToken().getType() >= 0 ? p.getTokenNames()[p.getCurrentToken().getType()] : "UNKNOWN") + " " + p.getCurrentToken().getStartIndex());
    }

    @SuppressWarnings("deprecation")
    public ParseException(String input, Parser p, ParseCancellationException e)
    {
        super("Error while parsing on line " + p.getCurrentToken().getLine() + ":\n" + highlightPosition(input, p.getCurrentToken().getLine(), p.getCurrentToken().getCharPositionInLine()) + "\n" +
            "Found: {" + p.getCurrentToken().getText() + "} " + (p.getCurrentToken().getType() < 0 ? "EOF" : p.getTokenNames()[p.getCurrentToken().getType()]) + " [" + p.getCurrentToken().getType() + "] " + p.getCurrentToken().getStartIndex(), e);
    }

    @SuppressWarnings("deprecation")
    public ParseException(String input, Parser p, RecognitionException e)
    {
        super("Error while parsing on line " + e.getOffendingToken().getLine() + ":\n" + highlightPosition(input, e.getOffendingToken().getLine(), e.getOffendingToken().getCharPositionInLine()) + "\n" +
            "Found: {" + e.getOffendingToken().getText() + "} " + (e.getOffendingToken().getType() < 0 ? "EOF" : p.getTokenNames()[e.getOffendingToken().getType()]) + " [" + p.getCurrentToken().getType() + "] " + e.getOffendingToken().getStartIndex(), e);
    }

    private static String highlightPosition(String input, int line, int charPositionInLine)
    {
        // Line starts at 1, so rebase to zero:
        line -= 1;
        
        String[] lines = Utility.splitLines(input);
        if (line < lines.length && charPositionInLine <= lines[line].length())
        {
            // Position is valid.
            return lines[line] + "\n" + StringUtils.leftPad("", charPositionInLine) + "^-- Unexpected\n";
        }
        else
        {
            return "Internal error: parse error position invalid: " + line + ":" + charPositionInLine + " in {{{" + input + "}}}";
        }
    }

    private static String formatLocation(ParserRuleContext problemItem)
    {
        if (problemItem.getStart().getLine() == problemItem.getStop().getLine())
            return "Line " + problemItem.getStart().getLine() + ", Columns " + problemItem.getStart().getCharPositionInLine() + " to " + problemItem.getStop().getCharPositionInLine();
        else
            return "Line " + problemItem.getStart().getLine() + " Column" + problemItem.getStart().getCharPositionInLine() + " to line " + problemItem.getStop().getLine() + " Column " + problemItem.getStop().getCharPositionInLine();
    }
}
