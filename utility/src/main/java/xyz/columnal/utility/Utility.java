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
import annotation.qual.ImmediateValue;
import annotation.qual.Value;
import annotation.units.AbsColIndex;
import annotation.units.AbsRowIndex;
import annotation.userindex.qual.UserIndex;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import info.debatty.java.stringsimilarity.Damerau;
import javafx.css.Styleable;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import xyz.columnal.log.Log;
import org.antlr.v4.runtime.BailErrorStrategy;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CodePointCharStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.Vocabulary;
import org.antlr.v4.runtime.misc.ParseCancellationException;
import org.apache.commons.io.FileUtils;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.initialization.qual.Initialized;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.PolyNull;
import org.checkerframework.checker.nullness.qual.UnknownKeyFor;
import org.checkerframework.dataflow.qual.Pure;
import org.sosy_lab.common.rationals.Rational;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.parse.ParseException;
import xyz.columnal.error.UserException;
import xyz.columnal.grammar.DataLexer;
import xyz.columnal.grammar.DataLexer2;
import xyz.columnal.grammar.DataParser;
import xyz.columnal.grammar.DataParser2;
import xyz.columnal.grammar.MainParser.DetailContext;
import xyz.columnal.grammar.MainParser.DetailPrefixedContext;
import xyz.columnal.grammar.MainParser2;
import xyz.columnal.grammar.TableParser2;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.function.ExBiFunction;
import xyz.columnal.utility.function.ExConsumer;
import xyz.columnal.utility.function.ExFunction;
import xyz.columnal.utility.function.ExSupplier;
import xyz.columnal.utility.function.FunctionInt;
import xyz.columnal.utility.function.simulation.SimulationSupplier;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.MathContext;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.temporal.TemporalAccessor;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Created by neil on 20/10/2016.
 */
public class Utility
{
    public static final String MRU_FILE_NAME = "recent.mru";
    public static final ReentrantLock saveLock = new ReentrantLock();

    public static <T, R> List<@NonNull R> mapList(List<@NonNull T> list, Function<@NonNull T, @NonNull R> func)
    {
        ArrayList<@NonNull R> r = new ArrayList<>(list.size());
        for (T t : list)
            r.add(func.apply(t));
        return r;
    }

    public static <T, R> ImmutableList<@NonNull R> mapListI(List<@NonNull T> list, Function<@NonNull T, @NonNull R> func)
    {
        ImmutableList.Builder<@NonNull R> r = ImmutableList.builderWithExpectedSize(list.size());
        for (T t : list)
            r.add(func.apply(t));
        return r.build();
    }

    public static <T, R> ImmutableList<@NonNull R> mapListInt(List<@NonNull T> list, FunctionInt<@NonNull T, @NonNull R> func) throws InternalException
    {
        ImmutableList.Builder<@NonNull R> r = ImmutableList.builderWithExpectedSize(list.size());
        for (T t : list)
            r.add(func.apply(t));
        return r.build();
    }

    public static <T, R> List<@NonNull R> mapListEx(List<@NonNull T> list, ExFunction<@NonNull T, @NonNull R> func) throws InternalException, UserException
    {
        ArrayList<@NonNull R> r = new ArrayList<>(list.size());
        for (T t : list)
            r.add(func.apply(t));
        return r;
    }

    public static <T, R> ImmutableList<@NonNull R> mapListExI(List<@NonNull T> list, ExFunction<@NonNull T, @NonNull R> func) throws InternalException, UserException
    {
        return ImmutableList.<@NonNull R>copyOf(Utility.<T, R>mapListEx(list, func));
    }

    @OnThread(Tag.Simulation)
    public static <R> ImmutableList<@NonNull R> mapListExI(ListEx list, ExFunction<@NonNull @Value Object, @NonNull R> func) throws InternalException, UserException
    {
        ArrayList<@NonNull R> r = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++)
        {
            r.add(func.apply(list.get(i)));
        }
        return ImmutableList.copyOf(r);
    }

    public static <T, R> ImmutableList<R> mapListExI_Index(List<T> list, ExBiFunction<Integer, T, R> func) throws InternalException, UserException
    {
        ArrayList<R> r = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++)
        {
            r.add(func.apply(i, list.get(i)));
        }
        return ImmutableList.<R>copyOf(r);
    }

    public static <T, R> ImmutableList<R> mapList_Index(List<T> list, BiFunction<Integer, T, R> func)
    {
        ArrayList<R> r = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++)
        {
            r.add(func.apply(i, list.get(i)));
        }
        return ImmutableList.<R>copyOf(r);
    }

    public static <T, R> @NonNull R @NonNull [] mapArray(Class<R> cls, @NonNull T @NonNull [] src, Function<@NonNull T, @NonNull R> func)
    {
        @SuppressWarnings("unchecked")
        @NonNull R @NonNull [] r = (@NonNull R @NonNull [])Array.newInstance(cls, src.length);
        for (int i = 0; i < src.length; i++)
        {
            r[i] = func.apply(src[i]);
        }
        return r;
    }
    
    public static <K, T, R> ImmutableMap<@NonNull K, @NonNull R> mapValues(Map<@NonNull K, @NonNull T> original, Function<@NonNull T, @NonNull R> apply)
    {
        ImmutableMap.Builder<@NonNull K, @NonNull R> r = ImmutableMap.builderWithExpectedSize(original.size());
        original.forEach((k, v) -> r.put(k, apply.apply(v)));
        return r.build();
    }

    public static <K, T, R> ImmutableMap<@NonNull K, @NonNull R> mapValuesInt(Map<@NonNull K, @NonNull T> original, FunctionInt<@NonNull T, @NonNull R> apply) throws InternalException
    {
        ImmutableMap.Builder<@NonNull K, @NonNull R> r = ImmutableMap.builderWithExpectedSize(original.size());
        for (Entry<@NonNull K, @NonNull T> entry : original.entrySet())
        {
            r.put(entry.getKey(), apply.apply(entry.getValue()));
        }
        return r.build();
    }

    public static <K, T, R> ImmutableMap<@NonNull K, @NonNull R> mapValuesEx(Map<@NonNull K, @NonNull T> original, ExFunction<@NonNull T, @NonNull R> apply) throws InternalException, UserException
    {
        ImmutableMap.Builder<@NonNull K, @NonNull R> r = ImmutableMap.builderWithExpectedSize(original.size());
        for (Entry<@NonNull K, @NonNull T> entry : original.entrySet())
        {
            r.put(entry.getKey(), apply.apply(entry.getValue()));
        }
        return r.build();
    }

    // From http://stackoverflow.com/questions/453018/number-of-lines-in-a-file-in-java
    public static int countLines(File filename, Charset charset) throws IOException
    {
        try (LineNumberReader lnr = new LineNumberReader(new InputStreamReader(new FileInputStream(filename), charset)))
        {
            char[] buffer = new char[1048576];
            boolean lastWasNewline = false;
            int numRead;
            boolean empty = true;
            while ((numRead = lnr.read(buffer, 0, buffer.length)) > 0)
            {
                empty = false;
                lastWasNewline = buffer[numRead - 1] == '\n';
            }

            // Nothing at all; zero lines
            if (empty)
                return 0;
            // Some content, no newline, must be one line:
            else if (lnr.getLineNumber() == 0 && !empty)
                return 1;
            else
            // Some content with newlines:
                return lnr.getLineNumber() + (lastWasNewline ? 0 : 1);
        }
    }

    // Given a table of values, skips the first "skipRows" rows,
    // Then extracts all values which are not from completely blank rows.
    public static List<String> sliceSkipBlankRows(List<? extends List<String>> vals, int skipRows, int columnIndex)
    {
        List<String> items = new ArrayList<>(vals.size());
        for (int i = skipRows; i < vals.size(); i++)
        {
            List<String> row = vals.get(i);
            if (!row.isEmpty() && !row.stream().allMatch(String::isEmpty))
            {
                if (columnIndex < row.size())
                    items.add(row.get(columnIndex));
                else if (!row.isEmpty())
                    items.add(row.get(row.size() - 1));
            }
        }
        return items;
    }

    @OnThread(Tag.FX)
    public static void addStyleClass(@UnknownInitialization(Styleable.class) Styleable styleable, String... classes)
    {
        styleable.getStyleClass().addAll(classes);
    }

    public static Point2D middle(Bounds bounds)
    {
        return new Point2D((bounds.getMinX() + bounds.getMaxX()) * 0.5, (bounds.getMinY() + bounds.getMaxY()) * 0.5);
    }

    @Pure
    @OnThread(Tag.Simulation)
    public static int compareLists(List<@NonNull @Value ?> a, List<@NonNull @Value ?> b) throws InternalException, UserException
    {
        return compareLists(a, b, null);
    }

    /**
     * Used to compare two unpacked values. 
     * 
     * Compares two lists lexicographically, i.e. it starts by comparing the first item
     * If that differs, that's the result, otherwise compare the next items, and so on.
     * 
     * The types of the first item in each list should match each other, and the
     * types should match at each subsequent stage if the lists up to that point
     * are identical.  That is:
     * 
     * 0, "hi", "bye" can compare to 1, 2.
     * 0, "hi", "bye" can compare to 0, "a", "b"
     * 0, "hi", "bye" CANNOT compare to 0, 1, "c"
     * 
     * The list can contain lists (which recurse to compare), any comparable
     * of the same type (e.g. String, Temporal, etc). Numbers are compared using compareNumbers;
     * see that method for more details.
     */
    @Pure
    @OnThread(Tag.Simulation)
    public static int compareLists(List<@NonNull @Value ?> a, List<@NonNull @Value ?> b, @Nullable Pair<EpsilonType, BigDecimal> epsilon) throws InternalException, UserException
    {
        for (int i = 0; i < a.size(); i++)
        {
            if (i >= b.size())
                return 1; // A was larger
            @Value Object ax = a.get(i);
            @Value Object bx = b.get(i);
            int cmp = compareValues(ax, bx, epsilon);
            if (cmp != 0)
                return cmp;

        }
        if (a.size() == b.size())
            return 0; // Same
        else
            return -1; // B must have been longer
    }

    @Pure
    @OnThread(Tag.Simulation)
    public static int compareLists(ListEx a, ListEx b, @Nullable Pair<EpsilonType, BigDecimal> epsilon) throws InternalException, UserException
    {
        for (int i = 0; i < a.size(); i++)
        {
            if (i >= b.size())
                return 1; // A was larger
            @Value Object ax = a.get(i);
            @Value Object bx = b.get(i);
            int cmp = compareValues(ax, bx, epsilon);
            if (cmp != 0)
                return cmp;

        }
        if (a.size() == b.size())
            return 0; // Same
        else
            return -1; // B must have been longer
    }

    @OnThread(Tag.Simulation)
    public static int compareValues(@Value Object ax, @Value Object bx) throws InternalException, UserException
    {
        return compareValues(ax, bx, null);
    }

    @OnThread(Tag.Simulation)
    public static int compareValues(@Value Object ax, @Value Object bx, @Nullable Pair<EpsilonType, BigDecimal> epsilon) throws InternalException, UserException
    {
        int cmp;
        if (ax instanceof Number)
            cmp = compareNumbers(Utility.cast(ax, Number.class), Utility.cast(bx, Number.class), epsilon);
        else if (ax instanceof ListEx)
            cmp = compareLists(Utility.cast(ax, ListEx.class), Utility.cast(bx, ListEx.class), epsilon);
        else if (ax instanceof Comparable)
        {
            // Need to be declaration to use annotation:
            try
            {
                @SuppressWarnings("unchecked")
                int cmpTmp = ((Comparable<Object>) ax).compareTo(bx);
                cmp = cmpTmp;
            }
            catch (ClassCastException e)
            {
                throw new InternalException("Mismatched internal types", e);
            }
        }
        else if (ax instanceof TaggedValue)
        {
            @Value TaggedValue at = cast(ax, TaggedValue.class);
            @Value TaggedValue bt = cast(bx, TaggedValue.class);
            cmp = Integer.compare(at.getTagIndex(), bt.getTagIndex());
            if (cmp != 0)
                return cmp;
            @Value Object a2 = at.getInner();
            @Value Object b2 = bt.getInner();
            if (a2 != null && b2 != null)
                return compareValues(a2, b2, epsilon);
            return 0; // Assume bx null too, if types match.
        }
        else if (ax instanceof Record)
        {
            @Value Record ao = cast(ax, Record.class);
            @Value Record bo = cast(bx, Record.class);
            ImmutableList<Entry<@ExpressionIdentifier String, @Value Object>> aoByName = ao.getFullContent().entrySet().stream().sorted(Comparator.<Entry<@ExpressionIdentifier String, @Value Object>, @ExpressionIdentifier String>comparing(e -> e.getKey())).collect(ImmutableList.<Entry<@ExpressionIdentifier String, @Value Object>>toImmutableList());
            ImmutableList<Entry<@ExpressionIdentifier String, @Value Object>> boByName = bo.getFullContent().entrySet().stream().sorted(Comparator.<Entry<@ExpressionIdentifier String, @Value Object>, @ExpressionIdentifier String>comparing(e -> e.getKey())).collect(ImmutableList.<Entry<@ExpressionIdentifier String, @Value Object>>toImmutableList());
            
            if (aoByName.size() != boByName.size())
                throw new InternalException("Trying to compare records of different size");
            for (int i = 0; i < aoByName.size(); i++)
            {
                Entry<@ExpressionIdentifier String, @Value Object> ae = aoByName.get(i);
                Entry<@ExpressionIdentifier String, @Value Object> be = boByName.get(i);
                if (!ae.getKey().equals(be.getKey()))
                    throw new InternalException("Record field names do not match: " + ae.getKey() + " vs " + be.getKey());
                cmp = compareValues(ae.getValue(), be.getValue(), epsilon);
                if (cmp != 0)
                    return cmp;
            }
            return 0;
        }
        else
            throw new InternalException("Uncomparable types: " + ax.getClass() + " " + bx.getClass());
        return cmp;
    }

    /**
     * Gets the fractional part as a String, excluding the dot
     *
     * @param minDisplayDP The minimum number of decimal places to show.  If zero,
     *                     empty string will be returned if the number is an integer.
     * @param maxDisplayDP The maximum number of decimal places to show.  There is
     *                     an in-built maximum in effect of around 35, as that is the
     *                     maximum precision possible in a BigDecimal.  So if this
     *                     number is much higher than 35, it effectively means no maximum.
     *                     Must be at least 1 (otherwise, why call this function?).
     *                     Passing a negative number is equivalent to unlimited.
     */


    public static String getFracPartAsString(Number number, int minDisplayDP, int maxDisplayDP)
    {
        if (number instanceof BigDecimal)
        {
            // Munged from http://stackoverflow.com/a/30761234/412908
            BigDecimal bd = (BigDecimal)number;
            String s = bd.abs().toPlainString();
            int dot = s.indexOf('.');
            if (dot != -1)
            {
                s = s.substring(dot + 1);
                while (s.length() < minDisplayDP)
                    s = s + "0";
                if (maxDisplayDP > 0 && s.length() > maxDisplayDP)
                {
                    s = s.substring(0, maxDisplayDP - 1) + "\u2026";
                }
                return s;
            }

            // Otherwise it's an integer; fall through:
        }
        char cs[] = new char[minDisplayDP];
        for (int i = 0; i < cs.length; i++)
        {
            cs[i] = '0';
        }
        return new String(cs);
    }

    // getFracPar(5.06, 3), will give 60.
    public static Number getFracPart(Number number, int bottomDigit)
    {
        if (number instanceof BigDecimal)
        {
            // From http://stackoverflow.com/a/30761234/412908
            BigDecimal bd = ((BigDecimal)number).stripTrailingZeros();
            return bd.remainder(BigDecimal.ONE).movePointRight(bottomDigit).abs().toBigInteger();

        }
        else
            return 0;
    }

    public static @Value Number getIntegerPart(@Value Number number)
    {
        if (number instanceof BigDecimal)
        {
            @SuppressWarnings("valuetype")
            @Value BigDecimal bigDecimal = new BigDecimal(((BigDecimal) number).toBigInteger());
            return bigDecimal;
        }
        else
            return number;
    }

    @SuppressWarnings("valuetype")
    public static @Value BigDecimal toBigDecimal(@Value Number n)
    {
        if (n instanceof BigDecimal)
            return (BigDecimal) n;
        else
            return BigDecimal.valueOf(n.longValue());
    }

    public static <R, PARSER extends Parser> R parseAsOne(String input, Function<CharStream, Lexer> makeLexer, Function<TokenStream, PARSER> makeParser, ExFunction<PARSER, R> withParser) throws InternalException, UserException
    {
        CodePointCharStream inputStream = CharStreams.fromString(input);
        // Could try speeding things up with: https://github.com/antlr/antlr4/issues/192
        DescriptiveErrorListener del = new DescriptiveErrorListener();
        Lexer lexer = makeLexer.apply(inputStream);
        lexer.removeErrorListeners();
        lexer.addErrorListener(del);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        PARSER parser = makeParser.apply(tokens);
        parser.setErrorHandler(new BailErrorStrategy());
        parser.removeErrorListeners();
        parser.addErrorListener(del);
        try
        {
            R r = withParser.apply(parser);
            if (!del.errors.isEmpty())
                throw new UserException("Parse errors while loading \"" + (input.length() < 80 ? input : (input.substring(0, 80) + "\u2026"))  + "\":\n" + del.errors.stream().collect(Collectors.joining("\n")));
            return r;
        }
        catch (ParseCancellationException e)
        {
            if (e.getCause() instanceof RecognitionException)
                throw new ParseException(input, parser, (RecognitionException)e.getCause());
            else
                throw new ParseException(input, parser, e);
        }
    }

    public static <T> ImmutableList<T> prependToList(T header, Collection<T> data)
    {
        ImmutableList.Builder<T> r = ImmutableList.builderWithExpectedSize(data.size() + 1);
        r.add(header);
        r.addAll(data);
        return r.build();
    }

    public static <T> ImmutableList<T> appendToList(List<T> data, T last)
    {
        ImmutableList.Builder<T> r = ImmutableList.builderWithExpectedSize(data.size() + 1);
        r.addAll(data);
        r.add(last);
        return r.build();
    }

    /**
     * Creates a new map containing the given map, plus the new entry.
     * The insertion order of the original map will be respected, and the new item will be placed at the end.
     * If the key was already in the map, it will be replaced, and the new value will still appear at the end.
     * Additionally, removeKey will be removed if it is present.
     */
    public static <K, V> ImmutableMap<K, V> appendToMap(Map<K, V> data, K key, V value, @Nullable K removeKey)
    {
        ImmutableMap.Builder<K, V> builder = ImmutableMap.builderWithExpectedSize(data.size() + 1);
        for (Entry<K, V> entry : data.entrySet())
        {
            if (!Objects.equals(entry.getKey(), key) && !Objects.equals(entry.getKey(), removeKey))
                builder.put(entry);
        }
        builder.put(key, value);
        return builder.build();
    }

    public static <T> ImmutableSet<T> appendToSet(Set<T> data, T item)
    {
        ImmutableSet.Builder<T> builder = ImmutableSet.builderWithExpectedSize(data.size() + 1);
        for (T existing : data)
        {
            // Can't add twice with builder:
            if (!Objects.equals(existing, item))
                builder.add(existing);
        }
        builder.add(item);
        return builder.build();
    }

    public static void report(InternalException e)
    {
        Log.log(e); // TODO log and send back
    }
    
    @SuppressWarnings("optional")
    public static @ImmediateValue Number parseNumber(String number) throws UserException
    {
        return parseNumberOpt(number).orElseThrow(() -> new UserException("Problem parsing number \"" + number + "\""));
    }

    @SuppressWarnings("valuetype")
    public static Optional<@ImmediateValue Number> parseNumberOpt(String number)
    {
        // First try as a long:
        try
        {
            return Optional.of(Long.valueOf(number));
        }
        catch (NumberFormatException ex)
        {
            // Not a long, then
        }
        // Last try: big decimal (and re-throw if not)
        try
        {
            return Optional.of(new BigDecimal(number, MathContext.DECIMAL128).stripTrailingZeros());
        }
        catch (NumberFormatException e)
        {
            return Optional.empty();
        }
    }

    public static OptionalInt parseIntegerOpt(String number)
    {
        // First try as an integer:
        try
        {
            return OptionalInt.of(Integer.valueOf(number).intValue());
        }
        catch (NumberFormatException ex)
        {
            return OptionalInt.empty();
        }
    }

    public static OptionalDouble parseDoubleOpt(String number)
    {
        // First try as a double:
        try
        {
            return OptionalDouble.of(Double.valueOf(number).doubleValue());
        }
        catch (NumberFormatException ex)
        {
            return OptionalDouble.empty();
        }
    }

    public static Rational rationalToPower(Rational original, int power)
    {
        if (power < 0)
        {
            original = original.reciprocal();
            power = -power;
        }
        Rational r = original;
        while (power > 1)
        {
            r = r.times(original);
            power -= 1;
        }
        return r;
    }

    public static Rational parseRational(String text)
    {
        return Rational.ofBigDecimal(new BigDecimal(text));
    }

    @SuppressWarnings("valuetype")
    public static @Value Number negate(@Value Number n)
    {
        if (n instanceof BigDecimal)
            return toBigDecimal(n).negate(MathContext.DECIMAL128);
        else
            return -n.longValue();
    }
    
    // If !add, subtract
    @SuppressWarnings("valuetype")
    public static @Value Number addSubtractNumbers(@Value Number lhs, @Value Number rhs, boolean add)
    {
        if (lhs instanceof BigDecimal || rhs instanceof BigDecimal)
        {
            if (add)
                return toBigDecimal(lhs).add(toBigDecimal(rhs), MathContext.DECIMAL128);
            else
                return toBigDecimal(lhs).subtract(toBigDecimal(rhs), MathContext.DECIMAL128);
        }
        else // Must both be Long or smaller:
        {
            try
            {
                if (add)
                    return Math.addExact(lhs.longValue(), rhs.longValue());
                else
                    return Math.subtractExact(lhs.longValue(), rhs.longValue());
            }
            catch (ArithmeticException e)
            {
                return addSubtractNumbers(toBigDecimal(lhs), toBigDecimal(rhs), add);
            }
        }
    }

    @SuppressWarnings("valuetype")
    public static @Value Number multiplyNumbers(@Value Number lhs, @Value Number rhs)
    {
        if (lhs instanceof BigDecimal || rhs instanceof BigDecimal)
        {
            return toBigDecimal(lhs).multiply(toBigDecimal(rhs), MathContext.DECIMAL128);
        }
        else
        {
            try
            {
                return Math.multiplyExact(lhs.longValue(), rhs.longValue());
            }
            catch (ArithmeticException e)
            {
                return multiplyNumbers(toBigDecimal(lhs), toBigDecimal(rhs));
            }
        }
    }

    @SuppressWarnings("valuetype")
    public static @Value Number divideNumbers(@Value Number lhs, @Value Number rhs) throws UserException
    {
        if (lhs instanceof BigDecimal || rhs instanceof BigDecimal)
        {
            @Value BigDecimal denom = toBigDecimal(rhs);
            if (denom.compareTo(BigDecimal.ZERO) == 0)
                throw new UserException("Division by zero");
            else
                return toBigDecimal(lhs).divide(denom, MathContext.DECIMAL128);
        }
        else
        {
            long lhsLong = lhs.longValue();
            long rhsLong = rhs.longValue();
            // Exact division possible:
            if (rhsLong != 0 && lhsLong % rhsLong == 0 && (lhsLong != Long.MIN_VALUE || rhsLong != -1))
                return lhsLong / rhsLong;
            else
                return divideNumbers(toBigDecimal(lhs), toBigDecimal(rhs));
        }
    }

    @SuppressWarnings("valuetype")
    public static @Value Number raiseNumber(@Value Number lhs, @Value Number rhs) throws UserException
    {
        // It must fit in an int and not be massive/tiny:
        if (((rhs instanceof BigDecimal && !rhs.equals(BigDecimal.valueOf(((BigDecimal)rhs).intValue())))
            || rhs.longValue() != (long)rhs.intValue())
            || Math.abs(rhs.intValue()) >= 1000)
        {
            // We must use doubles, nothing else supported:
            double lhsd = lhs.doubleValue();
            double rhsd = rhs.doubleValue();
            if (lhsd < 0 && rhsd != Math.floor(rhsd))
            {
                throw new UserException("Attempting to raise negative number (" + lhsd + ") to non-integral power (" + rhsd + ")");
            }

            double result = Math.pow(lhsd, rhsd);
            if (Double.isFinite(result))
                return BigDecimal.valueOf(result);
            else
                throw new UserException("Cannot store result of calculation " + lhsd + "^" + rhsd + ": " + result);
        }
        else // Integer power, we can use big decimal:
        {
            try
            {
                return toBigDecimal(lhs).pow(rhs.intValue(), MathContext.DECIMAL128);
            }
            catch (ArithmeticException e)
            {
                throw new UserException("Cannot store result of calculation: " + e.getLocalizedMessage() + " " + lhs + "^" + rhs);
            }
        }
    }

    @SuppressWarnings("valuetype")
    public static <T> T withNumber(@Value Object num, ExFunction<@Value Long, T> withLong, ExFunction<@Value BigDecimal, T> withBigDecimal) throws InternalException, UserException
    {
        if (num instanceof BigDecimal)
            return withBigDecimal.apply(Utility.cast(num, BigDecimal.class));
        else
            return withLong.apply(Utility.cast(num, Number.class).longValue());
    }

    public static <T> T withNumberInt(@Value Object num, FunctionInt<Long, T> withLong, FunctionInt<BigDecimal, T> withBigDecimal) throws InternalException
    {
        if (num instanceof BigDecimal)
            return withBigDecimal.apply(Utility.cast(num, BigDecimal.class));
        else
            return withLong.apply(Utility.cast(num, Number.class).longValue());
    }

    public static boolean isIntegral(@Value Object o) throws InternalException
    {
        // From http://stackoverflow.com/a/12748321/412908
        return withNumberInt(o, x -> true, bd -> {
            return bd.signum() == 0 || bd.scale() <= 0 || bd.stripTrailingZeros().scale() <= 0;
        });
    }

    public static <T> Iterable<T> iterableStream(Stream<T> values)
    {
        return () -> values.iterator();
    }

    public static Iterable<Integer> iterableStream(IntStream values)
    {
        return () -> values.iterator();
    }

    public static <T> String listToString(List<T> options)
    {
        return "[" + options.stream().map(Objects::toString).collect(Collectors.joining(", ")) + "]";
    }

    public static <T> ImmutableList<T> replaceList(ImmutableList<T> members, int replaceIndex, T newValue)
    {
        ArrayList<T> r = new ArrayList<T>(members.size());
        r.addAll(members);
        r.set(replaceIndex, newValue);
        return ImmutableList.copyOf(r);
    }

    @SuppressWarnings("valuetype")
    public static @Value ListEx valueList(@Value Object value) throws InternalException
    {
        if (!(value instanceof ListEx))
            throw new InternalException("Unexpected type problem: expected list but found " + value.getClass());
        return (ListEx)value;
    }

    @SuppressWarnings("valuetype")
    public static @Value Record valueRecord(@Value Object value) throws InternalException
    {
        if (!(value instanceof Record))
            throw new InternalException("Unexpected type problem: expected record but found " + value.getClass());
        return (Record) value;
    }

    @OnThread(Tag.Simulation)
    public static @Value Object getAtIndex(@Value ListEx objects, @UserIndex int userIndex) throws UserException, InternalException
    {
        if (userIndex < 1)
            throw new UserException("List index " + userIndex + " out of bounds, should be between 1 and " + objects.size() + " (inclusive)");
        try
        {
            // User index is one-based:
            return objects.get(userIndex - 1);
        }
        catch (IndexOutOfBoundsException e)
        {
            throw new UserException("List index " + userIndex + " out of bounds, should be between 1 and " + objects.size() + " (inclusive)");
        }
    }

    public static @Value Number valueNumber(@Value Object o) throws InternalException
    {
        if (o instanceof Number)
            return (@Value Number)o;
        throw new InternalException("Expected number but found " + o.getClass());
    }

    public static @Value Boolean valueBoolean(@Value Object o) throws InternalException
    {
        if (o instanceof Boolean)
            return (@Value Boolean)o;
        throw new InternalException("Expected boolean but found " + o.getClass());
    }

    public static @Value String valueString(@Value Object o) throws InternalException
    {
        if (o instanceof String)
            return (@Value String)o;
        throw new InternalException("Expected text but found " + o.getClass());
    }

    public static @Value TemporalAccessor valueTemporal(@Value Object o) throws InternalException
    {
        if (o instanceof TemporalAccessor)
            return (@Value TemporalAccessor)o;
        throw new InternalException("Expected temporal but found " + o.getClass());
    }

    public static @Value TaggedValue valueTagged(@Value Object o) throws InternalException
    {
        if (o instanceof TaggedValue)
            return (@Value TaggedValue)o;
        throw new InternalException("Expected tagged value but found " + o.getClass());
    }

    // Like indexOf on lists, but uses only reference equality, and thus doesn't mind about initialization
    @SuppressWarnings("interned")
    public static <T> int indexOfRef(List<T> list, @UnknownInitialization T item)
    {
        for (int i = 0; i < list.size(); i++)
        {
            if (list.get(i) == item)
                return i;
        }
        return -1;
    }

    @Pure
    public static <T> boolean containsRef(List<T> list, @UnknownInitialization T item)
    {
        return indexOfRef(list, item) != -1;
    }

    public static <T> List<T> replicate(int length, T value)
    {
        return new AbstractList<T>()
        {
            @Override
            public T get(int index)
            {
                return value;
            }

            @Override
            public int size()
            {
                return length;
            }
        };
    }

    @OnThread(Tag.FXPlatform)
    public static void usedFile(File src)
    {
        ArrayList<File> mruList = new ArrayList<>(readRecentFilesList());

        // Make sure it's only featured once, at the head:
        mruList.removeAll(Collections.singletonList(src));
        mruList.removeAll(Collections.singletonList(src.getAbsoluteFile()));
        mruList.add(0, src);
        while (mruList.size() > 12)
            mruList.remove(mruList.size() - 1);

        try
        {
            List<@NonNull File> newContent = Utility.mapList(mruList, File::getAbsoluteFile);
            FileUtils.writeLines(new File(getStorageDirectory(), MRU_FILE_NAME), "UTF-8", newContent);
        }
        catch (IOException e)
        {
            Log.log(e);
        }
    }

    // If item is null, returns empty stream, otherwise stream containing that item
    public static <T> Stream<@NonNull T> streamNullable(@Nullable T item)
    {
        return item == null ? Stream.empty() : Stream.of(item);
    }

    // Returns stream of all non-null values
    public static <T> Stream<@NonNull T> streamNullable(@Nullable T a, @Nullable T b)
    {
        List<@NonNull T> r = new ArrayList<>();
        if (a != null)
            r.add(a);
        if (b != null)
            r.add(b);
        return r.stream();
    }

    // Returns stream of all non-null values
    public static <T> Stream<@NonNull T> streamNullable(@Nullable T a, @Nullable T b, @Nullable T c)
    {
        List<@NonNull T> r = new ArrayList<>();
        if (a != null)
            r.add(a);
        if (b != null)
            r.add(b);
        if (c != null)
            r.add(c);
        return r.stream();
    }

    // Returns stream of all non-null values
    public static <T> Stream<@NonNull T> streamNullable(@Nullable T a, @Nullable T b, @Nullable T c, @Nullable T d)
    {
        List<@NonNull T> r = new ArrayList<>();
        if (a != null)
            r.add(a);
        if (b != null)
            r.add(b);
        if (c != null)
            r.add(c);
        if (d != null)
            r.add(d);
        return r.stream();
    }

    public static <T> Stream<T> concatStreams(Stream<T> a, Stream<T> b)
    {
        return Stream.concat(a, b);
    }
    
    public static <T> Stream<T> concatStreams(Stream<T> a, Stream<T> b, Stream<T> c)
    {
        return Stream.<T>concat(a, Stream.<T>concat(b, c));
    }

    public static <T> Stream<T> concatStreams(Stream<T> a, Stream<T> b, Stream<T> c, Stream<T> d)
    {
        return Stream.<T>concat(Stream.<T>concat(a, b), Stream.<T>concat(c, d));
    }

    public static <T> Stream<T> concatStreams(Stream<T> a, Stream<T> b, Stream<T> c, Stream<T> d, Stream<T> e)
    {
        return Stream.<T>concat(Stream.<T>concat(Stream.<T>concat(a, b), Stream.<T>concat(c, d)), e);
    }
    
    public static <A, B, R> ImmutableList<R> allPairs(List<A> as, List<B> bs, BiFunction<A, B, R> function)
    {
        ImmutableList.Builder<R> r = ImmutableList.builderWithExpectedSize(as.size() * bs.size());
        for (A a : as)
        {
            for (B b : bs)
            {
                r.add(function.apply(a, b));
            }
        }
        return r.build();
    }

    /**
     * Filters a stream down to only the items of the given class
     */
    public static <S, T extends S /*precludes interfaces*/> Stream<@NonNull T> filterClass(Stream<@NonNull S> stream, Class<T> targetClass)
    {
        return stream.<@NonNull T>flatMap(x ->
        {
            if (targetClass.isInstance(x))
                return Stream.<@NonNull T>of(targetClass.cast(x));
            else
                return Stream.<@NonNull T>empty();
        });
    }
    
    public static <T> ImmutableList<T> filterList(List<T> src, Predicate<T> filterBy)
    {
        return src.stream().filter(filterBy).collect(ImmutableList.<T>toImmutableList());
    }

    @SuppressWarnings("nullness")
    public static <T> Stream<@NonNull T> filterOutNulls(Stream<@Nullable T> stream)
    {
        return stream.filter(x -> x != null);
    }

    public static <T extends @NonNull Object> Stream<T> filterOptional(Stream<Optional<T>> stream)
    {
        return stream.flatMap(Optional::stream);
    }

    // Having different arity versions of this prevents the varargs/generics warning
    public static <T> List<T> concat(List<T> a, List<T> b)
    {
        ArrayList<T> r = new ArrayList<>(a.size() + b.size());
        r.addAll(a);
        r.addAll(b);
        return r;
    }
    public static <T> List<T> concat(List<T> a, List<T> b, List<T> c)
    {
        ArrayList<T> r = new ArrayList<>(a.size() + b.size() + c.size());
        r.addAll(a);
        r.addAll(b);
        r.addAll(c);
        return r;
    }
    public static <T> ImmutableList<T> concatI(List<? extends T> a, List<? extends T> b)
    {
        return ImmutableList.<T>builder().addAll(a).addAll(b).build();
    }

    public static <T> OptionalInt findFirstIndex(List<T> curValue, Predicate<T> match)
    {
        for (int i = 0; i < curValue.size(); i++)
        {
            if (match.test(curValue.get(i)))
            {
                return OptionalInt.of(i);
            }
        }
        return OptionalInt.empty();
    }

    public static <T> OptionalInt findLastIndex(List<T> curValue, Predicate<T> match)
    {
        for (int i = curValue.size() - 1; i >= 0; i--)
        {
            if (match.test(curValue.get(i)))
            {
                return OptionalInt.of(i);
            }
        }
        return OptionalInt.empty();
    }

    @SuppressWarnings("valuetype") // Input is @Value, so output will be too
    public static <T> @Value T cast(@Value Object x, Class<T> cls) throws InternalException
    {
        if (cls.isInstance(x))
        {
            return cls.cast(x);
        }
        throw new InternalException("Cannot cast " + x.getClass() + " into " + cls);
    }

    public static <T> @Nullable T castOrNull(@Nullable Object x, Class<T> cls)
    {
        if (cls.isInstance(x))
        {
            return cls.cast(x);
        }
        return null;
    }

    @SuppressWarnings("valuetype")
    public static @Value Object @Value [] castTuple(@Value Object x, int tupleSize) throws InternalException
    {
        Object[] tuple = cast(x, Object[].class);
        if (tuple.length != tupleSize)
            throw new InternalException("Tuple wrong size: " + tupleSize);
        return tuple;
    }

    public static @Value Object replaceNull(@Nullable @Value Object x, @Value Object y)
    {
        return x == null ? y : x;
    }
    
    // Could this be merged with replaceNull, above?
    public static <T> T orElse(@Nullable T x, T y)
    {
        return x != null ? x : y; 
    }

    public static <T> List<T> makeListEx(int len, ExFunction<Integer, T> makeOne) throws InternalException, UserException
    {
        List<T> r = new ArrayList<>();
        for (int i = 0; i < len; i++)
        {
            r.add(makeOne.apply(i));
        }
        return r;
    }

    public static int loadDataOld1(List<String> dataLines, ExConsumer<DataParser> withEachRow) throws UserException, InternalException
    {
        int count = 0;
        for (String lineText : dataLines)
        {
            count += 1;
            try
            {
                parseAsOne(lineText, DataLexer::new, DataParser::new, p ->
                {
                    try
                    {
                        p.startRow();
                        if (!p.isMatchedEOF())
                        {
                            withEachRow.accept(p);
                        }
                        p.endRow();
                        return 0;
                    }
                    catch (ParseCancellationException e)
                    {
                        throw new ParseException("token", p);
                    }
                });
            }
            catch (UserException  e)
            {
                throw new UserException("Error loading data line: \"" + lineText + "\"", e);
            }
        }
        return count;
    }

    public static int loadData(List<String> dataLines, ExConsumer<DataParser2> withEachRow) throws UserException, InternalException
    {
        int count = 0;
        for (String lineText : dataLines)
        {
            count += 1;
            try
            {
                parseAsOne(lineText, DataLexer2::new, DataParser2::new, p ->
                {
                    try
                    {
                        p.startRow();
                        if (!p.isMatchedEOF())
                        {
                            withEachRow.accept(p);
                        }
                        p.endRow();
                        return 0;
                    }
                    catch (ParseCancellationException e)
                    {
                        throw new ParseException("token", p);
                    }
                });
            }
            catch (UserException  e)
            {
                throw new UserException("Error loading data line: \"" + lineText + "\"", e);
            }
        }
        return count;
    }

    public static <T> Optional<T> getLast(List<@NonNull T> list)
    {
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(list.size() - 1));
    }

    public static String literal(Vocabulary vocabulary, int index)
    {
        String s = vocabulary.getLiteralName(index);
        if (s.startsWith("'") && s.endsWith("'"))
            return s.substring(1, s.length() - 1);
        else
            return s;
    }

    // Annotates that a String is universal, e.g. a symbol or a number, which doesn't need translating
    @SuppressWarnings("i18n")
    public static @Localized String universal(String string)
    {
        return string;
    }

    // Annotates that a String is user input, which doesn't need translating
    @SuppressWarnings("i18n")
    public static @Localized String userInput(String string)
    {
        return string;
    }

    @SuppressWarnings("i18n")
    public static @Localized String concatLocal(@Localized String a, @Localized String b)
    {
        return a + b;
    }

    @SuppressWarnings("i18n")
    public static @Localized String concatLocal(ImmutableList<@Localized String> ss)
    {
        return ss.stream().collect(Collectors.joining());
    }

    public static String codePointToString(int codepoint)
    {
        return new String(new int[] {codepoint}, 0, 1);
    }
    
    public static boolean containsCodepoint(String whole, int codepoint)
    {
        return whole.contains(codePointToString(codepoint));
    }

    /**
     * Sets the list to the given new size.  If elements
     * must be added, makeNew is called for each new element.
     * If elements must be removed, disposeOld is called for
     * each old element (removed from the end of the list)
     *
     * @param list
     * @param newSize
     * @param makeNew
     * @param disposeOld
     */
    public static <T> void resizeList(ArrayList<T> list, int newSize, Function<Integer, T> makeNew, Consumer<T> disposeOld)
    {
        while (list.size() < newSize)
            list.add(makeNew.apply(list.size()));
        while (list.size() > newSize)
            disposeOld.accept(list.remove(list.size() - 1));
    }

    public static <T> Stream<Pair<Integer, T>> streamIndexed(List<T> list)
    {
        return IntStream.range(0, list.size()).mapToObj(i -> new Pair<>(i, list.get(i)));
    }

    public static int clampIncl(int lowBoundIncl, int value, int highBoundIncl)
    {
        if (value < lowBoundIncl)
            return lowBoundIncl;
        else if (value > highBoundIncl)
            return highBoundIncl;
        else
            return value;
    }

    public static double clampIncl(double lowBoundIncl, double value, double highBoundIncl)
    {
        if (value < lowBoundIncl)
            return lowBoundIncl;
        else if (value > highBoundIncl)
            return highBoundIncl;
        else
            return value;
    }

    public static String numberToString(Number value)
    {
        String num;
        if (value instanceof Double)
            num = String.format("%f", value.doubleValue());
        else if (value instanceof BigDecimal)
            num = ((BigDecimal)value).toPlainString();
        else
            num =  value.toString();
        return num;
    }

    /**
     * Checks if the two ranges aMin--aMax and bMin--bMax overlap by at least overlapAmount (true = they do overlap by that much or more)
     */
    public static boolean rangeOverlaps(double overlapAmount, double aMin, double aMax, double bMin, double bMax)
    {
        // From https://stackoverflow.com/questions/2953967/built-in-function-for-computing-overlap-in-python

        return Math.max(0, Math.min(aMax, bMax)) - Math.max(aMin, bMin) >= overlapAmount;
    }

    // If item present in map, wrap it in optional, otherwise return empty optional
    // Added bonus: this method is type-safe in the key type, unlike Map.get.
    public static <K extends @NonNull Object, V> Optional<V> getIfPresent(Map<K, V> map, K key)
    {
        // We can't use Optional.ofNullable because the checker (wrongly IMO) rejects that:
        @Nullable V value = map.get(key);
        if (value != null)
            return Optional.of(value);
        else
            return Optional.empty();
    }

    public static @AbsRowIndex int minRow(@AbsRowIndex int a, @AbsRowIndex int b)
    {
        return (a <= b) ? a : b;
    }
    
    public static @AbsRowIndex int maxRow(@AbsRowIndex int a, @AbsRowIndex int b)
    {
        return (a >= b) ? a : b;
    }

    public static @AbsColIndex int minCol(@AbsColIndex int a, @AbsColIndex int b)
    {
        return (a <= b) ? a : b;
    }

    public static @AbsColIndex int maxCol(@AbsColIndex int a, @AbsColIndex int b)
    {
        return (a >= b) ? a : b;
    }

    public static <T extends @NonNull Object> @Initialized T later(@UnknownInitialization T t)
    {
        @SuppressWarnings("assignment")
        @Initialized T r = t; 
        return r;
    }

    public static String preprocessDate(String original)
    {
        // We split on colons, which we preserve (because they are usually in time):
        String[] splitByColon = original.split(":");
        StringBuilder reAssembled = new StringBuilder();
        for (int i = 0; i < splitByColon.length; i++)
        {
            // Generally, we replace any punctuation or space sequence with a single space, before the first colon:
            if (i == 0)
                splitByColon[i] = splitByColon[i].replaceAll("(?U)[^\\p{Alnum}]+", " ");
            reAssembled.append(splitByColon[i]);
            if (i < splitByColon.length - 1)
                reAssembled.append(":");
        }
        return reAssembled.toString();
    }

    /**
     * Turns a list of pairs into an immutablemap, with insertion order matching the given list.
     */
    public static <K, V> ImmutableMap<K, V> pairListToMap(ImmutableList<Pair<K, V>> list)
    {
        ImmutableMap.Builder<K, V> builder = ImmutableMap.builderWithExpectedSize(list.size());
        for (Pair<K, V> pair : list)
        {
            builder.put(pair.getFirst(), pair.getSecond());
        }
        return builder.build();
    }

    public static <T> Stream<T> appendStream(Stream<T> stream, T appendItem)
    {
        return Stream.<T>concat(stream, Stream.<T>of(appendItem));
    }

    // List.get, but throws InternalException if out of bounds
    public static <T> T getI(List<T> list, int index) throws InternalException
    {
        if (index < 0 || index >= list.size())
            throw new InternalException("List index out of bounds: " + index + " versus " + list.size());
        return list.get(index);
    }

    // Creates a live mirror of the given list.  Do not use
    // if the original will change (and you don't want to reflect the change)
    // or if you do not want to keep the original in memory,
    // or if you want to access the returned list multiple times and
    // not have to re-run the mapping.
    public static <S, T> List<T> mappedList(List<S> original, Function<S, T> mapping)
    {
        return new AbstractList<T>()
        {
            @Override
            public T get(int index)
            {
                return mapping.apply(original.get(index));
            }

            @Override
            public int size()
            {
                return original.size();
            }
        };
    }

    // In characters, how long is the common subsequence at the start of the strings?
    public static int longestCommonStart(String a, int aStartAt, String b, int bStartAt)
    {
        int i = 0;
        for (; i + aStartAt < a.length() && i + bStartAt < b.length(); i++)
        {
            if (a.charAt(i + aStartAt) != b.charAt(i + bStartAt))
                break;
        }
        return i;
    }

    public static int longestCommonStartIgnoringCase(String a, int aStartAt, String b, int bStartAt)
    {
        int i = 0;
        for (; i + aStartAt < a.length() && i + bStartAt < b.length(); i++)
        {
            // Borrowed from String's compareIgnoringCase:
            char c1 = a.charAt(i + aStartAt);
            char c2 = b.charAt(i + bStartAt);
            if (c1 != c2) {
                c1 = Character.toUpperCase(c1);
                c2 = Character.toUpperCase(c2);
                if (c1 != c2) {
                    c1 = Character.toLowerCase(c1);
                    c2 = Character.toLowerCase(c2);
                    if (c1 != c2) {
                        break;
                    }
                }
            }
        }
        return i;
    }

    public static String getDetail(DetailContext detail)
    {
        return detail.detailLine().stream().map(l -> l.DETAIL_LINE().getText().trim() + "\n").filter(s -> !s.trim().isEmpty()).collect(Collectors.joining());
    }

    public static String getDetail(DetailPrefixedContext detail)
    {
        return detail.detailLine().stream().map(l -> l.DETAIL_LINE().getText().trim() + "\n").filter(s -> !s.trim().isEmpty()).collect(Collectors.joining());
    }

    public static ImmutableList<String> getDetailLines(DetailPrefixedContext detail)
    {
        return detail.detailLine().stream().map(l -> l.DETAIL_LINE().getText().trim()).filter(s -> !s.isEmpty()).map(s -> s + "\n").collect(ImmutableList.<String>toImmutableList());
    }

    public static String getDetail(MainParser2.DetailContext detail)
    {
        return detail.detailLine().stream().map(l -> l.DETAIL_LINE().getText().trim() + "\n").filter(s -> !s.trim().isEmpty()).collect(Collectors.joining());
    }

    public static String getDetail(TableParser2.DetailContext detail)
    {
        return detail.detailLine().stream().map(l -> l.DETAIL_LINE().getText().trim() + "\n").filter(s -> !s.trim().isEmpty()).collect(Collectors.joining());
    }

    public static ImmutableList<String> getDetailLines(TableParser2.DetailContext detail)
    {
        return detail.detailLine().stream().map(l -> l.DETAIL_LINE().getText().trim()).filter(s -> !s.isEmpty()).map(s -> s + "\n").collect(ImmutableList.<String>toImmutableList());
    }

    public static ImmutableList<String> getDetailLines(MainParser2.DetailContext detail)
    {
        return detail.detailLine().stream().map(l -> l.DETAIL_LINE().getText().trim()).filter(s -> !s.isEmpty()).map(s -> s + "\n").collect(ImmutableList.<String>toImmutableList());
    }

    public static String[] splitLines(String input)
    {
        return input.split("\\r?\\n");
    }

    public interface WrappedCharSequence extends CharSequence
    {
        public int translateWrappedToOriginalPos(int position);
    }
    
    
    // We wrap the entire original sequence, but we only do
    // the date preprocess beginning at startPos
    public static WrappedCharSequence wrapPreprocessDate(CharSequence original, final int startPos)
    {
        // Until we see first colon, we replace any sequences of
        // non-alphanumeric characters with a single space
        return new WrappedCharSequence()
        {
            List<IndexRange> replaceRanges = new ArrayList<>();
            int checkedUpToInOriginal = startPos;
            int firstColonIndexInOriginal = Integer.MAX_VALUE;
            
            @Override
            public int length()
            {
                int length;
                for (length = startPos - 1; length < original.length() && translateWrappedToOriginalPos(length) < original.length(); length++)
                {
                    checkUpTo(length + 1);
                }
                return length;
            }

            @Override
            public int translateWrappedToOriginalPos(int target)
            {
                return translateWrappedToOriginal(target).getFirst();
            }

            // Translated pos, and true if in a replaced range
            private Pair<Integer, Boolean> translateWrappedToOriginal(int wrapped)
            {
                // Wrapped to original is identity transformation with no ranges.
                // Effect of range is to condense range into one char
                int add = 0;
                for (IndexRange rr : replaceRanges)
                {
                    if (wrapped + add == rr.start)
                    {
                        // Falls in range; replace with single space.
                        return new Pair<>(rr.start, true);
                    }
                    else if (wrapped + add > rr.start)
                    {
                        // Ranges are start(incl) to end(excl) and are replaced by single space.
                        // So e.g. given "a   b" range will be 1, 4 and we should subtract two chars:
                        add += (rr.end - rr.start - 1);
                    }
                    else
                    {
                        // Ranges are in order, so future ranges
                        // won't be before us, either
                        break;
                    }
                }
                return new Pair<>(wrapped + add, false);
            }

            @Override
            public char charAt(final int index)
            {
                if (index < 0)
                    throw new IllegalArgumentException("Index cannot be negative: " + index);
                if (index < startPos)
                    return original.charAt(index);

                checkUpTo(index);

                Pair<Integer, Boolean> mapped = translateWrappedToOriginal(index);
                if (mapped.getSecond())
                    return ' ';
                else
                    return original.charAt(mapped.getFirst());
            }

            private void checkUpTo(int index)
            {
                while (checkedUpToInOriginal < index && checkedUpToInOriginal < original.length() - 1)
                {
                    // Must do more checking:
                    checkedUpToInOriginal += 1;
                    char c = original.charAt(checkedUpToInOriginal);
                    boolean notLetterNorDigit = !Character.isLetterOrDigit(c);
                    boolean seenColon = firstColonIndexInOriginal != Integer.MAX_VALUE;
                    boolean whitespace = Character.isWhitespace(c);
                    if (c == ':')
                    {
                        if (!seenColon)
                            firstColonIndexInOriginal = Math.min(firstColonIndexInOriginal, checkedUpToInOriginal);
                    }
                    else if (notLetterNorDigit && (!seenColon || whitespace))
                    {
                        int start = checkedUpToInOriginal;
                        int end = start + 1;
                        while (end < original.length() && ((!Character.isLetterOrDigit(original.charAt(end)) && !whitespace) || (Character.isWhitespace(original.charAt(end)) && whitespace)))
                        {
                            end += 1;
                        }
                        // Form a replacement range
                        replaceRanges.add(new IndexRange(start, end));
                        checkedUpToInOriginal = end;
                    }
                }
            }

            @Override
            public CharSequence subSequence(int start, int end)
            {
                // For this, we use a string copy:
                char[] chars = new char[end - start];
                for (int i = start; i < end; i++)
                {
                    chars[i - start] = charAt(i);
                }
                return new String(chars);
            }

            @Override
            public String toString()
            {
                return subSequence(0, length()).toString();
            }
        };
    }

    public static <T> ImmutableList<T> replicateM(int length, Supplier<T> make)
    {
        ImmutableList.Builder<T> r = ImmutableList.builderWithExpectedSize(length);
        for (int i = 0; i < length; i++)
        {
            r.add(make.get());
        }
        return r.build();
    }
    
    public static <T> ImmutableList<T> replicateM_Ex(int length, ExSupplier<T> make) throws InternalException, UserException
    {
        ImmutableList.Builder<T> r = ImmutableList.builderWithExpectedSize(length);
        for (int i = 0; i < length; i++)
        {
            r.add(make.get());
        }
        return r.build();
    }

    public static class ReadState
    {
        private final File file;
        private final Iterator<String> lineStream;

        // Pass 0 to start at beginning of file, any other number
        // to skip that many lines at the beginning.
        public ReadState(File file, Charset charset, int firstLine) throws IOException
        {
            this.file = file;
            lineStream = com.google.common.io.Files.asCharSource(file, charset).lines().skip(firstLine).iterator();
            //Files.lines(file.toPath(), charset).skip(firstLine).iterator();
        }

        public @Nullable String nextLine() throws IOException
        {
            return lineStream.hasNext() ? lineStream.next() : null;
        }

        public String getAbsolutePath()
        {
            return file.getAbsolutePath();
        }
    }

    public static class IndexRange
    {
        public final int start;
        public final int end;

        public IndexRange(int start, int end)
        {
            this.start = start;
            this.end = end;
        }
    }

    public static ReadState readColumnChunk(ReadState readState, @Nullable String delimiter, @Nullable String quote, int columnIndex, ArrayList<String> fill) throws IOException
    {
        // This would send us into an infinite loop, so guard against it:
        if (quote != null && quote.isEmpty())
            throw new IllegalArgumentException("Quote cannot be empty");
        
        loopOverLines: for (int lineRead = 0; lineRead < 20; lineRead++)
        {
            String line = readState.nextLine();
            if (line == null)
                break loopOverLines; // No more lines to read!
            if (delimiter == null)
            {
                // All just one column, so must be us:
                fill.add(line);
            }
            else
            {
                int currentCol = 0;
                int currentColStart = 0;
                boolean inQuote = false;
                // If null, unused for this column:
                @Nullable StringBuilder withoutQuotes = null;
                for (int i = 0; i < line.length(); i++)
                {
                    if (!inQuote && line.regionMatches(i, delimiter, 0, delimiter.length()))
                    {
                        if (currentCol == columnIndex)
                        {
                            fill.add(withoutQuotes != null ? withoutQuotes.toString() : line.substring(currentColStart, i));
                            // No point going further in this line as we've found our column:
                            continue loopOverLines;
                        }
                        currentCol += 1;
                        currentColStart = i + delimiter.length();
                        // 1 will be added by loop:
                        i += delimiter.length() - 1;
                        // TODO this doesn't handle the last column (should fail test)
                        withoutQuotes = null;
                    }
                    else if (quote != null && line.regionMatches(i, quote, 0, quote.length()))
                    {
                        if (!inQuote)
                        {
                            inQuote = true;
                            if (currentCol == columnIndex)
                                withoutQuotes = new StringBuilder();
                        }
                        else
                        {
                            if (line.regionMatches(i + quote.length(), quote, 0, quote.length()))
                            {
                                // Escaped quote, no problem
                                i += quote.length();
                                if (withoutQuotes != null)
                                    withoutQuotes.append(quote);
                            }
                            else
                            {
                                inQuote = false;
                            }
                        }
                        // 1 will be added by loop:
                        i += quote.length() - 1;
                    }
                    else if (withoutQuotes != null)
                    {
                        withoutQuotes.append(line.charAt(i));
                    }
                }
                // Might be the last column we want:
                if (currentCol == columnIndex)
                {
                    fill.add(withoutQuotes != null ? withoutQuotes.toString() : line.substring(currentColStart));
                }
                else if (currentCol < columnIndex)
                {
                    // Blank:
                    fill.add("");
                }
            }
        }
        return readState;
    }

    public static int countIn(String small, String large)
    {
        int total = 0;
        for (int last = large.indexOf(small); last != -1; last = large.indexOf(small, last + 1))
        {
            total += 1;
        }
        return total;
    }

    public static ReadState skipFirstNRows(File src, Charset charset, final int headerRows) throws IOException
    {
        return new ReadState(src, charset, headerRows);
    }

    public static int compareNumbers(final Number a, final Number b)
    {
        return compareNumbers(a, b, null);
    }
    
    public static enum EpsilonType { ABSOLUTE, RELATIVE };

    public static int compareNumbers(final Number a, final Number b, @Nullable Pair<EpsilonType, BigDecimal> epsilon)
    {
        if (a instanceof BigDecimal || b instanceof BigDecimal || epsilon != null)
        {
            // Compare as BigDecimals:
            BigDecimal da, db;
            if (a instanceof BigDecimal)
                da = (BigDecimal)a;
            else
                da = BigDecimal.valueOf(((Number)a).longValue());
            if (b instanceof BigDecimal)
                db = (BigDecimal)b;
            else
                db = BigDecimal.valueOf(((Number)b).longValue());
            if (epsilon == null)
                return da.compareTo(db);
            else
            {
                try
                {
                    if (epsilon.getFirst() == EpsilonType.RELATIVE)
                    {
                        if (da.equals(db) || (!da.equals(BigDecimal.ZERO) && da.subtract(db).abs().divide(da, MathContext.DECIMAL128).subtract(BigDecimal.ONE).compareTo(epsilon.getSecond()) < 0 )
                                || (!db.equals(BigDecimal.ZERO) && da.subtract(db).abs().divide(db, MathContext.DECIMAL128).subtract(BigDecimal.ONE).compareTo(epsilon.getSecond()) < 0))
                            return 0;
                    }
                    else if (epsilon.getFirst() == EpsilonType.ABSOLUTE)
                    {
                        if (da.subtract(db, MathContext.DECIMAL128).abs().compareTo(epsilon.getSecond()) <= 0)
                            return 0;
                    }
                    return da.compareTo(db);
                }
                catch (ArithmeticException e)
                {
                    return da.compareTo(db);
                }
            }
        }
        else
        {
            // Standard numbers, compare as longs:
            return Long.compare(a.longValue(), b.longValue());
        }

    }

    public static class DescriptiveErrorListener extends BaseErrorListener
    {
        public final List<String> errors = new ArrayList<>();

        @Override
        public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol,
                                int line, int charPositionInLine,
                                String msg, RecognitionException e)
        {
            String sourceName = recognizer.getInputStream().getSourceName();
            if (!sourceName.isEmpty()) {
                sourceName = String.format("%s:%d:%d: ", sourceName, line, charPositionInLine);
            }

            errors.add(sourceName+"line "+line+":"+charPositionInLine+" "+msg);
        }
    }
    
    public static abstract class Record
    {
        public abstract @Value Object getField(@ExpressionIdentifier String name) throws InternalException;
        
        public abstract ImmutableMap<@ExpressionIdentifier String, @Value Object> getFullContent() throws InternalException;
    }
    
    public static final class RecordMap extends Record
    {
        private final ImmutableMap<@ExpressionIdentifier String, @Value Object> values;

        public RecordMap(Map<@ExpressionIdentifier String, @Value Object> values)
        {
            this.values = ImmutableMap.copyOf(values);
        }
        
        @SuppressWarnings("valuetype")
        public static @ImmediateValue RecordMap immediate(Map<@ExpressionIdentifier String, @ImmediateValue Object> values)
        {
            return new RecordMap(values);
        }

        @Override
        public @Value Object getField(@ExpressionIdentifier String name) throws InternalException
        {
            @Value Object value = values.get(name);
            if (value == null)
                throw new InternalException("Record unexpectedly lacking field: \"" + name + "\"");
            return value;
        }

        @Override
        public ImmutableMap<@ExpressionIdentifier String, @Value Object> getFullContent() throws InternalException
        {
            return values;
        }

        // Useful for debugging
        @Override
        public String toString()
        {
            return "RecordMap{" +
                    "values=" + values +
                    '}';
        }
    }
    
    // Eugh -- nasty way to represent ValueFunction which must depend on Expression
    public static abstract class ValueFunctionBase
    {
    }

    @OnThread(Tag.Simulation)
    public static abstract class ListEx
    {
        @OnThread(Tag.Any)
        public ListEx() {}

        public abstract int size() throws InternalException, UserException;
        public abstract @Value Object get(int index) throws InternalException, UserException;

        // For comparison during testing
        @Override
        public int hashCode()
        {
            try
            {
                int size = size();
                int result = size;
                for (int i = 0; i < size; i++)
                {
                    result = 31 * result + get(i).hashCode();
                }
                return result;
            }
            catch (InternalException | UserException e)
            {
                throw new RuntimeException(e);
            }
        }

        // For comparison during testing
        @Override
        public boolean equals(@Nullable Object obj)
        {
            if (obj == null || !(obj instanceof ListEx))
                return false;
            ListEx them = (ListEx)obj;
            try
            {
                int size = size();
                if (size != them.size())
                    return false;
                for (int i = 0; i < size; i++)
                {
                    if (Utility.compareValues(get(i), them.get(i)) != 0)
                        return false;
                }
                return true;
            }
            catch (InternalException | UserException e)
            {
                throw new RuntimeException(e);
            }
        }

        // To help during testing:

        @Override
        public String toString()
        {
            try
            {
                int size = size();
                StringBuilder b = new StringBuilder("[");
                for (int i = 0; i < size; i++)
                    b.append(i == 0 ? "" : ", ").append(get(i).toString());
                b.append("]");
                return b.toString();
            }
            catch (InternalException | UserException e)
            {
                return "ERR: " + e.getLocalizedMessage();
            }
        }

        @SuppressWarnings("valuetype")
        public static @Value ListEx empty()
        {
            return new ListEx()
            {
                @Override
                public int size() throws InternalException, UserException
                {
                    return 0;
                }

                @Override
                public @Value Object get(int index) throws InternalException, UserException
                {
                    throw new InternalException("Cannot access element of empty list");
                }
            };
        }
    }

    public static final @Value class ListExList extends ListEx
    {
        private final List<? extends @Value Object> items;

        @SuppressWarnings({"valuetype", "nullness"})
        public @Value <@Value T> ListExList(List<T> items)
        {
            this.items = items;
        }

        @SuppressWarnings({"valuetype", "nullness"})
        public static @ImmediateValue <T extends @ImmediateValue Object> ListExList immediate(List<T> items)
        {
            return new ListExList(items);
        }

        @Override
        public int size() throws InternalException, UserException
        {
            return items.size();
        }

        @Override
        public @Value Object get(int index) throws InternalException, UserException
        {
            return items.get(index);
        }
    }

    public static File getAutoSaveDirectory() throws IOException
    {
        File dir = new File(getStorageDirectory(), "autosave");
        dir.mkdirs();
        if (!dir.exists() || !dir.isDirectory())
        {
            throw new IOException("Cannot create auto-save directory: " + dir.getAbsolutePath());
        }
        return dir;
    }

    public static File getUndoDirectory() throws IOException
    {
        File dir = new File(getStorageDirectory(), "undo");
        dir.mkdirs();
        if (!dir.exists() || !dir.isDirectory())
        {
            throw new IOException("Cannot create undo directory: " + dir.getAbsolutePath());
        }
        return dir;
    }

    public static File getStorageDirectory() throws IOException
    {
        File dir = new File(System.getProperty("user.home"), ".columnal");
        dir.mkdirs();
        if (!dir.exists() || !dir.isDirectory())
        {
            throw new IOException("Cannot create profile directory: " + dir.getAbsolutePath());
        }
        return dir;
    }

    public static File getNewAutoSaveFile() throws IOException
    {
        File autoSaveDir = getAutoSaveDirectory();
        for (int i = 1; ;i++)
        {
            File next = new File(autoSaveDir, "Untitled-" + i);
            if (next.createNewFile())
            {
                return next;
            }
        }
    }

    @OnThread(Tag.FXPlatform)
    public static @NonNull ImmutableList<File> readRecentFilesList()
    {
        try
        {
            File mruFile = new File(getStorageDirectory(), MRU_FILE_NAME);
            if (mruFile.exists())
            {
                return FileUtils.readLines(mruFile, "UTF-8").stream().<File>map(File::new).collect(ImmutableList.<File>toImmutableList());
            }
        }
        catch (IOException e)
        {
            Log.log(e);
        }
        return ImmutableList.of();
    }

    // All loaded properties (each one is stored in a separate file)
    private static Map<String, Properties> properties = new HashMap<>();

    public static @Nullable String getProperty(String fileName, String propertyName)
    {
        Properties props = getPropertiesFile(fileName);
        return props.getProperty(propertyName);
    }

    public static void setProperty(String fileName, String propertyName, String value)
    {
        Properties props = getPropertiesFile(fileName);
        props.setProperty(propertyName, value);
        try (FileWriter w = new FileWriter(new File(Utility.getStorageDirectory(), fileName), StandardCharsets.UTF_8))
        {
            props.store(w, "");
        }
        catch (IOException e)
        {
            Log.log(e);
        }
    }

    private static Properties getPropertiesFile(String fileName)
    {
        return properties.computeIfAbsent(fileName, f -> {
            Properties p = new Properties();
            try
            {
                File propFile = new File(Utility.getStorageDirectory(), fileName);
                if (propFile.exists())
                {
                    try (FileReader r = new FileReader(propFile, StandardCharsets.UTF_8))
                    {
                        p.load(r);
                    }
                }
            }
            catch (IOException e)
            {
                Log.log(e);
            }
            return p;
        });
    }

    public static OptionalInt mapOptionalInt(OptionalInt src, UnaryOperator<Integer> op)
    {
        if (src.isPresent())
            return OptionalInt.of(op.apply(src.getAsInt()));
        else
            return OptionalInt.empty();
    }
    
    // Between each pair of items in the first stream, makes a new item and adds it 
    public static <T> Stream<T> intercalateStreamM(Stream<T> original, Supplier<T> makeSpacer)
    {
        // Not ideal, but it works:
        @UnknownKeyFor ImmutableList<T> origList = original.collect(ImmutableList.<T>toImmutableList());
        ArrayList<T> inclSpacers = new ArrayList<>();
        for (int i = 0; i < origList.size(); i++)
        {
            if (i > 0)
                inclSpacers.add(makeSpacer.get());
            inclSpacers.add(origList.get(i));
        }
        return inclSpacers.stream();
    }
    
    public static boolean lexesAs(String src, Function<CharStream, Lexer> makeLexer, int tokenType)
    {
        CodePointCharStream inputStream = CharStreams.fromString(src);
        Lexer lexer = makeLexer.apply(inputStream);
        DescriptiveErrorListener errorListener = new DescriptiveErrorListener();
        lexer.addErrorListener(errorListener);
        Token token = lexer.nextToken();
        return errorListener.errors.isEmpty() && token.getType() == tokenType && token.getText().equals(src);
    }
    
    
    public static <T, R> @PolyNull R onNullable(@PolyNull T t, Function<@NonNull T, @NonNull R> f)
    {
        if (t == null)
        {
            return null;
        }
        else
        {
            @NonNull R r = f.apply(t);
            return r;
        }
    }
    
    // Given an original user-entered String, and a stream of options
    // that can be converted to a String (or several alternatives), returns an ordered stream
    // (most likely first) of items that the user may have mis-spelt
    // Not all items are returned, only those that meet a threshold.
    public static <T> Stream<T> findAlternatives(String raw, Stream<T> possibleItems, Function<T, Stream<String>> extractString)
    {
        Damerau d = new Damerau();
        double threshold = raw.length() <= 4 ? 1 : 2;
        Stream<Pair<Double, T>> withDistance = possibleItems.flatMap(item -> {
            // For each item, only keep the string that is the closest match:
            return extractString.apply(item).map(s -> new Pair<>(d.distance(raw.toLowerCase(), s.toLowerCase()), item)).sorted(Comparator.comparing(Pair::getFirst)).limit(1);
        });
        return withDistance.filter(p -> p.getFirst() <= threshold).sorted(Comparator.comparing(Pair::getFirst)).map(Pair::getSecond);
    }
    
    // Like longer.startsWith(shorter) but ignoring case
    public static boolean startsWithIgnoreCase(String longer, String shorter)
    {
        return longer.regionMatches(true, 0, shorter, 0, shorter.length());
    }

    public static boolean startsWithIgnoreCase(String longer, String shorter, int offsetInLonger)
    {
        return longer.regionMatches(true, offsetInLonger, shorter, 0, shorter.length());
    }

    /**
     * Type-safe get method for Map.  Note important that it's Map[K,V] and not Map[? super K, V]
     */
    public static <K extends @NonNull Object, V> @Nullable V get(Map<K, V> map, K key)
    {
        return map.get(key);
    }

    /**
     * Type-safe get method for Map.  Note important that it's Map[K,V] and not Map[? super K, V]
     */
    public static <K extends @NonNull Object, V, E extends Throwable> V getOrThrow(Map<K, V> map, K key, Supplier<E> makeException) throws E
    {
        V v = map.get(key);
        if (v != null)
            return v;
        else
            throw makeException.get();
    }

    /**
     * Type-safe contains method for Set.  Note important that it's Map[K,V] and not Map[? super K, V]
     */
    public static <K extends @NonNull Object> boolean contains(Set<K> set, K key)
    {
        return set.contains(key);
    }

    /**
     * For running simulation on FX; only to be used where you're certain it's safe.
     */
    @OnThread(Tag.Any)
    public static <T> T launderSimulationEx(SimulationSupplier<T> simulationSupplier) throws UserException, InternalException
    {
        return ((ExSupplier<T>)simulationSupplier::get).get();
    }
}
