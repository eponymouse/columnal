package records.data;

import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import org.checkerframework.checker.guieffect.qual.SafeEffect;
import org.checkerframework.checker.initialization.qual.UnderInitialization;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.qual.Pure;
import records.error.InternalException;
import records.error.UserException;
import records.gui.DisplayValue;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Workers;
import utility.Workers.Worker;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A column of data in a RecordSet.
 *
 * Column assumptions:
 *   - There is only one Column object for a particular column.  So you
 *     can use (==) to compare two columns for equality.
 *   - A Column always belongs to exactly one RecordSet.
 *   - A column has a fixed name.  getName() will always return the same
 *     value.
 *   - A Column has typed entries.  Because the type is only known at
 *     run-time, we can't use generics, but you can assume that get(int)
 *     always returns the same type of object for the entire lifetime
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
    private final RecordSet recordSet;

    // Only modified on worker thread
    @OnThread(Tag.Simulation)
    private @Nullable Worker submittedFetch;
    // Only modified on worker thread
    @OnThread(Tag.Simulation)
    private final AtomicInteger fetchUpTo = new AtomicInteger(-1);

    protected Column(RecordSet recordSet)
    {
        this.recordSet = recordSet;
    }

    private static interface MoreListener
    {
        @SafeEffect
        @OnThread(Tag.Simulation)
        public void gotMore();
    }
    @OnThread(Tag.Simulation)
    @MonotonicNonNull
    private List<MoreListener> moreListeners;

    @OnThread(Tag.FXPlatform)
    private static class DisplayCache
    {
        private final int index;
        private final SimpleObjectProperty<DisplayValue> display;

        public DisplayCache(int index, SimpleObjectProperty<DisplayValue> display)
        {
            this.index = index;
            this.display = display;
        }
    }
    private static final int DISPLAY_CACHE_SIZE = 1000;
    @MonotonicNonNull
    @OnThread(Tag.FXPlatform)
    private Queue<DisplayCache> displayCache;

    @OnThread(Tag.Simulation)
    public abstract Object get(int index) throws UserException, InternalException;

    @OnThread(Tag.FXPlatform)
    public final ObservableValue<DisplayValue> getDisplay(int index)
    {
        if (displayCache == null)
            displayCache = new ArrayDeque<>(DISPLAY_CACHE_SIZE);
        else
            for (DisplayCache c : displayCache)
                if (c.index == index)
                    return c.display;

        SimpleObjectProperty<DisplayValue> v = new SimpleObjectProperty<>(new DisplayValue(0));
        Workers.onWorkerThread("Value load for display: " + index, new ValueLoader(index, v));

        // Add to cache:
        if (displayCache.size() >= DISPLAY_CACHE_SIZE)
            displayCache.poll();
        displayCache.offer(new DisplayCache(index, v));

        return v;
    }

    // Takes place on worker thread
    // Only call if indexProgress(index) <= 1.0
    @OnThread(Tag.Simulation)
    private void startFetch(int index)
    {
        if (submittedFetch != null)
        {
            int prev = fetchUpTo.get();
            if (prev < index)
                fetchUpTo.set(index);
        }
        else
        {
            fetchUpTo.set(index);
        }

        submittedFetch = () -> {
            try
            {
                int curRequest;
                do
                {
                    curRequest = fetchUpTo.get();
                    get(curRequest);
                    // Although get usually yields, it only does so per-chunk
                    // so we yield to avoid getting one chunk at a time and never yielding:
                    Workers.maybeYield();
                }
                while (curRequest < fetchUpTo.get());
                // Post-condition: we only return when we have got up to the
                // value of fetchUpTo
            }
            catch (InternalException | UserException e)
            {

            }
            finally
            {
                submittedFetch = null;
            }
        };
        Workers.onWorkerThread("startFetch: " + getName(), submittedFetch);
    }

    protected double indexProgress(int index) throws UserException
    {
        // Default:
        return indexValid(index) ? 2.0 : 0.0;
    }

    @Pure
    @OnThread(Tag.Any)
    public abstract String getName();

    public abstract long getVersion();

    public abstract Class<?> getType();

    public final boolean indexValid(int index) throws UserException
    {
        return recordSet.indexValid(index);
    }

    protected final void gotMore()
    {
        if (moreListeners != null)
            // Operate on a copy in case it changes:
            for (MoreListener l : new ArrayList<>(moreListeners))
                l.gotMore();
    }

    @OnThread(Tag.Simulation)
    public final void addMoreListener(MoreListener l)
    {
        if (moreListeners == null)
            moreListeners = new ArrayList<>();
        moreListeners.add(l);
    }

    public final void removeMoreListener(MoreListener l)
    {
        if (moreListeners == null)
            moreListeners = new ArrayList<>();
        moreListeners.remove(l);
    }

    // If supported, get number of distinct values quickly:
    public Optional<List<@NonNull ?>> fastDistinct() throws UserException
    {
        return Optional.empty();
    }

    private class ValueLoader implements Worker, MoreListener
    {
        private final int index;
        private final SimpleObjectProperty<DisplayValue> v;

        @OnThread(Tag.FXPlatform)
        public ValueLoader(int index, SimpleObjectProperty<DisplayValue> v)
        {
            this.index = index;
            this.v = v;
        }

        public void run() {
            try
            {
                // If > 1.0 then ready
                double d = indexProgress(index);
                if (d > 1.0)
                {
                    //System.out.println("Found value for " + index);
                    String val = get(index).toString();
                    Platform.runLater(() -> {
                        v.setValue(new DisplayValue(val));
                    });
                    // TODO catch exceptions
                }
                else
                {
                    //System.out.println("Starting fetch for " + index);
                    Platform.runLater(() -> v.setValue(new DisplayValue(d)));

                    // Fetch:
                    startFetch(index);
                    // Come back when there's more available:
                    addMoreListener(this);

                }
            }
            catch (UserException | InternalException e)
            {
                Platform.runLater(() -> {
                    String msg = e.getLocalizedMessage();
                    v.setValue(new DisplayValue(msg == null ? "ERROR" : msg, true));
                });
            }
        }

        @Override
        public @OnThread(Tag.Simulation) void gotMore()
        {
            removeMoreListener(this);
            run();
        }
    }
}
