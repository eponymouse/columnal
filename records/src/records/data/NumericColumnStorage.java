package records.data;

import org.checkerframework.checker.nullness.qual.EnsuresNonNull;
import org.checkerframework.checker.nullness.qual.EnsuresNonNullIf;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.qual.Pure;
import records.error.InternalException;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;

/**
 * Created by neil on 22/10/2016.
 */
public class NumericColumnStorage
{
    private int filled = 0;
    // We only use bytes, shorts, ints if all the numbers fit.
    private byte @Nullable [] bytes = new byte[8];
    private short @Nullable [] shorts;
    private int @Nullable [] ints;
    // We use longs if most of them fit.  Long.MAX_VALUE means consult bigIntegers array.
    // Long.MIN_VALUE means consult bigDecimals array.
    private long @Nullable [] longs;
    private static final long SEE_BIGINT = Long.MIN_VALUE;
    private static final long SEE_BIGDEC = Long.MAX_VALUE;
    private @Nullable BigInteger @Nullable [] bigIntegers;
    // If any are non-integer, we use bigDecimals
    private @Nullable BigDecimal @Nullable [] bigDecimals;

    public NumericColumnStorage()
    {
    }

    public void add(String number) throws InternalException, NumberFormatException
    {
        // First try as a long:
        try
        {
            long n = Long.valueOf(number);
            if (Byte.MIN_VALUE <= n && n <= Byte.MAX_VALUE)
                addByte((byte)n);
            else if (Short.MIN_VALUE <= n && n <= Short.MAX_VALUE)
                addShort((short)n);
            else if (Integer.MIN_VALUE <= n && n <= Integer.MAX_VALUE)
                addInteger((int)n);
            else if (Long.MIN_VALUE + 1 <= n && n <= Long.MAX_VALUE - 1)
                addLong(n, false);
            return;
        }
        catch (NumberFormatException ex) { }
        // Not a long; is it a big integer?
        try
        {
            addBigInteger(new BigInteger(number));
            System.err.println("Found BigInt: " + number);
            return;
        }
        catch (NumberFormatException ex) { }
        // Ok, last try: big decimal (and let it throw if not
        addBigDecimal(new BigDecimal(number));
    }

    private void addByte(byte n) throws InternalException
    {
        if (bytes == null)
            addShort(n); // Cascade upwards
        else
        {
            if (filled >= bytes.length)
            {
                bytes = Arrays.copyOf(bytes, increaseLength(bytes.length));
            }
            bytes[filled++] = n;
        }
    }

    private void addShort(short n) throws InternalException
    {
        if (bytes != null)
        {
            shorts = new short[bytes.length];
            for (int i = 0; i < shorts.length; i++)
                shorts[i] = bytes[i];
            bytes = null;
        }
        else if (shorts == null)
        {
            addInteger(n);
            return;
        }
        if (filled >= shorts.length)
        {
            shorts = Arrays.copyOf(shorts, increaseLength(shorts.length));
        }
        shorts[filled++] = n;
    }

    private void addInteger(int n) throws InternalException
    {
        if (ints == null)
        {
            if (bytes != null)
            {
                ints = new int[bytes.length];
                for (int i = 0; i < ints.length; i++)
                    ints[i] = bytes[i];
                bytes = null;
            }
            else if (shorts != null)
            {
                ints = new int[shorts.length];
                for (int i = 0; i < ints.length; i++)
                    ints[i] = shorts[i];
                shorts = null;
            }
            else
            {
                addLong(n, false);
                return;
            }
        }
        if (filled >= ints.length)
        {
            ints = Arrays.copyOf(ints, increaseLength(ints.length));
        }
        ints[filled++] = n;
    }

    @EnsuresNonNull("longs")
    private final void addLong(long n, boolean special) throws InternalException
    {
        // If it overlaps our special values but isn't special, store as biginteger:
        if (!special && (n == SEE_BIGINT || n == SEE_BIGDEC))
            addBigInteger(BigInteger.valueOf(n));

        if (longs == null)
        {
            if (bytes != null)
            {
                longs = new long[bytes.length];
                for (int i = 0; i < longs.length; i++)
                    longs[i] = bytes[i];
                bytes = null;
            }
            else if (shorts != null)
            {
                longs = new long[shorts.length];
                for (int i = 0; i < longs.length; i++)
                    longs[i] = shorts[i];
                shorts = null;
            }
            else if (ints != null)
            {
                longs = new long[ints.length];
                for (int i = 0; i < longs.length; i++)
                    longs[i] = ints[i];
                ints = null;
            }
            else
                throw new InternalException("All arrays null");
        }
        // If it is special, add as if normal
        if (filled >= longs.length)
        {
            longs = Arrays.copyOf(longs, increaseLength(longs.length));
        }
        longs[filled++] = n;
    }

    @EnsuresNonNull({"longs", "bigIntegers"})
    private final void addBigInteger(BigInteger bigInteger) throws InternalException
    {
        // This will convert to LONG_OR_BIG if needed:
        addLong(SEE_BIGINT, true);

        if (bigIntegers == null)
        {
            bigIntegers = new BigInteger[longs.length];
        }

        // Subtract one because addLong already increased it:
        if (filled - 1 >= bigIntegers.length)
        {
            bigIntegers = Arrays.copyOf(bigIntegers, increaseLength(bigIntegers.length));
        }
        bigIntegers[filled - 1] = bigInteger;

        assert longs != null : "@AssumeAssertion(nullness)";
    }

    @EnsuresNonNull({"longs", "bigDecimals"})
    private final void addBigDecimal(BigDecimal bigDecimal) throws InternalException
    {
        // This will convert to LONG_OR_BIG if needed:
        addLong(SEE_BIGINT, true);

        if (bigDecimals == null)
        {
            bigDecimals = new BigDecimal[longs.length];
        }

        // Subtract one because addLong already increased it:
        if (filled - 1 >= bigDecimals.length)
        {
            bigDecimals = Arrays.copyOf(bigDecimals, increaseLength(bigDecimals.length));
        }
        bigDecimals[filled - 1] = bigDecimal;
        assert longs != null : "@AssumeAssertion(nullness)";
    }

    private int increaseLength(int length)
    {
        return length * 2;
    }

    public int filled()
    {
        return filled;
    }

    @Pure
    public Number get(int index) throws InternalException
    {
        // Guessing here to order most likely cases:
        if (bytes != null)
            return bytes[index];
        else if (ints != null)
            return ints[index];
        else if (shorts != null)
            return shorts[index];
        else if (longs != null)
        {
            if (longs[index] == SEE_BIGDEC)
            {
                if (bigDecimals == null)
                    throw new InternalException("SEE_BIGDEC but null BigDecimal array");
                @Nullable BigDecimal bigDecimal = bigDecimals[index];
                if (bigDecimal == null)
                    throw new InternalException("SEE_BIGDEC but null BigDecimal");
                return bigDecimal;
            }
            else if (longs[index] == SEE_BIGINT)
            {
                if (bigIntegers == null)
                    throw new InternalException("SEE_BIGINT but null BigInteger array");
                @Nullable BigInteger bigInteger = bigIntegers[index];
                if (bigInteger == null)
                    throw new InternalException("SEE_BIGINT but null BigInteger");
                return bigInteger;
            }
            else
                return longs[index];
        }
        throw new InternalException("All arrays null in NumericColumnStorage");
    }
}
