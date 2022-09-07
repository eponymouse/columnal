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

import annotation.funcdoc.qual.FuncDocKey;
import xyz.columnal.error.InternalException;

/**
 * Base class for functions which take a single numeric input,
 * and give an output of the same type, with matching units.
 *
 * package-visible
 */
abstract class SingleNumericInOutFunction extends FunctionDefinition
{
    SingleNumericInOutFunction(@FuncDocKey String funcDocKey) throws InternalException
    {
        super(funcDocKey);
    }

    /*
    @Override
    public <E> Pair<List<Unit>, E> _test_typeFailure(Random r, _test_TypeVary<E> newExpressionOfDifferentType, UnitManager unitManager) throws UserException, InternalException
    {
        //TODO randomly pick from a few other options (e.g. zero param, 2 param, units)
        return new Pair<>(Collections.emptyList(), newExpressionOfDifferentType.getNonNumericType());
    }
    */
}
