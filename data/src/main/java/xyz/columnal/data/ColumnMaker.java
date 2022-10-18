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

import annotation.qual.Value;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.grammar.DataParser;
import xyz.columnal.grammar.DataParser2;
import xyz.columnal.utility.function.BiFunctionInt;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.function.ExBiConsumer;
import xyz.columnal.utility.function.ExFunction;
import xyz.columnal.utility.function.simulation.SimulationFunctionInt;
import xyz.columnal.utility.Utility;

public class ColumnMaker<C extends EditableColumn, V> implements SimulationFunctionInt<RecordSet, EditableColumn>
{
    private final ExBiConsumer<C, Either<String, V>> addToColumn;
    private final ExFunction<DataParser, Either<String, V>> parseValue1;
    private final ExFunction<DataParser2, Either<String, V>> parseValue;
    private final BiFunctionInt<RecordSet, @Value V, C> makeColumn;
    private final @Value V defaultValue;
    private @MonotonicNonNull C column;

    ColumnMaker(@Value Object defaultValue, Class<V> valueClass, BiFunctionInt<RecordSet, @Value V, C> makeColumn, ExBiConsumer<C, Either<String, V>> addToColumn, ExFunction<DataParser, Either<String, V>> parseValue1, ExFunction<DataParser2, Either<String, V>> parseValue) throws UserException, InternalException
    {
        this.makeColumn = makeColumn;
        this.addToColumn = addToColumn;
        this.parseValue1 = parseValue1;
        this.parseValue = parseValue;
        this.defaultValue = Utility.cast(defaultValue, valueClass);
    }

    public final EditableColumn apply(RecordSet rs) throws InternalException
    {
        column = makeColumn.apply(rs, defaultValue);
        return column;
    }

    // Only valid to call after apply:
    public void loadRow1(DataParser p) throws InternalException, UserException
    {
        if (column == null)
            throw new InternalException("Calling loadRow before column creation");
        addToColumn.accept(column, parseValue1.apply(p));
    }

    // Only valid to call after apply:
    public void loadRow(DataParser2 p) throws InternalException, UserException
    {
        if (column == null)
            throw new InternalException("Calling loadRow before column creation");
        addToColumn.accept(column, parseValue.apply(p));
    }
}
