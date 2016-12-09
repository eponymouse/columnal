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
import threadchecker.OnThread;
import threadchecker.Tag;

import java.io.IOException;
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
    public void testPureNumbers(@From(GenNumbers.class) List<String> input) throws IOException, InternalException, UserException
    {
        NumericColumnStorage storage = new NumericColumnStorage(NumberInfo.DEFAULT);
        for (String s : input)
            storage.addRead(s);

        assertEquals(input.size(), storage.filled());

        List<String> out = new ArrayList<>();
        for (int i = 0; i < input.size(); i++)
            out.add(storage.get(i).toString());
        TestUtil.assertEqualList(input, out);
    }

}
