package test.gui;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.When;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import javafx.scene.Node;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;
import org.apache.commons.io.FileUtils;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.junit.runner.RunWith;
import org.testfx.framework.junit.ApplicationTest;
import org.testfx.service.query.NodeQuery;
import org.testfx.util.WaitForAsyncUtils;
import records.data.Column;
import records.data.ColumnId;
import records.data.EditableRecordSet;
import records.data.ImmediateDataSource;
import records.data.Table;
import records.data.TableManager;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.DataTypeValue;
import records.error.InternalException;
import records.error.UserException;
import records.gui.TableDisplay;
import records.transformations.Transform;
import test.DummyManager;
import test.TestUtil;
import test.gen.ExpressionValue;
import test.gen.GenExpressionValueBackwards;
import test.gen.GenExpressionValueForwards;
import test.gen.GenRandom;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
import utility.Utility;
import utility.gui.FXUtility;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

@RunWith(JUnitQuickcheck.class)
public class TestExportToCSV extends ApplicationTest implements ScrollToTrait, CheckCSVTrait
{
    @OnThread(Tag.Any)
    @SuppressWarnings("nullness")
    private Stage windowToUse;

    @Override
    @OnThread(value = Tag.FXPlatform, ignoreParent = true)
    public void start(Stage stage) throws Exception
    {
        this.windowToUse = stage;
        FXUtility._test_setTestingMode();
    }


    /**
     * Generates a file with some raw data and a transform, then loads it and exports to CSV
     */
    @Property(trials = 5)
    @OnThread(Tag.Simulation)
    public void testCalculateToCSV(@From(GenExpressionValueForwards.class) @From(GenExpressionValueBackwards.class) ExpressionValue expressionValue) throws UserException, InternalException, InterruptedException, ExecutionException, InvocationTargetException, IOException
    {
        TableManager manager = new DummyManager();
        manager.getTypeManager()._test_copyTaggedTypesFrom(expressionValue.typeManager);

        Table srcData = new ImmediateDataSource(manager, new EditableRecordSet(expressionValue.recordSet));
        manager.record(srcData);

        Table calculated = new Transform(manager, null, srcData.getId(), ImmutableList.of(new Pair<>(new ColumnId("Result"), expressionValue.expression)));
        manager.record(calculated);

        TestUtil.openDataAsTable(windowToUse, manager).run();

        List<Pair<String, List<String>>> expectedContent = new ArrayList<>();
        for (Column column : expressionValue.recordSet.getColumns())
        {
            expectedContent.add(new Pair<>(column.getName().getRaw(), CheckCSVTrait.collapse(expressionValue.recordSet.getLength(), column.getType())));
        }
        expectedContent.add(new Pair<>("Result", Utility.mapListEx(expressionValue.value, o -> DataTypeUtility.valueToString(expressionValue.type, o, null))));

        exportToCSVAndCheck("", expectedContent, calculated.getId());
    }
}
