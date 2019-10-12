package test.gui.expressionEditor;

import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import org.junit.Test;
import org.junit.runner.RunWith;
import records.error.InternalException;
import records.error.UserException;
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
        testFix("\"A\"+S1+\"C\"", "C", "", "\"A\" ; column\\\\S1 ; \"C\"");
    }

    @Test
    @OnThread(Tag.Simulation)
    public void testDateSubtractionFix1()
    {
        testFix("date from ymd(2019{year}, 1{month}, 1{day}) - date{2018-12-31}", "2019", "", "@call function\\\\datetime\\days between(@call function\\\\datetime\\date from ymd(2019{year}, 1{month}, 1{day}), date{2018-12-31})");
    }

    @Test
    @OnThread(Tag.Simulation)
    public void testTimeSubtractionFix1()
    {
        testFix("time from hms(12{hour}, 34{minute}, 56{s}) - time{9:00AM}", "56", "", "@call function\\\\datetime\\seconds between(@call function\\\\datetime\\time from hms(12{hour}, 34{minute}, 56{s}), time{9:00AM})");
    }
    
    @Test
    public void testUnitLiteralFix1()
    {
        testFix("ACC1+6{1}", "6", "", "column\\\\ACC1 + 6{m/s^2}");
    }

    @Test
    public void testUnitLiteralFix1B()
    {
        testFix("6{1}-ACC1", "6", "", "6{m/s^2} - column\\\\ACC1");
    }

    @Test
    public void testUnitLiteralFix2() throws UserException, InternalException
    {
        testFix("ACC1>6{1}>ACC3", "6", dotCssClassFor("6{m/s^2}"), "column\\\\ACC1 > 6{m/s^2} > column\\\\ACC3");
    }

    @Test
    public void testUnitLiteralFix3() throws UserException, InternalException
    {
        testFix("ACC1<>103{m/s}", "103", dotCssClassFor("103{m/s^2}"), "column\\\\ACC1 <> 103{m/s^2}");
    }

    @Test
    public void testUnitLiteralFix3B() throws UserException, InternalException
    {
        testFix("ACC1=103{1}", "103", dotCssClassFor("103{m/s^2}"), "column\\\\ACC1 = 103{m/s^2}");
    }

    @Test
    public void testUnitLiteralFix4() throws UserException, InternalException
    {
        testFix("@ifACC1=ACC2=32{1}@then2@else7+6@endif", "32", dotCssClassFor("32{m/s^2}"), "@if (column\\\\ACC1 = column\\\\ACC2 = 32{m/s^2}) @then 2 @else (7 + 6) @endif");
    }

    @Test
    public void testUnitLiteralFix5() throws UserException, InternalException
    {
        testFix("@matchACC1@case3{1}@then5@endmatch", "3", dotCssClassFor("3{m/s^2}"), "@match column\\\\ACC1 @case 3{m/s^2} @then 5 @endmatch");
    }

    @Test
    public void testUnitLiteralFix6() throws UserException, InternalException
    {
        testFix("@matchACC1@case3{1}@then52@case12{1}@orcase14{1}@then63@endmatch", "3{1}", dotCssClassFor("3{m/s^2}"), "@match column\\\\ACC1 @case 3{m/s^2} @then 52 @case 12{1} @orcase 14{1} @then 63 @endmatch");
    }

    @Test
    public void testUnitLiteralFix6B() throws UserException, InternalException
    {
        testFix("@matchACC1@case3{1}@then52@case12{1}@orcase14{1}@then63@endmatch", "12", dotCssClassFor("12{m/s^2}"), "@match column\\\\ACC1 @case 3{1} @then 52 @case 12{m/s^2} @orcase 14{1} @then 63 @endmatch");
    }

    @Test
    public void testUnitLiteralFix6C() throws UserException, InternalException
    {
        testFix("@matchACC1@case3{1}@then52@case12{1}@orcase14{1}@then63@endmatch", "14", dotCssClassFor("14{m/s^2}"), "@match column\\\\ACC1 @case 3{1} @then 52 @case 12{1} @orcase 14{m/s^2} @then 63 @endmatch");
    }

    @Test
    public void testUnitLiteralFix6D() throws UserException, InternalException
    {
        testFix("@matchACC1@case12{1}@orcase14{1}@then63@endmatch", "14", dotCssClassFor("14{m/s^2}"), "@match column\\\\ACC1 @case 12{1} @orcase 14{m/s^2} @then 63 @endmatch");
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

    @Test
    public void testUnitLiteralFix9() throws UserException, InternalException
    {
        testFix("type{Number{new/s}}", "new", ".quick-fix-action", "type{Number{new/s}}", () -> {
            clickOn(".edit-unit-dialog .ok-button");
        });
    }
    
    @Test
    public void testAsType1()
    {
        testSimpleFix("minimum([])", "minimum", "@call function\\\\core\\as type(type{@invalidtypeops()},@call function\\\\comparison\\minimum([]))");
    }

    @Test
    public void testAsType1b()
    {
        testSimpleFix("from text(\"\")", "from", "@call function\\\\conversion\\from text to(type{@invalidtypeops()}, \"\")");
    }

    @Test
    public void testAsType2()
    {
        testSimpleFix("[]", "[", "@call function\\\\core\\as type(type{@invalidtypeops()},[])");
    }
}
