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

package xyz.columnal.gui.stable;

import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.id.ColumnId;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.utility.function.fx.FXPlatformConsumer;

public class ColumnDetails
{
    private final ColumnHandler columnHandler;
    private final ColumnId columnId;
    // What to actually put on screen; usually same as columnId.getRaw()
    private final @Localized String displayHeaderLabel;
    private final ImmutableList<String> displayHeaderClasses;
    private final DataType columnType;
    private final @Nullable FXPlatformConsumer<ColumnId> renameColumn;

    public ColumnDetails(ColumnId columnId, DataType columnType, @Nullable FXPlatformConsumer<ColumnId> renameColumn, ColumnHandler columnHandler, ImmutableList<String> headerStyleClasses)
    {
        this(columnId, columnId.getRaw(), columnType, renameColumn, columnHandler, headerStyleClasses);
    }

    private ColumnDetails(ColumnId columnId, @Localized String displayHeaderLabel, DataType columnType, @Nullable FXPlatformConsumer<ColumnId> renameColumn, ColumnHandler columnHandler, ImmutableList<String> displayHeaderClasses)
    {
        this.columnId = columnId;
        this.displayHeaderLabel = displayHeaderLabel;
        this.displayHeaderClasses = displayHeaderClasses;
        this.columnType = columnType;
        this.renameColumn = renameColumn;
        this.columnHandler = columnHandler;
    }
    
    public ColumnDetails withDisplayHeaderLabel(@Localized String displayHeaderLabel)
    {
        return new ColumnDetails(columnId, displayHeaderLabel, columnType, renameColumn, columnHandler, displayHeaderClasses);
    }
    
    public final ColumnId getColumnId()
    {
        return columnId;
    }

    public DataType getColumnType()
    {
        return columnType;
    }

    public final ColumnHandler getColumnHandler()
    {
        return columnHandler;
    }

    public @Nullable FXPlatformConsumer<ColumnId> getRenameColumn()
    {
        return renameColumn;
    }

    public @Localized String getDisplayHeaderLabel()
    {
        return displayHeaderLabel;
    }

    public ImmutableList<String> getDisplayHeaderClasses()
    {
        return displayHeaderClasses;
    }
}
