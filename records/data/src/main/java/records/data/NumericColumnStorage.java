package records.data;

import org.checkerframework.checker.nullness.qual.EnsuresNonNull;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.qual.Pure;
import records.data.Column.ProgressListener;
import records.data.datatype.DataType.NumberInfo;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.DataTypeValue;
import records.data.datatype.DataTypeValue.GetValue;
import records.error.FetchException;
import records.error.InternalException;
import records.error.UnimplementedException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.ExBiConsumer;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * A class to store numbers or a series of values of the type
 * Tag0, Tag1 Number, Tag2, Tag3, i.e. where at most one tag
 * has a number argument.  This includes
 * the case where none of the tags have a number argument;
 * this class works well for that case.
 *
 * The idea behind the class is that we use as small a storage as possible
 * if the column contains small integers.  So for example, if
 * we have a column filled with zeroes and ones, we don't want to
 * use an array of BigDecimal.  Additionally, if the user
 * has a type like: Missing | Number, we don't want to have to box
 * all the numbers just because some may be the missing tag.
 *
 * So the idea here is twofold.  First, tagged values (like Missing)
 * are stored as negative numbers, in the lowest values for the type.
 * So if you have Missing | NA | 0 | 1, these are stored in a byte
 * as -128, -127, 0 and 1.  Second, we use the smallest type
 * we need if all are integers. (If some are fractional, we just
 * accept the cost and store all the integers as longs, but could
 * revisit that in future).
 *
 * So we start with an array of bytes.  If we get any values that
 * are too big to fit a byte, we upgrade to shorts, then to ints and finally
 * to longs.  If we see any fractional, we jump straight to long.
 * Any fractional items are stored in the BigDecimal array
 * and any integers bigger than long in BigInteger.  But we keep
 * any integers that will fit in the long array, so ideally
 * BigDecimal and BigInteger arrays are as sparse as possible.
 *
 */
public class NumericColumnStorage implements ColumnStorage<Number>
{
    private int filled = 0;
    // We only use bytes, shorts, ints if all the numbers fit.
    private byte @Nullable [] bytes = new byte[8];
    private short @Nullable [] shorts;
    private int @Nullable [] ints;
    // We use longs if most of them fit.  Long.MAX_VALUE means consult bigIntegers array.
    // Long.MIN_VALUE means consult bigDecimals array.
    private long @Nullable [] longs;
    private static final byte BYTE_MIN = Byte.MIN_VALUE;
    private static final byte BYTE_MAX = Byte.MAX_VALUE;
    private static final short SHORT_MIN = Short.MIN_VALUE;
    private static final short SHORT_MAX = Short.MAX_VALUE;
    private static final int INT_MIN = Integer.MIN_VALUE;
    private static final int INT_MAX = Integer.MAX_VALUE;
    private static final long SEE_BIGDEC = Long.MIN_VALUE;
    private static final long LONG_MIN = Long.MIN_VALUE + 1;
    private static final long LONG_MAX = Long.MAX_VALUE;
    // If any are non-integer, we use bigDecimals
    // Note that bigDecimals.length <= longs.length, not ==
    // That is, bigDecimals may not be as long as the longs array if it doesn't have to be.
    private @Nullable BigDecimal @Nullable [] bigDecimals;
    @MonotonicNonNull
    @OnThread(value = Tag.Any, requireSynchronized = true)
    private DataTypeValue dataType;
    @OnThread(value = Tag.Any)
    private final NumberInfo displayInfo;
    private final @Nullable BeforeGet<NumericColumnStorage> beforeGet;

    public NumericColumnStorage(NumberInfo displayInfo)
    {
        this(displayInfo, null);
    }

    public NumericColumnStorage()
    {
        this(NumberInfo.DEFAULT, null);
    }

    public NumericColumnStorage(NumberInfo displayInfo, @Nullable BeforeGet<NumericColumnStorage> beforeGet)
    {
        this.displayInfo = displayInfo;
        this.beforeGet = beforeGet;
    }

    public void addRead(String number) throws InternalException, UserException
    {
        number = number.replace(",", ""); // TODO parameterise this behaviour
        // First try as a long:
        try
        {
            long n = Long.valueOf(number);
            if (BYTE_MIN <= n && n <= BYTE_MAX)
            {
                addByte(OptionalInt.empty(), (byte) n);
                return;
            }
            else if (SHORT_MIN <= n && n <= SHORT_MAX)
            {
                addShort(OptionalInt.empty(), (short) n);
                return;
            }
            else if (INT_MIN <= n && n <= INT_MAX)
            {
                addInteger(OptionalInt.empty(), (int) n);
                return;
            }
            else if (LONG_MIN <= n && n <= LONG_MAX)
            {
                addLong(OptionalInt.empty(), n, false);
                return;
            }
            // We may fall out of here if it parsed as a long
            // but is Long.MIN_VALUE or close by, as they are special values.
        }
        catch (NumberFormatException ex) { }
        // Ok, last try: big decimal (and rethrow if not)
        try
        {
            addBigDecimal(OptionalInt.empty(), new BigDecimal(number, MathContext.DECIMAL128));
        }
        catch (NumberFormatException e)
        {
            throw new FetchException("Could not parse number: \"" + number + "\"", e);
        }
    }

    private void addByte(OptionalInt index, byte n) throws InternalException
    {
        if (bytes == null)
            addShort(index, byteToShort(n)); // Cascade upwards
        else
        {
            if (isPresent(index))
            {
                bytes[index.getAsInt()] = n;
            }
            else
            {
                if (filled >= bytes.length)
                {
                    bytes = Arrays.copyOf(bytes, increaseLength(bytes.length));
                }
                bytes[filled++] = n;
            }
        }
    }

    private void addShort(OptionalInt index, short n) throws InternalException
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
            addInteger(index, shortToInt(n));
            return;
        }

        if (isPresent(index))
        {
            shorts[index.getAsInt()] = n;
        }
        else
        {
            if (filled >= shorts.length)
            {
                shorts = Arrays.copyOf(shorts, increaseLength(shorts.length));
            }
            shorts[filled++] = n;
        }
    }

    // For some reason index.isPresent() won't count as pure,
    // even with the @Pure annotation in the stubs file.  So we use this method:
    @Pure
    private static boolean isPresent(OptionalInt index)
    {
        return index.isPresent();
    }

    // For some reason index.getAsInt() won't count as pure,
    // even with the @Pure annotation in the stubs file.  So we use this method:
    @Pure
    private static int getAsInt(OptionalInt index)
    {
        return index.getAsInt();
    }

    @Pure
    private short byteToShort(byte b)
    {
        return b < BYTE_MIN ? (short)(SHORT_MIN - (BYTE_MIN - b)) : b;
    }
    @Pure
    private int shortToInt(short s)
    {
        return s < SHORT_MIN ? (INT_MIN - (SHORT_MIN - s)) : s;
    }
    @Pure
    private long intToLong(int x)
    {
        return x < INT_MIN ? (LONG_MIN - (long)(INT_MIN - x)) : x;
    }

    private void addInteger(OptionalInt index, int n) throws InternalException
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
                addLong(index, intToLong(n), false);
                return;
            }
        }

        if (isPresent(index))
        {
            ints[index.getAsInt()] = n;
        }
        else
        {
            if (filled >= ints.length)
            {
                ints = Arrays.copyOf(ints, increaseLength(ints.length));
            }
            ints[filled++] = n;
        }
    }

    @EnsuresNonNull("longs")
    private final void addLong(OptionalInt index, long n, boolean special) throws InternalException
    {
        // If it overlaps our special values but isn't special, store as biginteger:
        if (!special && (n < LONG_MIN))
        {
            addBigDecimal(index, BigDecimal.valueOf(n));
            return;
        }

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
        if (isPresent(index))
        {
            longs[index.getAsInt()] = n;
        }
        else
        {
            // If it is special, add as if normal
            if (filled >= longs.length)
            {
                longs = Arrays.copyOf(longs, increaseLength(longs.length));
            }
            longs[filled++] = n;
        }

        assert longs != null : "@AssumeAssertion(nullness)";
    }

    @EnsuresNonNull({"longs", "bigDecimals"})
    private final void addBigDecimal(OptionalInt index, BigDecimal bigDecimal) throws InternalException
    {
        // This will convert to LONG_OR_BIG if needed:
        addLong(index, SEE_BIGDEC, true);

        if (bigDecimals == null)
        {
            bigDecimals = new BigDecimal[longs.length];
        }

        if (isPresent(index))
        {
            if (getAsInt(index) >= bigDecimals.length)
            {
                bigDecimals = Arrays.copyOf(bigDecimals, increaseLength(bigDecimals.length));
            }

            bigDecimals[index.getAsInt()] = bigDecimal;
            assert longs != null : "@AssumeAssertion(nullness)";
        }
        else
        {
            // Subtract one because addLong already increased it:
            if (filled - 1 >= bigDecimals.length)
            {
                bigDecimals = Arrays.copyOf(bigDecimals, increaseLength(bigDecimals.length));
            }
            bigDecimals[filled - 1] = bigDecimal;
            assert longs != null : "@AssumeAssertion(nullness)";
        }

        assert bigDecimals != null : "@AssumeAssertion(nullness)";
    }

    private int increaseLength(int length)
    {
        return Math.max(filled, length) * 2;
    }

    public int filled()
    {
        return filled;
    }

    /*
    @Pure
    public @Value Number get(int index) throws InternalException
    {
        beforeGet(index);
        if (NUM_TAGS > 0)
        {
            if (getTag(index) == numericTag)
                return Utility.value(getNonBlank(index));
            else
                throw new InternalException("Calling get on tagged item with no" +
                    " value");
        }
        else
            return Utility.value(getNonBlank(index));
    }
    */

    // For when NumericColumnStorage is used as an internal integer store
    public int getInt(int index) throws InternalException, UserException
    {
        Number n = getNonBlank(index, null);
        if (n instanceof BigDecimal)
            throw new InternalException("BigDecimal in internal integer store");
        if (n.longValue() != (long)n.intValue())
            throw new InternalException("Too large a number in internal integer store");
        return n.intValue();
    }

    @NonNull
    private Number getNonBlank(int index, @Nullable ProgressListener progressListener) throws InternalException, UserException
    {
        // Must do this before checking range in case it adds more data:
        if (beforeGet != null)
            beforeGet.beforeGet(this, index, progressListener);
        checkRange(index);


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
                if (index >= bigDecimals.length)
                    throw new InternalException("SEE_BIGDEC but BigDecimal array not long enough");
                @Nullable BigDecimal bigDecimal = bigDecimals[index];
                if (bigDecimal == null)
                    throw new InternalException("SEE_BIGDEC but null BigDecimal");
                return bigDecimal;
            }
            else
                return longs[index];
        }
        throw new InternalException("All arrays null in NumericColumnStorage");
    }
    // Returns numericTag if that item is not a tag
    @Pure
    public int getTag(int index) throws InternalException
    {
        checkRange(index);
        // Guessing here to order most likely cases:
        if (bytes != null)
            return bytes[index];
        else if (shorts != null)
            return shorts[index];
        else if (ints != null)
            return ints[index];
        throw new InternalException("Tag not found in NumericColumnStorage; only longs/decimals");
    }

    private void checkRange(int index) throws InternalException
    {
        if (index < 0 || index >= filled)
            throw new InternalException("Trying to access element " + index + " but only have "+ filled);
    }

    public void addTag(int tagIndex) throws InternalException
    {
        add(tagIndex);
    }

    @OnThread(Tag.Any)
    public synchronized DataTypeValue getType()
    {
        /*
        if (longs != null)
        {
            for (long l : longs)
                if (l == SEE_BIGDEC)
                    return v -> v.number();
        }
        */
        if (dataType == null)
        {
            dataType = DataTypeValue.number(displayInfo, new GetValue<Number>()
            {
                @Override
                public Number getWithProgress(int i, @Nullable ProgressListener prog) throws UserException, InternalException
                {
                    return DataTypeUtility.value(NumericColumnStorage.this.getNonBlank(i, prog));
                }

                @Override
                public @OnThread(Tag.Simulation) void set(int index, Number value) throws InternalException
                {
                    NumericColumnStorage.this.set(OptionalInt.of(index), value);
                }
            });
        }
        return dataType;
    }

    @Override
    public void addAll(List<Number> items) throws InternalException
    {
        for (Number n : items)
        {
            add(n);
        }
    }

    // If index is empty, add at end.
    // public for testing
    public void set(OptionalInt index, Number n) throws InternalException
    {
        try
        {
            if (n instanceof BigDecimal)
                addBigDecimal(index, (BigDecimal) n);
            else
            {
                if ((long) n.byteValue() == n.longValue())
                    addByte(index, n.byteValue()); // Fits in a byte
                else if ((long) n.shortValue() == n.longValue())
                    addShort(index, n.shortValue()); // Fits in a short
                else if ((long) n.intValue() == n.longValue())
                    addInteger(index, n.intValue()); // Fits in a int
                else
                    addLong(index, n.longValue(), false);
            }
        }
        catch (IndexOutOfBoundsException e)
        {
            throw new InternalException("Out of bounds: " + index + " filled: " + filled, e);
        }
    }
    
    public void add(Number n) throws InternalException
    {
        set(OptionalInt.empty(), n);
    }
/*
    public int getNumericTag()
    {
        return numericTag;
    }

    public void setNumericTag(int numericTag)
    {
        this.numericTag = numericTag;
    }

    public List<String> getShrunk(int shrunkLength) throws InternalException
    {
        List<String> r = new ArrayList<>();
        for (int i = 0; i < shrunkLength; i++)
        {
            Number n = get(i);
            if (n instanceof BigDecimal)
                r.add(((BigDecimal)n).toPlainString());
            else
                r.add(n.toString());
        }
        return r;
    }
*/
    public NumberInfo getDisplayInfo()
    {
        return displayInfo;
    }

    @Override
    public void addRow() throws InternalException, UserException
    {
        addByte(OptionalInt.empty(), (byte)0);
    }
}
