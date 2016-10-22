package utility;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.HashMap;
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
public class CompleteStringPool
{
    private @Nullable String @NonNull [] pool = new String[]{"", null, null, null};
    private int used = 1;
    private final int limit;

    public CompleteStringPool(int limit)
    {
        this.limit = limit;
    }

    /**
     * Gets a String of equal content, using the stringPool if appropriate;
     * @param s
     * @return
     */
    public String pool(String s)
    {
        int index = Arrays.binarySearch(pool, 0, used, s);
        if (index >= 0 && pool[index] != null && pool[index].equals(s))
            return pool[index];
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
                String[] newPool = new String[Math.min(limit, pool.length * 2)];
                System.arraycopy(pool, 0, newPool, 0, index);
                System.arraycopy(pool, index, newPool, index + 1, used - index);
                pool = newPool;
            }
            pool[index] = s;
            used += 1;
        }
        return s;

    }
}
