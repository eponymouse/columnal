package test.gui.transformation;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.When;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import org.checkerframework.checker.nullness.qual.KeyFor;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.runner.RunWith;
import records.data.CellPosition;
import records.data.Column;
import records.data.ColumnId;
import records.data.Table;
import records.data.datatype.DataTypeUtility;
import records.error.InternalException;
import records.error.UserException;
import records.gui.MainWindow.MainWindowActions;
import records.gui.grid.RectangleBounds;
import records.gui.table.TableDisplay;
import records.importers.ClipboardUtils;
import records.importers.ClipboardUtils.LoadedColumnInfo;
import records.transformations.Filter;
import records.transformations.Sort;
import records.transformations.Sort.Direction;
import records.transformations.expression.ComparisonExpression;
import records.transformations.expression.ComparisonExpression.ComparisonOperator;
import records.transformations.expression.NumericLiteral;
import test.TestUtil;
import test.gen.GenImmediateData;
import test.gen.GenImmediateData.CanHaveErrorValues;
import test.gen.GenImmediateData.MustIncludeNumber;
import test.gen.GenImmediateData.NumTables;
import test.gen.GenRandom;
import test.gui.trait.ClickTableLocationTrait;
import test.gui.trait.ListUtilTrait;
import test.gui.trait.PopupTrait;
import test.gui.trait.ScrollToTrait;
import test.gui.util.FXApplicationTest;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.Pair;
import utility.Utility;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.Assert.*;

@RunWith(JUnitQuickcheck.class)
public class TestSort extends FXApplicationTest implements ListUtilTrait, ScrollToTrait, PopupTrait, ClickTableLocationTrait
{
    @Property(trials = 10)
    @OnThread(Tag.Simulation)
    public void propSort(
            @CanHaveErrorValues @From(GenImmediateData.class) GenImmediateData.ImmediateData_Mgr original,
            @From(GenRandom.class) Random r) throws Exception
    {
        // Save the table, then open GUI and load it, then add a sort transformation
        MainWindowActions mainWindowActions = TestUtil.openDataAsTable(windowToUse, original.mgr).get();
        TestUtil.sleep(5000);
        CellPosition targetPos = TestUtil.fx(() -> mainWindowActions._test_getTableManager().getNextInsertPosition(null));
        keyboardMoveTo(mainWindowActions._test_getVirtualGrid(), targetPos);
        clickOnItemInBounds(from(TestUtil.fx(() -> mainWindowActions._test_getVirtualGrid().getNode())), mainWindowActions._test_getVirtualGrid(), new RectangleBounds(targetPos, targetPos), MouseButton.PRIMARY);
        TestUtil.delay(100);
        clickOn(".id-new-transform");
        TestUtil.delay(100);
        clickOn(".id-transform-sort");
        TestUtil.delay(100);
        write(original.data().getId().getRaw());
        push(KeyCode.ENTER);
        TestUtil.sleep(200);
        // Then enter sort information.
        // Find random column (s):
        List<Column> allColumns = original.data().getData().getColumns();
        // Pick a random subset:
        ArrayList<Column> pickFrom = new ArrayList<>(allColumns);
        ArrayList<Pair<ColumnId, Direction>> pickedColumns = new ArrayList<>(); 
        do
        {
            pickedColumns.add(new Pair<>(pickFrom.remove(r.nextInt(pickFrom.size())).getName(), Direction.values()[r.nextInt(Direction.values().length)]));
        }
        while (r.nextInt(2) == 1 && !pickFrom.isEmpty());

        TextField prevFocus = null;
        boolean needToAdd = false;
        for (Pair<ColumnId, Direction> pickedColumn : pickedColumns)
        {
            if (needToAdd)
            {
                scrollTo(".id-fancylist-add");
                moveAndDismissPopupsAtPos(point(".id-fancylist-add"));
                clickOn(".id-fancylist-add");
                sleep(300);
            }
            needToAdd = true;
            TextField focused = TestUtil.checkNonNull(getFocusOwner(TextField.class));
            assertNotEquals(prevFocus, focused);
            prevFocus = focused;
            
            write(pickedColumn.getFirst().getRaw());
            // All columns start ascending.  We click n * 2 times, + 1 if descending
            int numClicks = 2 * r.nextInt(3) + (pickedColumn.getSecond() == Direction.DESCENDING ? 1 : 0);
            // Bit of a hack to find the matching button
            Parent sortPane = TestUtil.checkNonNull(TestUtil.<@Nullable Parent>fx(() -> TestUtil.findParent(focused.getParent(), p -> p.getStyleClass().contains("sort-pane"))));
            Node button = TestUtil.checkNonNull(TestUtil.fx(() -> sortPane.lookup(".sort-direction-button")));
            for (int i = 0; i < numClicks; i++)
            {
                clickOn(button);
                sleep(400);
            }
        }
        
        moveAndDismissPopupsAtPos(point(".ok-button"));
        clickOn(".ok-button");

        TestUtil.sleep(500);
        assertEquals(pickedColumns, Utility.filterClass(mainWindowActions._test_getTableManager().getAllTables().stream(), Sort.class).findFirst().get().getSortBy());

        // Now check output values by getting them from clipboard:
        showContextMenu(".table-display-table-title.transformation-table-title")
                .clickOn(".id-tableDisplay-menu-copyValues");
        TestUtil.sleep(1000);

        
                
        Optional<ImmutableList<LoadedColumnInfo>> clip = TestUtil.<Optional<ImmutableList<LoadedColumnInfo>>>fx(() -> ClipboardUtils.loadValuesFromClipboard(original.mgr.getTypeManager()));
        assertTrue(clip.isPresent());
        List<Map<ColumnId, Either<String, @Value Object>>> sorted = new ArrayList<>();
        int length = original.data().getData().getLength();
        for (int i = 0; i < length; i++)
        {
            sorted.add(getRow(clip.get(), i));
        }
        checkSorted(original.data().getData().getLength(), pickedColumns, sorted);
    }

    @OnThread(Tag.Simulation)
    public static void checkSorted(int length, List<Pair<ColumnId, Direction>> sortBy, List<Map<ColumnId, Either<String, @Value Object>>> actual) throws UserException, InternalException
    {
        // No point checking order if 1 row or less:
        if (length > 1)
        {
            // Need to check that items are in the right order:
            Map<ColumnId, Either<String, @Value Object>> prev = actual.get(0);
            for (int i = 1; i < length; i++)
            {
                Map<ColumnId, Either<String, @Value Object>> cur = actual.get(i);

                for (Pair<ColumnId, Direction> pickedColumn : sortBy)
                {
                    Either<String, @Value Object> prevVal = TestUtil.checkNonNull(prev.get(pickedColumn.getFirst()));
                    Either<String, @Value Object> curVal = TestUtil.checkNonNull(cur.get(pickedColumn.getFirst()));
                    // If both errors, sort by error text:
                    if (prevVal.isLeft() && curVal.isLeft())
                    {
                        MatcherAssert.assertThat(prevVal.getLeft("prev"), Matchers.lessThanOrEqualTo(curVal.getLeft("cur")));
                        if (!prevVal.equals(curVal))
                            break; // Don't check other columns
                    }
                    else if (prevVal.isRight() && curVal.isRight())
                    {
                        int actualComparison = Utility.compareValues(prevVal.getRight("prev"), curVal.getRight("cur"));
                        // For equals (i.e. zero) results, there's nothing to check (valid for ASC and DESC)
                        // and we continue on to compare the next column:
                        if (actualComparison != 0)
                        {
                            // For non-zero, check it is in right direction and break:
                            MatcherAssert.assertThat(actualComparison, pickedColumn.getSecond() == Direction.ASCENDING ? Matchers.lessThan(0) : Matchers.greaterThan(0));
                            break;
                        }
                    }
                    else
                    {
                        // A mix of left and right
                        // Left should always be first, regardless
                        assertTrue(prevVal.isLeft());
                        assertFalse(curVal.isLeft());
                        break;
                    }

                }

                prev = cur;
            }
        }
    }

    private Map<ColumnId, Either<String, @Value Object>> getRow(ImmutableList<LoadedColumnInfo> loadedColumnInfos, int row)
    {
        return loadedColumnInfos.stream().collect(Collectors.toMap(c -> TestUtil.checkNonNull(c.columnName), c -> c.dataValues.get(row)));
    }
}
