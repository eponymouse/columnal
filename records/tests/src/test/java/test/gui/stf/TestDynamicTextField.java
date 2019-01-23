package test.gui.stf;

import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.text.Text;
import org.junit.runner.RunWith;
import records.gui.kit.DynamicTextField;
import records.gui.kit.ReadOnlyDocument;
import test.TestUtil;
import test.gen.GenString;
import test.gui.util.FXApplicationTest;

import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

@RunWith(JUnitQuickcheck.class)
public class TestDynamicTextField extends FXApplicationTest
{
    private final DynamicTextField field = new DynamicTextField();
    
    @Property(trials=10)
    public void testBasic(@From(GenString.class) String s)
    {
        if (s.length() > 500)
            s = s.substring(0, s.length() % 500);

        String sFinal = s;
        TestUtil.fx_(() ->{
            windowToUse.setScene(new Scene(field));
            field.setDocument(new ReadOnlyDocument(sFinal));
        });
        assertEquals(s, getText(field));
    }

    private String getText(DynamicTextField field)
    {
        return TestUtil.fx(() -> from(field).lookup((Predicate<Node>) t -> t instanceof Text).queryAll().stream().map(n -> ((Text)n).getText()).collect(Collectors.joining()));
    }
}
