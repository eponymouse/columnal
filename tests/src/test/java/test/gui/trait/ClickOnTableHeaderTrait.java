package test.gui.trait;

import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.control.TextField;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.testfx.api.FxRobotInterface;
import records.data.CellPosition;
import records.data.TableId;
import records.data.TableManager;
import xyz.columnal.error.UserException;
import records.gui.grid.RectangleBounds;
import records.gui.grid.VirtualGrid;
import test.TestUtil;
import threadchecker.OnThread;
import threadchecker.Tag;

public interface ClickOnTableHeaderTrait extends FxRobotInterface, ScrollToTrait, ClickTableLocationTrait
{
    @OnThread(Tag.Any)
    public default FxRobotInterface triggerTableHeaderContextMenu(VirtualGrid virtualGrid, TableManager tableManager, TableId id) throws UserException
    {
        return triggerTableHeaderContextMenu(virtualGrid, TestUtil.tablePosition(tableManager, id));
    }
    
    @OnThread(Tag.Any)
    public default FxRobotInterface triggerTableHeaderContextMenu(VirtualGrid virtualGrid, CellPosition position) throws UserException
    {
        keyboardMoveTo(virtualGrid, position);
        
        Node tableNameField = withItemInBounds(lookup(".table-display-table-title .table-name-text-field"),
            virtualGrid, new RectangleBounds(position, position), (n, p) -> {});
        if (tableNameField == null)
            throw new RuntimeException("Could not find table name field for " + position);
        @SuppressWarnings("nullness")
        Node tableHeader = TestUtil.fx(() -> tableNameField.getParent());
        Bounds tableHeaderBounds = TestUtil.fx(() -> tableHeader.localToScreen(tableHeader.getBoundsInLocal()));
        return showContextMenu(tableHeader, new Point2D(tableHeaderBounds.getMinX() + 1, tableHeaderBounds.getMinY() + 2));
    }
    
    // Matches method in FXApplicationTest:
    @OnThread(Tag.Any)
    public FxRobotInterface showContextMenu(Node node, @Nullable Point2D pointOnScreen);
}
