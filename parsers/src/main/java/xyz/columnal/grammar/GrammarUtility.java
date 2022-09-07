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

package xyz.columnal.grammar;

/**
 * Created by neil on 08/02/2017.
 */
public class GrammarUtility
{
    // Processes out all the escapes in a quoted string:
    
    public static String processEscapes(String quotedAndEscaped)
    {
        return processEscapes(quotedAndEscaped, true);
    }

    // Processes out all the escapes in a string:
    public static String processEscapes(String escaped, boolean beginsAndEndsWithQuotes)
    {
        // Removing escapes always shortens so longest length is original - 2 (the quotes):
        int[] result = new int[escaped.length() - (beginsAndEndsWithQuotes ? 2 : 0)];
        int resultIndex = 0;
        // Don't process first and last because they are quotes
        int end = escaped.length() - (beginsAndEndsWithQuotes ? 1 : 0);
        for (int i = beginsAndEndsWithQuotes ? 1 : 0; i < end; i++)
        {
            // We can do it using chars not codepoints because non-^ characters
            // will be perfectly preserved:
            int c = escaped.charAt(i);
            if (c == '^')
            {
                if (i + 1 < end)
                {

                    switch (escaped.charAt(i + 1))
                    {
                        case 'r':
                            c = '\r';
                            break;
                        case 'n':
                            c = '\n';
                            break;
                        case 'q':
                            c = '\"';
                            break;
                        case 'c':
                            c = '^';
                            break;
                        case 'a':
                            c = '@';
                            break;
                        case 't':
                            c = '\t';
                            break;
                        case '{':
                            {
                                int close = escaped.indexOf('}', i + 2);
                                if (close != -1)
                                {
                                    String hex = escaped.substring(i + 2, close);
                                    try
                                    {
                                        c = Integer.parseInt(hex.trim(), 16);
                                        i = close - 1;
                                        break;
                                    }
                                    catch (NumberFormatException e)
                                    {
                                        // Not a number, invalid
                                    }
                                }
                                // If invalid escape:
                                c = escaped.charAt(i + 1);
                            }
                            break;
                        default:
                            // Invalid escape, probably came from user edit.  Best bet
                            // is just to preserve it:
                            c = escaped.charAt(i + 1);
                            break;
                    }
                }
                else
                {
                    // Invalid, but retain it:
                    c = '^';
                }
                i += 1;
            }

            result[resultIndex++] = c;
        }
        return new String(result, 0, resultIndex);
    }


    /**
     * Things like a00, abcd, random unicode emoji, return true
     * Things like 0aa, a+0, a!0, "a a" return false
     *
     * This method is used both to check for valid variable names and
     * what can be printed without needing quotes (same concept, essentially)
     *
     *
     * @param s
     * @return
     */
    public static boolean validUnquoted(String s)
    {
        if (s.isEmpty() || s.equals("true") || s.equals("false"))
            return false;
        int firstCodepoint = s.codePointAt(0);
        if (!Character.isAlphabetic(firstCodepoint))
            return false;
        // Underscore is not letter or digit, so needs special case here:
        return s.codePoints().skip(1).allMatch(c -> Character.isLetterOrDigit(c) || c == '_' || c ==' ');
    }
    
    public static boolean validIdentifier(String s)
    {
        if (s.isEmpty())
            return false;
        int[] codepoints = s.codePoints().toArray();
        if (!Character.isLetter(codepoints[0]))
            return false;
        final int SPACE_CODEPOINT = 32;
        boolean lastWasSpace = false;
        for (int i = 1; i < codepoints.length; i++)
        {
            if (Character.isAlphabetic(codepoints[i]) || Character.isDigit(codepoints[i]))
            {
                lastWasSpace = false;
            }
            else if (codepoints[i] == SPACE_CODEPOINT)
            {
                if (lastWasSpace)
                    return false;
                lastWasSpace = true;
            }
            else
                return false;
        }
        return !lastWasSpace;
    }

    public static String escapeChars(String s)
    {
        // Order matters; must replace ^ first:
        return s.replace("^", "^c").replace("\"", "^q").replace("\n", "^n").replace("\r", "^r").replace("@", "^a");
    }

    /**
     * Gets rid of beginning and trailing spaces, and collapses all other
     * consecutive whitespace into a single space.
     */
    public static String collapseSpaces(String s)
    {
        return s.replaceAll("(?U)\\s+", " ").trim();
    }
}
