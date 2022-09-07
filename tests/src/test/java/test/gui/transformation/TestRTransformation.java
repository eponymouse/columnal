package test.gui.transformation;

import com.google.common.collect.ImmutableList;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import org.junit.Test;
import org.junit.runner.RunWith;
import xyz.columnal.data.CellPosition;
import xyz.columnal.data.ColumnId;
import xyz.columnal.data.KnownLengthRecordSet;
import xyz.columnal.data.RecordSet;
import xyz.columnal.data.datatype.DataType;
import records.gui.MainWindow.MainWindowActions;
import records.gui.grid.RectangleBounds;
import xyz.columnal.transformations.RTransformation;
import test.TestUtil;
import test.gen.GenImmediateData;
import test.gen.GenImmediateData.CanHaveErrorValues;
import test.gen.GenRandom;
import test.gui.trait.ClickTableLocationTrait;
import test.gui.trait.ScrollToTrait;
import test.gui.util.FXApplicationTest;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.Either;

import java.util.Random;

import static org.junit.Assert.assertEquals;

@RunWith(JUnitQuickcheck.class)
public class TestRTransformation extends FXApplicationTest implements ScrollToTrait, ClickTableLocationTrait
{
    // The R functionality is tested more thoroughly in other tests,
    // this is about testing the GUI, and the package installation.
    
    @Test
    @OnThread(Tag.Simulation)
    public void testR() throws Exception
    {
        // Uninstall one package first to check installation works:
        Runtime.getRuntime().exec(new String[] {"R", "CMD", "REMOVE", "digest"});
        
        RecordSet original = new KnownLengthRecordSet(ImmutableList.of(DataType.TEXT.makeImmediateColumn(new ColumnId("The Strings"), ImmutableList.of(Either.right("Hello"), Either.right("There")), "")), 2);
        
        MainWindowActions mainWindowActions = TestUtil.openDataAsTable(windowToUse, null, original);
        TestUtil.sleep(5000);
        CellPosition targetPos = TestUtil.fx(() -> mainWindowActions._test_getTableManager().getNextInsertPosition(null));
        keyboardMoveTo(mainWindowActions._test_getVirtualGrid(), targetPos);
        clickOnItemInBounds(from(TestUtil.fx(() -> mainWindowActions._test_getVirtualGrid().getNode())), mainWindowActions._test_getVirtualGrid(), new RectangleBounds(targetPos, targetPos), MouseButton.PRIMARY);
        TestUtil.delay(100);
        clickOn(".id-new-transform");
        TestUtil.delay(100);
        scrollTo(".id-transform-runr");
        clickOn(".id-transform-runr");
        TestUtil.delay(200);
        // Focus should begin in the expression, so let's do that first:
        write("digest(str_c(Table1$\"The Strings\"))");
        
        clickOn(".id-fancylist-add");
        write("Ta");
        push(KeyCode.DOWN);
        push(KeyCode.ENTER);

        clickOn(".r-package-field");
        write("digest, stringr");
        
        clickOn(".ok-button");
        
        // Since we're installing packages, wait a bit longer than usual:
        sleep(1000);
        
        // Now check table exists, with correct output
        assertEquals(2, mainWindowActions._test_getTableManager().getAllTables().size());
        RTransformation rTransformation = (RTransformation)TestUtil.checkNonNull(mainWindowActions._test_getTableManager().getAllTables().stream().filter(t -> t instanceof RTransformation).findFirst().orElse(null));
        assertEquals(ImmutableList.of(new ColumnId("Result")), rTransformation.getData().getColumnIds());
        assertEquals("497859090e5f950944a1e8cf3989dd8d", rTransformation.getData().getColumns().get(0).getType().getCollapsed(0));
    }
}
