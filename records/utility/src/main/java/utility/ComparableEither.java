package utility;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.jetbrains.annotations.NotNull;

public class ComparableEither<A extends Comparable<A>, B extends Comparable<B>> extends Either<A, B> implements Comparable<ComparableEither<A, B>>
{
    private ComparableEither(@Nullable A a, @Nullable B b, boolean isA)
    {
        super(a, b, isA);
    }

    public static <A extends Comparable<A>, B extends Comparable<B>> ComparableEither<A, B> left(A a)
    {
        return new ComparableEither<>(a, null, true);
    }

    public static <A extends Comparable<A>, B extends Comparable<B>> ComparableEither<A, B> right(B b)
    {
        return new ComparableEither<>(null, b, false);
    }


    @Override
    public int compareTo(@NotNull ComparableEither<A, B> o)
    {
        return either(l -> o.either(l2 -> l.compareTo(l2), r2 -> -1), r -> o.either(l2 -> 1, r2 -> r.compareTo(r2)));
    }
}
