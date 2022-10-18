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
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.qual.Pure;
import xyz.columnal.data.Table.Display;
import xyz.columnal.data.datatype.DataTypeValue;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.id.ColumnId;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.function.simulation.SimulationFunctionInt;

import java.util.function.Predicate;

/**
 * A column of data in a RecordSet.
 *
 * Column assumptions:
 *   - There is only one Column object for a particular column.  So you
 *     can use (==) to compare two columns for equality.
 *   - A Column always belongs to exactly one RecordSet.
 *   - A column has a fixed name.  getName() will always return the same
 *     value.
 *   - A Column has typed entries.  Because the columntype is only known at
 *     run-time, we can't use generics, but you can assume that get(int)
 *     always returns the same columntype of object for the entire lifetime
 *     of this Column object (we may need to revisit this if we introduce
 *     algebraic data types).
 *   - The data in a column is immutable while getVersion() returns the
 *     same number.  I.e. any change in data will always be accompanied
 *     by an increase in the version.
 *   - Columns have a fixed size, but this is not always known a priori.
 *     So while there is a fixed unchanging limit on the data (unless
 *     getVersion's return increases), you can't just ask how much data there is.
 *     Instead you must use the indexValid method (or indexProgress) to
 *     ask if that item exists.  It is always true that if indexValid(i)==true,
 *     indexValid(j) == true for 0 <= j <= i.
 *   - Currently, data is loaded sequentially.  So if indexProgress(i) > 1.0
 *     then indexProgress(j) > 1.0 for 0 <= j <= i, but we may revisit this
 *     if we let data load in a random order.  It is definitely always true
 *     that if indexProgress(j) > 0 then indexValid(j)
 */
@OnThread(Tag.Simulation)
public abstract class Column
{
    protected final RecordSet recordSet;
    private final ColumnId name;

    protected Column(RecordSet recordSet, ColumnId name)
    {
        this.recordSet = recordSet;
        this.name = name;
    }

    @Pure
    @OnThread(Tag.Any)
    public final ColumnId getName()
    {
        return name;
    }

    @OnThread(Tag.Any)
    public abstract DataTypeValue getType() throws InternalException, UserException;

    /*
    public final Object get(int index) throws UserException, InternalException
    {
        return getWithProgress(index, null);
    }
    public abstract Object getWithProgress(int index, @Nullable ProgressListener progressListener) throws UserException, InternalException;
*/
    public final boolean indexValid(int index) throws UserException, InternalException
    {
        return recordSet.indexValid(index);
    }

    public final int getLength() throws UserException, InternalException
    {
        return recordSet.getLength();
    }
    
    @OnThread(Tag.Any)
    public static class EditableStatus
    {
        // If false, definitely not editable.  If true, run checkEditable
        public final boolean editable;
        // Return true to continue the edit, false to abandon:
        public final @Nullable SimulationFunctionInt<@TableDataRowIndex Integer, Boolean> checkEditable;

        public EditableStatus(boolean editable, @Nullable SimulationFunctionInt<@TableDataRowIndex Integer, Boolean> checkEditable)
        {
            this.editable = editable;
            this.checkEditable = checkEditable;
        }
    }

    @OnThread(Tag.Any)
    public EditableStatus getEditableStatus()
    {
        return new EditableStatus(true, null);
    }

    // For testing: return copy of column with length trimmed to shrunkLength
    public Column _test_shrink(RecordSet rs, int shrunkLength) throws InternalException, UserException
    {
        throw new RuntimeException("Unshrinkable!");
    }

    /**
     * Gets the default value for this column, if any.
     * (Used for immediate columns which have a default for when
     * you add a new row.)  Null if N/A.
     */
    @OnThread(Tag.Any)
    public @Nullable @Value Object getDefaultValue()
    {
        return null;
    }

    /**
     * The predicate is only checked if display is CUSTOM
     */
    @OnThread(Tag.Any)
    public final boolean shouldShow(Pair<Display, Predicate<ColumnId>> columnSelection)
    {
        switch (columnSelection.getFirst())
        {
            case ALL:
                return true;
            case ALTERED:
                return getAlteredState() != AlteredState.UNALTERED;
            case CUSTOM:
                return columnSelection.getSecond().test(getName());
        }
        return false;
    }
    
    public static enum AlteredState
    {
        // Completely unchanged from source table of transformation (e.g. unaltered column in Calculate):
        UNALTERED,
        // Rows removed or moved, e.g. Filter, Sort,
        FILTERED_OR_REORDERED,
        // Values changed, e.g. Calculate and Aggregate destination columns
        OVERWRITTEN
    }

    /**
     * Is this column altered in this record set?
     * Used to decide whether we show column if display setting is set to ALTERED,
     * and to decide whether to warn in expression editor about column being changed.
     */
    @OnThread(Tag.Any)
    public abstract AlteredState getAlteredState();

    public final RecordSet getRecordSet()
    {
        return recordSet;
    }
}
