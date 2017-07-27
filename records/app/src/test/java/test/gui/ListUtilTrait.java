package test.gui;

import javafx.scene.control.ComboBox;
import javafx.scene.control.ListView;
import javafx.scene.input.KeyCode;
import org.testfx.api.FxRobotInterface;
import utility.Utility;

import java.util.OptionalInt;
import java.util.function.Predicate;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Created by neil on 11/06/2017.
 */
public interface ListUtilTrait extends FxRobotInterface
{
    default <T> void selectGivenListViewItem(final ListView<T> list, final Predicate<T> findItem) {
        OptionalInt firstIndex = Utility.findFirstIndex(list.getItems(), findItem);
        assertTrue("Not found item satisfying predicate", firstIndex.isPresent());
        final int index = firstIndex.getAsInt();
        final int indexSel = list.getSelectionModel().getSelectedIndex();

        clickOn(list);

        if(index > indexSel)
            for(int i = indexSel; i < index; i++)
                type(KeyCode.DOWN);
        else if(index < indexSel)
            for(int i = indexSel; i > index; i--)
                type(KeyCode.UP);
    }
}
