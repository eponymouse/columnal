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

package test.expressions;

import annotation.qual.Value;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.runner.RunWith;
import test.TTableUtil;
import test.functions.TFunctionUtil;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.transformations.expression.ErrorAndTypeRecorderStorer;
import xyz.columnal.transformations.expression.EvaluateState;
import xyz.columnal.typeExp.TypeExp;
import test.DummyManager;
import test.gen.ExpressionValue;
import test.gen.GenExpressionValueBackwards;
import test.gen.GenExpressionValueForwards;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.Utility;
import xyz.columnal.utility.Utility.EpsilonType;

import java.math.BigDecimal;
import java.util.List;
import java.util.OptionalInt;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

/**
 * Created by neil on 10/12/2016.
 */
@RunWith(JUnitQuickcheck.class)
public class PropRunExpression
{
    @Property(trials = 2000)
    @OnThread(Tag.Simulation)
    public void propRunExpression(
            @From(GenExpressionValueBackwards.class) @From(GenExpressionValueForwards.class) ExpressionValue src) throws InternalException, UserException
    {
        try
        {
            DummyManager dummyManager = TFunctionUtil.managerWithTestTypes().getFirst();
            ErrorAndTypeRecorderStorer errors = new ErrorAndTypeRecorderStorer();
            @Nullable TypeExp checked = src.expression.checkExpression(src, TFunctionUtil.createTypeState(dummyManager.getTypeManager()), errors);
            assertEquals("Checked iff error", checked == null, errors.getAllErrors().count() != 0);
            errors.withFirst(s -> {throw new InternalException(s.toPlain());});
            for (int row = 0; row < src.value.size(); row++)
            {
                @Value Object actualValue = src.expression.calculateValue(new EvaluateState(dummyManager.getTypeManager(), OptionalInt.of(row))).value;
                assertTrue("{{{" + src.expression.toString() + "}}} should have been " + TTableUtil.toString(src.value.get(row)) + " but was " + TTableUtil.toString(actualValue) + " columns: " + src.recordSet.getColumnIds().stream().map(Object::toString).collect(Collectors.joining(", ")) + " " + src.recordSet.debugGetVals(row),
                    Utility.compareValues(src.value.get(row), actualValue, new Pair<>(EpsilonType.RELATIVE, new BigDecimal("0.000000001"))) == 0);
            }
        }
        catch (ArithmeticException | InternalException | UserException | ClassCastException e)
        {
            System.err.println("Expression: {{{" + src.expression.toString() + "}}} " + src.recordSet.debugGetVals());
            //throw e;
            e.printStackTrace();
            // Must fail, not throw, if we want to shrink:
            fail(e.getLocalizedMessage());
        }
    }

    private String toString(List<Object> value)
    {
        return "[" + value.stream().map(Object::toString).collect(Collectors.joining(", ")) + "]";
    }
}
