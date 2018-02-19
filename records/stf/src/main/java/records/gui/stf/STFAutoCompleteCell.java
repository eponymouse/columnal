package records.gui.stf;

import javafx.scene.control.ListCell;
import javafx.scene.input.MouseButton;
import org.checkerframework.checker.initialization.qual.Initialized;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.fxmisc.flowless.Cell;
import records.gui.stf.StructuredTextField.Suggestion;
import threadchecker.OnThread;
import threadchecker.Tag;

/**
 * Created by neil on 30/06/2017.
 */
public class STFAutoCompleteCell extends ListCell<Suggestion>
{
    public STFAutoCompleteCell(STFAutoComplete autoComplete)
    {
        getStyleClass().add("stf-autocomplete-item");
        setOnMouseClicked(e -> {
            Suggestion item = getItem();
            if (e.getButton() == MouseButton.PRIMARY && item != null)
            {
                autoComplete.fire(item);
                e.consume();
            }
        });
    }

    @Override
    @OnThread(value = Tag.FXPlatform, ignoreParent = true)
    protected void updateItem(@Nullable Suggestion item, boolean empty)
    {
        super.updateItem(item, empty);
        if (item != null && !empty)
            setText(item.suggestion);
        else
            setText("");
    }
}
