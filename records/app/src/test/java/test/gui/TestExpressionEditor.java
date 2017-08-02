package test.gui;

import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.When;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.junit.runner.RunWith;
import org.testfx.framework.junit.ApplicationTest;
import records.data.Transformation;
import records.gui.MainWindow;
import records.gui.View;
import records.transformations.Filter;
import records.transformations.TransformationInfo;
import records.transformations.expression.Expression;
import test.TestUtil;
import test.gen.GenNonsenseExpression;
import test.gen.GenRandom;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.io.File;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

@RunWith(JUnitQuickcheck.class)
@OnThread(Tag.Simulation)
public class TestExpressionEditor extends ApplicationTest implements ListUtilTrait
{
    @Override
    @OnThread(value = Tag.FXPlatform, ignoreParent = true)
    public void start(Stage stage) throws Exception
    {
        File dest = File.createTempFile("blank", "rec");
        dest.deleteOnExit();
        MainWindow.show(stage, dest, null);
    }

    @Property(trials = 10)
    public void testNonsenseEntry(@When(seed=7079855168420389553L) @From(GenNonsenseExpression.class) Expression expression, @From(GenRandom.class) Random r)
    {
        // Make new data table.  Don't need to add anything to it, as our expression is nonsense anyway:
        clickOn("#id-menu-data").clickOn(".id-menu-data-new");
        TestUtil.sleep(500);
        clickOn(".id-tableDisplay-menu-button").clickOn(".id-tableDisplay-menu-addTransformation");
        selectGivenListViewItem(lookup(".transformation-list").query(), (TransformationInfo ti) -> ti.getName().toLowerCase().matches("keep.?rows"));
        // Focus expression editor:
        push(KeyCode.TAB);
        enterExpression(expression, r);
        clickOn(".ok-button");
        // Now close dialog, and check for equality;
        View view = lookup(".view").query();
        Filter filter = (Filter)view.getManager().getAllTables().stream().filter(t -> t instanceof Transformation).findFirst().orElseThrow(() -> new RuntimeException("No transformation found"));
        assertEquals(expression, filter.getFilterExpression());
        // Just in case equals is wrong, check String comparison:
        assertEquals(expression.toString(), filter.getFilterExpression().toString());
    }

    private void enterExpression(Expression expression, Random r)
    {
        if (true)
        {
            write(expression.toString());
        }
        // TODO allow different entry
    }
}
