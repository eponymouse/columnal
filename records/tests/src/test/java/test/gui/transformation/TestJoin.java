package test.gui.transformation;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.When;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.junit.runner.RunWith;
import records.data.CellPosition;
import records.data.ColumnId;
import records.data.EditableColumn;
import records.data.EditableRecordSet;
import records.data.ImmediateDataSource;
import records.data.RecordSet;
import records.data.Table;
import records.data.Table.InitialLoadDetails;
import records.data.TableId;
import records.data.TableManager;
import records.data.datatype.DataTypeValue;
import records.gui.MainWindow.MainWindowActions;
import records.gui.grid.RectangleBounds;
import records.transformations.Join;
import test.DummyManager;
import test.TestUtil;
import test.gen.GenRandom;
import test.gen.type.GenDataTypeMaker;
import test.gen.type.GenDataTypeMaker.DataTypeAndValueMaker;
import test.gen.type.GenDataTypeMaker.DataTypeMaker;
import test.gen.type.GenDataTypeMaker.MustHaveValues;
import test.gui.trait.ClickTableLocationTrait;
import test.gui.trait.ScrollToTrait;
import test.gui.util.FXApplicationTest;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.Pair;
import utility.SimulationFunction;
import utility.TaggedValue;
import utility.Utility;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.Random;

import static org.junit.Assert.assertEquals;

@RunWith(JUnitQuickcheck.class)
public class TestJoin extends FXApplicationTest implements ScrollToTrait, ClickTableLocationTrait
{
    @Property(trials=5)
    @SuppressWarnings("identifier")
    @OnThread(Tag.Simulation)
    public void testJoin(@MustHaveValues @From(GenDataTypeMaker.class) DataTypeMaker dataTypeMaker, @From(GenRandom.class) Random r) throws Exception
    {
        // We make four types for columns (T1-T4), where table A has
        // T1-T3 and table B has T2-T4.  Table A has 
        ImmutableList<DataTypeAndValueMaker> dataTypes = Utility.replicateM_Ex(4, () -> dataTypeMaker.makeType());
        TableManager srcMgr = new DummyManager();
        srcMgr.getTypeManager()._test_copyTaggedTypesFrom(dataTypeMaker.getTypeManager());
        int aSize = 10 + r.nextInt(10);
        int bSize = 10 + r.nextInt(10);
        List<SimulationFunction<RecordSet, EditableColumn>> aColumns = new ArrayList<>();
        List<SimulationFunction<RecordSet, EditableColumn>> bColumns = new ArrayList<>();
        boolean columnsNamedSame = r.nextBoolean();
        boolean leftJoin = r.nextBoolean();
        // One entry per output row.  Which row numbers do the result rows come from? 
        ArrayList<Pair<Integer, OptionalInt>> resultSourceRows = new ArrayList<>();
        // Join on nothing (0), T1-T2 (1) or T1-T2+T2-T3 (2)
        int joinColumnCount = r.nextInt(3);
        // Start with all pairs:
        for (int i = 0; i < aSize; i++)
        {
            for (int j = 0; j < bSize; j++)
            {
                resultSourceRows.add(new Pair<>(i, OptionalInt.of(j)));
            }
            if (leftJoin)
            {
                resultSourceRows.add(new Pair<>(i, OptionalInt.empty()));
            }
        }
        for (int i = 0; i < 4; i++)
        {
            DataTypeAndValueMaker maker = dataTypes.get(i);
            ImmutableList<Either<String, @Value Object>> aValues = Utility.<Either<String, @Value Object>>replicateM_Ex(aSize, () -> Either.<String, @Value Object>right(maker.makeValue()));
            if (i <= 2)
            {
                aColumns.add(maker.getDataType().makeImmediateColumn(new ColumnId("T " + i), aValues, maker.makeValue()));
            }

            ArrayList<Either<String, @Value Object>> bValues = new ArrayList<>();
            // Pick some amount that are the same, and the rest that may or may not be:
            int duplicate = r.nextInt(bSize);
            for (int d = 0; d < duplicate; d++)
            {
                bValues.add(aValues.get(r.nextInt(aValues.size())));
            }
            bValues.addAll(Utility.<Either<String, @Value Object>>replicateM_Ex(bSize - bValues.size(), () -> Either.<String, @Value Object>right(maker.makeValue())));
            
            if (i > 0)
            {                
                // To cause deliberate name confusion, second table columns are offset by one:
                bColumns.add(maker.getDataType().makeImmediateColumn(new ColumnId((columnsNamedSame ? "T " : "R ") + (i + 1)), bValues, maker.makeValue()));
            }
            if (i > 0 && i - 1 < joinColumnCount)
            {
                // Remove any non-matches
                for (int a = 0; a < aSize; a++)
                {
                    for (int b = 0; b < bSize; b++)
                    {
                        if (Utility.compareValues(aValues.get(a).getRight("A"), bValues.get(b).getRight("B")) != 0)
                        {
                            resultSourceRows.remove(new Pair<>(a, OptionalInt.of(b)));
                        }
                    }
                }
            }
        }
        
        // Remove left join matches if any are present:
        for (int a = 0; a < aSize; a++)
        {
            int aFinal = a;
            if (resultSourceRows.stream().anyMatch(p -> p.getFirst() == aFinal && p.getSecond().isPresent()))
                resultSourceRows.remove(new Pair<>(a, OptionalInt.empty()));
        }
        
        srcMgr.record(new ImmediateDataSource(srcMgr, new InitialLoadDetails(new TableId("Table A"), null, null, null), new EditableRecordSet(aColumns, () -> aSize)));
        srcMgr.record(new ImmediateDataSource(srcMgr, new InitialLoadDetails(new TableId("Table B"), null, null, null), new EditableRecordSet(bColumns, () -> bSize)));
        
        
        
        MainWindowActions mainWindowActions = TestUtil.openDataAsTable(windowToUse, srcMgr).get();
        TestUtil.sleep(2000);
        CellPosition targetPos = TestUtil.fx(() -> mainWindowActions._test_getTableManager().getNextInsertPosition(null));
        keyboardMoveTo(mainWindowActions._test_getVirtualGrid(), targetPos);
        clickOnItemInBounds(from(TestUtil.fx(() -> mainWindowActions._test_getVirtualGrid().getNode())), mainWindowActions._test_getVirtualGrid(), new RectangleBounds(targetPos, targetPos), MouseButton.PRIMARY);
        TestUtil.delay(100);
        clickOn(".id-new-transform");
        TestUtil.delay(100);
        clickOn(".id-transform-join");
        TestUtil.delay(100);

        ArrayList<Pair<ColumnId, ColumnId>> expJoin = new ArrayList<>();
        if (joinColumnCount >= 1)
            expJoin.add(new Pair<>(new ColumnId("T 1"), new ColumnId(columnsNamedSame ? "T 2" : "R 2")));
        if (joinColumnCount >= 2)
            expJoin.add(new Pair<>(new ColumnId("T 2"), new ColumnId(columnsNamedSame ? "T 3" : "R 3")));
        
        write("Table A");
        push(KeyCode.TAB);
        write("Table B");
        if (leftJoin)
            clickOn(".id-join-isLeftJoin");
        for (Pair<ColumnId, ColumnId> pair : expJoin)
        {
            clickOn(".id-fancylist-add");
            write(pair.getFirst().getRaw());
            push(KeyCode.ESCAPE);
            push(KeyCode.TAB);
            write(pair.getSecond().getRaw());
        }
        clickOn(".ok-button");

        Table tableA = TestUtil.checkNonNull(mainWindowActions._test_getTableManager().getSingleTableOrThrow(new TableId("Table A")));
        Table tableB = TestUtil.checkNonNull(mainWindowActions._test_getTableManager().getSingleTableOrThrow(new TableId("Table B")));
        Join join = (Join)TestUtil.checkNonNull(mainWindowActions._test_getTableManager().getAllTables().stream().filter(t -> t instanceof Join).findFirst().orElse(null));
        assertEquals(ImmutableSet.of(new TableId("Table A"), new TableId("Table B")), join.getSources());
        assertEquals(leftJoin, join.isKeepPrimaryWithNoMatch());
        assertEquals(expJoin, join.getColumnsToMatch());
        
        // Check the data values in the actual table:
        for (int i = 0; i <= 3; i++)
        {
            ArrayList<Pair<Table, ColumnId>> srcOccurrences = new ArrayList<>();
            if (i < 3)
                srcOccurrences.add(new Pair<>(tableA, new ColumnId("T " + i)));
            if (i > 0)
                srcOccurrences.add(new Pair<>(tableB, new ColumnId((columnsNamedSame ? "T " : "R ") + (i + 1))));

            for (Pair<Table, ColumnId> srcColName : srcOccurrences)
            {
                ArrayList<Either<String, @Value Object>> expected = new ArrayList<>();

                DataTypeValue srcCol = srcColName.getFirst().getData().getColumn(srcColName.getSecond()).getType();

                for (Pair<Integer, OptionalInt> src : resultSourceRows)
                {
                    if (tableA == srcColName.getFirst())
                    {
                        expected.add(TestUtil.getSingleCollapsedData(srcCol, src.getFirst()));
                    }
                    else if (leftJoin)
                    {
                        if (src.getSecond().isPresent())
                        {
                            expected.add(TestUtil.getSingleCollapsedData(srcCol, src.getSecond().getAsInt()).<@Value Object>map(v -> new TaggedValue(1, v)));
                        }
                        else
                        {
                            expected.add(Either.right(new TaggedValue(0, null)));
                        }
                    }
                    else
                    {
                        expected.add(TestUtil.getSingleCollapsedData(srcCol, src.getSecond().getAsInt()));
                    }
                }
                
                // Adjust for overlapping names:
                ColumnId joinColName = srcColName.getSecond();
                if (tableA == srcColName.getFirst() && tableB.getData().getColumnIds().contains(joinColName))
                {
                    joinColName = new ColumnId(tableA.getId().getRaw() + " " + joinColName.getRaw());
                }
                if (tableB == srcColName.getFirst() && tableA.getData().getColumnIds().contains(joinColName))
                {
                    joinColName = new ColumnId(tableB.getId().getRaw() + " " + joinColName.getRaw());
                }
                TestUtil.assertValueListEitherEqual("Values for " + srcColName.getFirst().getId().getRaw() + " -> " + joinColName.getRaw(),
                    expected,
                    TestUtil.getAllCollapsedData(join.getData().getColumn(joinColName).getType(), expected.size()));
            }
        }

        
        
    }
}
