package records.grammar;

/**
 * Created by neil on 08/02/2017.
 */
public class GrammarUtility
{
    // Processes out all the escapes in a string:
    public static String processEscapes(String quotedAndEscaped)
    {
        // Removing escapes always shortens so longest length is original - 2 (the quotes):
        char[] result = new char[quotedAndEscaped.length() - 2];
        int resultIndex = 0;
        // Don't process first and last because they are quotes
        for (int i = 1; i < quotedAndEscaped.length() - 1; i++)
        {
            // We can do it using chars not codepoints because non-^ characters
            // will be perfectly preserved:
            char c = quotedAndEscaped.charAt(i);
            if (c == '^')
            {
                switch (quotedAndEscaped.charAt(i+1))
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
                    default:
                        // Invalid escape, probably came from user edit.  Best bet
                        // is just to preserve it:
                        c = quotedAndEscaped.charAt(i+1);
                        break;
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
        return s.codePoints().skip(1).allMatch(c -> Character.isLetterOrDigit(c) || c == '_' || c == '.' || c ==' ');
    }

    public static String escapeChars(String s)
    {
        // Order matters; must replace ^ first:
        return s.replace("^", "^c").replace("\"", "^q").replace("\n", "^n");
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
