package utility;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.error.InternalException;
import records.error.UserException;

import java.util.function.Function;

/**
 * Created by neil on 02/11/2016.
 */
public final class Pair<A, B>
{
    private final A first;
    private final B second;

    public Pair(A first, B second)
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

    public <C> Pair<C, B> mapFirstEx(ExFunction<A, C> map) throws InternalException, UserException
    {
        return new Pair<>(map.apply(first), second);
    }

    public <C> Pair<A, C> replaceSecond(C replacement)
    {
        return new Pair<A, C>(first, replacement);
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Pair<?, ?> pair = (Pair<?, ?>) o;

        if (first != null ? !first.equals(pair.first) : pair.first != null) return false;
        return second != null ? second.equals(pair.second) : pair.second == null;
    }

    @Override
    public int hashCode()
    {
        int result = first != null ? first.hashCode() : 0;
        result = 31 * result + (second != null ? second.hashCode() : 0);
        return result;
    }

    @Override
    public String toString()
    {
        return "(" + first + ", " + second + ")";
    }
}
