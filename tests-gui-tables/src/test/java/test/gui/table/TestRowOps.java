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

package test.gui.table;

import annotation.qual.Value;
import annotation.units.TableDataRowIndex;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import javafx.scene.Node;
import javafx.scene.control.Label;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.runner.RunWith;
import org.testfx.service.query.NodeQuery;
import test.gui.TAppUtil;
import test.gui.TFXUtil;
import xyz.columnal.data.*;
import xyz.columnal.data.Table.InitialLoadDetails;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.DataTypeUtility;
import xyz.columnal.data.datatype.DataTypeValue;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.gui.MainWindow.MainWindowActions;
import xyz.columnal.gui.table.RowLabelSupplier;
import xyz.columnal.gui.dtf.DocumentTextField;
import xyz.columnal.gui.grid.RectangleBounds;
import xyz.columnal.gui.grid.VirtualGrid;
import xyz.columnal.id.ColumnId;
import xyz.columnal.id.DataItemPosition;
import xyz.columnal.id.TableId;
import xyz.columnal.transformations.Calculate;
import xyz.columnal.transformations.Sort;
import xyz.columnal.transformations.Sort.Direction;
import test.DummyManager;
import test.gen.ExpressionValue;
import test.gen.GenExpressionValueBackwards;
import test.gen.GenExpressionValueForwards;
import test.gen.GenImmediateData;
import test.gen.GenImmediateData.ImmediateData_Mgr;
import test.gen.GenRandom;
import test.gui.trait.CheckCSVTrait;
import test.gui.trait.ClickOnTableHeaderTrait;
import test.gui.trait.ClickTableLocationTrait;
import test.gui.util.FXApplicationTest;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.Utility;
import xyz.columnal.utility.gui.FXUtility;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

@RunWith(JUnitQuickcheck.class)
public class TestRowOps extends FXApplicationTest implements CheckCSVTrait, ClickOnTableHeaderTrait, ClickTableLocationTrait
{
    @SuppressWarnings("units")
    public static final @TableDataRowIndex int ONE_ROW = 1;
    
    @OnThread(Tag.Any)
    @SuppressWarnings("nullness")
    private VirtualGrid virtualGrid;
    @OnThread(Tag.Any)
    @SuppressWarnings("nullness")
    private TableManager tableManager;

    /**
     * Generates a file with some raw data and a transform, then loads it and deletes a row in the source table.
     */
    @Property(trials = 5)
    @OnThread(Tag.Simulation)
    public void propTestDeleteRow(
        @From(GenExpressionValueForwards.class) @From(GenExpressionValueBackwards.class) ExpressionValue expressionValue,
        @From(GenRandom.class) Random r) throws Exception
    {
        if (expressionValue.recordSet.getLength() == 0)
            return; // Can't delete if there's no rows!
        if (expressionValue.recordSet.getColumns().isEmpty())
            return; // Likewise if there's no columns

        TableManager manager = new DummyManager();
        manager.getTypeManager()._test_copyTaggedTypesFrom(expressionValue.typeManager);

        Table srcData = new ImmediateDataSource(manager, new InitialLoadDetails(expressionValue.tableId, null, CellPosition.ORIGIN.offsetByRowCols(1, 1), null), new EditableRecordSet(expressionValue.recordSet));
        manager.record(srcData);

        InitialLoadDetails ild = new InitialLoadDetails(null, null, new CellPosition(CellPosition.row(1), CellPosition.col(2 + expressionValue.recordSet.getColumns().size())), null);
        Table calculated = new Calculate(manager, ild, srcData.getId(), ImmutableMap.of(new ColumnId("Result"), expressionValue.expression));
        manager.record(calculated);

        MainWindowActions details = TAppUtil.openDataAsTable(windowToUse, manager).get();
        tableManager = details._test_getTableManager();
        virtualGrid = details._test_getVirtualGrid();

        @SuppressWarnings("units")
        @TableDataRowIndex int randomRow = r.nextInt(expressionValue.recordSet.getLength());

        triggerRowLabelContextMenu(srcData.getId(), randomRow);
        clickOn(".id-virtGrid-row-delete");
        TFXUtil.sleep(500);
        scrollToRow(calculated.getId(), randomRow == 0 ? randomRow : randomRow - TableDataRowIndex.ONE);

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
            checkVisibleRowData("", srcData.getId(), randomRow, getRowVals(expressionValue.recordSet, randomRow + 1));
            checkVisibleRowData("", calculated.getId(), randomRow, getRowVals(expressionValue.recordSet, randomRow + 1));
        }
        List<Pair<String, List<String>>> expectedSrcContent = new ArrayList<>();
        List<Pair<String, List<String>>> expectedCalcContent = new ArrayList<>();
        for (Column column : srcData.getData().getColumns())
        {
            Pair<String, List<String>> colData = new Pair<>(column.getName().getRaw(), CheckCSVTrait.collapse(expressionValue.recordSet.getLength(), column.getType(), randomRow));
            expectedSrcContent.add(colData);
            expectedCalcContent.add(colData);
        }
        ArrayList<String> calcValuesFiltered = new ArrayList<>();
        for (int i = 0; i < expressionValue.value.size(); i++)
        {
            if (i != randomRow)
            {
                calcValuesFiltered.add(DataTypeUtility.valueToString(expressionValue.value.get(i)));
            }
        }
        expectedCalcContent.add(new Pair<>("Result", calcValuesFiltered));
        exportToCSVAndCheck(virtualGrid, details._test_getTableManager(),"After deleting " + randomRow, expectedSrcContent, srcData.getId());
        exportToCSVAndCheck(virtualGrid, details._test_getTableManager(),"After deleting " + randomRow, expectedCalcContent, calculated.getId());
    }

    @Property(trials = 5)
    @OnThread(Tag.Any)
    public void propTestInsertRow(
        @From(GenImmediateData.class)ImmediateData_Mgr srcDataAndMgr,
        @From(GenRandom.class) Random r) throws UserException, InternalException
    {
        if (srcDataAndMgr.data.isEmpty() || srcDataAndMgr.data.get(0).getData().getColumns().isEmpty())
            return; // Can't insert if there's no table or no columns

        TableManager manager = srcDataAndMgr.mgr;
        Table srcData = srcDataAndMgr.data.get(0);

        Column sortBy = srcData.getData().getColumns().get(r.nextInt(srcData.getData().getColumns().size()));
        InitialLoadDetails ild = new InitialLoadDetails(null, null, new CellPosition(CellPosition.row(1), CellPosition.col(2 + srcData.getData().getColumns().size())), null);
        Table calculated = TFXUtil.sim(() -> new Sort(manager, ild, srcData.getId(), ImmutableList.of(new Pair<>(sortBy.getName(), Direction.ASCENDING))));
        MainWindowActions details = TFXUtil.sim(() -> {
            manager.record(calculated);
            try
            {
                return TAppUtil.openDataAsTable(windowToUse, manager);
            }
            catch (Exception e)
            {
                throw new RuntimeException(e);
            }
        }).get();
        tableManager = details._test_getTableManager();
        virtualGrid = details._test_getVirtualGrid();

        int srcLength = TFXUtil.sim(() -> srcData.getData().getLength());
        @SuppressWarnings("units")
        @TableDataRowIndex int targetNewRow = r.nextInt(srcLength + 1);
        boolean originalWasEmpty = false;

        // Can't do insert-after if target is first row. Can't do insert-before if it's last row
        if (targetNewRow < srcLength && (targetNewRow == 0 || r.nextBoolean()))
        {
            // Let's do insert-before to get the new row
            triggerRowLabelContextMenu(srcData.getId(), targetNewRow);
            clickOn(".id-virtGrid-row-insertBefore");
            TFXUtil.sleep(500);
        }
        else if (targetNewRow > 0)
        {
            // Insert-after:
            @TableDataRowIndex int beforeTargetRow = targetNewRow - ONE_ROW;
            triggerRowLabelContextMenu(srcData.getId(), beforeTargetRow);
            clickOn(".id-virtGrid-row-insertAfter");
            TFXUtil.sleep(500);
        }
        else // targetNewRow == 0 && srcLength == 0
        {
            TFXUtil.sleep(2000);
            // No rows at all: need to click append button
            CellPosition pos = scrollToRow(srcData.getId(), DataItemPosition.row(0));
            clickOnItemInBounds(TFXUtil.fx(() -> lookup(".expand-arrow" /*".stable-view-row-append-button"*/).match(n -> FXUtility.hasPseudoclass(n, "expand-down"))), virtualGrid, new RectangleBounds(pos, pos));
            TFXUtil.sleep(500);
            originalWasEmpty = true;
        }

        // There's now several aspects to check:
        // - The row now has the default data in srcData.
        // - Ditto for calculated
        // - The row after in both cases has the old data
        // - Exporting srcData does features the new data and the other data moved.
        // - Ditto for calculated

        // Work out positions in sorted data:
        @SuppressWarnings("units")
        @TableDataRowIndex int beforeDefault = 0;
        @Nullable Pair<Integer, @Value Object> firstAfterDefault = null;
        @Nullable @Value Object sortDefault_ = TFXUtil.<@Nullable @Value Object>sim(() -> sortBy.getDefaultValue());
        if (sortDefault_ == null)
        {
            fail("Default value null for sortBy column: " + sortBy.getName() + " " + sortBy.getType());
            return;
        }
        @NonNull @Value Object sortDefault = sortDefault_;

        int newSrcLength = TFXUtil.sim(() -> srcData.getData().getLength());
        for (int i = 0; i < newSrcLength; i++)
        {
            int iFinal = i;
            @Value Object value = TFXUtil.<@Value Object>sim(() -> sortBy.getType().getCollapsed(iFinal));
            int cmp = TFXUtil.sim(() -> Utility.compareValues(value, sortDefault));
            // It will be before us if either it is strictly lower, or it is equal
            // and it started before us.
            if (cmp < 0 || (i < targetNewRow && cmp == 0))
            {
                beforeDefault += ONE_ROW;
            }
            else
            {
                @Nullable Pair<Integer, @Value Object> curFirstAfterDefault = firstAfterDefault;
                if (TFXUtil.sim(() -> curFirstAfterDefault == null || Utility.compareValues(value, curFirstAfterDefault.getSecond()) < 0))
                {
                    firstAfterDefault = new Pair<>(i, value);
                }
            }
        }
        // Position when sorted is simply the number before it in sort order:
        @TableDataRowIndex int positionPostSort = beforeDefault;

        // TODO check for default data in inserted spot

        scrollToRow(srcData.getId(), targetNewRow == 0 ? targetNewRow : targetNewRow - ONE_ROW);
        TFXUtil.sleep(1000);
        // Row still there, but data from the row after it:
        if (targetNewRow < newSrcLength)
        {
            String prefix = "Sorted by " + sortBy.getName().getRaw() + " inserted at " + targetNewRow + " before default is " + beforeDefault + " first after default " + (firstAfterDefault == null ? "null" : Integer.toString(firstAfterDefault.getFirst())) + ";";
            TFXUtil.sim_(() -> TBasicUtil.checkedToRuntime_(() -> checkVisibleRowData(prefix, srcData.getId(), targetNewRow + ONE_ROW, getRowVals(srcData.getData(), targetNewRow))));
            if (firstAfterDefault != null)
            {
                scrollToRow(calculated.getId(), beforeDefault + ONE_ROW);
                Pair<Integer, @Value Object> firstAfterDefaultFinal = firstAfterDefault;
                TFXUtil.sim_(() -> TBasicUtil.checkedToRuntime_(() -> checkVisibleRowData(prefix, calculated.getId(), positionPostSort + ONE_ROW, getRowVals(srcData.getData(), firstAfterDefaultFinal.getFirst()))));
            }
        }



        List<Pair<String, List<String>>> expectedSrcContent = new ArrayList<>();
        List<Pair<String, List<String>>> expectedCalcContent = new ArrayList<>();
        for (Column column : srcData.getData().getColumns())
        {
            DataTypeValue columnType = column.getType();
            Pair<String, List<String>> colData = new Pair<>(column.getName().getRaw(), TFXUtil.sim(() -> CheckCSVTrait.collapse(srcData.getData().getLength(), columnType)));
            // Add new default data at right point:
            @Nullable @Value Object defaultValue_ = TFXUtil.<@Nullable @Value Object>sim(() -> column.getDefaultValue());
            if (defaultValue_ == null)
            {
                fail("Null default value for column " + column.getName().getRaw());
            }
            else
            {
                @NonNull @Value Object defaultValue = defaultValue_;
                String defaultAsString = TFXUtil.sim(() -> DataTypeUtility.valueToString(defaultValue));
                expectedSrcContent.add(colData.mapSecond(d -> {
                    ArrayList<String> xs = new ArrayList<>(d);
                    xs.add(targetNewRow, defaultAsString);
                    return xs;
                }));
                expectedCalcContent.add(colData.mapSecond(d -> {
                    ArrayList<String> xs = new ArrayList<>(d);
                    xs.add(positionPostSort, defaultAsString);
                    return xs;
                }));
            }
        }
        
        try
        {
            exportToCSVAndCheck(virtualGrid, details._test_getTableManager(),"After inserting " + targetNewRow, expectedSrcContent, srcData.getId());
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
                // TODO sort expected output
                //exportToCSVAndCheck("After inserting " + targetNewRow, expectedCalcContent, calculated.getId());

    }

    @OnThread(Tag.Simulation)
    private void checkVisibleRowData(String prefix, TableId tableId, @TableDataRowIndex int targetRow, List<Pair<DataType, @Value Object>> expected) throws InternalException, UserException
    {
        // This is deliberately low-tech.  We really do want to
        // check the visible data on screen, not what the internals
        // report as they could be wrong.
        // Find the row label.  Should be visible based on previous actions:
        Node rowLabel = findRowLabel(tableId, targetRow);
        if (rowLabel == null)
            throw new RuntimeException("No row label for zero-based row " + targetRow + " in " + findVisRowLabels(tableId) + "focused: " + TFXUtil.fx(() -> targetWindow().getScene().getFocusOwner()));
        @NonNull Node rowLabelFinal = rowLabel;
        double rowLabelTop = TFXUtil.fx(() -> rowLabelFinal.localToScene(rowLabelFinal.getBoundsInLocal()).getMinY());
        List<Node> rowCells = TFXUtil.fx(() -> queryTableDisplay(tableId).lookup(".document-text-field").match(n -> Math.abs(n.localToScene(n.getBoundsInLocal()).getMinY()) - rowLabelTop <= 3).queryAll().stream().sorted(Comparator.comparing(n -> n.localToScene(n.getBoundsInLocal()).getMinX())).collect(Collectors.toList()));
        for (int i = 0; i < rowCells.size(); i++)
        {
            int iFinal = i;
            assertEquals(prefix + " " + i + ": ", DataTypeUtility.valueToString(expected.get(i).getSecond()), TFXUtil.fx(() -> ((DocumentTextField)rowCells.get(iFinal))._test_getGraphicalText()));
        }
    }

    @OnThread(Tag.Simulation)
    private List<Pair<DataType, @Value Object>> getRowVals(RecordSet recordSet, int targetRow)
    {
        return recordSet.getColumns().stream().<Pair<DataType, @Value Object>>map(c -> {
            try
            {
                return new Pair<DataType, @Value Object>(c.getType().getType(), c.getType().getCollapsed(targetRow));
            }
            catch (InternalException | UserException e)
            {
                throw new RuntimeException(e);
            }
        }).collect(Collectors.toList());
    }

    @OnThread(Tag.Any)
    private void triggerRowLabelContextMenu(TableId id, @TableDataRowIndex int targetRow) throws UserException
    {
        Node rowLabel = findRowLabel(id, targetRow);
        if (rowLabel == null)
            throw new RuntimeException("Row label for 0-based " + targetRow + " not found.  Labels: " + findVisRowLabels(id));
        showContextMenu(rowLabel, null);
    }

    @OnThread(Tag.Any)
    private String findVisRowLabels(TableId id)
    {
        NodeQuery tableDisplay = TFXUtil.fx(() -> queryTableDisplay(id));
        return tableDisplay.lookup((Node l) -> l instanceof Label).<Label>queryAll().stream().map(l -> TFXUtil.fx(() -> l.getText())).sorted().collect(Collectors.joining(", "));
    }

    // Note: table ID header will get clicked, to expose the labels
    @OnThread(Tag.Any)
    private @Nullable Node findRowLabel(TableId id, @TableDataRowIndex int targetRow) throws UserException
    {
        // Move to first cell in that row, which will make row labels visible
        // and ensure we are at correct Y position
        keyboardMoveTo(virtualGrid, tableManager, id, targetRow == 0 ? targetRow : targetRow - TableDataRowIndex.ONE);
        Set<Node> possibles = TFXUtil.fx(() ->  
            lookup(".virt-grid-row-label-pane")
            .match(Node::isVisible)
            .match((RowLabelSupplier.LabelPane p) -> id.equals(TFXUtil.<@Nullable TableId>fx(() -> p._test_getTableId())))
            .lookup(".virt-grid-row-label")
            .match((Label l) -> TFXUtil.fx(() -> l.getText().trim().equals(Integer.toString(1 + targetRow))))
            .queryAll());
        
        if (possibles.isEmpty())
        {
            return null;
        }
        else if (possibles.size() != 1)
        {
            fail("Possibles is not size 1: " + Utility.listToString(new ArrayList<>(possibles)));
        }
            
        return possibles.iterator().next();
    }

    @OnThread(Tag.FXPlatform)
    private NodeQuery queryTableDisplay(TableId id)
    {
        // TODO This is broken in new scheme
        return lookup(".tableDisplay");
    }

    @OnThread(Tag.Any)
    private CellPosition scrollToRow(TableId id, @TableDataRowIndex int targetRow) throws UserException
    {
        return keyboardMoveTo(virtualGrid, tableManager, id, targetRow);
    }
}
