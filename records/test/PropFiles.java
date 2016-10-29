import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.runner.RunWith;
import utility.Utility;

import java.io.IOException;

import static org.junit.Assert.assertEquals;

/**
 * Created by neil on 26/10/2016.
 */
@RunWith(JUnitQuickcheck.class)
public class PropFiles
{
    @Property
    public void testLineCount(@From(GenFile.class) TestTextFile input) throws IOException
    {
        assertEqualsMsg(input.getLineCount(), Utility.countLines(input.getFile()));
    }

    public static <T> void assertEqualsMsg(@NonNull T exp, @NonNull T act)
    {
        try
        {
            assertEquals(exp, act);
        }
        catch (AssertionError err)
        {
            System.err.println("Expected: " + exp + " Actual: " + act);
            throw err;
        }
    }
}
