package test;

import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.Before;
import org.junit.runner.RunWith;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataType.DateTimeInfo.DateTimeType;
import records.data.datatype.DataType.NumberInfo;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.function.FunctionDefinition;
import records.transformations.function.FunctionInstance;
import records.transformations.function.StringToDate;
import test.gen.GenDate;
import utility.Pair;

import java.time.LocalDate;
import java.util.Collections;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Created by neil on 14/12/2016.
 */
@RunWith(JUnitQuickcheck.class)
public class PropDateFunctions
{
    private @MonotonicNonNull UnitManager mgr;
    @Before
    public void init() throws UserException, InternalException
    {
        mgr = new UnitManager();
    }

    @Property
    public void testStringToDate(@From(GenDate.class) LocalDate src) throws Throwable
    {
        // Test with string input:
        Object o = runFunction1(src.toString(), DataType.TEXT, new StringToDate());
        assertEquals(LocalDate.class, o.getClass());
        assertEquals(src, o);
        // Test with date input:
        o = runFunction1(src, DataType.date(new DateTimeInfo(DateTimeType.YEARMONTHDAY)), new StringToDate());
        assertEquals(LocalDate.class, o.getClass());
        assertEquals(src, o);
    }

    // Tests single numeric input, numeric output function
    @SuppressWarnings("nullness")
    private Object runFunction1(Object src, DataType srcType, FunctionDefinition function) throws InternalException, UserException, Throwable
    {
        try
        {
            @Nullable Pair<FunctionInstance, DataType> instance = function.typeCheck(Collections.emptyList(), Collections.singletonList(srcType), s ->
            {
                throw new RuntimeException(new UserException(s));
            }, mgr);
            assertNotNull(instance);
            return instance.getFirst().getValue(0, Collections.singletonList(Collections.singletonList(src))).get(0);
        }
        catch (RuntimeException e)
        {
            if (e.getCause() != null)
                throw e.getCause();
            else
                throw e;
        }
    }
}
