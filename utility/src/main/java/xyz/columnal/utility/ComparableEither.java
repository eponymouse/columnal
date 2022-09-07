/*
 * Columnal: Safer, smoother data table processing.
 * Copyright (c) Neil Brown, 2016-2020, 2022.
 *
 * This file is part of Columnal.
 *
 * Columnal is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 *
 * Columnal is distributed in the hope that it will be useful, but WITHOUT 
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or 
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for 
 * more details.
 *
 * You should have received a copy of the GNU General Public License along 
 * with Columnal. If not, see <https://www.gnu.org/licenses/>.
 */

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
