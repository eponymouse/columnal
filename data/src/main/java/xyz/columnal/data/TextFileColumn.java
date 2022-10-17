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

import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.ColumnStorage.BeforeGet;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.DataType.DateTimeInfo;
import xyz.columnal.data.datatype.NumberInfo;
import xyz.columnal.data.datatype.DataType.TagType;
import xyz.columnal.data.datatype.DataTypeValue;
import xyz.columnal.data.datatype.TypeId;
import xyz.columnal.data.unit.Unit;
import xyz.columnal.error.FetchException;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.id.ColumnId;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.function.ExBiConsumer;
import xyz.columnal.utility.function.ExFunction;
import xyz.columnal.utility.function.FunctionInt;
import xyz.columnal.utility.TaggedValue;
import xyz.columnal.utility.Utility;
import xyz.columnal.utility.Utility.ReadState;

import java.io.IOException;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * Created by neil on 22/10/2016.
 */
public final class TextFileColumn extends Column
{
    private final @Nullable String sep;
    private final int columnIndex;
    private final boolean lastColumn;
    private ReadState reader;
    @OnThread(Tag.Any)
    private final DataTypeValue type;

    protected <S extends ColumnStorage<?>> TextFileColumn(RecordSet recordSet, ReadState reader, @Nullable String sep, @Nullable String quote,
                                                          ColumnId columnName, int columnIndex, int totalColumns,
                                                          ExFunction<@Nullable BeforeGet<S>, S> createStorage,
                                                          ExBiConsumer<S, ArrayList<String>> addValues) throws InternalException, UserException
    {
        super(recordSet, columnName);
        this.sep = sep;
        this.reader = reader;
        this.columnIndex = columnIndex;
        this.lastColumn = columnIndex == totalColumns - 1;
        S theStorage = createStorage.apply((storage, rowIndex, prog) -> {
            try
            {
                while (rowIndex >= storage.filled())
                {
                    // Should we share loading across columns for the same file?
                    ArrayList<String> next = new ArrayList<>();
                    this.reader = Utility.readColumnChunk(this.reader, sep, quote, columnIndex, next);
                    addValues.accept(storage, next);
                    // If we're not adding any more, give up and thus prevent infinite loop:
                    if (next.isEmpty())
                        break;
                }
            }
            catch (IOException e)
            {
                throw new FetchException("Error reading file " + reader.getAbsolutePath(), e);
            }
        });
        type = theStorage.getType();

    }

    @Override
    @OnThread(Tag.Any)
    public final synchronized DataTypeValue getType() throws UserException, InternalException
    {
        return type;
    }

    public static TextFileColumn dateColumn(RecordSet recordSet, ReadState reader, @Nullable String sep, @Nullable String quote, ColumnId columnName, int columnIndex, int totalColumns, DateTimeInfo dateTimeInfo, FunctionInt<String, Either<String, TemporalAccessor>> parse) throws InternalException, UserException
    {
        return new TextFileColumn(recordSet, reader, sep, quote, columnName, columnIndex, totalColumns, 
            (BeforeGet<TemporalColumnStorage> fill) -> new TemporalColumnStorage(dateTimeInfo, fill, true),
            (storage, values) -> storage.addAll(Utility.<String, Either<String, TemporalAccessor>>mapListInt(values, parse).stream())
        );

    }

    public static TextFileColumn numericColumn(RecordSet recordSet, ReadState reader, @Nullable String sep, @Nullable String quote, ColumnId columnName, int columnIndex, int totalColumns, NumberInfo numberInfo, @Nullable UnaryOperator<String> processString) throws InternalException, UserException
    {
        return new TextFileColumn(recordSet, reader, sep, quote, columnName, columnIndex, totalColumns, 
            (BeforeGet<NumericColumnStorage> fill) -> new NumericColumnStorage(numberInfo, fill, true),
            (storage, values) ->
            {
                for (String value : values)
                {
                    String processed = value;
                    if (processString != null)
                        processed = processString.apply(processed);
                    storage.addRead(processed);
                }
            }
        );
    }

    public static TextFileColumn stringColumn(RecordSet recordSet, ReadState reader, @Nullable String sep, @Nullable String quote, ColumnId columnName, int columnIndex, int totalColumns) throws InternalException, UserException
    {
        return new TextFileColumn(recordSet, reader, sep, quote, columnName, columnIndex, totalColumns,
            (BeforeGet<StringColumnStorage> fill) -> new StringColumnStorage(fill, true),
            (storage, values) -> storage.addAll(values.stream().map(x -> Either.<String, String>right(x)))
        );
    }

    public static <DT extends DataType> TextFileColumn taggedColumn(RecordSet recordSet, ReadState reader, @Nullable String sep, @Nullable String quote, ColumnId columnName, int columnIndex, int totalColumns, TypeId typeName, ImmutableList<Either<Unit, DataType>> typeVars, List<TagType<DT>> tagTypes, ExFunction<String, Either<String, TaggedValue>> parseValue) throws InternalException, UserException
    {
        return new TextFileColumn(recordSet, reader, sep, quote, columnName, columnIndex, totalColumns,
            (BeforeGet<TaggedColumnStorage> fill) -> new TaggedColumnStorage(typeName, typeVars, tagTypes, fill, true),
            (storage, values) -> {
                storage.addAll(Utility.mapListEx(values, parseValue).stream());
            }
        );
    }

    @Override
    public @OnThread(Tag.Any) AlteredState getAlteredState()
    {
        // If we are direct from text file, we must be new:
        return AlteredState.OVERWRITTEN;
    }

    @OnThread(Tag.Any)
    public EditableStatus getEditableStatus()
    {
        return new EditableStatus(false, null);
    }
}
