package utility;

import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javafx.application.Platform;
import javafx.beans.binding.DoubleBinding;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;
import javafx.css.Styleable;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.TextFieldListCell;
import javafx.scene.layout.Region;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.util.*;
import org.antlr.v4.runtime.ANTLRErrorStrategy;
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
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.qual.Pure;
import org.sosy_lab.common.rationals.Rational;
import records.data.Table;
import records.data.unit.Unit;
import records.error.InternalException;
import records.error.UserException;
import records.grammar.BasicLexer;
import records.gui.DisplayValue;
import records.transformations.function.TaglessFunctionInstance;
import threadchecker.OnThread;
import threadchecker.Tag;

/**
 * Created by neil on 20/10/2016.
 */
public class Utility
{
    private static final Set<String> loadedFonts = new HashSet<>();

    public static <T, R> List<@NonNull R> mapList(List<@NonNull T> list, Function<@NonNull T, @NonNull R> func)
    {
        ArrayList<@NonNull R> r = new ArrayList<>(list.size());
        for (T t : list)
            r.add(func.apply(t));
        return r;
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
    public static int compareLists(List<@NonNull ?> a, List<@NonNull ?> b) throws InternalException
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
    public static int compareLists(List<@NonNull ?> a, List<@NonNull ?> b, @Nullable BigDecimal epsilon) throws InternalException
    {
        for (int i = 0; i < a.size(); i++)
        {
            if (i >= b.size())
                return 1; // A was larger
            Object ax = a.get(i);
            Object bx = b.get(i);
            int cmp;
            if (ax instanceof Number)
                cmp = compareNumbers(ax, bx, epsilon);
            else if (ax instanceof List)
                cmp = compareLists((List<@NonNull ?>)ax, (List<@NonNull ?>)bx, epsilon);
            else if (ax instanceof Comparable)
                cmp = ((Comparable<Object>)ax).compareTo(bx);
            else
                throw new InternalException("Uncomparable types: " + a.getClass() + " " + b.getClass());
            if (cmp != 0)
                return cmp;

        }
        if (a.size() == b.size())
            return 0; // Same
        else
            return -1; // B must have been longer
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
        else if (n instanceof BigInteger)
            return new BigDecimal((BigInteger)n);
        else
            return BigDecimal.valueOf(n.longValue());
    }

    public static MathContext getMathContext()
    {
        return MathContext.DECIMAL64;
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

    public static Number parseNumber(String number)
    {
        // First try as a long:
        try
        {
            return Long.valueOf(number);
        }
        catch (NumberFormatException ex) { }
        // Not a long; is it a big integer?
        try
        {
            return new BigInteger(number);
        }
        catch (NumberFormatException ex) { }
        // Ok, last try: big decimal (and let it throw if not)
        return new BigDecimal(number);
    }

    /**
     * Things like a00, abcd, random unicode emoji, return true
     * Things like 0aa, a+0, a.0, "a a" return false
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
        return s.codePoints().skip(1).allMatch(Character::isLetterOrDigit);
    }

    public static DisplayValue toDisplayValue(Object o)
    {
        if (o instanceof Boolean)
            return new DisplayValue((Boolean)o);
        else if (o instanceof Number)
            return new DisplayValue((Number)o, Unit.SCALAR, 0);
        else if (o instanceof String)
            return new DisplayValue((String)o);
        throw new RuntimeException("Unexpected toDisplayValue type: " + o.getClass());
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
        else if (lhs instanceof BigInteger || rhs instanceof BigInteger)
        {
            if (add)
                return toBigInteger(lhs).add(toBigInteger(rhs));
            else
                return toBigInteger(lhs).subtract(toBigInteger(rhs));
        }
        else
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
                return addSubtractNumbers(toBigInteger(lhs), toBigInteger(rhs), add);
            }
        }
    }

    private static BigInteger toBigInteger(Number number)
    {
        if (number instanceof BigInteger)
            return (BigInteger)number;
        else
            return BigInteger.valueOf(number.longValue());
    }

    public static Number multiplyNumbers(Number lhs, Number rhs)
    {
        if (lhs instanceof BigDecimal || rhs instanceof BigDecimal)
        {
            return toBigDecimal(lhs).multiply(toBigDecimal(rhs), MathContext.DECIMAL128);
        }
        else if (lhs instanceof BigInteger || rhs instanceof BigInteger)
        {
            return toBigInteger(lhs).multiply(toBigInteger(rhs));
        }
        else
        {
            try
            {
                return Math.multiplyExact(lhs.longValue(), rhs.longValue());
            }
            catch (ArithmeticException e)
            {
                return multiplyNumbers(toBigInteger(lhs), toBigInteger(rhs));
            }
        }
    }

    public static Number divideNumbers(Number lhs, Number rhs)
    {
        if (lhs instanceof BigDecimal || rhs instanceof BigDecimal)
        {
            return toBigDecimal(lhs).divide(toBigDecimal(rhs), MathContext.DECIMAL128);
        }
        else if (lhs instanceof BigInteger || rhs instanceof BigInteger)
        {
            BigInteger[] divRem = toBigInteger(lhs).divideAndRemainder(toBigInteger(rhs));
            if (divRem[1].equals(BigInteger.ZERO))
                return divRem[0];
            else
                return divideNumbers(toBigDecimal(lhs), toBigDecimal(rhs));
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
            || (rhs instanceof BigInteger && !rhs.equals(BigInteger.valueOf(((BigInteger)rhs).intValue())))
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

    public static <T> T withNumber(Object num, ExFunction<Long, T> withLong, ExFunction<BigInteger, T> withBigInt, ExFunction<BigDecimal, T> withBigDecimal) throws InternalException, UserException
    {
        if (num instanceof BigDecimal)
            return withBigDecimal.apply((BigDecimal) num);
        else if (num instanceof BigInteger)
            return withBigInt.apply((BigInteger) num);
        else
            return withLong.apply(((Number)num).longValue());
    }

    public static boolean isIntegral(Object o) throws UserException, InternalException
    {
        // From http://stackoverflow.com/a/12748321/412908
        return withNumber(o, x -> true, x -> true, bd -> {
            return bd.signum() == 0 || bd.scale() <= 0 || bd.stripTrailingZeros().scale() <= 0;
        });
    }

    public static int requireInteger(Object o) throws UserException, InternalException
    {
        return withNumber(o, l -> {
            if (l.longValue() != l.intValue())
                throw new UserException("Number too large: " + l);
            return l.intValue();
        }, bi -> {
            try
            {
                return bi.intValueExact();
            }
            catch (ArithmeticException e)
            {
                throw new UserException("Number too large: " + bi);
            }
        }, bd -> {
            try
            {
                return bd.intValueExact();
            }
            catch (ArithmeticException e)
            {
                throw new UserException("Number not an integer or too large: " + bd);
            }
        });
    }

    public static void log(Exception e)
    {
        e.printStackTrace(); // TODO
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
            log(e);
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

    @OnThread(Tag.FX)
    public static void sizeToFit(TextField tf, @Nullable Double minSizeFocused, @Nullable Double minSizeUnfocused)
    {
        // Partly taken from http://stackoverflow.com/a/25643696/412908:
        // Set Max and Min Width to PREF_SIZE so that the TextField is always PREF
        tf.setMinWidth(Region.USE_PREF_SIZE);
        tf.setMaxWidth(Region.USE_PREF_SIZE);
        tf.prefWidthProperty().bind(new DoubleBinding()
        {
            {
                super.bind(tf.textProperty());
                super.bind(tf.promptTextProperty());
                super.bind(tf.fontProperty());
                super.bind(tf.focusedProperty());
            }
            @Override
            protected double computeValue()
            {
                Text text = new Text(tf.getText());
                if (text.getText().isEmpty() && !tf.getPromptText().isEmpty())
                    text.setText(tf.getPromptText());
                text.setFont(tf.getFont()); // Set the same font, so the size is the same
                double width = text.getLayoutBounds().getWidth() // This big is the Text in the TextField
                    //+ tf.getPadding().getLeft() + tf.getPadding().getRight() // Add the padding of the TextField
                    + tf.getInsets().getLeft() + + tf.getInsets().getRight()
                    + 5d; // Add some spacing
                return Math.max(tf.isFocused() ? (minSizeFocused == null ? 20 : minSizeFocused) : (minSizeUnfocused == null ? 20 : minSizeUnfocused), width);
            }
        });
    }

    public static <T> Iterable<T> iterableStream(Stream<T> values)
    {
        return values::iterator;
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
            else if (a instanceof BigInteger)
                da = new BigDecimal((BigInteger)a);
            else
                da = BigDecimal.valueOf(((Number)a).longValue());
            if (b instanceof BigDecimal)
                db = (BigDecimal)b;
            else if (b instanceof BigInteger)
                db = new BigDecimal((BigInteger)b);
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
        else if (a instanceof BigInteger || b instanceof BigInteger)
        {
            BigInteger ia, ib;
            ia = a instanceof BigInteger ? (BigInteger)a : BigInteger.valueOf(((Number)a).longValue());
            ib = b instanceof BigInteger ? (BigInteger)b : BigInteger.valueOf(((Number)b).longValue());
            return ia.compareTo(ib);
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

    @OnThread(Tag.FXPlatform)
    public static <T> ListView<@NonNull T> readOnlyListView(ObservableList<@NonNull T> content, Function<T, String> toString)
    {
        ListView<@NonNull T> listView = new ListView<>(content);
        listView.setCellFactory((ListView<@NonNull T> lv) -> {
            return new TextFieldListCell<>(new StringConverter<@NonNull T>()
            {
                @Override
                public String toString(T t)
                {
                    return toString.apply(t);
                }

                @Override
                public @NonNull T fromString(String string)
                {
                    throw new UnsupportedOperationException();
                }
            });
        });
        listView.setEditable(false);
        return listView;
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
}
