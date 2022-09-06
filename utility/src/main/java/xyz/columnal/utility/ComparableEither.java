package xyz.columnal.utility;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

public class ComparableEither<A extends Comparable<?super A>, B extends Comparable<? super B>> extends Either<A, B> implements Comparable<ComparableEither<A, B>>
{
    @SuppressWarnings("bound")
    private ComparableEither(@Nullable A a, @Nullable B b, boolean isA)
    {
        super(a, b, isA);
    }

    public static <A extends Comparable<? super A>, B extends Comparable<? super B>> ComparableEither<A, B> left(A a)
    {
        return new ComparableEither<>(a, null, true);
    }

    public static <A extends Comparable<? super A>, B extends Comparable<? super B>> ComparableEither<A, B> right(B b)
    {
        return new ComparableEither<>(null, b, false);
    }

    public static <A extends Comparable<? super A>, B extends Comparable<? super B>> ComparableEither<A, B> fromEither(Either<A, B> original)
    {
        return original.<ComparableEither<A, B>>either(x -> ComparableEither.<A, B>left(x), x -> ComparableEither.<A, B>right(x));
    }

    @Override
    public int compareTo(@NonNull ComparableEither<A, B> o)
    {
        return either(l -> o.either(l2 -> l.compareTo(l2), r2 -> -1), r -> o.either(l2 -> 1, r2 -> r.compareTo(r2)));
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean equals(@Nullable Object o)
    {
        return o instanceof ComparableEither && compareTo((ComparableEither<A, B>)o) == 0;
    }

    @Override
    public int hashCode()
    {
        return super.hashCode();
    }
}
