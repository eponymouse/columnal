package utility;

import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.MathContext;
import java.net.URL;
import java.nio.file.*;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import annotation.userindex.qual.UserIndex;
import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.css.Styleable;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;
import javafx.scene.text.Font;
import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.TokenStream;
import org.apache.commons.io.FileUtils;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.*;
import org.checkerframework.dataflow.qual.Pure;
import org.jetbrains.annotations.NotNull;
import org.sosy_lab.common.rationals.Rational;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.gui.FXUtility;

/**
 * Created by neil on 20/10/2016.
 */
public class Utility
{
    private static final Set<String> loadedFonts = new HashSet<>();
    @OnThread(Tag.FXPlatform)
    private static @MonotonicNonNull ObservableList<File> mruList;
    public static final String MRU_FILE_NAME = "recent.mru";

    public static <T, R> List<@NonNull R> mapList(List<@NonNull T> list, Function<@NonNull T, @NonNull R> func)
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
    public static int countLines(File filename) throws IOException
    {
        try (InputStream is = new BufferedInputStream(new FileInputStream(filename)))
        {
            byte[] c = new byte[1048576];
            int count = 1;
            int readChars;
            boolean empty = true;
            boolean lastWasNewline = false;
            while ((readChars = is.read(c)) > 0) {
                empty = false;
                for (int i = 0; i < readChars; ++i) {
                    if (c[i] == '\n') {
                        count += 1;
                    }
                }
                lastWasNewline = c[readChars - 1] == '\n';
            }
            // Nothing at all; zero lines
            if (empty)
                return 0;
            // Some content, no newline, must be one line:
            else if (count == 1 && !empty)
                return 1;
            else
            // Some content with newlines; ignore blank extra line if \n is very end of file:
                return count - (lastWasNewline ? 1 : 0);
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
    public static void addStyleClass(Styleable styleable, String... classes)
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
            cmp = ((Comparable<Object>)ax).compareTo(bx);
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

    // Gets the fractional part as a String
    public static String getFracPartAsString(Number number)
    {
        if (number instanceof BigDecimal)
        {
            // From http://stackoverflow.com/a/30761234/412908
            BigDecimal bd = ((BigDecimal)number).stripTrailingZeros();
            return bd.remainder(BigDecimal.ONE).movePointRight(bd.scale()).abs().toBigInteger().toString();

        }
        else
            return "";
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
        parser.removeErrorListeners();
        parser.addErrorListener(del);
        R r = withParser.apply(parser);
        if (!del.errors.isEmpty())
            throw new UserException("Parse errors while loading:\n" + del.errors.stream().collect(Collectors.joining("\n")));
        return r;
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
            return new BigDecimal(number);
        }
        catch (NumberFormatException e)
        {
            throw new UserException("Problem parsing number \"" + number + "\"");
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
    public static void log(String info, Exception e)
    {
        System.err.println(info);
        e.printStackTrace(); // TODO
    }

    public static void log(Exception e)
    {
        log("", e);
    }

    @OnThread(Tag.FXPlatform)
    public static <T> void onNonNull(ReadOnlyObjectProperty<T> property, FXPlatformConsumer<T> consumer)
    {
        property.addListener(new ChangeListener<T>()
        {
            @Override
            @OnThread(value = Tag.FXPlatform, ignoreParent = true)
            public void changed(ObservableValue<? extends T> observable, T oldValue, T newValue)
            {
                property.removeListener(this);
                consumer.consume(newValue);
            }
        });
    }

    @SuppressWarnings("nullness")
    public static String getStylesheet(String stylesheetName)
    {
        try
        {
            ClassLoader classLoader = ClassLoader.getSystemClassLoader();
            URL resource = classLoader.getResource(stylesheetName);
            return resource.toString();
        }
        catch (NullPointerException e)
        {
            log("Problem loading stylesheet: " + stylesheetName, e);
            return "";
        }
    }

    @SuppressWarnings("nullness")
    public static void ensureFontLoaded(String fontFileName)
    {
        if (!loadedFonts.contains(fontFileName))
        {
            try (InputStream fis = ClassLoader.getSystemClassLoader().getResourceAsStream(fontFileName))
            {
                Font.loadFont(fis, 10);
                loadedFonts.add(fontFileName);
            }
            catch (IOException | NullPointerException e)
            {
                log(e);
            }
        }
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
        return values::iterator;
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
    public static @Value Object[] valueTuple(@Value Object value, int size) throws InternalException
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
    public static <T> int indexOfRef(List<T> list, @UnknownInitialization T item)
    {
        for (int i = 0; i < list.size(); i++)
        {
            if (list.get(i) == item)
                return i;
        }
        return -1;
    }

    public static <T> boolean containsRef(List<T> list, @UnknownInitialization T item)
    {
        return indexOfRef(list, item) != -1;
    }

    public static <T> List<T> replicate(int length, T value)
    {
        List<T> r = new ArrayList<T>();
        for (int i = 0; i < length; i++)
            r.add(value);
        return r;
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

    public static class ReadState
    {
        public long startFrom;
        private int currentCol;
        private byte @Nullable [] currentEntry;
        private boolean eof;

        public ReadState()
        {
            startFrom = 0;
            currentCol = 0;
            currentEntry = null;
            eof = false;
        }

        public ReadState(long posOfRowStart)
        {
            this();
            startFrom = posOfRowStart;
        }

        public boolean isEOF()
        {
            return eof;
        }
    }

    public static ReadState readColumnChunk(File filename, ReadState state, byte delimiter, int columnIndex, ArrayList<String> fill) throws IOException
    {
        try (InputStream is = new BufferedInputStream(new FileInputStream(filename)))
        {
            byte[] buf = new byte[1048576 * 4];
            is.skip(state.startFrom);
            int readChars = is.read(buf);
            state.eof = readChars <= 0;
            // If we reach EOF but there's content left to process,
            // inject a fake trailing newline to try to finish file correctly:
            if (!state.eof || state.currentEntry != null)
            {
                if (state.eof) // currentEntry must be non-null so ignore EOF for now:
                {
                    state.eof = false;
                    buf[0] = '\n';
                    readChars = 1;
                }
                int startCurEntry = 0; // within buf at least, not counting previous leftover
                for (int i = 0; i < readChars; i++)
                {
                    if (buf[i] == '\n' || buf[i] == delimiter)
                    {
                        if (state.currentCol == columnIndex)
                        {
                            int len = i - startCurEntry;
                            if (state.currentEntry == null)
                            {
                                // No left-overs, just copy from current:
                                fill.add(new String(buf, startCurEntry, len));
                            }
                            else
                            {
                                // Need to join leftovers.  Must join raw byte arrays in case
                                // UTF-8 char spans the two.
                                byte[] combined = Arrays.copyOf(state.currentEntry, state.currentEntry.length + len);
                                System.arraycopy(buf, startCurEntry, combined, state.currentEntry.length, len);
                                fill.add(new String(combined));
                            }
                        }
                        if (buf[i] == '\n')
                            state.currentCol = 0;
                        else
                            state.currentCol += 1;
                        state.currentEntry = null;
                        startCurEntry = i + 1;
                    }
                }
                // Save any left-over:
                if (startCurEntry < readChars)
                {
                    state.currentEntry = Arrays.copyOfRange(buf, startCurEntry, readChars);
                }
                state.startFrom += readChars;
            }
            return state;
        }
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

    public static ReadState skipFirstNRows(File src, final int headerRows) throws IOException
    {
        if (headerRows == 0)
            return new ReadState(0);
        try (InputStream is = new BufferedInputStream(new FileInputStream(src)))
        {
            int seen = 0;
            long pos = 0;
            byte[] buf = new byte[1048576];
            int readChars;
            while ((readChars = is.read(buf)) > 0)
            {
                for (int i = 0; i < readChars; i++)
                {
                    if (buf[i] == '\n')
                    {
                        seen += 1;
                        if (seen == headerRows)
                        {
                            return new ReadState(pos + i + 1);
                        }
                    }
                }
                pos += readChars;
            }
            throw new EOFException("File \"" + src.getAbsolutePath() + "\" does not contain specified number of header rows: " + headerRows);
        }
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
    public static interface RunOrErrorFX
    {
        @OnThread(Tag.FXPlatform)
        void run() throws InternalException, UserException;
    }


    @OnThread(Tag.Simulation)
    public static void alertOnError_(RunOrError r)
    {
        try
        {
            r.run();
        }
        catch (InternalException | UserException e)
        {
            e.printStackTrace(); // TODO have proper log
            Platform.runLater(() ->
            {
                String localizedMessage = e.getLocalizedMessage();
                new Alert(AlertType.ERROR, localizedMessage == null ? "Unknown error" : localizedMessage, ButtonType.OK).showAndWait();
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
            e.printStackTrace(); // TODO have proper log
            String localizedMessage = e.getLocalizedMessage();
            new Alert(AlertType.ERROR, localizedMessage == null ? "Unknown error" : localizedMessage, ButtonType.OK).showAndWait();
        }
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
                e.printStackTrace(); // TODO have proper log
                String localizedMessage = e.getLocalizedMessage();
                new Alert(AlertType.ERROR, localizedMessage == null ? "Unknown error" : localizedMessage, ButtonType.OK).showAndWait();
            });
            return Optional.empty();
        }
    }

    @OnThread(Tag.FXPlatform)
    @SuppressWarnings("nullness")
    public static <T> void addChangeListenerPlatform(ObservableValue<T> property, FXPlatformConsumer<@Nullable T> listener)
    {
        // Defeat thread checker:
        property.addListener(new ChangeListener<T>()
        {
            @Override
            @OnThread(value = Tag.FXPlatform, ignoreParent = true)
            public void changed(ObservableValue<? extends T> a, T b, T newVal)
            {
                listener.consume(newVal);
            }
        });
    }

    @OnThread(Tag.FXPlatform)
    @SuppressWarnings("nullness")
    // NN = Not Null
    public static <T> void addChangeListenerPlatformNN(ObservableValue<T> property, FXPlatformConsumer<@NonNull T> listener)
    {
        // Defeat thread checker:
        property.addListener(new ChangeListener<T>()
        {
            @Override
            @OnThread(value = Tag.FXPlatform, ignoreParent = true)
            public void changed(ObservableValue<? extends T> a, T b, T newVal)
            {
                listener.consume(newVal);
            }
        });
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

    @OnThread(Tag.FXPlatform)
    public static void runAfter(FXPlatformRunnable r)
    {
        // Defeat thread-checker:
        ((Runnable)(() -> Platform.runLater(r::run))).run();
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
    }

    // Mainly, this method is to avoid having to cast to ListChangeListener to disambiguate
    // from the invalidionlistener overload in ObservableList
    @OnThread(Tag.FXPlatform)
    public static <T> void listen(ObservableList<T> list, FXPlatformConsumer<ListChangeListener.Change<? extends T>> listener)
    {
        list.addListener(listener::consume);
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
            Utility.listen(mruList, c -> {
                try
                {
                    FileUtils.writeLines(new File(getStorageDirectory(), MRU_FILE_NAME), "UTF-8", Utility.mapList(c.getList(), File::getAbsoluteFile));
                }
                catch (IOException e)
                {
                    Utility.log(e);
                }
            });

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
}
