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

package xyz.columnal.utility;

import annotation.identifier.qual.ExpressionIdentifier;
import annotation.identifier.qual.UnitIdentifier;
import annotation.units.RawInputLocation;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CodePointCharStream;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.Token;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.grammar.ExpressionLexer;
import xyz.columnal.grammar.UnitLexer;
import xyz.columnal.grammar.UnitParser.SingleUnitContext;
import xyz.columnal.utility.Utility.DescriptiveErrorListener;
import xyz.columnal.utility.adt.Pair;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

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
    public static @ExpressionIdentifier String fromParsed(xyz.columnal.grammar.ExpressionParser.IdentContext parsedIdent)
    {
        return parsedIdent.getText();
    }

    @SuppressWarnings("identifier")
    public static @ExpressionIdentifier String fromParsed(xyz.columnal.grammar.ExpressionParser2.SingleIdentContext parsedIdent)
    {
        return parsedIdent.getText();
    }

    @SuppressWarnings("identifier")
    public static @ExpressionIdentifier String fromParsed(xyz.columnal.grammar.FormatParser.IdentContext parsedIdent)
    {
        return parsedIdent.getText();
    }

    @SuppressWarnings("identifier")
    public static @ExpressionIdentifier String fromParsed(xyz.columnal.grammar.FormatParser.ColumnNameContext parsedIdent)
    {
        return parsedIdent.getText();
    }

    public static @Nullable @ExpressionIdentifier String fromParsed(xyz.columnal.grammar.DataParser.LabelContext parsedIdent)
    {
        return asExpressionIdentifier(parsedIdent.labelName().getText());
    }

    public static @Nullable @ExpressionIdentifier String fromParsed(xyz.columnal.grammar.DataParser2.LabelContext parsedIdent)
    {
        return asExpressionIdentifier(parsedIdent.labelName().getText());
    }

    @SuppressWarnings("identifier")
    public static @UnitIdentifier String fromParsed(SingleUnitContext parsedIdent)
    {
        return parsedIdent.IDENT().getText();
    }

    // If all parts are valid, joining them spaces is also valid
    @SuppressWarnings("identifier")
    public static @ExpressionIdentifier String spaceSeparated(@ExpressionIdentifier String... parts)
    {
        return Arrays.stream(parts).collect(Collectors.joining(" "));
    }

    // If all parts are valid, joining them spaces is also valid
    @SuppressWarnings("identifier")
    public static @ExpressionIdentifier String spaceSeparated(ImmutableList<@ExpressionIdentifier String> parts)
    {
        return parts.stream().collect(Collectors.joining(" "));
    }

    public static @ExpressionIdentifier String shorten(@ExpressionIdentifier String raw)
    {
        final int THRESHOLD = 8;
        if (raw.length() <= THRESHOLD)
            return raw;
        return IdentifierUtility.fixExpressionIdentifier(raw.substring(0, THRESHOLD).trim(), raw);
    }

    public static class Consumed<T>
    {
        public final T item;
        public final @RawInputLocation int positionAfter;
        public final ImmutableSet<@RawInputLocation Integer> removedCharacters;

        public Consumed(T item, @RawInputLocation int positionAfter, ImmutableSet<@RawInputLocation Integer> removedCharacters)
        {
            this.item = item;
            this.positionAfter = positionAfter;
            this.removedCharacters = removedCharacters;
        }
    }
    
    
    public static @Nullable Consumed<@ExpressionIdentifier String> consumeExpressionIdentifier(String content, @RawInputLocation int startFrom, @RawInputLocation int caretPos)
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

            // Find all the consecutive spaces:
            @RawInputLocation int posAfterLastSpace = end;
            while (posAfterLastSpace < content.length() && content.charAt(posAfterLastSpace) == ' ')
            {
                posAfterLastSpace += RawInputLocation.ONE;
            }
            
            // Is it followed by at least one space?
            if (posAfterLastSpace > end)
            {
                // Here's the options:
                // 1: there are one or more spaces, the caret is somewhere in them, preserve one before (if present), and one after (if present), delete rest.
                //    If followed by identifier, glue together
                // 2: there are one or more spaces, the caret doesn't touch any of them, and
                //   (a) there is an identifier following: delete down to one space and glue
                //   (b) there is not an identifier following: delete all
                
                @Nullable Consumed<@ExpressionIdentifier String> identAfter = consumeExpressionIdentifier(content, posAfterLastSpace, caretPos);
                
                // Is the caret in there?
                if (end <= caretPos && caretPos <= posAfterLastSpace)
                {
                    // It is -- preserve at most one space before and one after:
                    Set<@RawInputLocation Integer> removed = new HashSet<>();
                    for (@RawInputLocation int i = end + RawInputLocation.ONE; i < caretPos; i++)
                    {
                        removed.add(i);
                    }
                    for (@RawInputLocation int i = caretPos + RawInputLocation.ONE; i < posAfterLastSpace; i++)
                    {
                        removed.add(i);
                    }
                    String spaces = (end < caretPos ? " " : "") + (caretPos < posAfterLastSpace ? " " : "");
                    if (identAfter != null)
                    {
                        @SuppressWarnings("identifier")
                        @ExpressionIdentifier String withSpaces = token.getText() + spaces + identAfter.item;
                        return new Consumed<@ExpressionIdentifier String>(withSpaces, identAfter.positionAfter, ImmutableSet.<@RawInputLocation Integer>copyOf(Sets.<@RawInputLocation Integer>union(removed, identAfter.removedCharacters)));
                    }
                    else
                    {
                        @SuppressWarnings("identifier")
                        @ExpressionIdentifier String withSpaces = token.getText() + spaces;
                        return new Consumed<@ExpressionIdentifier String>(withSpaces, posAfterLastSpace, ImmutableSet.copyOf(removed));
                    }
                }
                else
                {
                    // Caret not in there:
                    Set<@RawInputLocation Integer> removed = new HashSet<>();
                    for (@RawInputLocation int i = end + (identAfter != null ? RawInputLocation.ONE : RawInputLocation.ZERO); i < posAfterLastSpace; i++)
                    {
                        removed.add(i);
                    }
                    if (identAfter != null)
                    {
                        @SuppressWarnings("identifier")
                        @ExpressionIdentifier String glued = token.getText() + " " + identAfter.item;
                        return new Consumed<@ExpressionIdentifier String>(glued, identAfter.positionAfter, ImmutableSet.<@RawInputLocation Integer>copyOf(Sets.<@RawInputLocation Integer>union(removed, identAfter.removedCharacters)));
                    }
                    else
                    {
                        @SuppressWarnings("identifier")
                        @ExpressionIdentifier String ident = token.getText();
                        return new Consumed<>(ident, posAfterLastSpace, ImmutableSet.copyOf(removed));
                    }
                }
            }
            @SuppressWarnings("identifier")
            @ExpressionIdentifier String tokenIdent = token.getText();
            return new Consumed<>(tokenIdent, end, ImmutableSet.of());
        }
        else
            return null;
    }

    public static @Nullable Consumed<Pair<@Nullable @ExpressionIdentifier String, ImmutableList<@ExpressionIdentifier String>>> consumePossiblyScopedExpressionIdentifier(String content, @RawInputLocation int startFrom, @RawInputLocation int includeTrailingSpaceOrDoubleSpaceIfEndsAt)
    {
        ArrayList<Consumed<@ExpressionIdentifier String>> items = new ArrayList<>();
        boolean firstHadDouble = false;
        @RawInputLocation int nextPos = startFrom;
        do
        {
            if (!items.isEmpty())
                nextPos = items.get(items.size() - 1).positionAfter + RawInputLocation.ONE;
            if (items.size() == 1 && nextPos < content.length() && content.charAt(nextPos) == '\\')
            {
                firstHadDouble = true;
                nextPos += RawInputLocation.ONE;
            }
            
            @Nullable Consumed<@ExpressionIdentifier String> next = consumeExpressionIdentifier(content, nextPos, includeTrailingSpaceOrDoubleSpaceIfEndsAt);
            if (next == null)
                break;
            else
                items.add(next);
        }
        while (items.get(items.size() - 1).positionAfter < content.length() && content.charAt(items.get(items.size() - 1).positionAfter) == '\\');
        
        if (items.isEmpty())
            return null;
        else
        {
            @Nullable @ExpressionIdentifier String first = null;
            ArrayList<@ExpressionIdentifier String> parts = new ArrayList<>();
            HashSet<@RawInputLocation Integer> removed = new HashSet<>();
            if (firstHadDouble && items.size() >= 2)
            {
                first = items.get(0).item;
                removed.addAll(items.get(0).removedCharacters);
                items.remove(0);
            }
            for (int i = 0; i < items.size(); i++)
            {
                parts.add(items.get(i).item);
                removed.addAll(items.get(i).removedCharacters);
            }
            
            return new Consumed<Pair<@Nullable @ExpressionIdentifier String, ImmutableList<@ExpressionIdentifier String>>>(new Pair<@Nullable @ExpressionIdentifier String, ImmutableList<@ExpressionIdentifier String>>(first, ImmutableList.<@ExpressionIdentifier String>copyOf(parts)), items.get(items.size() - 1).positionAfter, ImmutableSet.<@RawInputLocation Integer>copyOf(removed));
        }
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
            else if (last == Last.START && Character.isDigit(c))
            {
                // Stick an N on the front:
                processed.append('N').appendCodePoint(c);
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
