package test.gui;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.When;
import com.pholser.junit.quickcheck.internal.GeometricDistribution;
import com.pholser.junit.quickcheck.internal.generator.SimpleGenerationStatus;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import org.junit.runner.RunWith;
import records.data.CellPosition;
import records.data.ColumnId;
import records.data.TableId;
import records.data.datatype.DataType;
import records.error.InternalException;
import records.error.UserException;
import records.gui.MainWindow;
import records.gui.MainWindow.MainWindowActions;
import records.importers.ClipboardUtils;
import records.importers.ClipboardUtils.LoadedColumnInfo;
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
import utility.Pair;
import utility.Utility;

import javax.rmi.CORBA.Util;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.stream.IntStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

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
        
        ArrayList<ColumnDetails> columns = new ArrayList<>();
        // First add split variable:
        TypeAndValueGen splitType = genTypeAndValueGen.generate(r);
        mainWindowActions._test_getTableManager().getTypeManager()._test_copyTaggedTypesFrom(splitType.getTypeManager());
        List<@Value Object> distinctSplitValues = makeDistinctList(splitType);
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
        
        
        createDataTable(mainWindowActions, CellPosition.ORIGIN.offsetByRowCols(1, 1), "Src Data", columns);
        
        // Sanity check the data before proceeding:
        triggerTableHeaderContextMenu(mainWindowActions._test_getVirtualGrid(), mainWindowActions._test_getTableManager(), new TableId("Src Data"))
                .clickOn(".id-tableDisplay-menu-copyValues");
        TestUtil.sleep(1000);
        Optional<ImmutableList<LoadedColumnInfo>> clip = TestUtil.<Optional<ImmutableList<LoadedColumnInfo>>>fx(() -> ClipboardUtils.loadValuesFromClipboard(mainWindowActions._test_getTableManager().getTypeManager()));
        assertTrue(clip.isPresent());

        ImmutableList<LoadedColumnInfo> get = clip.get();
        for (int i = 0; i < get.size(); i++)
        {
            LoadedColumnInfo copiedColumn = get.get(i);
            assertEquals(copiedColumn.columnName, columns.get(i).name);
            TestUtil.assertValueListEitherEqual("" + i, columns.get(i).data, copiedColumn.dataValues);
        }
        
        // TODO now add the actual aggregate!
    }

    // Makes a list containing no duplicate values, using the given type.
    private List<@Value Object> makeDistinctList(TypeAndValueGen splitType) throws UserException, InternalException
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
        return r;
    }
}
