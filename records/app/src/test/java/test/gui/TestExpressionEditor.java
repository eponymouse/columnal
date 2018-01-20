package test.gui;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.When;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import javafx.application.Platform;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;
import jdk.nashorn.internal.codegen.CompilerConstants.Call;
import log.Log;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.Ignore;
import org.junit.runner.RunWith;
import org.testfx.framework.junit.ApplicationTest;
import records.data.ColumnId;
import records.data.Transformation;
import records.error.InternalException;
import records.error.UserException;
import records.grammar.ExpressionLexer;
import records.gui.MainWindow;
import records.gui.View;
import records.importers.ClipboardUtils;
import records.transformations.Filter;
import records.transformations.Transform;
import records.transformations.TransformationInfo;
import records.transformations.expression.*;
import records.transformations.expression.MatchExpression.MatchClause;
import records.transformations.expression.MatchExpression.Pattern;
import test.TestUtil;
import test.gen.ExpressionValue;
import test.gen.GenExpressionValueBackwards;
import test.gen.GenExpressionValueForwards;
import test.gen.GenNonsenseExpression;
import test.gen.GenRandom;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
import utility.Utility;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(JUnitQuickcheck.class)
@OnThread(Tag.Simulation)
public class TestExpressionEditor extends ApplicationTest implements ListUtilTrait, ScrollToTrait, EnterExpressionTrait
{
    @SuppressWarnings("nullness")
    private Stage windowToUse;

    @Override
    @OnThread(value = Tag.FXPlatform, ignoreParent = true)
    public void start(Stage stage) throws Exception
    {
        this.windowToUse = stage;
    }

    // TODO this test seems to cause later tests to fail
    @Property(trials = 10)
    public void testEntry(@When(seed=2L) @From(GenExpressionValueForwards.class) @From(GenExpressionValueBackwards.class) ExpressionValue expressionValue, @When(seed=1L) @From(GenRandom.class) Random r) throws InterruptedException, ExecutionException, InternalException, IOException, UserException, InvocationTargetException
    {
        TestUtil.openDataAsTable(windowToUse, expressionValue.typeManager, expressionValue.recordSet);
        try
        {
            scrollTo(".id-tableDisplay-menu-button");
            clickOn(".id-tableDisplay-menu-button").clickOn(".id-tableDisplay-menu-addTransformation");
            TestUtil.sleep(200);
            selectGivenListViewItem(lookup(".transformation-list").query(), (TransformationInfo ti) -> ti.getDisplayName().toLowerCase().startsWith("calculate"));
            push(KeyCode.TAB);
            write("DestCol");
            // Focus expression editor:
            push(KeyCode.TAB);
            push(KeyCode.TAB);
            Log.normal("Entering expression:\n" + expressionValue.expression.toString() + "\n");
            enterExpression(expressionValue.expression, false, r);
            // Finish any final column completion:
            push(KeyCode.TAB);
            // Hide any code completion (also: check it doesn't dismiss dialog)
            push(KeyCode.ESCAPE);
            push(KeyCode.ESCAPE);
            //TEMP:
            moveTo(".ok-button");
            TestUtil.sleep(2000);
            clickOn(".ok-button");
            // Now close dialog, and check for equality;
            View view = lookup(".view").query();
            if (view == null)
            {
                assertNotNull(view);
                return;
            }
            TestUtil.sleep(500);
            assertNull(lookup(".ok-button").query());
            Transform transform = (Transform) view.getManager().getAllTables().stream().filter(t -> t instanceof Transformation).findFirst().orElseThrow(() -> new RuntimeException("No transformation found"));

            // Check expressions match:
            Expression expression = transform.getCalculatedColumns().get(0).getSecond();
            assertEquals(expressionValue.expression, expression);
            // Just in case equals is wrong, check String comparison:
            assertEquals(expressionValue.expression.toString(), expression.toString());

            // Now check values match:
            scrollTo(".tableDisplay-transformation .id-tableDisplay-menu-button");
            clickOn(".tableDisplay-transformation .id-tableDisplay-menu-button").clickOn(".id-tableDisplay-menu-copyValues");
            TestUtil.sleep(1000);
            Optional<List<Pair<ColumnId, List<@Value Object>>>> clip = TestUtil.<Optional<List<Pair<ColumnId, List<@Value Object>>>>>fx(() -> ClipboardUtils.loadValuesFromClipboard(expressionValue.typeManager));
            assertTrue(clip.isPresent());
            // Need to fish out first column from clip, then compare item:
            //TestUtil.checkType(expressionValue.type, clip.get().get(0));
            List<@Value Object> actual = clip.get().stream().filter((Pair<ColumnId, List<@Value Object>> p) -> p.getFirst().equals(new ColumnId("DestCol"))).findFirst().orElseThrow(RuntimeException::new).getSecond();
            TestUtil.assertValueListEqual("Transformed", expressionValue.value, actual);
        }
        finally
        {
            Stage s = windowToUse;
            Platform.runLater(() -> s.hide());
        }
    }

    // TODO test that nonsense is preserved after load (which will change it all to invalid) -> save -> load (which should load invalid version)
}
