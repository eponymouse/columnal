package test.gui;

import com.google.common.collect.ImmutableMap;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.When;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import org.junit.runner.RunWith;
import records.data.Column;
import records.data.ColumnId;
import records.data.EditableRecordSet;
import records.data.ImmediateDataSource;
import records.data.Table;
import records.data.TableManager;
import records.data.datatype.DataTypeUtility;
import records.gui.MainWindow.MainWindowActions;
import records.transformations.Calculate;
import test.DummyManager;
import test.TestUtil;
import test.gen.ExpressionValue;
import test.gen.GenExpressionValueBackwards;
import test.gen.GenExpressionValueForwards;
import test.gui.trait.CheckCSVTrait;
import test.gui.trait.ScrollToTrait;
import test.gui.util.FXApplicationTest;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
import utility.Utility;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

@RunWith(JUnitQuickcheck.class)
public class TestExportToCSV extends FXApplicationTest implements ScrollToTrait, CheckCSVTrait
{
    /**
     * Generates a file with some raw data and a transform, then loads it and exports to CSV
     */
    @Property(trials = 5)
    @OnThread(Tag.Simulation)
    public void testCalculateToCSV(
            @When(seed=-7265728807976809456L)
            @From(GenExpressionValueForwards.class) @From(GenExpressionValueBackwards.class) ExpressionValue expressionValue) throws Exception
    {
        TableManager manager = new DummyManager();
        manager.getTypeManager()._test_copyTaggedTypesFrom(expressionValue.typeManager);

        Table srcData = new ImmediateDataSource(manager, TestUtil.ILD, new EditableRecordSet(expressionValue.recordSet));
        manager.record(srcData);

        Table calculated = new Calculate(manager, TestUtil.ILD, srcData.getId(), ImmutableMap.of(new ColumnId("Result"), expressionValue.expression));
        manager.record(calculated);

        MainWindowActions details = TestUtil.openDataAsTable(windowToUse, manager).get();

        List<Pair<String, List<String>>> expectedContent = new ArrayList<>();
        for (Column column : expressionValue.recordSet.getColumns())
        {
            expectedContent.add(new Pair<>(column.getName().getRaw(), CheckCSVTrait.collapse(expressionValue.recordSet.getLength(), column.getType())));
        }
        expectedContent.add(new Pair<>("Result", Utility.mapListEx(expressionValue.value, o -> DataTypeUtility.valueToString(expressionValue.type, o, null))));

        exportToCSVAndCheck(details._test_getVirtualGrid(), details._test_getTableManager(),"", expectedContent, calculated.getId());
    }
}
