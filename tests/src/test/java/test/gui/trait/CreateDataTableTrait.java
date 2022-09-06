package test.gui.trait;

import annotation.identifier.qual.ExpressionIdentifier;
import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import javafx.application.Platform;
import javafx.scene.input.KeyCode;
import org.testfx.api.FxRobotInterface;
import records.data.CellPosition;
import records.data.Column;
import records.data.ColumnId;
import records.data.DataTestUtil;
import records.data.TableId;
import records.data.datatype.DataType;
import xyz.columnal.error.InvalidImmediateValueException;
import records.gui.MainWindow.MainWindowActions;
import records.gui.grid.RectangleBounds;
import records.gui.grid.VirtualGrid;
import records.importers.ClipboardUtils;
import records.importers.ClipboardUtils.RowRange;
import xyz.columnal.styled.StyledString;
import test.TestUtil;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.Either;
import xyz.columnal.utility.Pair;
import xyz.columnal.utility.Utility;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.assertTrue;

public interface CreateDataTableTrait extends FxRobotInterface, ScrollToTrait, ClickTableLocationTrait, FocusOwnerTrait, EnterTypeTrait, PopupTrait, TextFieldTrait
{
    public static class ColumnDetails
    {
        public final ColumnId name;
        public final DataType dataType;
        public final ImmutableList<Either<String, @Value Object>> data;

        public ColumnDetails(ColumnId name, DataType dataType, ImmutableList<Either<String, @Value Object>> data)
        {
            this.name = name;
            this.dataType = dataType;
            this.data = data;
        }
    }
    
    @OnThread(Tag.Simulation)
    public default void createDataTable(MainWindowActions mainWindowActions, CellPosition targetPos, @ExpressionIdentifier String tableName, List<ColumnDetails> columns) throws Exception
    {
        VirtualGrid virtualGrid = mainWindowActions._test_getVirtualGrid();
        keyboardMoveTo(virtualGrid, targetPos);
        
        /* TODO support other methods of table creation
        
        // Only need to click once as already selected by keyboard:
        clickOnItemInBounds(lookup(".create-table-grid-button"), virtualGrid, new RectangleBounds(targetPos, targetPos), MouseButton.PRIMARY);
        correctTargetWindow().clickOn(".id-new-data");
        correctTargetWindow();
        write(tableName, 1);
        push(KeyCode.TAB);
        // Add first column in this dialog:
        write(columns.get(0).name.getRaw(), 1);
        push(KeyCode.TAB);
        enterType(TypeExpression.fromDataType(columns.get(0).dataType), new Random(1));
        moveAndDismissPopupsAtPos(point(".ok-button"));
        clickOn(".ok-button");
        TestUtil.delay(200);
        
        
        // Now need to add other columns, then put the data in clipboard and paste in.

        // Start at one, since we already added first column:
        for (int i = 1; i < columns.size(); i++)
        {
            clickOnItemInBounds(lookup(".expand-arrow"),virtualGrid, new RectangleBounds(targetPos.offsetByRowCols(2, 1), targetPos.offsetByRowCols(2, 1)));
            TestUtil.sleep(500);
            write(columns.get(i).name.getRaw(), 1);
            push(KeyCode.TAB);
            enterType(TypeExpression.fromDataType(columns.get(i).dataType), new Random(i));
            moveAndDismissPopupsAtPos(point(".ok-button"));
            clickOn(".ok-button");
            TestUtil.delay(500);
        }
        */

        CompletableFuture<Boolean> copyDone = new CompletableFuture<>();
        @SuppressWarnings("units")
        RowRange rowRange = new RowRange(0, columns.get(0).data.size() - 1);
        Platform.runLater(() -> {
            try
            {
                ClipboardUtils.copyValuesToClipboard(mainWindowActions._test_getTableManager().getUnitManager(), mainWindowActions._test_getTableManager().getTypeManager(), Utility.mapListExI(columns, c -> new Pair<>(c.name, c.dataType.fromCollapsed((i, prog) -> c.data.get(i).<@Value Object>eitherEx(invalid -> {throw new InvalidImmediateValueException(StyledString.s("cdtt_err"), invalid);
                }, valid -> valid)))), () -> rowRange, copyDone);
            }
            catch (Exception e)
            {
                copyDone.complete(false);
            }
        });
        
        boolean successfulCopy = copyDone.get();
        assertTrue(successfulCopy);
        
        // Now all columns should be in place, select table and hit paste:
        
        keyboardMoveTo(virtualGrid, targetPos);
        push(KeyCode.SHORTCUT, KeyCode.V);
        TestUtil.sleep(3000);
        // Enter table name:
        clickOnItemInBounds(lookup(".table-display-table-title .table-name-text-field"), virtualGrid, new RectangleBounds(targetPos, targetPos));
        selectAllCurrentTextField();
        write(tableName, 1);
        push(KeyCode.ENTER);
        // Give time for rename:
        TestUtil.sleep(3000);

        // Double check the values via direct access:
        List<Column> actualColumns = mainWindowActions._test_getTableManager().getSingleTableOrThrow(new TableId(tableName)).getData().getColumns();

        for (int i = 0; i < columns.size(); i++)
        {
            DataTestUtil.assertValueListEitherEqual("Column " + columns.get(i).name, columns.get(i).data, DataTestUtil.getAllCollapsedData(actualColumns.get(i).getType(), columns.get(i).data.size()));
        }
    }
}
