package records.data;

import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
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
    @OnThread(Tag.FXPlatform)
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
    public abstract RecordSet getData() throws UserException;

    public static interface Saver
    {
        @OnThread(Tag.FXPlatform)
        public void saveTable(String tableSrc);

        @OnThread(Tag.FXPlatform)
        public void saveUnit(String unitSrc);

        @OnThread(Tag.FXPlatform)
        public void saveType(String typeSrc);
    }

    @OnThread(Tag.FXPlatform)
    public static class BlankSaver implements Saver
    {
        @Override
        public @OnThread(Tag.FXPlatform) void saveTable(String tableSrc)
        {
        }

        @Override
        @OnThread(Tag.FXPlatform)
        public void saveUnit(String unitSrc)
        {
        }

        @Override
        @OnThread(Tag.FXPlatform)
        public void saveType(String typeSrc)
        {
        }
    }

    @OnThread(Tag.FXPlatform)
    public static class FullSaver implements Saver
    {
        private final List<String> units = new ArrayList<>();
        private final List<String> types = new ArrayList<>();
        private final List<String> tables = new ArrayList<>();

        @Override
        public @OnThread(Tag.FXPlatform) void saveTable(String tableSrc)
        {
            tables.add(tableSrc);
        }

        @Override
        @OnThread(Tag.FXPlatform)
        public void saveUnit(String unitSrc)
        {
            units.add(unitSrc.endsWith("\n") ? unitSrc : unitSrc + "\n");
        }

        @Override
        @OnThread(Tag.FXPlatform)
        public void saveType(String typeSrc)
        {
            types.add(typeSrc.endsWith("\n") ? typeSrc : typeSrc + "\n");
        }

        @OnThread(Tag.FXPlatform)
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

    @OnThread(Tag.FXPlatform)
    public abstract void save(@Nullable File destination, Saver then);

    @OnThread(Tag.FXPlatform)
    public void setDisplay(TableDisplay display)
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

    @OnThread(Tag.FXPlatform)
    protected synchronized final void savePosition(OutputBuilder out)
    {
        if (display != null)
        {
            prevPosition = display.getBoundsInParent();
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
    public @Nullable TableDisplay getDisplay()
    {
        return display;
    }


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
