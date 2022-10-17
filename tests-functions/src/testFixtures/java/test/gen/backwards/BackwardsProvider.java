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

package test.gen.backwards;

import annotation.qual.Value;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.unit.Unit;
import xyz.columnal.data.unit.UnitManager;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.transformations.expression.CallExpression;
import xyz.columnal.transformations.expression.Expression;
import xyz.columnal.transformations.function.FunctionList;

import java.util.List;

public abstract class BackwardsProvider
{
    protected final SourceOfRandomness r;
    protected final RequestBackwardsExpression parent;

    public BackwardsProvider(SourceOfRandomness r, RequestBackwardsExpression parent)
    {
        this.r = r;
        this.parent = parent;
    }
    
    public abstract List<ExpressionMaker> terminals(DataType targetType, @Value Object targetValue) throws InternalException, UserException;

    public abstract List<ExpressionMaker> deep(int maxLevels, DataType targetType, @Value Object targetValue) throws InternalException, UserException;

    protected final CallExpression call(String name, Expression... args)
    {
        return new CallExpression(FunctionList.getFunctionLookup(parent.getTypeManager().getUnitManager()), name, args);
    }

    protected Unit getUnit(String name) throws InternalException, UserException
    {
        UnitManager m = parent.getTypeManager().getUnitManager();
        return m.loadUse(name);
    }
}
