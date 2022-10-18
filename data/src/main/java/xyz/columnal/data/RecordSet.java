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
import annotation.units.TableDataRowIndex;
import com.google.common.collect.ImmutableList;
import javafx.application.Platform;
import xyz.columnal.id.ColumnId;
import xyz.columnal.log.Log;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.datatype.DataTypeValue;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.InvalidImmediateValueException;
import xyz.columnal.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.function.simulation.SimulationFunction;
import xyz.columnal.utility.Utility;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * A RecordSet is a collection of columns.  It is the nitty gritty data part
 * of Table, which deals with wider book-keeping.
 *
 * RecordSet assumptions:
 *
 *  - All of the columns in RecordSet have the same number of entries.
 *    A RecordSet will not be bottom-ragged.
 *  - This number of entries is not known a priori.  See Column.indexValid
 *    for a discussion of that method.
 *  - Columns are otherwise treated independently.  Just because one column
 *    value is loaded doesn't mean that any values in any other columns
 *    will/won't be loaded.
 */
public abstract class RecordSet
{
    @OnThread(Tag.Any)
    protected final ArrayList<Column> columns;

    @OnThread(Tag.FXPlatform)
    protected @MonotonicNonNull RecordSetListener listener;

    protected RecordSet()
    {
        this.columns = new ArrayList<>();
    }
    
    public <C extends Column> RecordSet(List<SimulationFunction<RecordSet, C>> columns) throws InternalException, UserException
    {
        this.columns = new ArrayList<>();
        Set<ColumnId> colNames = new HashSet<>();
        for (SimulationFunction<RecordSet, ? extends Column> f : columns)
        {
            @SuppressWarnings("argument")
            Column newCol = f.apply(this);
            this.columns.add(newCol);
            colNames.add(newCol.getName());
        }
        if (colNames.size() != columns.size())
            throw new UserException("Duplicate column names found: " + this.columns.stream().map(c -> c.getName().getOutput()).collect(Collectors.joining(", ")));
    }
/*
    @OnThread(Tag.FXPlatform)
    public final List<TableColumn<Integer, DisplayValueBase>> getDisplayColumns() throws InternalException, UserException
    {
        ExFunction<@NonNull Column, @NonNull TableColumn<Integer, DisplayValueBase>> makeDisplayColumn = data ->
        {
            TableColumn<Integer, DisplayValueBase> c = new TableColumn<>(data.getName().toString());
            c.setEditable(data.isEditable());
            // If last row is index 8, we add an integer -9 (note the minus) afterwards
            // to indicate a special "add more data" row.
            c.setCellValueFactory(cdf ->
                    cdf.getValue() < 0 ?
                            new ReadOnlyObjectWrapper<DisplayValueBase>(DisplayValue.getAddDataItem(cdf.getValue() == Integer.MIN_VALUE ? 0 : -cdf.getValue())) :
                            data.getDisplay(cdf.getValue()));
            DataType.StringConvBase converter = data.getType().makeConverter();
            c.setCellFactory(col -> {
                return new TextFieldTableCell<Integer, DisplayValueBase>(converter) {
                    @Override
                    @OnThread(Tag.FX)
                    public void updateItem(DisplayValueBase itemBase, boolean empty)
                    {
                        super.updateItem(itemBase, empty);
                        if (itemBase != null && !(itemBase instanceof DisplayValueBase))
                        {
                            // Shouldn't happen, but prevent an exception in case:
                            setText("ERR:DVB");
                            setGraphic(null);
                            return;
                        }
                        DisplayValue item = (DisplayValue)itemBase;
                        if (item == null)
                        {
                            setText("");
                            setGraphic(null);
                        }
                        else if (item.isAddExtraRowItem())
                        {
                            setText("Add more data...");
                            setGraphic(null);
                        }
                        else if (item.getNumber() != null)
                        {
                            @NonNull Number n = item.getNumber();
                            setText("");
                            HBox container = new HBox();
                            Utility.addStyleClass(container, "number-display");
                            Text prefix = new Text(item.getUnit().getDisplayPrefix());
                            Utility.addStyleClass(prefix, "number-display-prefix");
                            String integerPart = Utility.getIntegerPart(n).toString();
                            integerPart = integerPart.replace("-", "\u2012");
                            Text whole = new Text(integerPart);
                            Utility.addStyleClass(whole, "number-display-int");
                            String fracPart = Utility.getFracPartAsString(n);
                            while (fracPart.length() < item.getMinimumDecimalPlaces())
                                fracPart += "0";
                            Text frac = new Text(fracPart.isEmpty() ? "" : ("." + fracPart));
                            Utility.addStyleClass(frac, "number-display-frac");
                            Pane spacer = new Pane();
                            spacer.setVisible(false);
                            HBox.setHgrow(spacer, Priority.ALWAYS);
                            container.getChildren().addAll(prefix, spacer, whole, frac);
                            setGraphic(container);
                        }
                        else
                        {
                            setGraphic(null);
                            setText(item.toString());
                        }
                    }

                    @Override
                    @OnThread(Tag.FX)
                    public void startEdit()
                    {
                        super.startEdit();
                        converter.setRowIndex(getIndex());
                    }

                    @Override
                    @OnThread(Tag.FX)
                    public void commitEdit(DisplayValueBase writtenValue)
                    {
                        // We will have been passed an EnteredDisplayValue
                        // We must store the value then pass a DisplayValue
                        // to the parent for actual display
                        Workers.onWorkerThread("Storing value " + writtenValue, Workers.Priority.SAVE_ENTRY, () ->
                        {
                            Utility.alertOnError_(() ->
                            {
                                // If we're editing the special extra row, extend all columns first:
                                if (writtenValue.getRowIndex() == data.getLength())
                                {
                                    addRow();
                                }
                                DisplayValue readableValue = data.storeValue((EnteredDisplayValue) writtenValue);
                                Platform.runLater(() -> super.commitEdit(readableValue));
                            });
                        });
                    }
                };
            });
            c.setSortable(false);
            data.withDisplay(type -> {
                c.setText("");
                c.setGraphic(new Label(type + "\n" + data.getName()));
            });
            return c;
        };
        return Utility.mapListEx(columns, makeDisplayColumn);
    }
*/

    @OnThread(Tag.Any)
    public final Column getColumn(ColumnId name) throws UserException
    {
        for (Column c : columns)
        {
            if (c.getName().equals(name))
                return c;
        }
        throw new UserException("Column not found: {{{" + name.getRaw() + "}}}");
    }

    @OnThread(Tag.Any)
    public final @Nullable Column getColumnOrNull(ColumnId name)
    {
        for (Column c : columns)
        {
            if (c.getName().equals(name))
                return c;
        }
        return null;
    }

    //package-protected:
    public abstract boolean indexValid(int index) throws UserException, InternalException;

    // Only use when you really need to know the length!
    // Override in subclasses if you can do it faster
    @SuppressWarnings("units")
    public @TableDataRowIndex int getLength() throws UserException, InternalException
    {
        int i = 0;
        while (indexValid(i))
        {
            i += 1;
        }
        return i;
    }

    @OnThread(Tag.Any)
    public final List<Column> getColumns(@UnknownInitialization(RecordSet.class) RecordSet this)
    {
        return Collections.unmodifiableList(columns);
    }

    public String debugGetVals()
    {
        try
        {
            return columns.stream().map(c ->
            {
                try
                {
                    return c.getName() + "::" + c.getType();
                }
                catch (InternalException | UserException e)
                {
                    return c.getName() + "::TYPE_ERR";
                }
            }).collect(Collectors.joining(" ")) +
                IntStream.range(0, getLength()).mapToObj(this::debugGetVals).collect(Collectors.joining("\n"));
        }
        catch (InternalException | UserException e)
        {
            return "ERR:" + e.getLocalizedMessage();
        }
    }

    public String debugGetVals(int i)
    {
        return columns.stream().map(c -> { 
            try
            {
                return c.getName().getRaw() + ":\"" + c.getType().getCollapsed(i).toString() + "\"";
            }
            catch (Exception e)
            {
                return "ERR:" + e.getLocalizedMessage();
            }
        }).collect(Collectors.joining(", "));
    }

    @OnThread(Tag.Any)
    public final ImmutableList<ColumnId> getColumnIds()
    {
        return Utility.<Column, ColumnId>mapListI(columns, Column::getName);
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || !(o instanceof RecordSet)) return false;

        RecordSet recordSet = (RecordSet) o;

        if (columns.size() != recordSet.columns.size()) return false;
        try
        {
            int length = getLength();
            if (length != recordSet.getLength()) return false;
            for (Column usCol : columns)
            {
                Column themCol = recordSet.getColumn(usCol.getName()); // Throws if not there
                DataTypeValue us = usCol.getType();
                DataTypeValue them = themCol.getType();
                if (!us.getType().equals(them.getType()))
                    return false;
                for (int i = 0; i < length; i++)
                {
                    Either<String, @Value Object> ax = getCollapsedErr(us, i);
                    Either<String, @Value Object> bx = getCollapsedErr(them, i);
                    boolean same = ax.eitherEx(aerr -> bx.eitherEx(berr -> aerr.equals(berr), _bv -> false), av -> bx.eitherEx(_ae -> false, bv -> Utility.compareValues(av, bv) == 0));
                    if (!same)
                        return false;
                }
            }
        }
        catch (InternalException | UserException e)
        {
            Log.log(e);
            // Only used for testing anyway:
            return false;
        }
        return true;
    }

    @OnThread(Tag.Simulation)
    private Either<String, @Value Object> getCollapsedErr(DataTypeValue dataTypeValue, int index) throws InternalException, UserException
    {
        try
        {
            return Either.right(dataTypeValue.getCollapsed(index));
        }
        catch (InvalidImmediateValueException e)
        {
            return Either.left(e.getInvalid());
        }
    }

    @Override
    public int hashCode()
    {
        int result = columns.size();
        // It's bad performance but semantically valid to not compare the column
        // values here.  Lots of hash code collisions but that's valid.
        return result;
    }

    @OnThread(Tag.FXPlatform)
    public void setListener(RecordSetListener listener)
    {
        this.listener = listener;
    }

    @OnThread(Tag.Simulation)
    public void modified(@Nullable ColumnId columnId, @Nullable Integer rowIndex)
    {
        Platform.runLater(() -> {
            if (listener != null)
                listener.modifiedDataItems(rowIndex == null ? -1 : rowIndex, rowIndex == null ? -1 : rowIndex);
        });
    }

    @OnThread(Tag.FXPlatform)
    public static interface RecordSetListener
    {
        // Range of rows modified.  -1 for both params means all rows
        @OnThread(Tag.FXPlatform)
        public void modifiedDataItems(int startRowIncl, int endRowIncl);

        // Starting at startRowIncl, removedRowsCount (>= 0) was removed,
        // and in its place was added addedRowsCount (>= 0).
        @OnThread(Tag.FXPlatform)
        public void removedAddedRows(int startRowIncl, int removedRowsCount, int addedRowsCount);

        @OnThread(Tag.FXPlatform)
        public void addedColumn(Column newColumn);

        @OnThread(Tag.FXPlatform)
        public void removedColumn(ColumnId oldColumnId);
    }
}
