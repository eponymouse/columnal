package records.data;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import org.checkerframework.checker.i18n.qual.LocalizableKey;
import org.checkerframework.checker.i18n.qual.Localized;
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
import records.grammar.MainParser.ItemContext;
import records.loadsave.OutputBuilder;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
import utility.Utility;
import utility.gui.TranslationUtility;

import javax.validation.constraints.NotNull;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private Bounds prevPosition = new BoundingBox(10, 10, 400, 600);
    @OnThread(value = Tag.Any, requireSynchronized = true)
    private ImmutableMap<ColumnId, Double> prevColumnWidths = ImmutableMap.of();

    // The list is the blacklist, only applicable if first is CUSTOM:
    @OnThread(value = Tag.Any, requireSynchronized = true)
    private Pair<Display, ImmutableList<ColumnId>> showColumns = new Pair<>(Display.ALL, ImmutableList.of());

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
                display.loadPosition(prevPosition, showColumns);
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
        display.loadPosition(prevPosition, showColumns);
        display.loadColumnWidths(prevColumnWidths);
    }

    public synchronized final void loadPosition(DisplayContext display) throws UserException
    {
        try
        {
            double x = Double.parseDouble(display.displayTablePosition().item(0).getText());
            double y = Double.parseDouble(display.displayTablePosition().item(1).getText());
            double mx = Double.parseDouble(display.displayTablePosition().item(2).getText());
            double my = Double.parseDouble(display.displayTablePosition().item(3).getText());
            prevPosition = new BoundingBox(x, y, mx - x, my - y);

            // Now handle the show-columns:
            if (display.displayShowColumns().ALL() != null)
                showColumns = new Pair<>(Display.ALL, ImmutableList.of());
            else if (display.displayShowColumns().ALTERED() != null)
                showColumns = new Pair<>(Display.ALTERED, ImmutableList.of());
            else if (display.displayShowColumns().COLLAPSED() != null)
                showColumns = new Pair<>(Display.COLLAPSED, ImmutableList.of());
            else
            {
                ImmutableList<ColumnId> blackList = display.displayShowColumns().item().stream().map(itemContext -> new ColumnId(itemContext.getText())).collect(ImmutableList.toImmutableList());
                showColumns = new Pair<>(Display.CUSTOM, blackList);
            }

            Map<ColumnId, Double> columnWidths = new HashMap<>();
            List<ItemContext> columnWidthContexts = display.displayColumnWidths().item();
            for (int i = 0; i < columnWidthContexts.size(); i += 2)
            {
                ColumnId columnId = new ColumnId(columnWidthContexts.get(i).getText());
                Utility.parseDoubleOpt(columnWidthContexts.get(i + 1).getText()).ifPresent(w -> columnWidths.put(columnId, w));
            }
            prevColumnWidths = ImmutableMap.copyOf(columnWidths);

            if (this.display != null)
            {
                this.display.loadPosition(prevPosition, showColumns);
                this.display.loadColumnWidths(columnWidths);
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

        if (display != null)
        {
            prevColumnWidths = display.getColumnWidths();
        }
        out.t(MainLexer.COLUMNWIDTHS);
        prevColumnWidths.entrySet().stream().sorted(Comparator.comparing(e -> e.getKey())).forEach(e -> {
            out.id(e.getKey()).d(e.getValue());
        });

        out.nl();
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

    /**
     * Add the given new column to the table.
     */
    public abstract void addColumn(String newColumnName, DataType newColumnType, @Value Object defaultValue) throws InternalException, UserException;

    @OnThread(Tag.Any)
    public abstract TableOperations getOperations();

    /** Message to show when the table has no columns or no rows */
    @OnThread(Tag.Any)
    public abstract MessageWhenEmpty getDisplayMessageWhenEmpty();

    @OnThread(Tag.Any)
    public static class MessageWhenEmpty
    {
        private final @Localized String noColumns;
        private final @Localized String noRows;

        public MessageWhenEmpty(@LocalizableKey String noColumnsKey, @LocalizableKey String noRowsKey)
        {
            this.noColumns = TranslationUtility.getString(noColumnsKey);
            this.noRows = TranslationUtility.getString(noRowsKey);
        }

        /** Message to show when the table has no columns */
        public @Localized String getDisplayMessageNoColumns()
        {
            return noColumns;
        }

        /** Message to show when the table has no rows */
        public @Localized String getDisplayMessageNoRows()
        {
            return noRows;
        }
    }

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
        public void loadPosition(Bounds bounds, Pair<Display, ImmutableList<ColumnId>> display);

        @OnThread(Tag.Any)
        public Bounds getPosition();

        @OnThread(Tag.FXPlatform)
        public Bounds getHeaderBoundsInParent();

        @OnThread(Tag.Any)
        public void loadColumnWidths(Map<ColumnId, Double> columnWidths);

        @OnThread(Tag.Any)
        public ImmutableMap<ColumnId, Double> getColumnWidths();
    }
}
