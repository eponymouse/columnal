package records.data;

import com.sun.xml.internal.ws.api.pipe.FiberContextSwitchInterceptor.Work;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import org.checkerframework.checker.guieffect.qual.UIEffect;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.qual.Pure;
import records.error.InternalException;
import records.error.UserException;
import records.gui.DisplayValue;
import utility.Workers;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by neil on 20/10/2016.
 */
public abstract class Column
{
    // Only modified on worker thread
    private @Nullable Runnable submittedFetch;
    // Only modified on worker thread
    private final AtomicInteger fetchUpTo = new AtomicInteger(-1);
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
    private int DISPLAY_CACHE_SIZE = 1000;
    private final Queue<DisplayCache> displayCache = new ArrayDeque<>(DISPLAY_CACHE_SIZE);

    public abstract Object get(int index) throws UserException, InternalException;

    @UIEffect
    public final ObservableValue<DisplayValue> getDisplay(int index)
    {
        for (DisplayCache c : displayCache)
            if (c.index == index)
                return c.display;

        SimpleObjectProperty<DisplayValue> v = new SimpleObjectProperty<>(new DisplayValue(0));
        System.out.println("Queueing look for " + index);
        Workers.onWorkerThread("Value load for display: " + index, new Runnable() { public void run() {
            System.out.println("Running look for " + index);
            try
            {
                // If > 1.0 then ready
                double d = indexProgress(index);
                if (d > 1.0)
                {
                    System.out.println("Found value for " + index);
                    String val = get(index).toString();
                    Platform.runLater(() -> {
                        v.setValue(new DisplayValue(val));
                    });
                    // TODO catch exceptions
                }
                else
                {
                    System.out.println("Starting fetch for " + index);
                    Platform.runLater(() -> v.setValue(new DisplayValue(d)));
                    // Fetch:
                    startFetch(index);
                    // TODO rather than poll, make the column notify when more is available
                    // and listen to that.
                    // Check back again soon:
                    Workers.onWorkerThread("Re-check value load for display: " + index, this, 1000);
                }
            }
            catch (UserException | InternalException e)
            {
                Platform.runLater(() -> {
                    String msg = e.getLocalizedMessage();
                    v.setValue(new DisplayValue(msg == null ? "ERROR" : msg, true));
                });
            }
        }});

        // Add to cache:
        if (displayCache.size() >= DISPLAY_CACHE_SIZE)
            displayCache.poll();
        displayCache.offer(new DisplayCache(index, v));

        return v;
    }

    // Takes place on worker thread
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
                int lastKnownFetch = index;
                while (lastKnownFetch < fetchUpTo.get())
                {
                    lastKnownFetch = fetchUpTo.get();
                    get(lastKnownFetch);
                    // Although get usually yields, it only does so per-chunk
                    // so we yield to avoid getting one chunk at a time and never yielding:
                    Workers.maybeYield();
                }
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
    public abstract String getName();

    public abstract long getVersion();

    public abstract Class<?> getType();

    public abstract boolean indexValid(int index) throws UserException;

    // If supported, get number of distinct values quickly:
    public Optional<List<@NonNull ?>> fastDistinct() throws UserException
    {
        return Optional.empty();
    }
}
