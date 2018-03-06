package test.gui;

import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseButton;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.testfx.api.FxRobotInterface;
import records.data.CellPosition;
import records.data.Table.TableDisplayBase;
import records.data.TableId;
import records.data.TableManager;
import records.error.UserException;
import records.gui.grid.VirtualGrid;
import test.TestUtil;
import threadchecker.OnThread;
import threadchecker.Tag;

public interface ClickOnTableHeaderTrait extends FxRobotInterface, ScrollToTrait
{
    @OnThread(Tag.Any)
    public default void clickOnTableHeader(VirtualGrid virtualGrid, TableManager tableManager, TableId id, MouseButton... buttons) throws UserException
    {
        @NonNull TableDisplayBase display = TestUtil.checkNonNull(TestUtil.<@Nullable TableDisplayBase>fx(() -> tableManager.getSingleTableOrThrow(id).getDisplay()));
        keyboardMoveTo(virtualGrid, display.getMostRecentPosition());
        
        Node tableNameField = lookup(".table-display-table-title .table-name-text-field")
            .match(t -> TestUtil.fx(() -> ((TextField) t).getText().equals(id.getRaw())))
            .query();
        if (tableNameField == null)
            throw new RuntimeException("Could not find table name field for " + id);
        @SuppressWarnings("nullness")
        Node tableHeader = TestUtil.fx(() -> tableNameField.getParent());
        Bounds tableHeaderBounds = TestUtil.fx(() -> tableHeader.localToScreen(tableHeader.getBoundsInLocal()));
        clickOn(tableHeaderBounds.getMinX() + 1, tableHeaderBounds.getMinY() + 2, buttons);
    }
}
