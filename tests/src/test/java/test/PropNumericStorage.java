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

package test;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.When;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import org.junit.runner.RunWith;
import xyz.columnal.data.NumericColumnStorage;
import xyz.columnal.data.datatype.NumberInfo;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import test.gen.GenNumber;
import test.gen.GenNumbers;
import test.gen.GenNumbersAsString;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.Utility;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

import static org.junit.Assert.assertEquals;

/**
 * Created by neil on 05/11/2016.
 */
@RunWith(JUnitQuickcheck.class)
public class PropNumericStorage
{
    @Property
    @OnThread(Tag.Simulation)
    public void testNumbersFromString(@From(GenNumbersAsString.class) List<String> input) throws IOException, InternalException, UserException
    {
        NumericColumnStorage storage = new NumericColumnStorage(NumberInfo.DEFAULT, true);
        for (String s : input)
            storage.addRead(s);

        assertEquals(input.size(), storage.filled());

        List<String> out = new ArrayList<>();
        for (int i = 0; i < input.size(); i++)
            out.add(Utility.numberToString((Number)storage.getType().getCollapsed(i)));

        ImmutableList<String> inputLessTrailingZeroes = input.stream()
            .map(s -> s.replaceAll("\\.((?:0*[1-9])+)0+$", "\\.$1"))
            .collect(ImmutableList.toImmutableList());
        
        TestUtil.assertEqualList(inputLessTrailingZeroes, out);
    }

    @Property
    @OnThread(Tag.Simulation)
    public void testNumbers(@From(GenNumbers.class) List<@Value Number> input) throws IOException, InternalException, UserException
    {
        NumericColumnStorage storage = new NumericColumnStorage(NumberInfo.DEFAULT, true);
        for (Number n : input)
            storage.add(n);

        assertEquals(input.size(), storage.filled());

        List<Number> out = new ArrayList<>();
        for (int i = 0; i < input.size(); i++)
            out.add(Utility.toBigDecimal(Utility.cast(storage.getType().getCollapsed(i), Number.class)));
        TestUtil.assertEqualList(Utility.<@Value Number, @Value BigDecimal>mapList(input, Utility::toBigDecimal), out);
    }

    @Property(trials = 1000)
    @OnThread(Tag.Simulation)
    public void testNumbersAdd(@From(GenNumbers.class) List<@Value Number> input, @From(GenNumber.class) @Value Number n) throws IOException, InternalException, UserException
    {
        // These numbers come from a fixed bit size, so may lack
        // a high number
        testNumbers(input);
        // We add a new number which may cause the whole thing to be shifted
        // to higher storage, then test with that:
        input = new ArrayList<>(input);
        input.add(n);
        testNumbers(input);
    }

    @OnThread(Tag.Simulation)
    private void testSet(List<@Value Number> input, int index, @Value Number n) throws InternalException, UserException
    {
        NumericColumnStorage storage = new NumericColumnStorage(NumberInfo.DEFAULT, true);
        for (Number orig : input)
            storage.add(orig);

        storage.set(OptionalInt.of(index), n);

        List<@Value Number> expected = new ArrayList<>(input);
        expected.set(index, n);

        assertEquals(expected.size(), storage.filled());

        List<Number> out = new ArrayList<>();
        for (int i = 0; i < input.size(); i++)
            out.add(Utility.toBigDecimal(Utility.cast(storage.getType().getCollapsed(i), Number.class)));
        TestUtil.assertEqualList(Utility.mapList(expected, Utility::toBigDecimal), out);
    }

    @Property(trials = 1000)
    @OnThread(Tag.Simulation)
    public void testNumbersSet(@From(GenNumbers.class) List<@Value Number> input, int index, @From(GenNumber.class) @Value Number n) throws IOException, InternalException, UserException
    {
        // These numbers come from a fixed bit size, so may lack
        // a high number
        testNumbers(input);
        if (!input.isEmpty())
        {
            index = Math.abs(index) % input.size();
            testSet(input, index, n);
        }
    }
}
