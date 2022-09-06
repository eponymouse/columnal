package records.gui.stable;

import annotation.qual.Value;
import annotation.units.TableDataRowIndex;
import javafx.scene.input.KeyCode;
import records.data.CellPosition;
import records.data.RecordSet.RecordSetListener;
import records.error.InternalException;
import records.error.UserException;
import records.gui.dtf.DocumentTextField;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformBiConsumer;
import utility.FXPlatformConsumer;

import java.util.Collection;

@OnThread(Tag.FXPlatform)
public interface ColumnHandler extends RecordSetListener
{
    // Called to fetch a value.  Once available, receiver should be called.
    // Until then it will be blank.  You can call receiver multiple times though,
    // so you can just call it with a placeholder before returning.
    public void fetchValue(@TableDataRowIndex int rowIndex, FXPlatformConsumer<Boolean> focusListener, FXPlatformBiConsumer<KeyCode, CellPosition> relinquishFocus, EditorKitCallback setCellContent);

    // Called when the column gets resized (graphically).  Width is in pixels
    public void columnResized(double width);

    // Should return an InputMap, if any, to put on the parent node of the display.
    // Useful if you want to be able to press keys directly without beginning editing
    //public @Nullable InputMap<?> getInputMapForParent(int rowIndex);

    // Called when the user initiates an error, either by double-clicking
    // (in which case the point is passed) or by pressing enter (in which case
    // point is null).
    // Will only be called if isEditable returns true
    //public void edit(int rowIndex, @Nullable Point2D scenePoint);

    // Can this column be edited?
    public boolean isEditable();

    // Is this column value currently being edited?
    //public boolean editHasFocus(int rowIndex);
    
    // Used for doing a copy-to-clipboard of table content:
    @OnThread(Tag.Simulation)
    public @Value Object getValue(int index) throws InternalException, UserException;

    void styleTogether(Collection<? extends DocumentTextField> cellsInColumn, double columnSize);
}
