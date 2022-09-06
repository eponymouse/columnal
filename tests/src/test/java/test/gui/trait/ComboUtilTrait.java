package test.gui.trait;

import javafx.collections.ObservableList;
import javafx.scene.control.ComboBox;
import javafx.scene.input.KeyCode;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.testfx.api.FxRobotInterface;
import test.TestUtil;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.Utility;

import static org.junit.Assert.fail;

/**
 * Created by neil on 11/06/2017.
 */
public interface ComboUtilTrait extends FxRobotInterface
{
    @OnThread(Tag.Any)
    default <T> void selectNextComboBoxItem(final ComboBox<T> combo) {
        clickOn(combo).type(KeyCode.DOWN).type(KeyCode.ENTER);
    }

    @OnThread(Tag.Any)
    default <@NonNull T> void selectGivenComboBoxItem(final ComboBox<@NonNull T> combo, final @NonNull T item) {
        ObservableList<@NonNull T> comboItems = TestUtil.<ObservableList<@NonNull T>>fx(() -> combo.getItems());
        final int index = comboItems.indexOf(item);
        final int indexSel = TestUtil.fx(() -> combo.getSelectionModel().getSelectedIndex());

        if(index == -1)
            fail("The item " + item + " " + item.getClass() + " is not in the combo box " + combo + " items are " + Utility.listToString(comboItems) + " " + (comboItems.isEmpty() ? "" : comboItems.get(0).getClass()));

        clickOn(combo);

        if(index > indexSel)
            for(int i = indexSel; i < index; i++)
                type(KeyCode.DOWN);
        else if(index < indexSel)
            for(int i = indexSel; i > index; i--)
                type(KeyCode.UP);

        type(KeyCode.ENTER);
    }
}
