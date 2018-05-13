package test;

import annotation.qual.Value;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import org.junit.runner.RunWith;
import records.data.NumericColumnStorage;
import records.data.datatype.NumberInfo;
import records.error.InternalException;
import records.error.UserException;
import test.gen.GenNumber;
import test.gen.GenNumbers;
import test.gen.GenNumbersAsString;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Utility;

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
        NumericColumnStorage storage = new NumericColumnStorage(NumberInfo.DEFAULT);
        for (String s : input)
            storage.addRead(s);

        assertEquals(input.size(), storage.filled());

        List<String> out = new ArrayList<>();
        for (int i = 0; i < input.size(); i++)
            out.add(storage.getType().getCollapsed(i).toString());
        TestUtil.assertEqualList(input, out);
    }

    @Property
    @OnThread(Tag.Simulation)
    public void testNumbers(@From(GenNumbers.class) List<@Value Number> input) throws IOException, InternalException, UserException
    {
        NumericColumnStorage storage = new NumericColumnStorage(NumberInfo.DEFAULT);
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
    public void testNumbersAdd(@From(GenNumbers.class) List<@Value Number> input, @From(GenNumber.class) Number n) throws IOException, InternalException, UserException
    {
        // These numbers come from a fixed bit size, so may lack
        // a high number
        testNumbers(input);
        // We add a new number which may cause the whole thing to be shifted
        // to higher storage, then test with that:
        input.add(n);
        testNumbers(input);
    }

    @OnThread(Tag.Simulation)
    private void testSet(List<@Value Number> input, int index, @Value Number n) throws InternalException, UserException
    {
        NumericColumnStorage storage = new NumericColumnStorage(NumberInfo.DEFAULT);
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
    public void testNumbersSet(@From(GenNumbers.class) List<@Value Number> input, int index, @From(GenNumber.class) Number n) throws IOException, InternalException, UserException
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
