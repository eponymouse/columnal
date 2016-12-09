package test;

import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import records.data.datatype.DataType;
import records.data.datatype.DataType.TagType;
import records.error.InternalException;
import records.error.UserException;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Created by neil on 09/12/2016.
 */
@RunWith(JUnitQuickcheck.class)
public class PropTypecheck
{
    List<DataType> distinctTypes = Arrays.<DataType>asList(
        DataType.BOOLEAN,
        DataType.TEXT,
        DataType.DATE,
        DataType.NUMBER,
        DataType.tagged(Arrays.asList(new TagType<DataType>("Single", null))),
        DataType.tagged(Arrays.asList(new TagType<DataType>("Single ", null)))
    );

    @Test
    @SuppressWarnings("intern")
    public void testTypeComparison() throws InternalException, UserException
    {
        for (DataType a : distinctTypes)
        {
            for (DataType b : distinctTypes)
            {
                assertEquals(a == b, a.equals(b));
                assertEquals(a == b, a.equals(b.copy((i, prog) -> Collections.<@NonNull Object>emptyList())));
            }
        }
    }
}
