/*
 * Columnal: Safer, smoother data table processing.
 * Copyright (c) Neil Brown, 2016-2020, 2022.
 *
 * This file is part of Columnal.
 *
 * Columnal is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 *
 * Columnal is distributed in the hope that it will be useful, but WITHOUT 
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or 
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for 
 * more details.
 *
 * You should have received a copy of the GNU General Public License along 
 * with Columnal. If not, see <https://www.gnu.org/licenses/>.
 */

package test.gui.transformation;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.runner.RunWith;
import test.gui.TAppUtil;
import test.gui.TFXUtil;
import xyz.columnal.data.CellPosition;
import xyz.columnal.data.Column;
import xyz.columnal.data.TBasicUtil;
import xyz.columnal.id.ColumnId;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.gui.MainWindow.MainWindowActions;
import xyz.columnal.gui.grid.RectangleBounds;
import xyz.columnal.importers.ClipboardUtils;
import xyz.columnal.importers.ClipboardUtils.LoadedColumnInfo;
import xyz.columnal.transformations.Sort;
import xyz.columnal.transformations.Sort.Direction;
import test.gen.GenImmediateData;
import test.gen.GenImmediateData.CanHaveErrorValues;
import test.gen.GenRandom;
import test.gui.trait.ClickTableLocationTrait;
import test.gui.trait.ListUtilTrait;
import test.gui.trait.PopupTrait;
import test.gui.trait.ScrollToTrait;
import test.gui.util.FXApplicationTest;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.Utility;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Collectors;

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
        MainWindowActions mainWindowActions = TAppUtil.openDataAsTable(windowToUse, original.mgr).get();
        TFXUtil.sleep(5000);
        CellPosition targetPos = TFXUtil.fx(() -> mainWindowActions._test_getTableManager().getNextInsertPosition(null));
        keyboardMoveTo(mainWindowActions._test_getVirtualGrid(), targetPos);
        clickOnItemInBounds(fromNode(TFXUtil.fx(() -> mainWindowActions._test_getVirtualGrid().getNode())), mainWindowActions._test_getVirtualGrid(), new RectangleBounds(targetPos, targetPos), MouseButton.PRIMARY);
        TFXUtil.sleep(100);
        clickOn(".id-new-transform");
        TFXUtil.sleep(100);
        clickOn(".id-transform-sort");
        TFXUtil.sleep(100);
        write(original.data().getId().getRaw());
        push(KeyCode.ENTER);
        TFXUtil.sleep(200);
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
            TextField focused = TBasicUtil.checkNonNull(getFocusOwner(TextField.class));
            assertNotEquals(prevFocus, focused);
            prevFocus = focused;
            
            write(pickedColumn.getFirst().getRaw());
            // All columns start ascending.  We click n * 2 times, + 1 if descending
            int numClicks = 2 * r.nextInt(3) + (pickedColumn.getSecond() == Direction.DESCENDING ? 1 : 0);
            // Bit of a hack to find the matching button
            Parent sortPane = TBasicUtil.checkNonNull(TFXUtil.<@Nullable Parent>fx(() -> TFXUtil.findParent(focused.getParent(), p -> p.getStyleClass().contains("sort-pane"))));
            Node button = TBasicUtil.checkNonNull(TFXUtil.fx(() -> sortPane.lookup(".sort-direction-button")));
            for (int i = 0; i < numClicks; i++)
            {
                clickOn(button);
                sleep(400);
            }
        }
        
        moveAndDismissPopupsAtPos(point(".ok-button"));
        clickOn(".ok-button");

        TFXUtil.sleep(500);
        assertEquals(pickedColumns, Utility.filterClass(mainWindowActions._test_getTableManager().getAllTables().stream(), Sort.class).findFirst().get().getSortBy());

        // Now check output values by getting them from clipboard:
        showContextMenu(".table-display-table-title.transformation-table-title")
                .clickOn(".id-tableDisplay-menu-copyValues");
        TFXUtil.sleep(1000);

        
                
        Optional<ImmutableList<LoadedColumnInfo>> clip = TFXUtil.<Optional<ImmutableList<LoadedColumnInfo>>>fx(() -> ClipboardUtils.loadValuesFromClipboard(original.mgr.getTypeManager()));
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
                    Either<String, @Value Object> prevVal = TBasicUtil.checkNonNull(prev.get(pickedColumn.getFirst()));
                    Either<String, @Value Object> curVal = TBasicUtil.checkNonNull(cur.get(pickedColumn.getFirst()));
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
        return loadedColumnInfos.stream().collect(Collectors.toMap(c -> TBasicUtil.checkNonNull(c.columnName), c -> c.dataValues.get(row)));
    }
}
