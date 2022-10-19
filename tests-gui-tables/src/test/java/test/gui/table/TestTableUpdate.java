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

import annotation.identifier.qual.ExpressionIdentifier;
import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import javafx.scene.input.KeyCode;
import org.checkerframework.checker.initialization.qual.Initialized;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.runner.RunWith;
import test.gui.TAppUtil;
import test.gui.TFXUtil;
import xyz.columnal.data.CellPosition;
import xyz.columnal.data.ColumnUtility;
import xyz.columnal.id.ColumnId;
import xyz.columnal.data.TBasicUtil;
import xyz.columnal.data.EditableColumn;
import xyz.columnal.data.EditableRecordSet;
import xyz.columnal.data.ImmediateDataSource;
import xyz.columnal.data.RecordSet;
import xyz.columnal.data.Table.InitialLoadDetails;
import xyz.columnal.id.TableId;
import xyz.columnal.data.TableManager;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.DataTypeUtility;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.gui.table.DataCellSupplier.VersionedSTF;
import xyz.columnal.gui.MainWindow.MainWindowActions;
import xyz.columnal.gui.grid.RectangleBounds;
import xyz.columnal.gui.table.app.TableDisplay;
import xyz.columnal.transformations.Calculate;
import xyz.columnal.transformations.Filter;
import xyz.columnal.transformations.Sort;
import xyz.columnal.transformations.expression.BooleanLiteral;
import xyz.columnal.transformations.function.FromString;
import test.DummyManager;
import test.gen.GenRandom;
import test.gen.type.GenTypeAndValueGen;
import test.gen.type.GenTypeAndValueGen.TypeAndValueGen;
import test.gui.trait.ClickOnTableHeaderTrait;
import test.gui.trait.EnterStructuredValueTrait;
import test.gui.trait.FocusOwnerTrait;
import test.gui.trait.ScrollToTrait;
import test.gui.util.FXApplicationTest;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.function.simulation.SimulationFunction;
import xyz.columnal.utility.Utility;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.*;

@RunWith(JUnitQuickcheck.class)
public class TestTableUpdate extends FXApplicationTest implements ScrollToTrait, FocusOwnerTrait, EnterStructuredValueTrait, ClickOnTableHeaderTrait
{    
    /**
     * We make a two-column table, and two chained identity transformations of it, so that it all fits on screen
     * (if any update problems appear, it will be most noticeable if the relevant value is already shown).
     * Then we edit the original, press enter and check:
     *  - A: the original cell has altered graphical content
     *  - B: copying the original cell copies new value
     *  - C: the two transformations have altered graphical content
     *  - D: copying the two transformations gets the altered content.
     */
    @Property(trials = 10)
    @OnThread(Tag.Simulation)
    public void propUpdate(
            @From(GenTypeAndValueGen.class) TypeAndValueGen colA,
            @From(GenTypeAndValueGen.class) TypeAndValueGen colB,
            @From(GenRandom.class) Random r) throws Exception
    {
        TBasicUtil.printSeedOnFail(() -> {
            final @Initialized int tableLength = 1 + r.nextInt(20);
            MainWindowActions details = createTables(colA, colB, r, tableLength);
            
            int changes = 3;
            for (int i = 0; i < changes; i++)
            {
                int targetColumn = r.nextInt(2);
                int targetRow = r.nextInt(tableLength);
                TypeAndValueGen colType = targetColumn == 0 ? colA : colB;
                @Value Object newVal = colType.makeValue();

                keyboardMoveTo(details._test_getVirtualGrid(), CellPosition.ORIGIN.offsetByRowCols(targetRow + 4, targetColumn + 1));
                push(KeyCode.ENTER);
                enterStructuredValue(colType.getType(), newVal, r, true, false);
                push(KeyCode.ENTER);
                TFXUtil.sleep(2000);

                List<List<@Nullable String>> latestDataA = getDataViaGraphics(details, 0);
                checkAllMatch(colA.getType(), latestDataA);
                List<List<@Nullable String>> latestDataB = getDataViaGraphics(details, 1);
                checkAllMatch(colB.getType(), latestDataB);
            }
        });
    }

    @Property(trials = 5)
    @OnThread(Tag.Simulation)
    public void propDelete(
        @From(GenTypeAndValueGen.class) TypeAndValueGen colA,
        @From(GenTypeAndValueGen.class) TypeAndValueGen colB,
        @From(GenRandom.class) Random r) throws Exception
    {
        TBasicUtil.printSeedOnFail(() -> {
            final @Initialized int tableLength = 1 + r.nextInt(20);
            MainWindowActions details = createTables(colA, colB, r, tableLength);

            // We try deleting a random table in the chain, and check that its cells are gone
            // and any dependents also have their cells gone
            int tableToDelete = r.nextInt(3);
            ImmutableList<TableId> tableIds = ImmutableList.of(new TableId("Src"), new TableId("T1"), new TableId("T2"));
            triggerTableHeaderContextMenu(details._test_getVirtualGrid(), details._test_getTableManager(), tableIds.get(tableToDelete));
            clickOn(".id-tableDisplay-menu-delete");
            sleep(500);
            
            // Now check that the cells are gone for table and dependents:
            for (int i = tableToDelete; i < 3; i++)
            {
                int iFinal = i;
                CellPosition pos = CellPosition.ORIGIN.offsetByRowCols(1 + 3, 1 + 3 * iFinal);
                assertThrows(RuntimeException.class, () -> withItemInBounds(".document-text-field", details._test_getVirtualGrid(), new RectangleBounds(pos, pos), (n, p) -> {}));
            }
            
            // Put table back and check everything updates:

            switch (tableToDelete)
            {
                case 0:
                    EditableRecordSet origRecordSet = new EditableRecordSet(ImmutableList.<SimulationFunction<RecordSet, EditableColumn>>of(
                        ColumnUtility.makeImmediateColumn(colA.getType(), new ColumnId("A"), Utility.<Either<String, @Value Object>>replicateM_Ex(tableLength, () -> Either.right(colA.makeValue())), colA.makeValue()),
                        ColumnUtility.makeImmediateColumn(colB.getType(), new ColumnId("B"), Utility.<Either<String, @Value Object>>replicateM_Ex(tableLength, () -> Either.right(colB.makeValue())), colB.makeValue())
                    ), () -> tableLength);
                    details._test_getTableManager().record(new ImmediateDataSource(details._test_getTableManager(), new InitialLoadDetails(new TableId("Src"), null, CellPosition.ORIGIN.offsetByRowCols(1, 1), null), origRecordSet));
                    break;
                case 1:
                    addTransformation(details._test_getTableManager(), "Src", "T1", CellPosition.ORIGIN.offsetByRowCols(1, 4), r);
                    break;
                case 2:
                    addTransformation(details._test_getTableManager(), "T1", "T2", CellPosition.ORIGIN.offsetByRowCols(1, 7), r);
                    break;
            }
            sleep(1000);
            
            // Check all cells are found this time, will throw if not:
            for (int i = tableToDelete; i < 3; i++)
            {
                int iFinal = i;
                CellPosition pos = CellPosition.ORIGIN.offsetByRowCols(1 + 3, 1 + 3 * iFinal);
                withItemInBounds(".document-text-field", details._test_getVirtualGrid(), new RectangleBounds(pos, pos), (n, p) -> {});
            }

            // Check data matches:
            List<List<@Nullable String>> latestDataA = getDataViaGraphics(details, 0);
            checkAllMatch(colA.getType(), latestDataA);
            List<List<@Nullable String>> latestDataB = getDataViaGraphics(details, 1);
            checkAllMatch(colB.getType(), latestDataB);

            // Check changes are properly linked up:
            int changes = 3;
            for (int i = 0; i < changes; i++)
            {
                int targetColumn = r.nextInt(2);
                int targetRow = r.nextInt(tableLength);
                TypeAndValueGen colType = targetColumn == 0 ? colA : colB;
                @Value Object newVal = colType.makeValue();

                keyboardMoveTo(details._test_getVirtualGrid(), CellPosition.ORIGIN.offsetByRowCols(targetRow + 4, targetColumn + 1));
                push(KeyCode.ENTER);
                enterStructuredValue(colType.getType(), newVal, r, true, false);
                push(KeyCode.ENTER);
                TFXUtil.sleep(2000);

                latestDataA = getDataViaGraphics(details, 0);
                checkAllMatch(colA.getType(), latestDataA);
                latestDataB = getDataViaGraphics(details, 1);
                checkAllMatch(colB.getType(), latestDataB);
            }
        });
    }

    @OnThread(Tag.Simulation)
    private MainWindowActions createTables(@From(GenTypeAndValueGen.class) GenTypeAndValueGen.TypeAndValueGen colA, @From(GenTypeAndValueGen.class) GenTypeAndValueGen.TypeAndValueGen colB, @From(GenRandom.class) Random r, @Initialized int tableLength) throws Exception
    {
        TableManager dummy = new DummyManager();
        dummy.getTypeManager()._test_copyTaggedTypesFrom(colA.getTypeManager());
        dummy.getTypeManager()._test_copyTaggedTypesFrom(colB.getTypeManager());

        EditableRecordSet origRecordSet = new EditableRecordSet(ImmutableList.<SimulationFunction<RecordSet, EditableColumn>>of(
                ColumnUtility.makeImmediateColumn(colA.getType(), new ColumnId("A"), Utility.<Either<String, @Value Object>>replicateM_Ex(tableLength, () -> Either.right(colA.makeValue())), colA.makeValue()),
                ColumnUtility.makeImmediateColumn(colB.getType(), new ColumnId("B"), Utility.<Either<String, @Value Object>>replicateM_Ex(tableLength, () -> Either.right(colB.makeValue())), colB.makeValue())
        ), () -> tableLength);
        dummy.record(new ImmediateDataSource(dummy, new InitialLoadDetails(new TableId("Src"), null, CellPosition.ORIGIN.offsetByRowCols(1, 1), null), origRecordSet));
        // Now add two transformations:
        addTransformation(dummy, "Src", "T1", CellPosition.ORIGIN.offsetByRowCols(1, 4), r);
        addTransformation(dummy, "T1", "T2", CellPosition.ORIGIN.offsetByRowCols(1, 7), r);

        MainWindowActions details = TAppUtil.openDataAsTable(windowToUse, dummy).get();
        TFXUtil.sleep(1000);
        // First check that the data is valid to begin with:
        List<List<@Nullable String>> origDataA = getDataViaGraphics(details, 0);
        checkAllMatch(colA.getType(), origDataA);
        List<List<@Nullable String>> origDataB = getDataViaGraphics(details, 1);
        checkAllMatch(colB.getType(), origDataB);
        return details;
    }

    @OnThread(Tag.Any)
    private List<List<@Nullable String>> getDataViaGraphics(MainWindowActions details, int columnIndex) throws UserException
    {
        List<List<@Nullable String>> r = new ArrayList<>();
        for (@ExpressionIdentifier String tableName : ImmutableList.<@ExpressionIdentifier String>of("Src", "T1", "T2"))
        {
            @SuppressWarnings("nullness") // Will just throw if it turns out to be null, which is fine
            TableDisplay tableDisplay = (TableDisplay) TFXUtil.fx(() -> details._test_getTableManager().getSingleTableOrThrow(new TableId(tableName)).getDisplay());
            int rowCount = TFXUtil.fx(() -> tableDisplay._test_getRowCount());
            for (int i = 0; i < rowCount; i++)
            {
                if (r.size() <= i)
                    r.add(new ArrayList<>());
                int iFinal = i;
                r.get(i).add(TFXUtil.<@Nullable String>fx(() -> getGraphicalValue(details, tableDisplay, columnIndex, iFinal)));
            }
        }
        return r;
    }
    
    @OnThread(Tag.FXPlatform)
    private @Nullable String getGraphicalValue(MainWindowActions details, TableDisplay tableDisplay, int columnIndex, int row)
    {
        @Nullable VersionedSTF cell = details._test_getDataCell(tableDisplay.getPosition().offsetByRowCols(tableDisplay.getHeaderRowCount() + row, columnIndex));
        if (cell != null)
            return cell._test_getGraphicalText().replace(", ", ",");
        else
            return null;
    }

    @OnThread(Tag.Simulation)
    private final void checkAllMatch(DataType dataType, List<List<@Nullable String>> data)
    {
        for (int row = 0; row < data.size(); row++)
        {
            @Nullable String first = data.get(row).get(0);
            List<@Nullable String> valsForRow = data.get(row);
            for (int column = 0; column < valsForRow.size(); column++)
            {
                String value = valsForRow.get(column);
                if (first != null && value != null && !first.equals(value))
                {
                    // Try flexibly parsing then compare:
                    try
                    {
                        TBasicUtil.assertValueEqual("Flexible", FromString.convertEntireString(DataTypeUtility.value(first), dataType), FromString.convertEntireString(DataTypeUtility.value(value), dataType));
                        return;
                    }
                    catch (Throwable t)
                    {
                        assertNull(t);
                    }
                }
                assertEquals("Row " + row + " table " + column, first, value);
            }
        }
    }

    @OnThread(Tag.Simulation)
    private static void addTransformation(TableManager mgr, @ExpressionIdentifier String srcTable, @ExpressionIdentifier String destTable, CellPosition position, Random r) throws InternalException
    {
        // We choose between Sort (original order), Calculate(empty list) and Filter (true)
        switch (r.nextInt(3))
        {
            case 0:
                mgr.record(new Sort(mgr, new InitialLoadDetails(new TableId(destTable), null, position, null), new TableId(srcTable), ImmutableList.of()));
                break;
            case 1:
                mgr.record(new Calculate(mgr, new InitialLoadDetails(new TableId(destTable), null, position, null), new TableId(srcTable), ImmutableMap.of()));
                break;
            case 2:
                mgr.record(new Filter(mgr, new InitialLoadDetails(new TableId(destTable), null, position, null), new TableId(srcTable), new BooleanLiteral(true)));
                break;
        }
    }
}
