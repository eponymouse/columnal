package utility;

import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.error.InternalException;

import java.lang.ref.WeakReference;
import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A string pool which keeps in every String it is given up to a limit
 * after which no new Strings are added.
 *
 * This may seem dangerous, but it only slightly increases memory usage
 * (if the String is kept in memory forever, it just adds one extra reference
 * size to that String) and most columns are unlikely to have huge numbers
 * of completely distinct Strings.
 *
 * TODO add a pool which counts references.
 */
public class DumbObjectPool<T>
{
    private final Class<T> cls;
    private final @Nullable Comparator<T> comparator;
    private @Nullable T @NonNull [] pool;
    private int used = 0;
    private final int limit;

    @SuppressWarnings("unchecked")
    public DumbObjectPool(Class<T> cls, int limit, @Nullable Comparator<T> comparator)
    {
        this.cls = cls;
        this.pool = (T[])Array.newInstance(cls, 4);
        this.limit = limit;
        this.comparator = comparator;
    }

    /**
     * Gets a String of equal content, using the stringPool if appropriate;
     * @param s
     * @return
     */
    @SuppressWarnings({"unchecked", "nullness"})
    public T pool(T s) throws InternalException
    {
        int index = Arrays.binarySearch(pool, 0, used, s, comparator);
        if (index >= 0)
        {
            if (pool[index] != null && pool[index].equals(s))
                return pool[index];
            else
                throw new InternalException("Object found in pool yet does not satisfy equals: " + pool[index] + " vs " + s + " equals gives " + pool[index].equals(s) + " but comparator " + comparator.compare(pool[index], s) + " class is " + pool[index].getClass() + " and " + s.getClass());
        }
        // Change to insertion point:
        index = ~index;
        if (used < limit)
        {
            // Insert at index:
            if (used < pool.length)
            {
                // Shuffle everything else up one:
                System.arraycopy(pool, index, pool, index + 1, used - index);
            }
            else
            {
                T[] newPool = (T[])Array.newInstance(cls, Math.min(limit, pool.length * 2));
                System.arraycopy(pool, 0, newPool, 0, index);
                System.arraycopy(pool, index, newPool, index + 1, used - index);
                pool = newPool;
            }
            pool[index] = s;
            used += 1;
        }
        return s;
    }

    public boolean isFull()
    {
        return used >= limit;
    }

    @SuppressWarnings("nullness")
    public List<@NonNull T> get()
    {
        return ImmutableList.copyOf(Arrays.<T>copyOfRange(pool, 0, used));
    }
}
