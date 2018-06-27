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
        assertTrue(u("a_bc_d"));
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

    @Test
    public void testExpressionIdents()
    {
        assertTrue(e("a"));
        assertFalse(e("0"));
        assertFalse(e(""));
        assertFalse(e(" "));

        // Numbers valid in names after first pos:
        assertTrue(e("a0"));
        assertTrue(e("a 01313"));
        assertTrue(e("y"));
        assertTrue(e("y_z"));
        assertTrue(e("a_b"));
        assertTrue(e("a_b_c"));
        // Underscores not allowed in leading or trailing pos:
        assertFalse(e("_a"));
        assertFalse(e("a_"));
        assertFalse(e("a_b_"));
        // Nor doubled:
        assertFalse(e("a__b"));
        // Same rule for spaces:
        assertFalse(e(" a"));
        assertFalse(e("a "));
        assertFalse(e("a b "));
        // Nor doubled:
        assertFalse(e("a  b"));

        // Currency is not allowed in expressions:
        assertFalse(e("£"));
        assertFalse(e("$"));
        assertFalse(e("$a"));
        assertFalse(e("a$"));

        // Operators are not allowed:
        assertFalse(e("a+b"));
        assertFalse(e("a*"));

        // Brackets and quotes are not:
        assertFalse(e("a("));
        assertFalse(e("a}"));
        assertFalse(e("a\""));
        assertFalse(e("a\'"));
        assertFalse(e("a#"));
        assertFalse(e("a:b"));

        assertFalse(e("a,g"));
        assertFalse(e("a.z"));
        assertFalse(e("a|b¬g"));
    }

    private static boolean e(String src)
    {
        return src.equals(IdentifierUtility.asExpressionIdentifier(src));
    }
}
