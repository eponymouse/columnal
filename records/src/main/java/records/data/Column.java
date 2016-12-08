package records.data;

import javafx.application.Platform;
import javafx.beans.value.ObservableValue;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.qual.Pure;
import records.data.datatype.DataType;
import records.data.datatype.DataType.TagType;
import records.data.datatype.DataTypeValue;
import records.error.InternalException;
import records.error.UserException;
import records.gui.DisplayCache;
import records.gui.DisplayValue;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformConsumer;
import utility.Pair;
import utility.Utility;
import utility.Workers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

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
    public abstract ColumnId getName();

    @OnThread(Tag.FXPlatform)
    public void withDisplay(FXPlatformConsumer<String> withType)
    {
        Workers.onWorkerThread("Fetching display from column " + getName(), () -> {
            Utility.alertOnError_(() -> {
                String s = getType().getHeaderDisplay();
                Platform.runLater(() -> withType.consume(s));
            });
        });
    }

    @OnThread(Tag.Any)
    public abstract DataTypeValue getType() throws InternalException, UserException;

    /*
    public final Object get(int index) throws UserException, InternalException
    {
        return getWithProgress(index, null);
    }
    public abstract Object getWithProgress(int index, @Nullable ProgressListener progressListener) throws UserException, InternalException;
*/
    public final boolean indexValid(int index) throws UserException, InternalException
    {
        return recordSet.indexValid(index);
    }

    // If supported, get number of distinct values quickly:
    public Optional<List<@NonNull ?>> fastDistinct() throws UserException
    {
        return Optional.empty();
    }

    public final int getLength() throws UserException, InternalException
    {
        return recordSet.getLength();
    }

    // For testing: return copy of column with length trimmed to shrunkLength
    public Column shrink(RecordSet rs, int shrunkLength) throws InternalException, UserException
    {
        throw new RuntimeException("Unshrinkable!");
    }

    public static interface ProgressListener
    {
        @OnThread(Tag.Simulation)
        public void progressUpdate(double progress);

        public static Pair<@Nullable ProgressListener, @Nullable ProgressListener> split(final @Nullable ProgressListener prog)
        {
            if (prog == null)
                return new Pair<@Nullable ProgressListener, @Nullable ProgressListener>(null, null);
            @NonNull ProgressListener progFinal = prog;
            return new Pair<>(a -> progFinal.progressUpdate(a * 0.5), b -> progFinal.progressUpdate(b * 0.5 + 0.5));
        }
    }
}
