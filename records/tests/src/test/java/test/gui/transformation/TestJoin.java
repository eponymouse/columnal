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
import records.gui.MainWindow.MainWindowActions;
import records.gui.grid.RectangleBounds;
import records.transformations.Join;
import test.DummyManager;
import test.TestUtil;
import test.gen.GenRandom;
import test.gen.type.GenDataTypeMaker;
import test.gen.type.GenDataTypeMaker.DataTypeAndValueMaker;
import test.gen.type.GenDataTypeMaker.DataTypeMaker;
import test.gui.trait.ClickTableLocationTrait;
import test.gui.trait.ScrollToTrait;
import test.gui.util.FXApplicationTest;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.Pair;
import utility.SimulationFunction;
import utility.Utility;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertEquals;

@RunWith(JUnitQuickcheck.class)
public class TestJoin extends FXApplicationTest implements ScrollToTrait, ClickTableLocationTrait
{
    @Property(trials=1)
    @SuppressWarnings("identifier")
    @OnThread(Tag.Simulation)
    public void testJoin(@When(seed=1L) @From(GenDataTypeMaker.class) DataTypeMaker dataTypeMaker, @When(seed=1L) @From(GenRandom.class) Random r) throws Exception
    {
        // We make four types for columns (T1-T4), where table A has
        // T1-T3 and table B has T2-T4.  Table A has 
        ImmutableList<DataTypeAndValueMaker> dataTypes = Utility.replicateM_Ex(4, () -> dataTypeMaker.makeType());
        TableManager srcMgr = new DummyManager();
        srcMgr.getTypeManager()._test_copyTaggedTypesFrom(srcMgr.getTypeManager());
        int aSize = 10 + r.nextInt(10);
        int bSize = 10 + r.nextInt(10);
        List<SimulationFunction<RecordSet, EditableColumn>> aColumns = new ArrayList<>();
        List<SimulationFunction<RecordSet, EditableColumn>> bColumns = new ArrayList<>();
        boolean columnsNamedSame = r.nextBoolean();
        for (int i = 0; i < 4; i++)
        {
            DataTypeAndValueMaker maker = dataTypes.get(i);
            if (i <= 2)
            {
                aColumns.add(maker.getDataType().makeImmediateColumn(new ColumnId("T " + i), Utility.<Either<String, @Value Object>>replicateM_Ex(aSize, () -> Either.<String, @Value Object>right(maker.makeValue())), maker.makeValue()));
            }
            if (i > 0)
            {
                bColumns.add(maker.getDataType().makeImmediateColumn(new ColumnId((columnsNamedSame ? "T " : "R ") + (i + 1)), Utility.<Either<String, @Value Object>>replicateM_Ex(aSize, () -> Either.<String, @Value Object>right(maker.makeValue())), maker.makeValue()));
            }
        }
        srcMgr.record(new ImmediateDataSource(srcMgr, new InitialLoadDetails(new TableId("Table A"), null, null), new EditableRecordSet(aColumns, () -> aSize)));
        srcMgr.record(new ImmediateDataSource(srcMgr, new InitialLoadDetails(new TableId("Table B"), null, null), new EditableRecordSet(bColumns, () -> bSize)));
        boolean leftJoin = r.nextBoolean();
        boolean joinOn1And2 = r.nextBoolean();
        
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
        expJoin.add(new Pair<>(new ColumnId("T 1"), new ColumnId(columnsNamedSame ? "T 1" : "R 1")));
        if (joinOn1And2)
            expJoin.add(new Pair<>(new ColumnId("T 2"), new ColumnId(columnsNamedSame ? "T 2" : "R 2")));
        
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
        
        Join join = (Join)TestUtil.checkNonNull(mainWindowActions._test_getTableManager().getAllTables().stream().filter(t -> t instanceof Join).findFirst().orElse(null));
        assertEquals(ImmutableSet.of(new TableId("Table A"), new TableId("Table B")), join.getSources());
        assertEquals(leftJoin, join.isKeepPrimaryWithNoMatch());
        assertEquals(expJoin, join.getColumnsToMatch());
        
        // TODO check the data values in the actual table
    }
}
