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
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;

import java.time.temporal.TemporalAccessor;
import java.util.Optional;

/**
 * A visitor which effectively reveals the structure of RValue.
 */
public interface RVisitor<T>
{
    public static class PairListEntry
    {
        public final @Nullable RValue attributes;
        public final @Nullable RValue tag;
        public final RValue item;

        public PairListEntry(@Nullable RValue attributes, @Nullable RValue tag, RValue item)
        {
            this.attributes = attributes;
            this.tag = tag;
            this.item = item;
        }
    }
    
    public T visitString(@Nullable @Value String s, boolean isSymbol) throws InternalException, UserException;
    // If attributes reveal this is a factor, it won't be called; visitFactorList will be instead
    public T visitIntList(int[] values, @Nullable RValue attributes) throws InternalException, UserException;
    public T visitDoubleList(double[] values, @Nullable RValue attributes) throws InternalException, UserException;
    public T visitLogicalList(boolean[] values, boolean @Nullable [] isNA, @Nullable RValue attributes) throws InternalException, UserException;
    public T visitStringList(ImmutableList<Optional<@Value String>> values, @Nullable RValue attributes) throws InternalException, UserException;
    public T visitTemporalList(DateTimeType dateTimeType, ImmutableList<Optional<@Value TemporalAccessor>> values, @Nullable RValue attributes) throws InternalException, UserException;
    public T visitGenericList(ImmutableList<RValue> values, @Nullable RValue attributes, boolean isObject) throws InternalException, UserException;
    public T visitPairList(ImmutableList<PairListEntry> items) throws InternalException, UserException;
    public T visitFactorList(int[] values, ImmutableList<String> levelNames) throws InternalException, UserException;
    public T visitNil() throws  InternalException, UserException;
}
