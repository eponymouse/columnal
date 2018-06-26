package test.utility;

import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import org.junit.Test;
import org.junit.runner.RunWith;
import utility.IdentifierUtility;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(JUnitQuickcheck.class)
public class TestIdentifier
{
    @Test
    public void testUnitIdents()
    {
        assertTrue(u("a"));
        assertFalse(u("0"));
        assertFalse(u(""));
        assertFalse(u(" "));
        
        // Numbers not valid anywhere in unit names:
        assertFalse(u("a0"));
        assertTrue(u("a_"));
        assertTrue(u("a_b"));
        // Underscores not allowed in leading pos:
        assertFalse(u("_a"));

        // Currency is allowed anywhere:
        assertTrue(u("£"));
        assertTrue(u("$"));
        assertTrue(u("$a"));
        assertTrue(u("a$"));
        
        // Spaces are allowed:
        assertTrue(u("a b"));
        
        // Operators are not:
        assertFalse(u("a+b"));
        assertFalse(u("a*"));
        
        // Brackets and quotes are not:
        assertFalse(u("a("));
        assertFalse(u("a}"));
        assertFalse(u("a\""));
        assertFalse(u("a\'"));
        assertFalse(u("a#"));
        assertFalse(u("a:b"));

        assertFalse(u("a,g"));
        assertFalse(u("a.z"));
        assertFalse(u("a|b¬g"));
    }
    
    private static boolean u(String src)
    {
        return src.equals(IdentifierUtility.asUnitIdentifier(src));
    }
}
