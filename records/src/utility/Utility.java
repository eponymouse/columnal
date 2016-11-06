package utility;

import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;
import javafx.css.Styleable;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ListView;
import javafx.scene.control.cell.TextFieldListCell;
import javafx.util.*;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.qual.Pure;
import records.data.Column;
import records.error.FetchException;
import records.error.InternalException;
import records.error.UserException;
import records.gui.Table;
import records.transformations.SummaryStatistics.SummaryType;
import threadchecker.OnThread;
import threadchecker.Tag;

/**
 * Created by neil on 20/10/2016.
 */
public class Utility
{
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
        InputStream is = new BufferedInputStream(new FileInputStream(filename));
        try {
            byte[] c = new byte[65536];
            int count = 1;
            int readChars = 0;
            boolean empty = true;
            boolean lastWasNewline = false;
            while ((readChars = is.read(c)) != -1) {
                empty = false;
                for (int i = 0; i < readChars; ++i) {
                    if (c[i] == '\n') {
                        ++count;
                        lastWasNewline = true;
                    }
                    else
                        lastWasNewline = false;
                }
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
        } finally {
            is.close();
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

    // Compare lexicographically.  Types should match at each stage if earlier part of list was the same
    @Pure
    public static int compareLists(List<@NonNull ?> a, List<@NonNull ?> b) throws InternalException
    {
        for (int i = 0; i < a.size(); i++)
        {
            if (i >= b.size())
                return -1; // A was lower
            Object ax = a.get(i);
            Object bx = b.get(i);
            int cmp;
            if (ax instanceof Number)
                cmp = compareNumbers(ax, bx);
            else if (ax instanceof List)
                cmp = compareLists((List<@NonNull ?>)ax, (List<@NonNull ?>)bx);
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
            return 1; // B must have been longer
    }

    public static String getFracPart(Number number)
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

    public static String getIntegerPart(Number number)
    {
        if (number instanceof BigDecimal)
        {
            return ((BigDecimal)number).toBigInteger().toString();
        }
        else
            return number.toString();
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
            if (!state.eof)
            {
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

    public static double variance(List<? extends Number> src)
    {
        if (src.isEmpty())
            return 0;
        double mean = src.stream().<Number>mapToDouble(Number::doubleValue).summaryStatistics().getAverage();
        return src.stream().<Number>mapToDouble(Number::doubleValue).map(x -> (x - mean) * (x - mean)).sum();
    }

    public static ReadState skipFirstNRows(File src, final int headerRows) throws IOException
    {
        try (InputStream is = new BufferedInputStream(new FileInputStream(src)))
        {
            int seen = 0;
            long pos = 0;
            byte[] buf = new byte[65536];
            int readChars;
            while ((readChars = is.read(buf)) != -1)
            {
                for (int i = 0; i < readChars; i++)
                {
                    pos += 1;
                    if (buf[i] == '\n')
                    {
                        seen += 1;
                        if (seen == headerRows)
                        {
                            return new ReadState(pos);
                        }
                    }
                }
            }
            throw new EOFException("File does not contain specified number of header rows");
        }
    }

    // Params passed as Object to avoid double cast
    public static int compareNumbers(final Object a, final Object b)
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
            return da.compareTo(db);
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


    @OnThread(Tag.Simulation)
    public static void alertOnError_(RunOrError r)
    {
        try
        {
            r.run();
        }
        catch (InternalException | UserException e)
        {
            Platform.runLater(() ->
            {
                String localizedMessage = e.getLocalizedMessage();
                new Alert(AlertType.ERROR, localizedMessage == null ? "Unknown error" : localizedMessage, ButtonType.OK).showAndWait();
            });
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
}
