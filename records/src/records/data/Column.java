package records.data;

import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.util.StringConverter;
import org.checkerframework.checker.guieffect.qual.SafeEffect;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.qual.Pure;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DataTypeVisitorGet;
import records.data.datatype.DataType.GetValue;
import records.data.datatype.DataType.TagType;
import records.error.InternalException;
import records.error.UserException;
import records.gui.DisplayCache;
import records.gui.DisplayCacheItem;
import records.gui.DisplayValue;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformConsumer;
import utility.Utility;
import utility.Workers;
import utility.Workers.Worker;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
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
 *   - A Column has typed entries.  Because the columntype is only known at
 *     run-time, we can't use generics, but you can assume that get(int)
 *     always returns the same columntype of object for the entire lifetime
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
    protected final RecordSet recordSet;

    protected Column(RecordSet recordSet)
    {
        this.recordSet = recordSet;
    }

    @MonotonicNonNull
    @OnThread(Tag.FXPlatform)
    private DisplayCache displayCache;

    @OnThread(Tag.FXPlatform)
    public final ObservableValue<DisplayValue> getDisplay(int index)
    {
        if (displayCache == null)
            displayCache = new DisplayCache(this);
        return displayCache.getDisplay(index);
    }

    @OnThread(Tag.FXPlatform)
    public final void cancelGetDisplay(int index)
    {
        if (displayCache != null)
        {
            displayCache.cancelGetDisplay(index);
        }
    }

    @Pure
    @OnThread(Tag.Any)
    public abstract String getName();

    public abstract long getVersion();

    @OnThread(Tag.FXPlatform)
    public void withDisplayType(FXPlatformConsumer<String> withType)
    {
        Workers.onWorkerThread("Fetching type of column " + getName(), () -> {
            Utility.alertOnError_(() -> {
                String s = getType().getHeaderDisplay();
                Platform.runLater(() -> withType.consume(s));
            });
        });
    }

    public abstract DataType getType() throws InternalException, UserException;

    /*
    public final Object get(int index) throws UserException, InternalException
    {
        return getWithProgress(index, null);
    }
    public abstract Object getWithProgress(int index, @Nullable ProgressListener progressListener) throws UserException, InternalException;
*/
    public final boolean indexValid(int index) throws UserException
    {
        return recordSet.indexValid(index);
    }

    // If supported, get number of distinct values quickly:
    public Optional<List<@NonNull ?>> fastDistinct() throws UserException
    {
        return Optional.empty();
    }

    public final int getLength() throws UserException
    {
        return recordSet.getLength();
    }

    public final List<Object> getCollapsed(int index) throws UserException, InternalException
    {
        return getType().apply(new DataTypeVisitorGet<List<Object>>()
        {
            @Override
            public List<Object> number(GetValue<Number> g) throws InternalException, UserException
            {
                return Collections.singletonList(g.get(index));
            }

            @Override
            public List<Object> text(GetValue<String> g) throws InternalException, UserException
            {
                return Collections.singletonList(g.get(index));
            }

            @Override
            public List<Object> tagged(List<TagType> tagTypes, GetValue<Integer> g) throws InternalException, UserException
            {
                List<Object> l = new ArrayList<>();
                Integer tagIndex = g.get(index);
                l.add(tagIndex);
                @Nullable DataType inner = tagTypes.get(tagIndex).getInner();
                if (inner != null)
                    l.addAll(inner.apply(this));
                return l;
            }
        });
    }

    public static interface ProgressListener
    {
        @OnThread(Tag.Simulation)
        public void progressUpdate(double progress);
    }
}
