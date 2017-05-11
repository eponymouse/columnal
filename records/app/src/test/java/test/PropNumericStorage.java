package test;

import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import org.junit.runner.RunWith;
import records.data.NumericColumnStorage;
import records.data.datatype.DataType.NumberInfo;
import records.error.InternalException;
import records.error.UserException;
import test.gen.GenNumbers;
import test.gen.GenNumbersAsString;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Utility;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

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
    public void testNumbers(@From(GenNumbers.class) List<Number> input) throws IOException, InternalException, UserException
    {
        NumericColumnStorage storage = new NumericColumnStorage(NumberInfo.DEFAULT);
        for (Number n : input)
            storage.add(n);

        assertEquals(input.size(), storage.filled());

        List<Number> out = new ArrayList<>();
        for (int i = 0; i < input.size(); i++)
            out.add(Utility.toBigDecimal((Number) storage.getType().getCollapsed(i)));
        TestUtil.assertEqualList(Utility.mapList(input, Utility::toBigDecimal), out);
    }

}
