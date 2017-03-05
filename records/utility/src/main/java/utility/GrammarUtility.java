package utility;

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
}
