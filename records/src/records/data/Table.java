package records.data;

import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import org.checkerframework.checker.initialization.qual.UnderInitialization;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.error.UserException;
import records.grammar.MainLexer;
import records.grammar.MainParser.PositionContext;
import records.gui.TableDisplay;
import records.loadsave.OutputBuilder;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformConsumer;

import javax.validation.constraints.NotNull;
import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by neil on 09/11/2016.
 */
public abstract class Table
{
    private final TableId id;
    @OnThread(Tag.FXPlatform)
    private @MonotonicNonNull TableDisplay display;
    @OnThread(value = Tag.Any, requireSynchronized = true)
    private Bounds prevPosition = new BoundingBox(0, 0, 100, 400);

    // Assigns a new arbitrary ID which is not in use
    protected Table(TableManager mgr)
    {
        this.id = mgr.getNextFreeId(this);
    }

    protected Table(TableManager mgr, @Nullable TableId id) throws UserException
    {
        if (id == null)
            this.id = mgr.getNextFreeId(this);
        else
        {
            mgr.checkIdUnused(id, this);
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
    public abstract RecordSet getData() throws UserException;

    @OnThread(Tag.FXPlatform)
    public abstract void save(@Nullable File destination, FXPlatformConsumer<String> then);

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
}
