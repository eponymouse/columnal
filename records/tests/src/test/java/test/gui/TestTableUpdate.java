package test.gui;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.When;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import javafx.scene.input.KeyCode;
import org.checkerframework.checker.initialization.qual.Initialized;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.runner.RunWith;
import records.data.CellPosition;
import records.data.ColumnId;
import records.data.EditableColumn;
import records.data.EditableRecordSet;
import records.data.ImmediateDataSource;
import records.data.RecordSet;
import records.data.Table.InitialLoadDetails;
import records.data.TableId;
import records.data.TableManager;
import records.error.InternalException;
import records.error.UserException;
import records.gui.DataCellSupplier.VersionedSTF;
import records.gui.MainWindow.MainWindowActions;
import records.gui.TableDisplay;
import records.transformations.Filter;
import records.transformations.Sort;
import records.transformations.Calculate;
import records.transformations.expression.BooleanLiteral;
import test.DummyManager;
import test.TestUtil;
import test.gen.GenRandom;
import test.gen.GenTypeAndValueGen;
import test.gen.GenTypeAndValueGen.TypeAndValueGen;
import test.gui.trait.EnterStructuredValueTrait;
import test.gui.trait.FocusOwnerTrait;
import test.gui.trait.ScrollToTrait;
import test.gui.util.FXApplicationTest;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.SimulationFunction;
import utility.Utility;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(JUnitQuickcheck.class)
public class TestTableUpdate extends FXApplicationTest implements ScrollToTrait, FocusOwnerTrait, EnterStructuredValueTrait
{    
    /**
     * We make a two-column table, and two chained transformations of it, so that it all fits on screen
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
        TestUtil.printSeedOnFail(() -> {
            TableManager dummy = new DummyManager();
            dummy.getTypeManager()._test_copyTaggedTypesFrom(colA.getTypeManager());
            dummy.getTypeManager()._test_copyTaggedTypesFrom(colB.getTypeManager());
            @Initialized final int tableLength = 1 + r.nextInt(20);
            EditableRecordSet origRecordSet = new EditableRecordSet(ImmutableList.<SimulationFunction<RecordSet, EditableColumn>>of(
                    colA.getType().makeImmediateColumn(new ColumnId("A"), Utility.<Either<String, @Value Object>>replicateM_Ex(tableLength, () -> Either.right(colA.makeValue())), colA.makeValue()),
                    colB.getType().makeImmediateColumn(new ColumnId("B"), Utility.<Either<String, @Value Object>>replicateM_Ex(tableLength, () -> Either.right(colB.makeValue())), colB.makeValue())
            ), () -> tableLength);
            dummy.record(new ImmediateDataSource(dummy, new InitialLoadDetails(new TableId("Src"), CellPosition.ORIGIN, null), origRecordSet));
            // Now add two transformations:
            addTransformation(dummy, "Src", "T1", CellPosition.ORIGIN.offsetByRowCols(0, 3), r);
            addTransformation(dummy, "T1", "T2", CellPosition.ORIGIN.offsetByRowCols(0, 6), r);

            MainWindowActions details = TestUtil.openDataAsTable(windowToUse, dummy).get();
            TestUtil.sleep(1000);
            // First check that the data is valid to begin with:
            List<List<@Nullable String>> origDataA = getDataViaGraphics(details, 0);
            checkAllMatch(origDataA);
            List<List<@Nullable String>> origDataB = getDataViaGraphics(details, 1);
            checkAllMatch(origDataB);
        /*
        List<List<String>> origClipboardA = getDataViaCopy("A");
        checkAllMatch(origDataA, origClipboardA);
        List<List<String>> origClipboardB = getDataViaCopy("B");
        checkAllMatch(origDataB, origClipboardB);
        */
            int changes = 3;
            for (int i = 0; i < changes; i++)
            {
                int targetColumn = r.nextInt(2);
                int targetRow = r.nextInt(tableLength);
                TypeAndValueGen colType = targetColumn == 0 ? colA : colB;
                @Value Object newVal = colType.makeValue();

                keyboardMoveTo(details._test_getVirtualGrid(), CellPosition.ORIGIN.offsetByRowCols(targetRow + 3, targetColumn));
                push(KeyCode.ENTER);
                enterStructuredValue(colType.getType(), newVal, r, false);
                push(KeyCode.ENTER);
                TestUtil.sleep(2000);

                List<List<@Nullable String>> latestDataA = getDataViaGraphics(details, 0);
                checkAllMatch(latestDataA);
                List<List<@Nullable String>> latestDataB = getDataViaGraphics(details, 1);
                checkAllMatch(latestDataB);
            }
        });
    }

    @OnThread(Tag.Any)
    private List<List<@Nullable String>> getDataViaGraphics(MainWindowActions details, int columnIndex) throws UserException
    {
        List<List<@Nullable String>> r = new ArrayList<>();
        for (String tableName : ImmutableList.of("Src", "T1", "T2"))
        {
            @SuppressWarnings("nullness") // Will just throw if it turns out to be null, which is fine
            TableDisplay tableDisplay = (TableDisplay)TestUtil.fx(() -> details._test_getTableManager().getSingleTableOrThrow(new TableId(tableName)).getDisplay());
            int rowCount = TestUtil.fx(() -> tableDisplay._test_getRowCount());
            for (int i = 0; i < rowCount; i++)
            {
                if (r.size() <= i)
                    r.add(new ArrayList<>());
                int iFinal = i;
                r.get(i).add(TestUtil.<@Nullable String>fx(() -> getGraphicalValue(details, tableDisplay, columnIndex, iFinal)));
            }
        }
        return r;
    }
    
    private @Nullable String getGraphicalValue(MainWindowActions details, TableDisplay tableDisplay, int columnIndex, int row)
    {
        @OnThread(Tag.FXPlatform) @Nullable VersionedSTF cell = details._test_getDataCell(tableDisplay.getPosition().offsetByRowCols(tableDisplay.getHeaderRowCount() + row, columnIndex));
        if (cell != null)
            return cell._test_getGraphicalText().replace(", ", ",");
        else
            return null;
    }

    @OnThread(Tag.Any)
    @SafeVarargs
    private final void checkAllMatch(List<List<@Nullable String>>... originals)
    {
        for (int row = 0; row < originals[0].size(); row++)
        {
            @Nullable String first = originals[0].get(row).get(0);
            for (List<List<@Nullable String>> data : originals)
            {
                List<@Nullable String> valsForRow = data.get(row);
                for (int column = 0; column < valsForRow.size(); column++)
                {
                    String value = valsForRow.get(column);
                    assertEquals("Row " + row + " table " + column, first, value);
                }
            }
        }
    }

    @OnThread(Tag.Simulation)
    private static void addTransformation(TableManager mgr, String srcTable, String destTable, CellPosition position, Random r) throws InternalException
    {
        // We choose between Sort (original order), Calculate(empty list) and Filter (true)
        switch (r.nextInt(3))
        {
            case 0:
                mgr.record(new Sort(mgr, new InitialLoadDetails(new TableId(destTable), position, null), new TableId(srcTable), ImmutableList.of()));
                break;
            case 1:
                mgr.record(new Calculate(mgr, new InitialLoadDetails(new TableId(destTable), position, null), new TableId(srcTable), ImmutableMap.of()));
                break;
            case 2:
                mgr.record(new Filter(mgr, new InitialLoadDetails(new TableId(destTable), position, null), new TableId(srcTable), new BooleanLiteral(true)));
                break;
        }
    }
}
