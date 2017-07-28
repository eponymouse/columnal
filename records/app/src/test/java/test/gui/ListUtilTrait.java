package test.gui;

import javafx.scene.control.ComboBox;
import javafx.scene.control.ListView;
import javafx.scene.input.KeyCode;
import org.testfx.api.FxRobotInterface;
import test.TestUtil;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Utility;

import java.util.OptionalInt;
import java.util.function.Predicate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Created by neil on 11/06/2017.
 */
public interface ListUtilTrait extends FxRobotInterface
{
    @OnThread(Tag.Any)
    default <T> void selectGivenListViewItem(final ListView<T> list, final Predicate<T> findItem) {
        OptionalInt firstIndex = TestUtil.fx(() -> Utility.findFirstIndex(list.getItems(), findItem));
        assertTrue("Not found item satisfying predicate", firstIndex.isPresent());
        final int index = firstIndex.getAsInt();
        int existingIndex = TestUtil.fx(() -> list.getSelectionModel().getSelectedIndex());

        // If nothing selected, selection will begin when you hit a key:
        if (existingIndex < 0)
            existingIndex = 0;

        clickOn(list);

        if(index > existingIndex)
        {
            for (int i = existingIndex; i < index; i++)
                type(KeyCode.DOWN);
        }
        else if (index < existingIndex)
        {
            for (int i = existingIndex; i > index; i--)
                type(KeyCode.UP);
        }

        assertEquals(index, (int)TestUtil.fx(() -> list.getSelectionModel().getSelectedIndex()));
    }
}
