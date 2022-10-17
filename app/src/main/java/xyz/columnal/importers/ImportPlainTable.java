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

package xyz.columnal.importers;

import com.google.common.collect.ImmutableList;
import javafx.beans.binding.ObjectExpression;
import javafx.beans.property.SimpleObjectProperty;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.id.ColumnId;
import xyz.columnal.data.EditableRecordSet;
import xyz.columnal.data.RecordSet;
import xyz.columnal.data.TableManager;
import xyz.columnal.data.columntype.TextColumnType;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.importers.GuessFormat.Import;
import xyz.columnal.importers.GuessFormat.TrimChoice;
import xyz.columnal.importers.ImportPlainTable.PlainImportInfo;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.UnitType;

import java.util.List;

abstract class ImportPlainTable implements Import<UnitType, PlainImportInfo>
{
    private final int numSrcColumns;
    private final TableManager mgr;
    private final List<List<String>> vals;
    protected final SimpleObjectProperty<@Nullable UnitType> format = new SimpleObjectProperty<>(UnitType.UNIT);

    // vals must be rectangular
    public ImportPlainTable(int numSrcColumns, TableManager mgr, List<? extends List<String>> vals)
    {
        this.numSrcColumns = numSrcColumns;
        this.mgr = mgr;
        // Fix type issue:
        this.vals = vals.stream().<List<String>>map(v -> v).collect(ImmutableList.<List<String>>toImmutableList());
    }

    @Override
    public ObjectExpression<@Nullable UnitType> currentSrcFormat()
    {
        return format;
    }

    @Override
    public SrcDetails loadSource(UnitType u) throws UserException, InternalException
    {
        TrimChoice trimChoice = GuessFormat.guessTrim(vals);
        ImmutableList.Builder<ColumnInfo> columns = ImmutableList.builder();
        ImmutableList.Builder<@Localized String> columnDisplayNames = ImmutableList.builder();
        for (int i = 0; i < numSrcColumns; i++)
        {
            Pair<ColumnId, @Localized String> name = srcColumnName(i);
            columns.add(new ColumnInfo(new TextColumnType(), name.getFirst()));
            columnDisplayNames.add(name.getSecond());
        }
        EditableRecordSet recordSet = ImporterUtility.makeEditableRecordSet(mgr.getTypeManager(), vals, columns.build());
        return new SrcDetails(trimChoice, recordSet, columnDisplayNames.build());
    }

    // Gets internal column name, and name to display in GUI for src table
    public abstract Pair<ColumnId, @Localized String> srcColumnName(int index);

    public ColumnId destColumnName(TrimChoice trimChoice, int index)
    {
        return srcColumnName(index).getFirst();
    }

    @Override
    public Pair<PlainImportInfo, RecordSet> loadDest(UnitType u, TrimChoice trimChoice) throws UserException, InternalException
    {
        ImmutableList<ColumnInfo> columns = GuessFormat.guessGeneralFormat(mgr.getUnitManager(), processTrimmed(vals), trimChoice, (trim, i) -> destColumnName(trim, i));
        return new Pair<>(new PlainImportInfo(columns, trimChoice), ImporterUtility.makeEditableRecordSet(mgr.getTypeManager(), processTrimmed(trimChoice.trim(vals)), columns));
    }

    // Allows for String trim()ing in subclass
    @OnThread(Tag.Simulation)
    public List<? extends List<String>> processTrimmed(List<List<String>> trimmed)
    {
        return trimmed;
    }

    public static class PlainImportInfo
    {
        public final ImmutableList<ColumnInfo> columnInfo;
        public final TrimChoice trim;

        public PlainImportInfo(ImmutableList<ColumnInfo> columnInfo, TrimChoice trim)
        {
            this.columnInfo = columnInfo;
            this.trim = trim;
        }
    }
}
