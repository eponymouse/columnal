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
        assertTrue(u("y"));
        assertTrue(u("y_z"));
        assertTrue(u("a_b"));
        assertTrue(u("a_b_c"));
        // Underscores not allowed in leading or trailing pos:
        assertFalse(u("_a"));
        assertFalse(u("a_"));
        assertFalse(u("a_b_"));
        // Nor doubled:
        assertFalse(u("a__b"));

        // Currency is allowed anywhere:
        assertTrue(u("£"));
        assertTrue(u("$"));
        assertTrue(u("$a"));
        assertTrue(u("a$"));
        
        // Spaces are not currently allowed:
        assertFalse(u("a b"));
        
        // Operators are not allowed:
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
