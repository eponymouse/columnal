package utility;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Created by neil on 02/11/2016.
 */
public final class Pair<A, B>
{
    @NonNull private final A first;
    @NonNull private final B second;

    public Pair(@NonNull A first, @NonNull B second)
    {
        this.first = first;
        this.second = second;
    }

    public A getFirst()
    {
        return first;
    }

    public B getSecond()
    {
        return second;
    }

    @Override
    @SuppressWarnings("intern")
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Pair<?, ?> pair = (Pair<?, ?>) o;

        if (!first.equals(pair.first)) return false;
        return second.equals(pair.second);

    }

    @Override
    public int hashCode()
    {
        int result = first.hashCode();
        result = 31 * result + second.hashCode();
        return result;
    }
}
