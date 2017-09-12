package records.data;

import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.EnsuresNonNullIf;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.qual.Pure;
import records.data.datatype.DataType;
import records.error.InternalException;
import records.error.UserException;
import records.grammar.MainLexer;
import records.grammar.MainParser.DisplayContext;
import records.loadsave.OutputBuilder;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
import utility.Utility;

import javax.validation.constraints.NotNull;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
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
        /** Only those affected by transformations since last time table was shown */
        ALTERED,
        /** A custom set (done by black list, not white list) */
        CUSTOM;
    }

    private final TableManager mgr;
    private final TableId id;
    @OnThread(value = Tag.Any, requireSynchronized = true)
    private @MonotonicNonNull TableDisplayBase display;
    @OnThread(value = Tag.Any, requireSynchronized = true)
    private Bounds prevPosition = new BoundingBox(0, 0, 100, 400);

    // The list is the blacklist, only applicable if first is CUSTOM:
    @OnThread(value = Tag.Any, requireSynchronized = true)
    private Pair<Display, List<ColumnId>> showColumns = new Pair<>(Display.ALL, Collections.emptyList());

    /**
     * If id is null, an arbitrary free id is taken
     * @param mgr
     * @param id
     */
    protected Table(TableManager mgr, @Nullable TableId id)
    {
        this.mgr = mgr;
        if (id == null)
            this.id = mgr.registerNextFreeId();
        else
        {
            this.id = id;
        }
    }

    @OnThread(Tag.Any)
    public final TableId getId(@UnknownInitialization(Table.class) Table this)
    {
        return id;
    }

    @NotNull
    @OnThread(Tag.Any)
    public abstract RecordSet getData() throws UserException, InternalException;

    public final synchronized Table loadPosition(@Nullable Bounds position)
    {
        if (position != null)
        {
            prevPosition = position;
            if (display != null)
                display.loadPosition(prevPosition, getShowColumns());
        }
        return this;
    }

    @OnThread(Tag.Any)
    public synchronized Bounds getPosition()
    {
        if (display != null)
            return display.getPosition();
        else
            return prevPosition;
    }

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
                + "\n";
        }
    }

    @OnThread(Tag.Simulation)
    public abstract void save(@Nullable File destination, Saver then);

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
        display.loadPosition(prevPosition, getShowColumns());
    }

    public synchronized final void loadPosition(DisplayContext display) throws UserException
    {
        try
        {
            double x = Double.parseDouble(display.item(0).getText());
            double y = Double.parseDouble(display.item(1).getText());
            double mx = Double.parseDouble(display.item(2).getText());
            double my = Double.parseDouble(display.item(3).getText());
            prevPosition = new BoundingBox(x, y, mx - x, my - y);

            // Now handle the show-columns:
            if (display.ALL() != null)
                showColumns = new Pair<>(Display.ALL, Collections.emptyList());
            else if (display.ALTERED() != null)
                showColumns = new Pair<>(Display.ALTERED, Collections.emptyList());
            else if (display.COLLAPSED() != null)
                showColumns = new Pair<>(Display.COLLAPSED, Collections.emptyList());
            else
            {
                List<ColumnId> blackList = Utility.mapList(display.item().subList(4, display.item().size()), itemContext -> new ColumnId(itemContext.getText()));
                showColumns = new Pair<>(Display.CUSTOM, blackList);
            }

            if (this.display != null)
            {
                this.display.loadPosition(prevPosition, getShowColumns());
            }
        }
        catch (Exception e)
        {
            throw new UserException("Could not parse position: \"" + display.getText() + "\"");
        }
    }

    @OnThread(Tag.Any)
    protected synchronized final void savePosition(OutputBuilder out)
    {
        if (display != null)
        {
            prevPosition = display.getPosition();
        }
        Bounds b = prevPosition;
        out.t(MainLexer.POSITION).d(b.getMinX()).d(b.getMinY()).d(b.getMaxX()).d(b.getMaxY()).nl();
        out.t(MainLexer.SHOWCOLUMNS);
        switch (showColumns.getFirst())
        {
            case ALL: out.t(MainLexer.ALL); break;
            case ALTERED: out.t(MainLexer.ALTERED); break;
            case COLLAPSED: out.t(MainLexer.COLLAPSED); break;
            case CUSTOM:
                out.t(MainLexer.EXCEPT);
                // TODO output the list;
                break;
        }
        out.nl();
    }

    @OnThread(Tag.Any)
    public synchronized void setShowColumns(Display newState, List<ColumnId> blackList)
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

    /**
     * If this returns true, show the add column button.  Before
     * a column is added, addColumn() will be called, where you
     * can prompt the user if needed.
     */
    @OnThread(Tag.FXPlatform)
    public abstract boolean showAddColumnButton();

    /**
     * Add the given new column to the table.
     */
    public abstract void addColumn(String newColumnName, DataType newColumnType, String defaultValueUnparsed) throws InternalException, UserException;

    @OnThread(Tag.Any)
    public abstract TableOperations getOperations();

    @OnThread(Tag.Any)
    protected TableManager getManager()
    {
        return mgr;
    }

    /**
     * Slightly ham-fisted way to break the data->gui module dependency
     * while still letting Table store a link to its display.
     */
    public static interface TableDisplayBase
    {
        @OnThread(Tag.Any)
        public void loadPosition(Bounds bounds, Pair<Display, Predicate<ColumnId>> display);

        @OnThread(Tag.Any)
        public Bounds getPosition();

        @OnThread(Tag.FXPlatform)
        public Bounds getHeaderBoundsInParent();
    }
}
