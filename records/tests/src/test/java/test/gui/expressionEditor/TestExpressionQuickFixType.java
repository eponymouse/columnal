package test.gui.expressionEditor;

import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.expression.ExpressionUtil;
import threadchecker.OnThread;
import threadchecker.Tag;

@RunWith(JUnitQuickcheck.class)
@OnThread(Tag.Simulation)
public class TestExpressionQuickFixType extends BaseTestQuickFix
{
    // Test that adding two strings suggests a quick fix to switch to string concatenation
    @Test
    @OnThread(Tag.Simulation)
    public void testStringAdditionFix1()
    {
        testFix("\"A\"+\"B\"", "A", "", "\"A\";\"B\"");
    }
    
    @Test
    @OnThread(Tag.Simulation)
    public void testStringAdditionFix2()
    {
        testFix("\"A\"+S1+\"C\"", "C", "", "\"A\" ; @column S1 ; \"C\"");
    }
    
    @Test
    public void testUnitLiteralFix1()
    {
        testFix("ACC1+6{1}", "6", "", "@column ACC1 + 6{m/s^2}");
    }

    @Test
    public void testUnitLiteralFix1B()
    {
        testFix("6{1}-ACC1", "6", "", "6{m/s^2} - @column ACC1");
    }

    @Test
    public void testUnitLiteralFix2()
    {
        testFix("ACC1>6{1}>ACC3", "6", "", "@column ACC1 > 6{m/s^2} > @column ACC3");
    }

    @Test
    public void testUnitLiteralFix3()
    {
        testFix("ACC1<>103{m/s}", "103", "", "@column ACC1 <> 103{m/s^2}");
    }

    @Test
    public void testUnitLiteralFix3B()
    {
        testFix("ACC1=103{1}", "103", "", "@column ACC1 = 103{m/s^2}");
    }

    @Test
    public void testUnitLiteralFix4()
    {
        testFix("@ifACC1=ACC2=32{1}@then2@else7+6@endif", "32", "", "@if (@column ACC1 = @column ACC2 = 32{m/s^2}) @then 2 @else (7 + 6) @endif");
    }

    @Test
    public void testUnitLiteralFix5()
    {
        testFix("@matchACC1@case3{1}@then5@endmatch", "3", "", "@match @column ACC1 @case 3{m/s^2} @then 5 @endmatch");
    }

    @Test
    public void testUnitLiteralFix6()
    {
        testFix("@matchACC1@case3{1}@then52@case12{1}@orcase14{1}@then63@endmatch", "3{1}", "", "@match @column ACC1 @case 3{m/s^2} @then 52 @case 12{1} @orcase 14{1} @then 63 @endmatch");
    }

    @Test
    public void testUnitLiteralFix6B()
    {
        testFix("@matchACC1@case3{1}@then52@case12{1}@orcase14{1}@then63@endmatch", "12", "", "@match @column ACC1 @case 3{1} @then 52 @case 12{m/s^2} @orcase 14{1} @then 63 @endmatch");
    }

    @Test
    public void testUnitLiteralFix6C()
    {
        testFix("@matchACC1@case3{1}@then52@case12{1}@orcase14{1}@then63@endmatch", "14", "", "@match @column ACC1 @case 3{1} @then 52 @case 12{1} @orcase 14{m/s^2} @then 63 @endmatch");
    }

    @Test
    public void testUnitLiteralFix6D()
    {
        testFix("@matchACC1@case12{1}@orcase14{1}@then63@endmatch", "14", "", "@match @column ACC1 @case 12{1} @orcase 14{m/s^2} @then 63 @endmatch");
    }

    @Test
    public void testUnitLiteralFix7() throws UserException, InternalException
    {
        testFix("12{metre/s}", "metre", dotCssClassFor("m"), "12{m/s}");
    }

    @Test
    public void testUnitLiteralFix8() throws UserException, InternalException
    {
        testFix("type{Number{metre/s}}", "metre", dotCssClassFor("m"), "type{Number{m/s}}");
    }    
}
