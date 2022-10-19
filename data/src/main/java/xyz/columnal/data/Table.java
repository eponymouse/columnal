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

import annotation.identifier.qual.ExpressionIdentifier;
import annotation.qual.Value;
import annotation.units.AbsColIndex;
import annotation.units.AbsRowIndex;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.EnsuresNonNullIf;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.qual.Pure;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.grammar.DisplayLexer;
import xyz.columnal.grammar.DisplayParser;
import xyz.columnal.grammar.DisplayParser.TableDisplayDetailsContext;
import xyz.columnal.grammar.MainLexer;
import xyz.columnal.grammar.MainParser.DetailPrefixedContext;
import xyz.columnal.grammar.MainParser.DisplayContext;
import xyz.columnal.grammar.Versions.OverallVersion;
import xyz.columnal.id.ColumnId;
import xyz.columnal.id.SaveTag;
import xyz.columnal.id.TableAndColumnRenames;
import xyz.columnal.id.TableId;
import xyz.columnal.loadsave.OutputBuilder;
import xyz.columnal.log.Log;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.IdentifierUtility;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.function.simulation.SimulationRunnable;
import xyz.columnal.utility.Utility;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * A Table is a wrapper for a RecordSet which keeps various metadata
 * on where the data originates from, and details about displaying it.
 */
public abstract class Table
{
    public static enum Display
    {
        /** No table shown, just a label */
        COLLAPSED,
        /** All columns shown */
        ALL,
        /** Only those affected by transformation compared to source table */
        ALTERED,
        /** A custom set (done by black list, not white list) */
        CUSTOM;
    }

    private final TableManager mgr;
    private final TableId id;
    protected final SaveTag saveTag;
    @OnThread(value = Tag.Any, requireSynchronized = true)
    private @MonotonicNonNull TableDisplayBase display;
    @OnThread(value = Tag.Any, requireSynchronized = true)
    @SuppressWarnings("units")
    private CellPosition prevPosition = new CellPosition(1, 1);

    // The list is the blacklist, only applicable if first is CUSTOM:
    @OnThread(value = Tag.Any, requireSynchronized = true)
    private Pair<Display, ImmutableList<ColumnId>> showColumns = new Pair<>(Display.ALL, ImmutableList.of());

    public static class InitialLoadDetails
    {
        private final @Nullable TableId tableId;
        private final @Nullable SaveTag saveTag;
        private final @Nullable CellPosition initialPosition;
        private final @Nullable Pair<Display, ImmutableList<ColumnId>> initialShowColumns;

        @OnThread(Tag.Any)
        public InitialLoadDetails(@Nullable TableId tableId, @Nullable SaveTag saveTag, @Nullable CellPosition initialPosition, @Nullable Pair<Display, ImmutableList<ColumnId>> initialShowColumns)
        {
            this.tableId = tableId;
            this.saveTag = saveTag;
            this.initialPosition = initialPosition;
            this.initialShowColumns = initialShowColumns;
        }

        @OnThread(Tag.Any)
        public InitialLoadDetails(CellPosition initialPosition)
        {
            this(null, null, initialPosition, null);
        }
        
        public InitialLoadDetails withTableId(TableId tableId)
        {
            return new InitialLoadDetails(tableId, this.saveTag, this.initialPosition, this.initialShowColumns);
        }
    }
    
    /**
     * If id is null, an arbitrary free id is taken
     * @param mgr
     * @param id
     */
    protected Table(TableManager mgr, InitialLoadDetails initialLoadDetails)
    {
        this.mgr = mgr;
        if (initialLoadDetails.tableId == null || initialLoadDetails.tableId.getRaw().isEmpty())
            this.id = mgr.registerNextFreeId();
        else
        {
            this.id = initialLoadDetails.tableId;
        }
        if (initialLoadDetails.saveTag == null || initialLoadDetails.saveTag.getTag().isEmpty())
        {
            this.saveTag = SaveTag.generateRandom();
        }
        else
        {
            this.saveTag = initialLoadDetails.saveTag;
        }
        if (initialLoadDetails.initialPosition != null)
            prevPosition = initialLoadDetails.initialPosition;
        if (initialLoadDetails.initialShowColumns != null)
            showColumns = initialLoadDetails.initialShowColumns;
    }

    @OnThread(Tag.Any)
    @Pure
    public final TableId getId(@UnknownInitialization(Table.class) Table this)
    {
        return id;
    }

    @OnThread(Tag.Any)
    public abstract RecordSet getData() throws UserException, InternalException;

    public static interface Saver
    {
        @OnThread(Tag.Simulation)
        public void saveTable(String tableSrc);

        @OnThread(Tag.Simulation)
        public void saveUnit(String unitSrc);

        @OnThread(Tag.Simulation)
        public void saveType(String typeSrc);

        @OnThread(Tag.Simulation)
        public void saveComment(String commentSrc);
    }

    @OnThread(Tag.Simulation)
    public static class BlankSaver implements Saver
    {
        @Override
        public @OnThread(Tag.Simulation) void saveTable(String tableSrc)
        {
        }

        @Override
        @OnThread(Tag.Simulation)
        public void saveUnit(String unitSrc)
        {
        }

        @Override
        @OnThread(Tag.Simulation)
        public void saveType(String typeSrc)
        {
        }

        @Override
        @OnThread(Tag.Simulation)
        public void saveComment(String commentSrc)
        {
        }
    }

    @OnThread(Tag.Simulation)
    public static class FullSaver implements Saver
    {
        private final List<String> units = new ArrayList<>();
        private final List<String> types = new ArrayList<>();
        private final List<String> tables = new ArrayList<>();
        private final List<String> comments = new ArrayList<>();
        private final @Nullable ImmutableList<String> displayDetailLines;

        public FullSaver(@Nullable ImmutableList<String> displayDetailLines)
        {
            this.displayDetailLines = displayDetailLines;
        }

        @Override
        public @OnThread(Tag.Simulation) void saveTable(String tableSrc)
        {
            tables.add(tableSrc);
        }

        @Override
        @OnThread(Tag.Simulation)
        public void saveUnit(String unitSrc)
        {
            units.add(unitSrc.endsWith("\n") ? unitSrc : unitSrc + "\n");
        }

        @Override
        @OnThread(Tag.Simulation)
        public void saveType(String typeSrc)
        {
            types.add(typeSrc.endsWith("\n") ? typeSrc : typeSrc + "\n");
        }

        @Override
        public @OnThread(Tag.Simulation) void saveComment(String commentSrc)
        {
            comments.add(commentSrc);
        }

        @OnThread(Tag.Simulation)
        public String getCompleteFile()
        {
            return "COLUMNAL\nVERSION " + OverallVersion.latest().asNumber() + "\n\nUNITS @BEGIN UU\n"
                + units.stream().map(u -> "UU " + u).collect(Collectors.joining())
                + "@END UU UNITS\n\nTYPES @BEGIN TT\n"
                + types.stream().map(t -> "TT " + t).collect(Collectors.joining())
                + "@END TT TYPES\n"
                + tables.stream().collect(Collectors.joining("\n"))
                + comments.stream().collect(Collectors.joining("\n"))
                + (displayDetailLines == null ? "" : ("DISPLAY @BEGIN DD" + displayDetailLines.stream().map(d -> "\nDD " + d).collect(Collectors.joining()) + "\n@END DD DISPLAY"))
                + "\n";
        }
    }

    @OnThread(Tag.Simulation)
    public abstract void save(@Nullable File destination, Saver then, TableAndColumnRenames renames);

    @OnThread(Tag.FXPlatform)
    public synchronized void setDisplay(TableDisplayBase display)
    {
        if (this.display != null)
        {
            try
            {
                throw new InternalException("Overwriting table display!");
            }
            catch (InternalException e)
            {
                Utility.report(e);
            }
        }
        this.display = display;
        display.loadPosition(prevPosition, showColumns);
    }

    public static InitialLoadDetails loadDetails(TableId tableId, DetailPrefixedContext tagFromContext, @Nullable DisplayContext detailContext) throws UserException, InternalException
    {
        if (detailContext == null)
            return new InitialLoadDetails(tableId, new SaveTag(tagFromContext.BEGIN().getText().substring("@BEGIN".length()).trim()), null, null);
        return Utility.parseAsOne(Utility.getDetail(detailContext.detail()), DisplayLexer::new, DisplayParser::new, p -> {
            return loadDetails(tableId, new SaveTag(tagFromContext.BEGIN().getText().substring("@BEGIN".length()).trim()), p.tableDisplayDetails());
        });
    }

    public static InitialLoadDetails loadDetails(TableId tableId, SaveTag saveTag, TableDisplayDetailsContext displayContext) throws UserException
    {
        try
        {
            @SuppressWarnings("units")
            @AbsColIndex int x = Integer.parseInt(displayContext.displayTablePosition().item(0).getText());
            @SuppressWarnings("units")
            @AbsRowIndex int y = Integer.parseInt(displayContext.displayTablePosition().item(1).getText());
            CellPosition initialPosition = new CellPosition(y, x);

            Pair<Display, ImmutableList<ColumnId>> initialShowColumns;
            // Now handle the show-columns:
            if (displayContext.displayShowColumns().ALL() != null)
                initialShowColumns = new Pair<>(Display.ALL, ImmutableList.of());
            else if (displayContext.displayShowColumns().ALTERED() != null)
                initialShowColumns = new Pair<>(Display.ALTERED, ImmutableList.of());
            else if (displayContext.displayShowColumns().COLLAPSED() != null)
                initialShowColumns = new Pair<>(Display.COLLAPSED, ImmutableList.of());
            else
            {
                ImmutableList<ColumnId> blackList = displayContext.displayShowColumns().item().stream().map(itemContext -> {
                    @ExpressionIdentifier String text = IdentifierUtility.fixExpressionIdentifier(itemContext.getText(), "Could Not Load");
                    return new ColumnId(text);
                }).collect(ImmutableList.<ColumnId>toImmutableList());
                initialShowColumns = new Pair<>(Display.CUSTOM, blackList);
            }

            return new InitialLoadDetails(tableId, saveTag, initialPosition, initialShowColumns);
        }
        catch (Exception e)
        {
            throw new UserException("Could not parse position: \"" + displayContext.getText() + "\"");
        }
    }

    @OnThread(Tag.Any)
    protected synchronized final void savePosition(OutputBuilder out)
    {
        if (display != null)
        {
            prevPosition = display.getMostRecentPosition();
        }
        out.t(MainLexer.DISPLAY, MainLexer.VOCABULARY).begin().nl();
        out.t(DisplayLexer.POSITION, DisplayLexer.VOCABULARY).n(prevPosition.columnIndex).n(prevPosition.rowIndex).nl();
        out.t(DisplayLexer.SHOWCOLUMNS, DisplayLexer.VOCABULARY);
        switch (showColumns.getFirst())
        {
            case ALL: out.t(DisplayLexer.ALL, DisplayLexer.VOCABULARY); break;
            case ALTERED: out.t(DisplayLexer.ALTERED, DisplayLexer.VOCABULARY); break;
            case COLLAPSED: out.t(DisplayLexer.COLLAPSED, DisplayLexer.VOCABULARY); break;
            case CUSTOM:
                out.t(DisplayLexer.EXCEPT, DisplayLexer.VOCABULARY);
                // TODO output the list;
                break;
        }
        out.nl();
        out.end().t(MainLexer.DISPLAY, MainLexer.VOCABULARY).nl();
    }

    @OnThread(Tag.Any)
    public synchronized void setShowColumns(Display newState, ImmutableList<ColumnId> blackList)
    {
        showColumns = new Pair<>(newState, blackList);
    }

    @OnThread(Tag.Any)
    public synchronized Pair<Display, Predicate<ColumnId>> getShowColumns()
    {
        return showColumns.mapSecond(blackList -> s -> !blackList.contains(s));
    }

    protected class WholeTableException extends UserException
    {
        @OnThread(Tag.Any)
        public WholeTableException(String message)
        {
            super(message);
        }
    }

    @Override
    @EnsuresNonNullIf(expression = "#1", result = true)
    public synchronized boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Table table = (Table) o;

        if (!id.equals(table.id)) return false;
        return prevPosition.equals(table.prevPosition);

    }

    @Override
    public synchronized int hashCode()
    {
        int result = id.hashCode();
        result = 31 * result + prevPosition.hashCode();
        return result;
    }

    @OnThread(Tag.FXPlatform)
    @Pure
    public synchronized @Nullable TableDisplayBase getDisplay()
    {
        return display;
    }

    @OnThread(Tag.Any)
    public abstract TableOperations getOperations();

    @OnThread(Tag.Any)
    public TableManager getManager(@UnknownInitialization(Table.class) Table this)
    {
        return mgr;
    }

    public synchronized final CellPosition _test_getPrevPosition()
    {
        return prevPosition;
    }

    public synchronized InitialLoadDetails getDetailsForCopy(@UnknownInitialization(Table.class) Table this, TableId overrideId)
    {
        return new InitialLoadDetails(overrideId, saveTag, prevPosition, showColumns);
    }
    
    /**
     * Slightly ham-fisted way to break the data->gui module dependency
     * while still letting Table store a link to its display.
     */
    public static interface TableDisplayBase
    {
        @OnThread(Tag.FXPlatform)
        public void loadPosition(CellPosition position, Pair<Display, ImmutableList<ColumnId>> display);

        @OnThread(Tag.Any)
        public CellPosition getMostRecentPosition();

        @OnThread(Tag.FXPlatform)
        public CellPosition getBottomRightIncl();

        /**
         * The user has made an edit in a transformation;
         * prompt them to ask if they want a manual edit, and
         * if yes, pop it in.
         * @param index The edited row index
         * @param column The edited column name and type
         * @param value The resulting value.
         */
        @OnThread(Tag.FXPlatform)
        void promptForTransformationEdit(int index, Pair<ColumnId, DataType> column, Either<String, @Value Object> value);

        @OnThread(Tag.Any)
        Table getTable();
    }

    @OnThread(Tag.Any)
    public @Nullable ColumnId proposeNewColumnName()
    {
        try
        {
            @MonotonicNonNull ColumnId name = null;
            @ExpressionIdentifier String stem = "C";
            List<ColumnId> columnIds = getData().getColumnIds();
            for (int i = 1; i < 100000; i++)
            {
                name = new ColumnId(IdentifierUtility.identNum(stem, i));
                if (!columnIds.contains(name))
                    break;
            }
            // Give up!
            if (name == null || columnIds.contains(name))
                throw new UserException("Column name already exists in table: " + name);
            return name;
        }
        catch (UserException | InternalException e)
        {
            Log.log(e);
            return null;
        }
    }

    // Returns the runnable that re-evaluates the table, if applicable
    @OnThread(Tag.Any)
    public @Nullable SimulationRunnable getReevaluateOperation()
    {
        return null;
    }
    
    // Gets the suggested name for this table
    public abstract TableId getSuggestedName();
}
