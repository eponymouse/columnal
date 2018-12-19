package test.gui;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.When;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;
import org.junit.Ignore;
import org.junit.runner.RunWith;
import org.testfx.framework.junit.ApplicationTest;
import records.data.Column;
import records.data.ColumnId;
import records.data.datatype.DataTypeUtility;
import records.error.InternalException;
import records.error.UserException;
import records.importers.ClipboardUtils;
import records.transformations.TransformationInfo;
import test.TestUtil;
import test.gen.GenImmediateData;
import test.gen.GenImmediateData.MustIncludeNumber;
import test.gen.GenImmediateData.NumTables;
import test.gen.GenRandom;
import test.gui.util.FXApplicationTest;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
import utility.Utility;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(JUnitQuickcheck.class)
public class TestFilter extends FXApplicationTest implements ListUtilTrait, ScrollToTrait
{
    @Property(trials = 10)
    @OnThread(Tag.Simulation)
    public void propNumberFilter(
            @When(seed=6793627309703186619L) @NumTables(maxTables = 1) @MustIncludeNumber @From(GenImmediateData.class) GenImmediateData.ImmediateData_Mgr original,
            @When(seed=9064559552451687290L) @From(GenRandom.class) Random r) throws Exception
    {
        // Save the table, then open GUI and load it, then add a filter transformation (rename to keeprows)
        TestUtil.openDataAsTable(windowToUse, original.mgr).get();
        clickOn(".id-tableDisplay-menu-button").clickOn(".id-tableDisplay-menu-addTransformation");
        // TODO fix this
        //selectGivenListViewItem(lookup(".transformation-list").query(), (TransformationInfo ti) -> ti.getDisplayName().toLowerCase().matches("filter.*"));
        // Then enter filter condition.
        // Find numeric column:
        Column srcColumn = original.data().getData().getColumns().stream().filter(c -> TestUtil.checkedToRuntime(() -> c.getType().isNumber())).findFirst().orElseGet((Supplier<Column>)(() -> {throw new AssertionError("No numeric column");}));
        // Pick arbitrary value as cut-off:
        @Value Number cutOff = (Number)srcColumn.getType().getCollapsed(r.nextInt(srcColumn.getLength()));

        // Focus expression editor:
        push(KeyCode.TAB);
        // Select column in auto complete:
        write(srcColumn.getName().getRaw());
        push(KeyCode.TAB);
        write(">");
        write(DataTypeUtility._test_valueToString(cutOff));
        clickOn(".ok-button");

        TestUtil.sleep(1000);
        scrollTo(".tableDisplay-transformation .id-tableDisplay-menu-button");
        // Now check output values by getting them from clipboard:
        clickOn(".tableDisplay-transformation .id-tableDisplay-menu-button").clickOn(".id-tableDisplay-menu-copyValues");
        TestUtil.sleep(1000);
        
        assertEquals(new ComparisonExpression(ImmutableList.of(new ColumnReference(srcColumn.getName(), ColumnReferenceType.CORRESPONDING_ROW), new NumericLiteral(cutOff, null)), ImmutableList.of(ComparisonOperator.GREATER_THAN)), Utility.filterClass(mainWindowActions._test_getTableManager().streamAllTables(), Filter.class).findFirst().get().getFilterExpression());
                
        Optional<ImmutableList<Pair<ColumnId, List<@Value Object>>>> clip = TestUtil.<Optional<ImmutableList<Pair<ColumnId, List<@Value Object>>>>>fx(() -> ClipboardUtils.loadValuesFromClipboard(original.mgr.getTypeManager()));
        assertTrue(clip.isPresent());
        // Need to fish out first column from clip, then compare item:
        List<@Value Object> expected = IntStream.range(0, srcColumn.getLength()).mapToObj(i -> TestUtil.checkedToRuntime(() -> srcColumn.getType().getCollapsed(i))).filter(x -> Utility.compareNumbers(x, cutOff) > 0).collect(Collectors.toList());
        TestUtil.assertValueListEqual("Filtered", expected, clip.get().get(0).getSecond());
    }
}
