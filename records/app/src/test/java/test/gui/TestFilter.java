package test.gui;

import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.When;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import javafx.application.Platform;
import javafx.scene.control.ListView;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;
import org.apache.commons.io.FileUtils;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.runner.RunWith;
import org.testfx.framework.junit.ApplicationTest;
import org.testfx.util.WaitForAsyncUtils;
import records.data.Column;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.DataTypeValue;
import records.error.InternalException;
import records.error.UserException;
import records.gui.MainWindow;
import records.transformations.TransformationInfo;
import test.TestUtil;
import test.gen.GenImmediateData;
import test.gen.GenImmediateData.MustIncludeNumber;
import test.gen.GenImmediateData.NumTables;
import test.gen.GenRandom;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

import static org.junit.Assert.fail;

@RunWith(JUnitQuickcheck.class)
public class TestFilter extends ApplicationTest implements ListUtilTrait
{
    @OnThread(Tag.Any)
    private Stage windowToUse;

    @SuppressWarnings("nullness")
    public TestFilter() { }

    @Property(trials = 10)
    @OnThread(Tag.Simulation)
    public void propNumberFilter(
            @When(seed=6793627309703186619L) @NumTables(maxTables = 1) @MustIncludeNumber @From(GenImmediateData.class) GenImmediateData.ImmediateData_Mgr original,
            @When(seed=9064559552451687290L) @From(GenRandom.class) Random r) throws IOException, InterruptedException, ExecutionException, InvocationTargetException, InternalException, UserException
    {
        // Save the table, then open GUI and load it, then add a filter transformation (rename to keeprows)
        File temp = File.createTempFile("srcdata", "tables");
        temp.deleteOnExit();
        String saved = TestUtil.save(original.mgr);
        Platform.runLater(() -> TestUtil.checkedToRuntime_(() -> MainWindow.show(windowToUse, temp, saved)));
        TestUtil.sleep(2000);
        clickOn(".id-tableDisplay-menu-button").clickOn(".id-tableDisplay-menu-addTransformation");
        selectGivenListViewItem(lookup(".transformation-list").query(), (TransformationInfo ti) -> ti.getName().toLowerCase().matches("keep.?rows"));
        // Then enter filter condition.
        // Find numeric column:
        Column srcColumn = original.data().getData().getColumns().stream().filter(c -> TestUtil.checkedToRuntime(() -> c.getType().isNumber())).findFirst().orElseGet((Supplier<Column>)(() -> {throw new AssertionError("No numeric column");}));
        // Pick arbitrary value as cut-off:
        Number cutOff = (Number)srcColumn.getType().getCollapsed(r.nextInt(srcColumn.getLength()));

        // Focus expression editor:
        push(KeyCode.TAB);
        // Select column in auto complete:
        write(srcColumn.getName().getRaw());
        push(KeyCode.TAB);
        write(">");
        write(DataTypeUtility._test_valueToString(cutOff));
        clickOn(".ok-button");

        // TODO now check output values.  Either by examining GUI, or export to CSV file?
        fail("TODO");
    }

    @Override
    @OnThread(value = Tag.FXPlatform, ignoreParent = true)
    public void start(Stage stage) throws Exception
    {
        windowToUse = stage;
    }
}
