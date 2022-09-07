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
import org.checkerframework.dataflow.qual.Pure;
import org.checkerframework.framework.qual.Covariant;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;

import java.util.Comparator;
import java.util.function.Function;

/**
 * Created by neil on 02/11/2016.
 */
@Covariant({0, 1})
public final class Pair<A, B>
{
    private final A first;
    private final B second;

    public Pair(A first, B second)
    {
        this.first = first;
        this.second = second;
    }

    @Pure
    public A getFirst()
    {
        return first;
    }

    @Pure
    public B getSecond()
    {
        return second;
    }

    public <C, D> Pair<C, D> map(Function<A, C> mapFirst, Function<B, D> mapSecond)
    {
        return new Pair<>(mapFirst.apply(first), mapSecond.apply(second));
    }

    public <C> Pair<C, B> mapFirst(Function<A, C> map)
    {
        return new Pair<>(map.apply(first), second);
    }

    public <C> Pair<C, B> mapFirstEx(ExFunction<A, C> map) throws InternalException, UserException
    {
        return new Pair<>(map.apply(first), second);
    }

    public <C> Pair<A, C> mapSecond(Function<B, C> map)
    {
        return new Pair<>(first, map.apply(second));
    }

    public <C> Pair<C, B> replaceFirst(C replacement)
    {
        return new Pair<C, B>(replacement, second);
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

    public static <A extends Comparable<A>, B extends Comparable<B>> Comparator<Pair<A, B>> comparator()
    {
        return Comparator.<Pair<A, B>, A>comparing(p -> p.getFirst()).thenComparing(p -> p.getSecond());
    }

    public static <A extends Comparable<A>, B> Comparator<Pair<A, B>> comparatorFirst()
    {
        return Comparator.<Pair<A, B>, A>comparing(p -> p.getFirst());
    }

}
