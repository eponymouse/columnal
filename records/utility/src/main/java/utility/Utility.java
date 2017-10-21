package utility;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.MathContext;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.time.temporal.TemporalAccessor;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import annotation.userindex.qual.UserIndex;
import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.css.Styleable;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;
import javafx.stage.Modality;
import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.BailErrorStrategy;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.Vocabulary;
import org.antlr.v4.runtime.misc.ParseCancellationException;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.apache.commons.io.FileUtils;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.*;
import org.checkerframework.dataflow.qual.Pure;
import org.sosy_lab.common.rationals.Rational;
import records.error.InternalException;
import records.error.ParseException;
import records.error.UserException;
import records.grammar.DataLexer;
import records.grammar.DataParser;
import records.grammar.MainParser.DetailContext;
import threadchecker.OnThread;
import threadchecker.Tag;

/**
 * Created by neil on 20/10/2016.
 */
public class Utility
{
    @OnThread(Tag.FXPlatform)
    private static @MonotonicNonNull ObservableList<File> mruList;
    public static final String MRU_FILE_NAME = "recent.mru";
    @OnThread(value = Tag.Any, requireSynchronized = true)
    private static final Map<Thread, StackTraceElement[]> threadedCallers = new WeakHashMap<>();

    public static <T, R> List<@NonNull R> mapList(List<@NonNull T> list, Function<@NonNull T, @NonNull R> func)
    {
        ArrayList<@NonNull R> r = new ArrayList<>(list.size());
        for (T t : list)
            r.add(func.apply(t));
        return r;
    }

    @OnThread(Tag.Simulation)
    public static <T, R> List<@NonNull R> mapListInt(List<@NonNull T> list, FunctionInt<@NonNull T, @NonNull R> func) throws InternalException
    {
        ArrayList<@NonNull R> r = new ArrayList<>(list.size());
        for (T t : list)
            r.add(func.apply(t));
        return r;
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
        return ImmutableList.copyOf(mapListEx(list, func));
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
        ArrayList<R> r = new ArrayList<>();
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
            // Some content with newlines; ignore blank extra line if \n is very end of file:
                return lnr.getLineNumber() + 1 - (lastWasNewline ? 1 : 0);
        }
    }

    // Given a table of values, skips the first "skipRows" rows,
    // Then extracts all values which are not from completely blank rows.
    public static List<String> sliceSkipBlankRows(List<List<String>> vals, int skipRows, int columnIndex)
    {
        List<String> items = new ArrayList<>(vals.size());
        for (int i = skipRows; i < vals.size(); i++)
        {
            List<String> row = vals.get(i);
            if (!row.isEmpty() && !row.stream().allMatch(String::isEmpty))
                items.add(row.get(columnIndex));
        }
        return items;
    }

    @OnThread(Tag.FX)
    public static void addStyleClass(@UnknownInitialization Styleable styleable, String... classes)
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
    public static int compareLists(List<@NonNull @Value ?> a, List<@NonNull @Value ?> b, @Nullable BigDecimal epsilon) throws InternalException, UserException
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
    public static int compareLists(ListEx a, ListEx b, @Nullable BigDecimal epsilon) throws InternalException, UserException
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
    public static int compareValues(@Value Object ax, @Value Object bx, @Nullable BigDecimal epsilon) throws InternalException, UserException
    {
        int cmp;
        if (ax instanceof Number)
            cmp = compareNumbers(ax, bx, epsilon);
        else if (ax instanceof List)
            cmp = compareLists((List<@NonNull @Value ?>)ax, (List<@NonNull @Value ?>)bx, epsilon);
        else if (ax instanceof ListEx)
            cmp = compareLists((ListEx)ax, (ListEx)bx, epsilon);
        else if (ax instanceof Comparable)
        {
            // Need to be declaration to use annotation:
            @SuppressWarnings("unchecked")
            int cmpTmp = ((Comparable<Object>) ax).compareTo(bx);
            cmp = cmpTmp;
        }
        else if (ax instanceof TaggedValue)
        {
            TaggedValue at = (TaggedValue) ax;
            TaggedValue bt = (TaggedValue) bx;
            cmp = Integer.compare(at.getTagIndex(), bt.getTagIndex());
            if (cmp != 0)
                return cmp;
            @Value Object a2 = at.getInner();
            @Value Object b2 = bt.getInner();
            if (a2 != null && b2 != null)
                return compareValues(a2, b2, epsilon);
            return 0; // Assume bx null too, if types match.
        }
        else if (ax instanceof Object[])
        {
            @Value Object[] ao = (@Value Object[]) ax;
            @Value Object[] bo = (@Value Object[]) bx;
            if (ao.length != bo.length)
                throw new InternalException("Trying to compare tuples of different size");
            for (int i = 0; i < ao.length; i++)
            {
                cmp = compareValues(ao[i], bo[i], epsilon);
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

    public static Number getIntegerPart(Number number)
    {
        if (number instanceof BigDecimal)
        {
            return ((BigDecimal)number).toBigInteger();
        }
        else
            return number;
    }

    public static BigDecimal toBigDecimal(Number n)
    {
        if (n instanceof BigDecimal)
            return (BigDecimal) n;
        else
            return BigDecimal.valueOf(n.longValue());
    }

    public static <R, PARSER extends Parser> R parseAsOne(String input, Function<CharStream, Lexer> makeLexer, Function<TokenStream, PARSER> makeParser, ExFunction<PARSER, R> withParser) throws InternalException, UserException
    {
        ANTLRInputStream inputStream = new ANTLRInputStream(input);
        return parse(makeLexer, makeParser, withParser, inputStream);
    }

    public static <R, PARSER extends Parser> R parseAsOne(InputStream input, Function<CharStream, Lexer> makeLexer, Function<TokenStream, PARSER> makeParser, ExFunction<PARSER, R> withParser) throws InternalException, UserException, IOException
    {
        ANTLRInputStream inputStream = new ANTLRInputStream(input);
        return parse(makeLexer, makeParser, withParser, inputStream);
    }

    private static <R, PARSER extends Parser> R parse(Function<CharStream, Lexer> makeLexer, Function<TokenStream, PARSER> makeParser, ExFunction<PARSER, R> withParser, ANTLRInputStream inputStream) throws UserException, InternalException
    {
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
                throw new UserException("Parse errors while loading:\n" + del.errors.stream().collect(Collectors.joining("\n")));
            return r;
        }
        catch (ParseCancellationException e)
        {
            throw new ParseException(parser, e);
        }
    }

    public static <T> List<T> consList(T header, List<T> data)
    {
        ArrayList<T> r = new ArrayList<T>();
        r.add(header);
        r.addAll(data);
        return r;
    }

    public static void report(InternalException e)
    {
        e.printStackTrace(); // TODO log and send back
    }

    public static Number parseNumber(String number) throws UserException
    {
        // First try as a long:
        try
        {
            return Long.valueOf(number);
        }
        catch (NumberFormatException ex) { }
        // Last try: big decimal (and re-throw if not)
        try
        {
            return new BigDecimal(number, MathContext.DECIMAL128);
        }
        catch (NumberFormatException e)
        {
            throw new UserException("Problem parsing number \"" + number + "\"");
        }
    }

    public static OptionalInt parseIntegerOpt(String number)
    {
        // First try as an integer:
        try
        {
            return OptionalInt.of(Integer.valueOf(number));
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
            return OptionalDouble.of(Double.valueOf(number));
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

    // If !add, subtract
    public static Number addSubtractNumbers(Number lhs, Number rhs, boolean add)
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

    public static Number multiplyNumbers(Number lhs, Number rhs)
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

    public static Number divideNumbers(Number lhs, Number rhs)
    {
        if (lhs instanceof BigDecimal || rhs instanceof BigDecimal)
        {
            return toBigDecimal(lhs).divide(toBigDecimal(rhs), MathContext.DECIMAL128);
        }
        else
        {
            long lhsLong = lhs.longValue();
            long rhsLong = rhs.longValue();
            // Exact division possible:
            if (lhsLong % rhsLong == 0 && (lhsLong != Long.MIN_VALUE || rhsLong != -1))
                return lhsLong / rhsLong;
            else
                return divideNumbers(toBigDecimal(lhs), toBigDecimal(rhs));
        }
    }

    public static Number raiseNumber(Number lhs, Number rhs) throws UserException
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

    public static <T> T withNumber(Object num, ExFunction<Long, T> withLong, ExFunction<BigDecimal, T> withBigDecimal) throws InternalException, UserException
    {
        if (num instanceof BigDecimal)
            return withBigDecimal.apply((BigDecimal) num);
        else
            return withLong.apply(((Number)num).longValue());
    }

    public static boolean isIntegral(Object o) throws UserException, InternalException
    {
        // From http://stackoverflow.com/a/12748321/412908
        return withNumber(o, x -> true, bd -> {
            return bd.signum() == 0 || bd.scale() <= 0 || bd.stripTrailingZeros().scale() <= 0;
        });
    }

    @SuppressWarnings("i18n")
    public static void log(String info, Throwable e)
    {
        System.err.println(info);
        // Print our stack trace
        System.err.println(e);
        StackTraceElement[] trace = e.getStackTrace();
        for (StackTraceElement traceElement : trace)
            System.err.println("\tat " + traceElement);

        // Print suppressed exceptions, if any
        for (Throwable se : e.getSuppressed())
            log("Suppressed:", se);

        // Print cause, if any
        Throwable ourCause = e.getCause();
        if (ourCause != null)
            log("Caused by:", ourCause);

        synchronized (Utility.class)
        {
            StackTraceElement[] el = threadedCallers.get(Thread.currentThread());
            if (el != null)
            {
                System.err.println("Original caller:");
                for (StackTraceElement traceElement : el)
                    System.err.println("\tat " + traceElement);
            }
        }
    }

    /**
     * For the current thread, store the stack as extra info that will be printed
     * if an exception is logged in this thread.  Will be overwritten by another
     * call to this same method on the same thread.
     */
    public synchronized static void storeThreadedCaller(StackTraceElement @Nullable [] stack)
    {
        if (stack != null)
            threadedCallers.put(Thread.currentThread(), stack);
        else
            threadedCallers.remove(Thread.currentThread());
    }

    public static void log(Exception e)
    {
        log("", e);
    }

    public static void logStackTrace(String s)
    {
        try
        {
            throw new Exception(s);
        }
        catch (Exception e)
        {
            log(e);
        }
    }

    public static <T> Iterable<T> iterableStream(Stream<T> values)
    {
        return () -> values.iterator();
    }

    public static Iterable<Integer> iterableStream(IntStream values)
    {
        return () -> values.iterator();
    }

    public static <T> String listToString(List<@NonNull T> options)
    {
        return "[" + options.stream().map(Object::toString).collect(Collectors.joining(", ")) + "]";
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
    public static @Value Object @Value[] valueTuple(@Value Object value, int size) throws InternalException
    {
        if (!(value instanceof Object[]))
            throw new InternalException("Unexpected type problem: expected tuple but found " + value.getClass());
        if (size != ((Object[]) value).length)
            throw new InternalException("Unexpected tuple size: expected " + size + " but found " + ((Object[]) value).length);
        return (Object[]) value;
    }

    @OnThread(Tag.Simulation)
    public static @Value Object getAtIndex(@Value ListEx objects, @UserIndex int userIndex) throws UserException, InternalException
    {
        if (userIndex < 1 || userIndex > objects.size())
            throw new UserException("List index " + userIndex + " out of bounds, should be between 1 and " + objects.size() + " (inclusive)");
        // User index is one-based:
        return objects.get(userIndex - 1);
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
    public static <T> int indexOfRef(List<? extends T> list, @UnknownInitialization T item)
    {
        for (int i = 0; i < list.size(); i++)
        {
            if (list.get(i) == item)
                return i;
        }
        return -1;
    }

    public static <T> boolean containsRef(List<? extends T> list, @UnknownInitialization T item)
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
        getRecentFilesList();

        // This may cause multiple file writes of MRU, but we can live with that:
        mruList.removeAll(src);
        mruList.removeAll(src.getAbsoluteFile());
        mruList.add(0, src);
        while (mruList.size() > 15)
            mruList.remove(mruList.size() - 1);
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

    /**
     * Gets rid of beginning and trailing spaces, and collapses all other
     * consecutive whitespace into a single space.
     */
    public static String collapseSpaces(String s)
    {
        return s.replaceAll("(?U)\\s+", " ").trim();
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

    @SuppressWarnings("nullness")
    public static <T> Stream<@NonNull T> filterOutNulls(Stream<@Nullable T> stream)
    {
        return stream.filter(x -> x != null);
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
    public static <T> ImmutableList<T> concatI(List<T> a, List<T> b)
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

    @SuppressWarnings("value") // Input is value, so output can be too
    public static <T> @Value T cast(@Value Object x, Class<T> cls) throws InternalException
    {
        if (cls.isInstance(x))
        {
            return cls.cast(x);
        }
        throw new InternalException("Cannot cast " + x.getClass() + " into " + cls);
    }

    public static @Value Object replaceNull(@Nullable @Value Object x, @Value Object y)
    {
        return x == null ? y : x;
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

    public static int loadData(DetailContext detail, ExConsumer<DataParser> withEachRow) throws UserException, InternalException
    {
        int count = 0;
        for (TerminalNode line : detail.DETAIL_LINE())
        {
            count += 1;
            String lineText = line.getText();
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

    @SuppressWarnings("i18n")
    public static @Localized String concatLocal(@Localized String a, @Localized String b)
    {
        return a + b;
    }

    public static String codePointToString(int codepoint)
    {
        return new String(new int[] {codepoint}, 0, 1);
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

    public static class ReadState
    {
        private final File file;
        private final Iterator<String> lineStream;

        // Pass 0 to start at beginning of file, any other number
        // to skip that many lines at the beginning.
        public ReadState(File file, Charset charset, int firstLine) throws IOException
        {
            this.file = file;
            //TODO change to lines() not readLines().stream() to handle large files:
            lineStream = com.google.common.io.Files.asCharSource(file, charset).readLines().stream().skip(firstLine).iterator();
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

        public int getLength()
        {
            return end - start;
        }
    }

    public static ReadState readColumnChunk(ReadState readState, @Nullable String delimiter, int columnIndex, ArrayList<String> fill, @Nullable ArrayList<Pair<String, IndexRange>> fillFullAndIndexes) throws IOException
    {
        loopOverLines: for (int lineRead = 0; lineRead < 20; lineRead++)
        {
            String line = readState.nextLine();
            if (line == null)
                break loopOverLines; // No more lines to read!
            if (delimiter == null)
            {
                // All just one column, so must be us:
                fill.add(line);
                if (fillFullAndIndexes != null)
                    fillFullAndIndexes.add(new Pair<>(line, new IndexRange(0, line.length())));
            }
            else
            {
                int currentCol = 0;
                int currentColStart = 0;
                for (int i = 0; i < line.length(); i++)
                {
                    if (line.regionMatches(i, delimiter, 0, delimiter.length()))
                    {
                        if (currentCol == columnIndex)
                        {
                            fill.add(line.substring(currentColStart, i));
                            if (fillFullAndIndexes != null)
                                fillFullAndIndexes.add(new Pair<>(line, new IndexRange(currentColStart, i)));
                            // No point going further in this line as we've found our column:
                            continue loopOverLines;
                        }
                        currentCol += 1;
                        currentColStart = i + delimiter.length();
                        // TODO this doesn't handle the last column (should fail test)
                    }
                }
                // Might be the last column we want:
                if (currentCol == columnIndex)
                {
                    fill.add(line.substring(currentColStart));
                    if (fillFullAndIndexes != null)
                        fillFullAndIndexes.add(new Pair<>(line, new IndexRange(currentColStart, line.length())));
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

    public static double variance(Collection<? extends Number> src)
    {
        if (src.isEmpty())
            return 0;
        double mean = src.stream().<Number>mapToDouble(Number::doubleValue).summaryStatistics().getAverage();
        return src.stream().<Number>mapToDouble(Number::doubleValue).map(x -> (x - mean) * (x - mean)).sum();
    }

    public static ReadState skipFirstNRows(File src, Charset charset, final int headerRows) throws IOException
    {
        return new ReadState(src, charset, headerRows);
    }

    public static int compareNumbers(final Object a, final Object b)
    {
        return compareNumbers(a, b, null);
    }

    // Params passed as Object to avoid double cast
    public static int compareNumbers(final Object a, final Object b, @Nullable BigDecimal epsilon)
    {
        if (a instanceof BigDecimal || b instanceof BigDecimal)
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
                    if (da.equals(db) || (!da.equals(BigDecimal.ZERO) && da.subtract(db).abs().divide(da, MathContext.DECIMAL128).subtract(BigDecimal.ONE).compareTo(epsilon) == -1)
                        || (!db.equals(BigDecimal.ZERO) && da.subtract(db).abs().divide(db, MathContext.DECIMAL128).subtract(BigDecimal.ONE).compareTo(epsilon) == -1))
                        return 0;
                    else
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
            return Long.compare(((Number)a).longValue(), ((Number)b).longValue());
        }

    }

    public static interface GenOrError<T>
    {
        @OnThread(Tag.Simulation)
        T run() throws InternalException, UserException;
    }
    public static interface RunOrError
    {
        @OnThread(Tag.Simulation)
        void run() throws InternalException, UserException;
    }
    public static interface GenOrErrorFX<T>
    {
        @OnThread(Tag.FXPlatform)
        T run() throws InternalException, UserException;
    }
    public static interface RunOrErrorFX
    {
        @OnThread(Tag.FXPlatform)
        void run() throws InternalException, UserException;
    }

    @OnThread(Tag.Simulation)
    public static void alertOnError_(RunOrError r)
    {
        alertOnError_(err -> err, r);
    }

    @OnThread(Tag.Simulation)
    public static void alertOnError_(Function<@Localized String, @Localized String> errWrap, RunOrError r)
    {
        try
        {
            r.run();
        }
        catch (InternalException | UserException e)
        {
            Platform.runLater(() ->
            {
                showError(errWrap, e);
            });
        }
    }

    @OnThread(Tag.FXPlatform)
    public static void alertOnErrorFX_(RunOrErrorFX r)
    {
        try
        {
            r.run();
        }
        catch (InternalException | UserException e)
        {
            showError(e);
        }
    }

    @OnThread(Tag.FXPlatform)
    public static <T> @Nullable T alertOnErrorFX(GenOrErrorFX<T> r)
    {
        try
        {
            return r.run();
        }
        catch (InternalException | UserException e)
        {
            showError(e);
            return null;
        }
    }

    private static List<Exception> queuedErrors = new ArrayList<>();
    private static boolean showingError = false;

    @OnThread(Tag.FXPlatform)
    public static void showError(Exception e)
    {
        showError(x -> x, e);
    }

    @OnThread(Tag.FXPlatform)
    public static void showError(Function<@Localized String, @Localized String> errWrap, Exception e)
    {
        if (showingError)
        {
            // TODO do something with the queued errors; add to shown dialog?
            queuedErrors.add(e);
        }
        else
        {
            log(e);
            // Don't show dialog which would interrupt a JUnit test:
            if (!isJUnitTest())
            {
                String localizedMessage = errWrap.apply(e.getLocalizedMessage());
                Alert alert = new Alert(AlertType.ERROR, localizedMessage == null ? "Unknown error" : localizedMessage, ButtonType.OK);
                alert.initModality(Modality.APPLICATION_MODAL);
                showingError = true;
                alert.showAndWait();
                showingError = false;
            }
        }
    }

    // From https://stackoverflow.com/a/12717377/412908 but tweaked to check all threads
    private static boolean isJUnitTest()
    {
        for (StackTraceElement[] stackTrace : ((Supplier<Map<Thread, StackTraceElement[]>>)Thread::getAllStackTraces).get().values())
        {
            List<StackTraceElement> list = Arrays.asList(stackTrace);
            for (StackTraceElement element : list)
            {
                if (element.getClassName().startsWith("org.junit."))
                {
                    return true;
                }
            }
        }
        return false;
    }

    @OnThread(Tag.Simulation)
    public static <T> Optional<T> alertOnError(GenOrError<@Nullable T> r)
    {
        try
        {
            @Nullable T t = r.run();
            if (t == null)
                return Optional.empty();
            else
                return Optional.of(t);
        }
        catch (InternalException | UserException e)
        {
            Platform.runLater(() ->
            {
                showError(e);
            });
            return Optional.empty();
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

        public static ListEx empty()
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

    public static @Value class ListExList extends ListEx
    {
        private final List<@Value Object> items;

        public @Value ListExList(List<@Value Object> items)
        {
            this.items = items;
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

    //package-visible
    static File getStorageDirectory() throws IOException
    {
        File dir = new File(System.getProperty("user.home"), ".records");
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
    @EnsuresNonNull("mruList")
    public static @NonNull ObservableList<File> getRecentFilesList()
    {
        if (mruList != null)
            return mruList;
        try
        {
            mruList = FXCollections.observableArrayList();
            reloadMRU();
            // After loading, add the listener:
            mruList.addListener((ListChangeListener<File>)(c -> {
                try
                {
                    FileUtils.writeLines(new File(getStorageDirectory(), MRU_FILE_NAME), "UTF-8", Utility.mapList(c.getList(), File::getAbsoluteFile));
                }
                catch (IOException e)
                {
                    Utility.log(e);
                }
            }));

            // From http://stackoverflow.com/questions/16251273/can-i-watch-for-single-file-change-with-watchservice-not-the-whole-directory
            final Path path = getStorageDirectory().toPath();
            Thread t = new Thread(() -> {
                try (final WatchService watchService = FileSystems.getDefault().newWatchService())
                {
                    final WatchKey watchKey = path.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);

                    // Because mruList is monotonic non-null and is non-null
                    // at the start of the enclosing method, this is true:
                    assert mruList != null : "@AssumeAssertion(nullness)";
                    watchMRU(watchService);
                }
                catch (IOException e)
                {
                    // No need to tell user, we just won't have the list
                    Utility.log(e);
                }
            });
            t.setDaemon(true);
            t.start();
        }
        catch (IOException e)
        {
            // No need to tell user, we just won't have the list
            Utility.log(e);
            mruList = FXCollections.observableArrayList();
        }
        return mruList;
    }

    @RequiresNonNull("mruList")
    private static void watchMRU(WatchService watchService)
    {
        while (true)
        {
            try
            {
                final WatchKey wk = watchService.take();
                for (WatchEvent<?> event : wk.pollEvents())
                {
                    //we only register "ENTRY_MODIFY" so the context is always a Path.
                    final Path changed = (Path) event.context();
                    if (changed != null && changed.endsWith(MRU_FILE_NAME))
                    {
                        Platform.runLater(() -> {
                            // Because mruList is monotonic non-null and is non-null
                            // at the start of the enclosing method, this is true:
                            assert mruList != null : "@AssumeAssertion(nullness)";
                            reloadMRU();
                        });
                    }
                }
                // reset the key
                boolean valid = wk.reset();
                if (!valid)
                {
                    // Just stop watching:
                    return;
                }
            }
            catch (InterruptedException e)
            {
                // Go round again...
            }
        }
    }

    @OnThread(Tag.FXPlatform)
    @RequiresNonNull("mruList")
    private static void reloadMRU()
    {
        try
        {
            File mruFile = new File(getStorageDirectory(), MRU_FILE_NAME);
            if (mruFile.exists())
            {
                List<String> content = FileUtils.readLines(mruFile, "UTF-8");
                mruList.setAll(Utility.mapList(content, File::new));
            }
            else
            {
                mruList.clear();
            }
        }
        catch (IOException e)
        {
            Utility.log(e);
        }
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
        try
        {
            props.store(new FileWriter(new File(Utility.getStorageDirectory(), fileName)), "");
        }
        catch (IOException e)
        {
            Utility.log(e);
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
                    p.load(new FileReader(propFile));
            }
            catch (IOException e)
            {
                Utility.log(e);
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
}
