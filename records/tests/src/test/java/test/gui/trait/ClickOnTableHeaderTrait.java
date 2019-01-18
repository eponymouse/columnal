package test.gui.trait;

import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.control.TextField;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.testfx.api.FxRobotInterface;
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
    public default FxRobotInterface triggerTableHeaderContextMenu(VirtualGrid virtualGrid, TableManager tableManager, TableId id) throws UserException
    {
        keyboardMoveTo(virtualGrid, TestUtil.tablePosition(tableManager, id));
        
        Node tableNameField = lookup(".table-display-table-title .table-name-text-field")
            .match(t -> TestUtil.fx(() -> ((TextField) t).getText().equals(id.getRaw())))
            .query();
        if (tableNameField == null)
            throw new RuntimeException("Could not find table name field for " + id);
        @SuppressWarnings("nullness")
        Node tableHeader = TestUtil.fx(() -> tableNameField.getParent());
        Bounds tableHeaderBounds = TestUtil.fx(() -> tableHeader.localToScreen(tableHeader.getBoundsInLocal()));
        return showContextMenu(tableHeader, new Point2D(tableHeaderBounds.getMinX() + 1, tableHeaderBounds.getMinY() + 2));
    }
    
    // Matches method in FXApplicationTest:
    @OnThread(Tag.Any)
    public FxRobotInterface showContextMenu(Node node, @Nullable Point2D pointOnScreen);
}
