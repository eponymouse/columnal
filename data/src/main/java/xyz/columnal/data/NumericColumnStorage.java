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

package xyz.columnal.data;

import annotation.qual.ImmediateValue;
import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.EnsuresNonNull;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.qual.Pure;
import xyz.columnal.data.datatype.ProgressListener;;
import xyz.columnal.data.datatype.NumberInfo;
import xyz.columnal.data.datatype.DataTypeUtility;
import xyz.columnal.data.datatype.DataTypeValue;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.function.simulation.SimulationRunnable;
import xyz.columnal.utility.Utility;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Arrays;
import java.util.List;
import java.util.OptionalInt;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
public class NumericColumnStorage extends SparseErrorColumnStorage<Number> implements ColumnStorage<Number>
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
    
    @OnThread(value = Tag.Any, requireSynchronized = true)
    private @MonotonicNonNull DataTypeValue dataType;
    @OnThread(value = Tag.Any)
    private final NumberInfo displayInfo;
    private final @Nullable BeforeGet<NumericColumnStorage> beforeGet;

    public NumericColumnStorage(NumberInfo displayInfo, boolean isImmediateData)
    {
        this(displayInfo, null, isImmediateData);
    }

    public NumericColumnStorage(boolean isImmediateData)
    {
        this(NumberInfo.DEFAULT, null, isImmediateData);
    }

    public NumericColumnStorage(NumberInfo displayInfo, @Nullable BeforeGet<NumericColumnStorage> beforeGet, boolean isImmediateData)
    {
        super(isImmediateData);
        this.displayInfo = displayInfo;
        this.beforeGet = beforeGet;
    }

    public void addRead(String number) throws InternalException
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
        catch (NumberFormatException ex)
        {
            // Not a valid long
        }
        // Ok, last try: big decimal (and add text if not)
        try
        {
            addBigDecimal(OptionalInt.empty(), new BigDecimal(number, MathContext.DECIMAL128).stripTrailingZeros());
        }
        catch (NumberFormatException e)
        {
            addAll(Stream.of(Either.<String, Number>left(number)));
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

    private @ImmediateValue Number getNonBlank(int index, @Nullable ProgressListener progressListener) throws InternalException, UserException
    {
        checkRange(index);


        // Guessing here to order most likely cases:
        if (bytes != null)
            return DataTypeUtility.value(bytes[index]);
        else if (ints != null)
            return DataTypeUtility.value(ints[index]);
        else if (shorts != null)
            return DataTypeUtility.value(shorts[index]);
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
                return DataTypeUtility.value(bigDecimal);
            }
            else
                return DataTypeUtility.value(longs[index]);
        }
        throw new InternalException("All arrays null in NumericColumnStorage");
    }
    // Returns numericTag if that item is not a tag
    @Pure
    public int getInt(int index) throws UserException, InternalException
    {
        checkRange(index);
        // Guessing here to order most likely cases:
        if (bytes != null)
            return bytes[index];
        else if (shorts != null)
            return shorts[index];
        else if (ints != null)
            return ints[index];
        else if (longs != null && (int)longs[index] == longs[index])
            return (int)longs[index];
        throw new InternalException("Int not found in NumericColumnStorage; only longs/decimals");
    }

    private void checkRange(int index) throws UserException
    {
        if (index < 0 || index >= filled)
            throw new UserException("Trying to access zero-based element " + index + " but only have "+ filled);
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
            dataType = DataTypeValue.number(displayInfo, new GetValueOrError<@Value Number>()
            {
                @Override
                protected @OnThread(Tag.Simulation) void _beforeGet(int index, @Nullable ProgressListener progressListener) throws UserException, InternalException
                {
                    if (beforeGet != null)
                        beforeGet.beforeGet(NumericColumnStorage.this, index, progressListener);
                }

                @Override
                public @Value Number _getWithProgress(int i, @Nullable ProgressListener prog) throws UserException, InternalException
                {
                    return NumericColumnStorage.this.getNonBlank(i, prog);
                }

                @Override
                public @OnThread(Tag.Simulation) void _set(int index, @Nullable @Value Number value) throws InternalException, UserException
                {
                    if (value == null)
                        value = DataTypeUtility.value(0);
                    
                    if (index == filled)
                    {
                        NumericColumnStorage.this.set(OptionalInt.empty(), value);
                    }
                    else
                    {
                        NumericColumnStorage.this.set(OptionalInt.of(index), value);
                    }
                }
            });
        }
        return dataType;
    }

    @Override
    public SimulationRunnable _insertRows(int index, List<@Nullable Number> items) throws InternalException
    {
        addAll(index, items.stream().map(n -> n == null ? 0 : n));
        int count = items.size();
        return () -> _removeRows(index, count);
    }

    @Override
    public SimulationRunnable _removeRows(int index, int count) throws InternalException
    {
        ImmutableList<Either<String, Number>> old = getAllCollapsed(index, index + count);
        if (bytes != null)
            System.arraycopy(bytes, index + count, bytes, index, filled - (index + count));
        else if (shorts != null)
            System.arraycopy(shorts, index + count, shorts, index, filled - (index + count));
        else if (ints != null)
            System.arraycopy(ints, index + count, ints, index, filled - (index + count));
        else if (longs != null)
        {
            System.arraycopy(longs, index + count, longs, index, filled - (index + count));
            if (bigDecimals != null && index + count < bigDecimals.length)
                System.arraycopy(bigDecimals, index + count, bigDecimals, index, bigDecimals.length - (index + count));
        }
        filled -= count;
        // Can't use ImmutableList because of nulls: 
        return () -> _insertRows(index, old.stream().<@Nullable Number>map(e -> e.<@Nullable Number>either(s -> null, n -> n)).collect(Collectors.<@Nullable Number>toList()));
    }

    @Override
    public void addAll(Stream<Either<String, Number>> items) throws InternalException
    {
        _addAll(items);
    }
    
    public int _addAll(Stream<Either<String, Number>> items) throws InternalException
    {
        int count = 0;
        // TODO this could be improved rather than calling add lots of times
        for (Either<String, Number> item : Utility.iterableStream(items))
        {
            add(item.either(err -> {
                setError(filled, err);
                return 0;
            }, v -> v));
            count++;
        }
        return count;
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
    
    @Override
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

    public void addAll(int insertAtIndex, Stream<Number> newNumbers) throws InternalException
    {
        int originalLength = this.filled;
        // First, add them on the end:
        int newNumbersSize = _addAll(newNumbers.map(x -> Either.<String, Number>right(x)));
        // Now, swap existing numbers and new numbers:
        if (bytes != null)
        {
            // New to temp:
            byte[] newVals = Arrays.copyOfRange(bytes, originalLength, filled);
            // Old to new:
            System.arraycopy(bytes, insertAtIndex, bytes, insertAtIndex + newNumbersSize, originalLength - insertAtIndex);
            // New [temp] to old:
            System.arraycopy(newVals, 0, bytes, insertAtIndex, newNumbersSize);
        }
        else if (shorts != null)
        {
            // New to temp:
            short[] newVals = Arrays.copyOfRange(shorts, originalLength, filled);
            // Old to new:
            System.arraycopy(shorts, insertAtIndex, shorts, insertAtIndex + newNumbersSize, originalLength - insertAtIndex);
            // New [temp] to old:
            System.arraycopy(newVals, 0, shorts, insertAtIndex, newNumbersSize);
        }
        else if (ints != null)
        {
            // New to temp:
            int[] newVals = Arrays.copyOfRange(ints, originalLength, filled);
            // Old to new:
            System.arraycopy(ints, insertAtIndex, ints, insertAtIndex + newNumbersSize, originalLength - insertAtIndex);
            // New [temp] to old:
            System.arraycopy(newVals, 0, ints, insertAtIndex, newNumbersSize);
        }
        else if (longs != null)
        {
            // New to temp:
            long[] newVals = Arrays.copyOfRange(longs, originalLength, filled);
            // Old to new:
            System.arraycopy(longs, insertAtIndex, longs, insertAtIndex + newNumbersSize, originalLength - insertAtIndex);
            // New [temp] to old:
            System.arraycopy(newVals, 0, longs, insertAtIndex, newNumbersSize);

            if (bigDecimals != null)
            {
                // Big decimals can be less than filled in length.  Although a bit inefficient, we size up first
                // before doing the copy (more straightforward than handling various fiddly cases):
                if (bigDecimals.length < filled)
                    bigDecimals = Arrays.copyOf(bigDecimals, filled);

                // New to temp:
                @Nullable BigDecimal @Nullable[] newValsBD = Arrays.copyOfRange(bigDecimals, originalLength, filled);
                // Old to new:
                System.arraycopy(bigDecimals, insertAtIndex, bigDecimals, insertAtIndex + newNumbersSize, originalLength - insertAtIndex);
                // New [temp] to old:
                System.arraycopy(newValsBD, 0, bigDecimals, insertAtIndex, newNumbersSize);
            }
        }
    }
}
