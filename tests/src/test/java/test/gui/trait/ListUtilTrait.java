package test.gui.trait;

import javafx.scene.control.ComboBox;
import javafx.scene.control.ListView;
import javafx.scene.input.KeyCode;
import org.testfx.api.FxRobotInterface;
import test.TestUtil;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.FXPlatformSupplier;
import xyz.columnal.utility.Utility;

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

        clickOn(list);
        push(KeyCode.SPACE);
        sleep(100);
        // If nothing selected, selection will begin when you hit a key:
        FXPlatformSupplier<Integer> cur = () -> TestUtil.fx(() -> list.getSelectionModel().getSelectedIndex());
        
        while (cur.get() < index)
            push(KeyCode.DOWN);
        while (cur.get() > index)
            push(KeyCode.UP);
        
        assertEquals(index, (int)cur.get());
    }
}
