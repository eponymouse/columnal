package records.gui.stable;

import javafx.geometry.Point2D;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.fxmisc.wellbehaved.event.InputMap;
import records.gui.stable.StableView.ColumnHandler;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformConsumer;
import utility.FXPlatformRunnable;
import utility.SimulationFunction;

public abstract class ReadOnlyStringColumnHandler implements ColumnHandler
{
    @Override
    public void fetchValue(int rowIndex, FXPlatformConsumer<Boolean> focusListener, FXPlatformRunnable relinquishFocus, FXPlatformConsumer<Region> setCellContent, int firstVisibleRowIndexIncl, int lastVisibleRowIndexIncl)
    {
        fetchValueForRow(rowIndex, s -> setCellContent.consume(new Label(s)));
    }

    @OnThread(Tag.FXPlatform)
    public abstract void fetchValueForRow(int rowIndex, FXPlatformConsumer<String> withValue);

    @Override
    public void columnResized(double width)
    {

    }

    @Override
    public @Nullable InputMap<?> getInputMapForParent(int rowIndex)
    {
        return null;
    }

    @Override
    public void edit(int rowIndex, @Nullable Point2D scenePoint)
    {
        // Not editable
    }

    @Override
    public boolean isEditable()
    {
        return false;
    }

    @Override
    public boolean editHasFocus(int rowIndex)
    {
        return false;
    }
}
