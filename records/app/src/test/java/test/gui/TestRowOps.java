package test.gui;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.When;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.geometry.VerticalDirection;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.junit.runner.RunWith;
import org.testfx.framework.junit.ApplicationTest;
import org.testfx.service.query.NodeQuery;
import records.data.ColumnId;
import records.data.EditableRecordSet;
import records.data.ImmediateDataSource;
import records.data.RecordSet;
import records.data.Table;
import records.data.TableId;
import records.data.TableManager;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeUtility;
import records.error.InternalException;
import records.error.UserException;
import records.gui.MainWindow;
import records.gui.TableDisplay;
import records.gui.stable.VirtScrollStrTextGrid;
import records.gui.stf.StructuredTextField;
import records.transformations.Transform;
import test.DummyManager;
import test.TestUtil;
import test.gen.ExpressionValue;
import test.gen.GenExpressionValueBackwards;
import test.gen.GenExpressionValueForwards;
import test.gen.GenRandom;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
import utility.gui.FXUtility;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

@RunWith(JUnitQuickcheck.class)
public class TestRowOps extends ApplicationTest
{
    @OnThread(Tag.Any)
    @SuppressWarnings("nullness")
    private Stage windowToUse;

    @Override
    @OnThread(value = Tag.FXPlatform, ignoreParent = true)
    public void start(Stage stage) throws Exception
    {
        this.windowToUse = stage;
        FXUtility._test_setTestingMode();
    }


    /**
     * Generates a file with some raw data and a transform, then loads it and deletes a row in the source table.
     */
    @Property(trials = 10)
    @OnThread(Tag.Simulation)
    public void propTestDeleteRow(
        @When(seed=-5355460200260233164L) @From(GenExpressionValueForwards.class) @From(GenExpressionValueBackwards.class) ExpressionValue expressionValue,
        @When(seed=7745846878156322529L) @From(GenRandom.class) Random r) throws UserException, InternalException, InterruptedException, ExecutionException, InvocationTargetException, IOException
    {
        if (expressionValue.recordSet.getLength() == 0)
            return; // Can't delete if there's no rows!
        if (expressionValue.recordSet.getColumns().isEmpty())
            return; // Likewise if there's no rows

        TableManager manager = new DummyManager();
        manager.getTypeManager()._test_copyTaggedTypesFrom(expressionValue.typeManager);

        Table srcData = new ImmediateDataSource(manager, new EditableRecordSet(expressionValue.recordSet));
        srcData.loadPosition(new BoundingBox(0, 0, 200, 600));
        manager.record(srcData);

        Table calculated = new Transform(manager, null, srcData.getId(), ImmutableList.of(new Pair<>(new ColumnId("Result"), expressionValue.expression)));
        calculated.loadPosition(new BoundingBox(250, 0, 200, 600));
        manager.record(calculated);

        TestUtil.openDataAsTable(windowToUse, manager);

        int randomRow = r.nextInt(expressionValue.recordSet.getLength());

        scrollToRow(calculated.getId(), randomRow);
        scrollToRow(srcData.getId(), randomRow);
        rightClickRowLabel(srcData.getId(), randomRow);
        clickOn(".id-stableView-row-delete");
        TestUtil.delay(500);
        scrollToRow(calculated.getId(), randomRow);

        // There's now several aspects to check:
        // - The row is immediately no longer visible in srcData.
        //     (Well, a row of that number will be, but with diff data).
        // - Ditto for calculated
        // - Exporting srcData does not feature the data.
        // - Ditto for calculated
        if (randomRow == expressionValue.recordSet.getLength() - 1)
        {
            // Row should be gone:
            assertNull(findRowLabel(srcData.getId(), randomRow));
            assertNull(findRowLabel(calculated.getId(), randomRow));
        }
        else
        {
            // Row still there, but data from the row after it:
            checkVisibleRowData(srcData.getId(), randomRow, getRowVals(expressionValue.recordSet, randomRow + 1));
            checkVisibleRowData(calculated.getId(), randomRow, getRowVals(expressionValue.recordSet, randomRow + 1));
        }
        // TODO test the export
    }

    @OnThread(Tag.Simulation)
    private void checkVisibleRowData(TableId tableId, int targetRow, List<Pair<DataType, @Value Object>> expected) throws InternalException, UserException
    {
        // This is deliberately low-tech.  We really do want to
        // check the visible data on screen, not what the internals
        // report as they could be wrong.
        // Find the row label.  Should be visible based on previous actions:
        Node rowLabel = findRowLabel(tableId, targetRow);
        if (rowLabel == null)
            throw new RuntimeException("No row label " + targetRow + " in " + findVisRowLabels(tableId));
        @NonNull Node rowLabelFinal = rowLabel;
        double rowLabelTop = TestUtil.fx(() -> rowLabelFinal.localToScene(rowLabelFinal.getBoundsInLocal()).getMinY());
        List<Node> rowCells = queryTableDisplay(tableId).lookup(".virt-grid-cell").match(n -> Math.abs(TestUtil.fx(() -> n.localToScene(n.getBoundsInLocal()).getMinY()) - rowLabelTop) <= 3).queryAll().stream().sorted(Comparator.comparing(n -> TestUtil.fx(() -> n.localToScene(n.getBoundsInLocal()).getMinX()))).collect(Collectors.toList());
        for (int i = 0; i < expected.size(); i++)
        {
            int iFinal = i;
            assertEquals("" + i, DataTypeUtility.valueToString(expected.get(i).getFirst(), expected.get(i).getSecond(), null), TestUtil.fx(() -> ((StructuredTextField)rowCells.get(iFinal)).getText()));
        }
    }

    @OnThread(Tag.Simulation)
    private List<Pair<DataType, @Value Object>> getRowVals(RecordSet recordSet, int targetRow)
    {
        return recordSet.getColumns().stream().map(c -> {
            try
            {
                return new Pair<DataType, @Value Object>(c.getType(), c.getType().getCollapsed(targetRow));
            }
            catch (InternalException | UserException e)
            {
                throw new RuntimeException(e);
            }
        }).collect(Collectors.toList());
    }

    @OnThread(Tag.Any)
    private void rightClickRowLabel(TableId id, int targetRow)
    {
        Node rowLabel = findRowLabel(id, targetRow);
        if (rowLabel == null)
            throw new RuntimeException("Row label "+ targetRow + " not found.  Labels: " + findVisRowLabels(id));
        rightClickOn(rowLabel);
    }

    @OnThread(Tag.Any)
    private String findVisRowLabels(TableId id)
    {
        NodeQuery tableDisplay = queryTableDisplay(id);
        return tableDisplay.lookup((Node l) -> l instanceof Label).<Label>queryAll().stream().map(l -> TestUtil.fx(() -> l.getText())).sorted().collect(Collectors.joining(", "));
    }

    @OnThread(Tag.Any)
    private @Nullable Node findRowLabel(TableId id, int targetRow)
    {
        NodeQuery tableDisplay = queryTableDisplay(id);
        return tableDisplay.lookup((Node l) -> l instanceof Label && TestUtil.fx(() -> ((Label)l).getText()).equals(Integer.toString(targetRow))).query();
    }

    @OnThread(Tag.Any)
    private NodeQuery queryTableDisplay(TableId id)
    {
        return lookup(".tableDisplay").match(t -> t instanceof TableDisplay && ((TableDisplay) t).getTable().getId().equals(id));
    }

    @OnThread(Tag.Any)
    private void scrollToRow(TableId id, int targetRow)
    {
        // Bring table to front:
        clickOn("#id-menu-view").clickOn(".id-menu-view-find");
        write(id.getRaw());
        push(KeyCode.ENTER);

        Node tableDisplay = lookup(".tableDisplay").match(t -> t instanceof TableDisplay && ((TableDisplay) t).getTable().getId().equals(id)).query();
        if (tableDisplay == null)
            throw new RuntimeException("Table " + id + " not found");
        @NonNull Node tableDisplayFinal = tableDisplay;
        VirtScrollStrTextGrid grid = TestUtil.fx(() -> ((TableDisplay)tableDisplayFinal)._test_getGrid());
        moveTo(TestUtil.fx(() -> grid.getNode()));
        // Avoid infinite loop in case of test failure:
        for (int scrolls = 0; targetRow < TestUtil.fx(() -> grid._test_getFirstLogicalVisibleRowIncl()) && scrolls < 100; scrolls++)
        {
            scroll(VerticalDirection.UP);
        }
        for (int scrolls = 0; targetRow >= TestUtil.fx(() -> grid._test_getLastLogicalVisibleRowExcl()) && scrolls < 100; scrolls++)
        {
            scroll(VerticalDirection.UP);
        }
        // Wait for animated scroll to finish:
        TestUtil.delay(500);
    }
}