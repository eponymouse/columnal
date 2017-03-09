package records.data;

import annotation.qual.Value;
import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.EnsuresNonNullIf;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;
import records.error.InternalException;
import records.error.UserException;
import records.grammar.MainLexer;
import records.grammar.MainParser.PositionContext;
import records.gui.TableDisplay;
import records.loadsave.OutputBuilder;
import records.transformations.expression.TypeState;
import threadchecker.OnThread;
import threadchecker.Tag;

import javax.validation.constraints.NotNull;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by neil on 09/11/2016.
 */
public abstract class Table
{
    private final TableManager mgr;
    private final TableId id;
    @OnThread(value = Tag.Any, requireSynchronized = true)
    private @MonotonicNonNull TableDisplay display;
    @OnThread(value = Tag.Any, requireSynchronized = true)
    private Bounds prevPosition = new BoundingBox(0, 0, 100, 400);

    // Assigns a new arbitrary ID which is not in use
    protected Table(TableManager mgr)
    {
        this.mgr = mgr;
        this.id = mgr.getNextFreeId(this);
    }

    protected Table(TableManager mgr, @Nullable TableId id)
    {
        this.mgr = mgr;
        if (id == null)
            this.id = mgr.getNextFreeId(this);
        else
        {
            this.id = id;
            mgr.registerId(id, this);
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
    public synchronized void setDisplay(TableDisplay display)
    {
        this.display = display;
    }

    public synchronized final void loadPosition(PositionContext position) throws UserException
    {
        try
        {
            double x = Double.parseDouble(position.item(0).getText());
            double y = Double.parseDouble(position.item(1).getText());
            double mx = Double.parseDouble(position.item(2).getText());
            double my = Double.parseDouble(position.item(3).getText());
            prevPosition = new BoundingBox(x, y, mx - x, my - y);
        }
        catch (Exception e)
        {
            throw new UserException("Could not parse position: \"" + position.getText() + "\"");
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
    public synchronized @Nullable TableDisplay getDisplay()
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
     * Add the given new column to the table.  Should return a new Table
     * with the same Id as the original table.
     */
    public abstract Table addColumn(String newColumnName, DataType newColumnType, @Value Object newColumnValue) throws InternalException, UserException;

    protected TypeState getTypeState()
    {
        return mgr.getTypeState();
    }

    @OnThread(Tag.Any)
    protected TableManager getManager()
    {
        return mgr;
    }

}
