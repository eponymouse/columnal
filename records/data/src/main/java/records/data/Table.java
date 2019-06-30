package records.data;

import annotation.identifier.qual.ExpressionIdentifier;
import annotation.qual.Value;
import annotation.units.AbsColIndex;
import annotation.units.AbsRowIndex;
import annotation.units.TableDataColIndex;
import com.google.common.collect.ImmutableList;
import log.Log;
import org.checkerframework.checker.i18n.qual.LocalizableKey;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.EnsuresNonNullIf;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.qual.Pure;
import records.data.datatype.DataType;
import records.error.InternalException;
import records.error.UserException;
import records.grammar.DisplayLexer;
import records.grammar.DisplayParser;
import records.grammar.DisplayParser.TableDisplayDetailsContext;
import records.grammar.MainLexer;
import records.grammar.MainParser.DetailContext;
import records.grammar.MainParser.DisplayContext;
import records.loadsave.OutputBuilder;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.IdentifierUtility;
import utility.Pair;
import utility.Utility;
import utility.TranslationUtility;

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
        private final @Nullable CellPosition initialPosition;
        private final @Nullable Pair<Display, ImmutableList<ColumnId>> initialShowColumns;

        @OnThread(Tag.Any)
        public InitialLoadDetails(@Nullable TableId tableId, @Nullable CellPosition initialPosition, @Nullable Pair<Display, ImmutableList<ColumnId>> initialShowColumns)
        {
            this.tableId = tableId;
            this.initialPosition = initialPosition;
            this.initialShowColumns = initialShowColumns;
        }
        
        public InitialLoadDetails withTableId(TableId tableId)
        {
            return new InitialLoadDetails(tableId, this.initialPosition, this.initialShowColumns);
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
        if (initialLoadDetails.initialPosition != null)
            prevPosition = initialLoadDetails.initialPosition;
        if (initialLoadDetails.initialShowColumns != null)
            showColumns = initialLoadDetails.initialShowColumns;
    }

    @OnThread(Tag.Any)
    public final TableId getId(@UnknownInitialization(Table.class) Table this)
    {
        return id;
    }

    @NonNull
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
    }

    @OnThread(Tag.Simulation)
    public static class FullSaver implements Saver
    {
        private final List<String> units = new ArrayList<>();
        private final List<String> types = new ArrayList<>();
        private final List<String> tables = new ArrayList<>();
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

        @OnThread(Tag.Simulation)
        public String getCompleteFile()
        {
            return "VERSION 1\n\nUNITS @BEGIN\n"
                + units.stream().collect(Collectors.joining())
                + "@END UNITS\n\nTYPES @BEGIN\n"
                + types.stream().collect(Collectors.joining())
                + "@END TYPES\n"
                + tables.stream().collect(Collectors.joining("\n"))
                + (displayDetailLines == null ? "" : ("DISPLAY @BEGIN\n" + displayDetailLines.stream().collect(Collectors.joining("\n")) + "\n@END DISPLAY"))
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

    public static InitialLoadDetails loadDetails(TableId tableId, @Nullable DisplayContext detailContext) throws UserException, InternalException
    {
        if (detailContext == null)
            return new InitialLoadDetails(tableId, null, null);
        return Utility.parseAsOne(detailContext.detail().DETAIL_LINE().stream().map(d -> d.getText()).collect(Collectors.joining()), DisplayLexer::new, DisplayParser::new, p -> {
            return loadDetails(tableId, p.tableDisplayDetails());
        });
    }

    public static InitialLoadDetails loadDetails(TableId tableId, TableDisplayDetailsContext displayContext) throws UserException
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

            return new InitialLoadDetails(tableId, initialPosition, initialShowColumns);
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

    public synchronized InitialLoadDetails getDetailsForCopy(@UnknownInitialization(Table.class) Table this)
    {
        return new InitialLoadDetails(id, prevPosition, showColumns);
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

}
