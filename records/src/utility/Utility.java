package utility;

import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.error.FetchException;
import records.error.InternalException;

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

    // From http://stackoverflow.com/questions/453018/number-of-lines-in-a-file-in-java
    public static int countLines(File filename) throws IOException
    {
        InputStream is = new BufferedInputStream(new FileInputStream(filename));
        try {
            byte[] c = new byte[65536];
            int count = 0;
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
            return (count == 0 && !empty) ? 1 : (count - (lastWasNewline ? 1 : 0));
        } finally {
            is.close();
        }
    }

    public static class ReadState
    {
        private long startFrom;
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

        private ReadState(long posOfRowStart)
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
            byte[] buf = new byte[65536];
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
        double mean = src.stream().mapToDouble(Number::doubleValue).summaryStatistics().getAverage();
        return src.stream().mapToDouble(Number::doubleValue).map(x -> (x - mean) * (x - mean)).sum();
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
}
