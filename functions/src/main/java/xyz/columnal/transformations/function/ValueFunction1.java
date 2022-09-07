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

package xyz.columnal.transformations.function;

import annotation.qual.Value;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.transformations.expression.function.ValueFunction;

/**
 * A helper extension for ValueFunction which does the casting
 * for you.
 */
public abstract class ValueFunction1<A> extends ValueFunction
{
    private final Class<A> classA;

    public ValueFunction1(Class<A> classA)
    {
        this.classA = classA;
    }

    @Override
    @OnThread(Tag.Simulation)
    public final @Value Object _call() throws InternalException, UserException
    {
        return call1(arg(0, classA));
    }

    @OnThread(Tag.Simulation)
    public abstract @Value Object call1(@Value A a) throws InternalException, UserException;
}
