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

package test.functions;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.Test;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.DataTypeUtility;
import xyz.columnal.data.unit.UnitManager;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.transformations.function.FunctionDefinition;
import xyz.columnal.transformations.function.Not;
import xyz.columnal.transformations.function.Xor;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.transformations.expression.function.ValueFunction;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class TestBooleanFunctions
{
    private UnitManager mgr;
    {
        try
        {
            mgr = new UnitManager();
        }
        catch (InternalException | UserException e)
        {
            throw new RuntimeException(e);
        }
    }

    @Test
    @OnThread(Tag.Simulation)
    public void testNot() throws UserException, InternalException
    {
        FunctionDefinition function = new Not();
        @Nullable Pair<ValueFunction, DataType> checked = TFunctionUtil.typeCheckFunction(function, ImmutableList.of(DataType.BOOLEAN));
        if (checked == null)
        {
            fail("Type check failure");
        }
        else
        {
            assertEquals(DataType.BOOLEAN, checked.getSecond());
            // Not too hard to exhaustively test this one:
            assertEquals(true, (Boolean) checked.getFirst().call(new @Value Object[] {DataTypeUtility.value(false)}));
            assertEquals(false, (Boolean) checked.getFirst().call(new @Value Object[] {DataTypeUtility.value(true)}));
        }
    }

    @Test
    @OnThread(Tag.Simulation)
    public void testXor() throws InternalException, UserException
    {
        FunctionDefinition function = new Xor();
        @Nullable Pair<ValueFunction, DataType> checked = TFunctionUtil.typeCheckFunction(function, ImmutableList.of(DataType.BOOLEAN, DataType.BOOLEAN));
        if (checked == null)
        {
            fail("Type check failure");
        }
        else
        {
            assertEquals(DataType.BOOLEAN, checked.getSecond());
            // Not too hard to exhaustively test this one:
            assertEquals(true, (Boolean) checked.getFirst().call(new @Value Object[]{DataTypeUtility.value(true), DataTypeUtility.value(false)}));
            assertEquals(true, (Boolean) checked.getFirst().call(new @Value Object[]{DataTypeUtility.value(false), DataTypeUtility.value(true)}));
            assertEquals(false, (Boolean) checked.getFirst().call(new @Value Object[]{DataTypeUtility.value(true), DataTypeUtility.value(true)}));
            assertEquals(false, (Boolean) checked.getFirst().call(new @Value Object[]{DataTypeUtility.value(false), DataTypeUtility.value(false)}));
        }
    }

}
