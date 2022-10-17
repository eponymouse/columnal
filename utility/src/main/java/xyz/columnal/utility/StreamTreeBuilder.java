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

import xyz.columnal.utility.adt.Either;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Builds a stream from a sequence of single items and nested streams.
 * @param <T>
 */
public class StreamTreeBuilder<T>
{
    private final ArrayList<Either<T, Stream<T>>> items = new ArrayList<>();
    
    public void add(T singleItem)
    {
        items.add(Either.left(singleItem));
    }
    
    public void addAll(List<T> multipleItems)
    {
        addAll(multipleItems.stream());
    }
    
    public void addAll(Stream<T> itemStream)
    {
        items.add(Either.right(itemStream));
    }
    
    public Stream<T> stream()
    {
        return items.stream().flatMap(t -> t.either(x -> Stream.of(x), s -> s));
    }
}
