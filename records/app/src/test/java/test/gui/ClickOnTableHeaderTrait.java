package test.gui;

import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseButton;
import org.testfx.api.FxRobotInterface;
import records.data.TableId;
import test.TestUtil;
import threadchecker.OnThread;
import threadchecker.Tag;

public interface ClickOnTableHeaderTrait extends FxRobotInterface
{
    @OnThread(Tag.Any)
    public default void clickOnTableHeader(TableId id, MouseButton... buttons)
    {
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
