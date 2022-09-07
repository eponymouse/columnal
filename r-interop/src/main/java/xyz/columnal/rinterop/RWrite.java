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

package xyz.columnal.rinterop;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.datatype.DataType.DateTimeInfo.DateTimeType;
import xyz.columnal.data.datatype.DataTypeUtility;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.utility.Utility;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAccessor;
import java.util.Optional;

public class RWrite
{
    public static void writeRData(File destFile, RValue topLevel) throws IOException, UserException, InternalException
    {
        final DataOutputStream d = new DataOutputStream(new FileOutputStream(destFile, false));
        // TODO compress
        d.writeByte('X');
        d.writeByte('\n');
        d.writeInt(3);
        d.writeInt(0x03_06_01); // We should match R 3.6.1
        d.writeInt(0x03_05_00);
        d.writeInt("UTF-8".length());
        d.writeBytes("UTF-8");
        topLevel.visit(new RVisitor<@Nullable Void>()
        {
            private void writeInt(int value) throws UserException
            {
                try
                {
                    d.writeInt(value);
                }
                catch (IOException e)
                {
                    throw new UserException("IO error", e);
                }
            }

            private void writeHeader(int value, @Nullable RValue attributes, @Nullable RValue tag) throws UserException, InternalException
            {
                writeHeader(value, attributes, tag, false);
            }

            private void writeHeader(int value, @Nullable RValue attributes, @Nullable RValue tag, boolean isObject) throws UserException, InternalException
            {
                try
                {
                    d.writeInt(value | (isObject ? 0x100 : 0) | (attributes != null ? 0x200 : 0) | (tag != null ? 0x400 : 0));
                    if (value == RUtility.PAIR_LIST)
                    {
                        writeAttributes(attributes);
                        writeAttributes(tag);
                    }
                }
                catch (IOException e)
                {
                    throw new UserException("IO error", e);
                }
            }



            private void writeDouble(double value) throws UserException
            {
                try
                {
                    d.writeDouble(value);
                }
                catch (IOException e)
                {
                    throw new UserException("IO error", e);
                }
            }

            private void writeBytes(byte[] value) throws UserException
            {
                try
                {
                    d.write(value);
                }
                catch (IOException e)
                {
                    throw new UserException("IO error", e);
                }
            }
            
            private void writeAttributes(@Nullable RValue attributes) throws UserException, InternalException
            {
                if (attributes == null)
                    return;
                attributes.visit(this);
            }
            
            @Override
            public @Nullable Void visitString(@Nullable @Value String s, boolean isSymbol) throws InternalException, UserException
            {
                if (isSymbol)
                    writeInt(RUtility.SYMBOL);
                writeInt(RUtility.STRING_SINGLE);
                writeLenString(s);
                return null;
            }

            private void writeLenString(@Nullable String s) throws UserException
            {
                if (s == null)
                    writeInt(-1);
                else
                {
                    byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
                    writeInt(bytes.length);
                    writeBytes(bytes);
                }
            }

            @Override
            public @Nullable Void visitStringList(ImmutableList<Optional<@Value String>> values, @Nullable RValue attributes) throws InternalException, UserException
            {
                writeHeader(RUtility.STRING_VECTOR, attributes, null);
                writeInt(values.size());
                for (Optional<@Value String> value : values)
                {
                    writeInt(RUtility.STRING_SINGLE);
                    writeLenString(value.orElse(null));
                }
                writeAttributes(attributes);
                return null;
            }

            @Override
            public @Nullable Void visitTemporalList(DateTimeType dateTimeType, ImmutableList<Optional<@Value TemporalAccessor>> values, @Nullable RValue attributes) throws InternalException, UserException
            {
                switch (dateTimeType)
                {
                    case YEARMONTHDAY:
                        writeHeader(RUtility.DOUBLE_VECTOR, attributes, null);
                        writeInt(values.size());
                        for (Optional<@Value TemporalAccessor> value : values)
                        {
                            if (value.isPresent())
                                writeDouble(((LocalDate)value.get()).toEpochDay());
                            else
                                writeDouble(Double.NaN);
                        }
                        writeAttributes(attributes);
                        break;
                    case DATETIME:
                        writeHeader(RUtility.DOUBLE_VECTOR, attributes, null);
                        writeInt(values.size());
                        for (Optional<@Value TemporalAccessor> value : values)
                        {
                            if (value.isPresent())
                            {
                                LocalDateTime ldt = (LocalDateTime) value.get();
                                double seconds = (double) ldt.toEpochSecond(ZoneOffset.UTC) + (double) ldt.getNano() / 1_000_000_000.0;
                                writeDouble(seconds / (60.0 * 60.0 * 24.0));
                            }
                            else
                                writeDouble(Double.NaN);
                        }
                        writeAttributes(attributes);
                        break;
                    case DATETIMEZONED:
                        writeHeader(RUtility.DOUBLE_VECTOR, attributes, null);
                        writeInt(values.size());
                        for (Optional<@Value TemporalAccessor> value : values)
                        {
                            if (value.isPresent())
                            {
                                ZonedDateTime zdt = (ZonedDateTime) value.get();
                                writeDouble((double) zdt.toEpochSecond() + ((double) zdt.getNano() / 1_000_000_000.0));
                            }
                            else
                                writeDouble(Double.NaN);
                        }
                        writeAttributes(attributes);
                        break;
                    case TIMEOFDAY:
                        writeHeader(RUtility.DOUBLE_VECTOR, attributes, null);
                        writeInt(values.size());
                        for (Optional<@Value TemporalAccessor> value : values)
                        {
                            if (value.isPresent())
                            {
                                LocalTime lt = (LocalTime) value.get();
                                writeDouble((double) lt.toNanoOfDay() / 1_000_000_000.0);
                            }
                            else
                                writeDouble(Double.NaN);
                        }
                        writeAttributes(attributes);
                        break;
                    case YEARMONTH:
                        writeHeader(RUtility.DOUBLE_VECTOR, attributes, null);
                        writeInt(values.size());
                        for (Optional<@Value TemporalAccessor> value : values)
                        {
                            if (value.isPresent())
                            {
                                YearMonth yearMonth = (YearMonth) value.get();
                                writeDouble(yearMonth.atDay(1).toEpochDay());
                            }
                            else
                                writeDouble(Double.NaN);
                        }
                        writeAttributes(attributes);
                        break;
                }
                return null;
            }

            @Override
            public @Nullable Void visitIntList(int[] values, @Nullable RValue attributes) throws InternalException, UserException
            {
                writeHeader(RUtility.INT_VECTOR, attributes, null);
                writeInt(values.length);
                for (int value : values)
                {
                    writeInt(value);
                }
                writeAttributes(attributes);
                return null;
            }

            @Override
            public @Nullable Void visitDoubleList(double[] values, @Nullable RValue attributes) throws InternalException, UserException
            {
                writeHeader(RUtility.DOUBLE_VECTOR, attributes, null);
                writeInt(values.length);
                for (double value : values)
                {
                    writeDouble(value);
                }
                writeAttributes(attributes);
                return null;
            }

            @Override
            public @Nullable Void visitLogicalList(boolean[] values, boolean @Nullable [] isNA, @Nullable RValue attributes) throws InternalException, UserException
            {
                writeHeader(RUtility.LOGICAL_VECTOR, attributes, null);
                writeInt(values.length);
                for (int i = 0; i < values.length; i++)
                {
                    writeInt(isNA != null && isNA[i] ? RUtility.NA_AS_INTEGER : (values[i] ? 1 : 0));
                }
                writeAttributes(attributes);
                return null;
            }

            @Override
            public @Nullable Void visitGenericList(ImmutableList<RValue> values, @Nullable RValue attributes, boolean isObject) throws InternalException, UserException
            {
                writeHeader(RUtility.GENERIC_VECTOR, attributes, null, isObject);
                writeInt(values.size());
                for (RValue value : values)
                {
                    value.visit(this);
                }
                writeAttributes(attributes);
                return null;
            }

            @Override
            public @Nullable Void visitPairList(ImmutableList<PairListEntry> items) throws InternalException, UserException
            {
                switch (items.size())
                {
                    case 0:
                        break;
                    case 1:
                        writeHeader(RUtility.PAIR_LIST, items.get(0).attributes, items.get(0).tag);
                        items.get(0).item.visit(this);
                        visitNil();
                        break;
                    /*
                    case 2:
                        writeHeader(PAIR_LIST, items.get(0).attributes, items.get(0).tag);
                        items.get(0).item.visit(this);
                        items.get(1).item.visit(this);
                        break;*/
                    default:
                        writeHeader(RUtility.PAIR_LIST, items.get(0).attributes, items.get(0).tag);
                        items.get(0).item.visit(this);
                        visitPairList(items.subList(1, items.size()));
                        break;
                }
                return null;
            }

            @Override
            public @Nullable Void visitFactorList(int[] values, ImmutableList<String> levelNames) throws InternalException, UserException
            {
                RValue attributes = RUtility.makePairList(ImmutableList.of(new PairListEntry(null, RUtility.string("levels", false), RUtility.stringVector(Utility.<String, Optional<@Value String>>mapListI(levelNames, s -> Optional.of(DataTypeUtility.value(s))), null))));
                return visitIntList(values, attributes);
            }

            @Override
            public @Nullable Void visitNil() throws InternalException, UserException
            {
                writeInt(RUtility.NIL);
                return null;
            }
        });
        d.flush();
        d.close();
    }
}
