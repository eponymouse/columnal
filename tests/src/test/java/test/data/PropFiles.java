package test.data;

import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.junit.runner.RunWith;
import test.gen.GenFile;
import xyz.columnal.utility.Utility;

import java.io.IOException;

import static org.junit.Assert.assertEquals;

/**
 * Created by neil on 26/10/2016.
 */
@RunWith(JUnitQuickcheck.class)
public class PropFiles
{
    @Property
    public void testLineCount(@From(GenFile.class) GeneratedTextFile input) throws IOException
    {
        assertEqualsMsg("Counting lines for " + input.getCharset(), input.getLineCount(), Utility.countLines(input.getFile(), input.getCharset()));
    }

    public static <T> void assertEqualsMsg(String msg, @NonNull T exp, @NonNull T act)
    {
        try
        {
            assertEquals(msg, exp, act);
        }
        catch (AssertionError err)
        {
            System.err.println("Expected: " + exp + "\n  Actual: " + act);
            throw err;
        }
    }
}
