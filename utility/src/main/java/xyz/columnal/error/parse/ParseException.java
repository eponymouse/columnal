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

package xyz.columnal.error.parse;

import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.misc.ParseCancellationException;
import org.apache.commons.lang3.StringUtils;
import xyz.columnal.error.UserException;
import xyz.columnal.utility.Utility;

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
