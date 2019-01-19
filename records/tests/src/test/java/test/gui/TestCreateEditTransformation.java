package test.gui;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.When;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import org.junit.runner.RunWith;
import records.data.CellPosition;
import records.data.ColumnId;
import records.data.Table;
import records.data.TableId;
import records.data.datatype.DataTypeUtility;
import records.error.InternalException;
import records.error.UserException;
import records.gui.MainWindow;
import records.gui.MainWindow.MainWindowActions;
import records.gui.grid.RectangleBounds;
import records.importers.ClipboardUtils;
import records.importers.ClipboardUtils.LoadedColumnInfo;
import records.transformations.SummaryStatistics;
import test.TestUtil;
import test.gen.GenRandom;
import test.gen.GenTypeAndValueGen;
import test.gen.GenTypeAndValueGen.TypeAndValueGen;
import test.gui.trait.ClickOnTableHeaderTrait;
import test.gui.trait.CreateDataTableTrait;
import test.gui.util.FXApplicationTest;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.Utility;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.stream.IntStream;

import static org.junit.Assert.*;

@RunWith(JUnitQuickcheck.class)
@OnThread(Tag.Simulation)
public class TestCreateEditTransformation extends FXApplicationTest implements CreateDataTableTrait, ClickOnTableHeaderTrait
{
    @Property(trials = 3)
    public void testAggregate(@When(seed=8741518136966126489L) @From(GenRandom.class) Random r) throws Exception
    {
        GenTypeAndValueGen genTypeAndValueGen = new GenTypeAndValueGen(false);

        File dest = File.createTempFile("blank", "rec");
        dest.deleteOnExit();
        MainWindowActions mainWindowActions = TestUtil.fx(() -> MainWindow.show(windowToUse, dest, null));
        
        List<ColumnDetails> columns = new ArrayList<>();
        // First add split variable:
        TypeAndValueGen splitType = genTypeAndValueGen.generate(r);
        mainWindowActions._test_getTableManager().getTypeManager()._test_copyTaggedTypesFrom(splitType.getTypeManager());
        List<@Value Object> distinctSplitValues = makeDistinctSortedList(splitType);
        // Now make random duplication count for each:
        List<Integer> replicationCounts = Utility.mapList(distinctSplitValues, _s -> 1 + r.nextInt(10));
        int totalLength = replicationCounts.stream().mapToInt(n -> n).sum();
        columns.add(new ColumnDetails(new ColumnId("Split Col"), splitType.getType(),
            IntStream.range(0, distinctSplitValues.size()).mapToObj(i -> i)
                .<Either<String, @Value Object>>flatMap(i -> Utility.<Either<String, @Value Object>>replicate(replicationCounts.get(i), Either.right(distinctSplitValues.get(i))).stream())
                .collect(ImmutableList.<Either<String, @Value Object>>toImmutableList())));
        
        // Then add source column for aggregate calculations (summing etc):
        // TODO

        // Add some extra columns with errors just to complicate things:
        int numExtraColumns = r.nextInt(4);
        for (int i = 0; i < numExtraColumns; i++)
        {
            TypeAndValueGen extraType = genTypeAndValueGen.generate(r);
            mainWindowActions._test_getTableManager().getTypeManager()._test_copyTaggedTypesFrom(extraType.getTypeManager());
            columns.add(r.nextInt(columns.size() + 1), new ColumnDetails(new ColumnId("Extra " + i), extraType.getType(), Utility.<Either<String, @Value Object>>replicateM_Ex(totalLength, () -> r.nextInt(10) == 1 ? Either.<String, @Value Object>left("@") : Either.<String, @Value Object>right(extraType.makeValue()))));
        }

        for (ColumnDetails column : columns)
        {
            assertEquals(column.name.getRaw(), totalLength, column.data.size());
        }

        columns = scrambleDataOrder(columns, r);
        
        createDataTable(mainWindowActions, CellPosition.ORIGIN.offsetByRowCols(1, 1), "Src Data", columns);
        
        // Sanity check the data before proceeding:
        ImmutableList<LoadedColumnInfo> clip = copyTableData(mainWindowActions, "Src Data");

        for (int i = 0; i < clip.size(); i++)
        {
            LoadedColumnInfo copiedColumn = clip.get(i);
            assertEquals(copiedColumn.columnName, columns.get(i).name);
            TestUtil.assertValueListEitherEqual("" + i, columns.get(i).data, copiedColumn.dataValues);
        }
        
        // Now add the actual aggregate:
        CellPosition aggTarget = CellPosition.ORIGIN.offsetByRowCols(1, columns.size() + 2);
        keyboardMoveTo(mainWindowActions._test_getVirtualGrid(), aggTarget);
        clickOnItemInBounds(from(TestUtil.fx(() -> mainWindowActions._test_getVirtualGrid().getNode())), mainWindowActions._test_getVirtualGrid(), new RectangleBounds(aggTarget, aggTarget), MouseButton.PRIMARY);
        TestUtil.delay(100);
        clickOn(".id-new-transform");
        TestUtil.delay(100);
        clickOn(".id-transform-aggregate");
        TestUtil.delay(100);
        write("Src Data");
        push(KeyCode.ENTER);
        TestUtil.sleep(200);
        write("Split Col");
        moveAndDismissPopupsAtPos(point(".ok-button"));
        clickOn(".ok-button");
        TestUtil.sleep(3000);
        
        // Should be one column at the moment, with the distinct split values:
        SummaryStatistics aggTable = (SummaryStatistics)mainWindowActions._test_getTableManager().getAllTables().stream().filter(t -> !t.getId().equals(new TableId("Src Data"))).findFirst().orElseThrow(RuntimeException::new);
        assertEquals(ImmutableList.of(new ColumnId("Split Col")), aggTable.getSplitBy());
        String aggId = aggTable.getId().getRaw();
        ImmutableList<LoadedColumnInfo> initialAgg = copyTableData(mainWindowActions, aggId);
        TestUtil.assertValueListEitherEqual("Table " + aggId, Utility.<@Value Object, Either<String, @Value Object>>mapList(distinctSplitValues, v -> Either.right(v)), initialAgg.get(0).dataValues);
        
        // Now add the calculations:
        // TODO
    }

    private List<ColumnDetails> scrambleDataOrder(List<ColumnDetails> columns, Random r)
    {
        return Utility.mapList(columns, c -> {
            List<Either<String, @Value Object>> scrambledData = new ArrayList<>(c.data);
            Collections.shuffle(scrambledData, r);
            return new ColumnDetails(c.name, c.dataType, ImmutableList.copyOf(scrambledData));
        });
    }

    public ImmutableList<LoadedColumnInfo> copyTableData(MainWindowActions mainWindowActions, String tableName) throws UserException
    {
        triggerTableHeaderContextMenu(mainWindowActions._test_getVirtualGrid(), mainWindowActions._test_getTableManager(), new TableId(tableName))
                .clickOn(".id-tableDisplay-menu-copyValues");
        TestUtil.sleep(1000);
        Optional<ImmutableList<LoadedColumnInfo>> clip = TestUtil.<Optional<ImmutableList<LoadedColumnInfo>>>fx(() -> ClipboardUtils.loadValuesFromClipboard(mainWindowActions._test_getTableManager().getTypeManager()));
        assertTrue(clip.isPresent());
        return clip.get();
    }

    // Makes a list containing no duplicate values, using the given type.
    private List<@Value Object> makeDistinctSortedList(TypeAndValueGen splitType) throws UserException, InternalException
    {
        int targetAmount = 12;
        int attempts = 50;
        ArrayList<@Value Object> r = new ArrayList<>();
        nextAttempt: for (int i = 0; i < attempts && r.size() < targetAmount; i++)
        {
            @Value Object newVal = splitType.makeValue();
            // This is O(N^2) but it's only test code, and small N:
            for (@Value Object existing : r)
            {
                if (Utility.compareValues(newVal, existing) == 0)
                    continue nextAttempt;
            }
            r.add(newVal);
        }

        Collections.sort(r, DataTypeUtility.getValueComparator());
        
        return r;
    }
}
