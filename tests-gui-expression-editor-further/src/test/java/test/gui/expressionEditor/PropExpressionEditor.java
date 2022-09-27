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

package test.gui.expressionEditor;

import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.When;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import org.junit.runner.RunWith;
import test.gen.ExpressionValue;
import test.gen.GenExpressionValueBackwards;
import test.gen.GenExpressionValueForwards;
import test.gen.GenRandom;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.util.Random;

@RunWith(JUnitQuickcheck.class)
@OnThread(Tag.Simulation)
public class PropExpressionEditor extends BaseTestExpressionEditorEntry
{
    @Property(trials = 10)
    public void testEntry(@When(satisfies = "#_.expressionLength < 500") @From(GenExpressionValueForwards.class) @From(GenExpressionValueBackwards.class) ExpressionValue expressionValue, @From(GenRandom.class) Random r) throws Exception
    {
        testEntry_Impl(expressionValue, r);
    }
}
