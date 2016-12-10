package test;

import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sosy_lab.common.rationals.Rational;
import records.data.datatype.DataType;
import records.data.datatype.DataType.NumberInfo;
import records.data.datatype.DataType.TagType;
import records.data.unit.Unit;
import records.error.InternalException;
import records.error.UserException;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

/**
 * Created by neil on 09/12/2016.
 */
@RunWith(JUnitQuickcheck.class)
public class PropTypecheck
{
    List<DataType> distinctTypes;
    {
        try
        {
            distinctTypes = Arrays.<DataType>asList(
                DataType.BOOLEAN,
                DataType.TEXT,
                DataType.DATE,
                DataType.NUMBER,
                DataType.number(new NumberInfo(new Unit(Rational.of(2)), 0)),
                DataType.number(new NumberInfo(DummyManager.INSTANCE.getUnitManager().loadUse("m"), 0)),
                DataType.number(new NumberInfo(DummyManager.INSTANCE.getUnitManager().loadUse("cm"), 0)),
                DataType.tagged(Arrays.asList(new TagType<DataType>("Single", null))),
                DataType.tagged(Arrays.asList(new TagType<DataType>("Single ", null)))
            );
        }
        catch (UserException | InternalException e)
        {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testTypeComparison() throws InternalException, UserException
    {
        for (DataType a : distinctTypes)
        {
            for (DataType b : distinctTypes)
            {
                assertEqualIfSame(a, b);
            }
        }
    }

    @SuppressWarnings("intern")
    private void assertEqualIfSame(DataType a, DataType b) throws InternalException, UserException
    {
        // Equivalent to assertEquals(a == b, a.equals(b)) but gives better errors
        if (a == b)
        {
            assertEquals(a, b);
            assertEquals(a, b.copy((i, prog) -> Collections.<@NonNull Object>emptyList()));
        }
        else
        {
            assertNotEquals(a, b);
            assertNotEquals(a, b.copy((i, prog) -> Collections.<@NonNull Object>emptyList()));
        }
    }
}
