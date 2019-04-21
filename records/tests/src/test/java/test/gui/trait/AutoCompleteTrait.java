package test.gui.trait;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.scene.control.ListView;
import javafx.scene.input.KeyCode;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.Assert;
import org.testfx.api.FxRobotInterface;
import records.gui.expressioneditor.AutoComplete.Completion;
import test.TestUtil;
import utility.Utility;

import java.util.Objects;

public interface AutoCompleteTrait extends FxRobotInterface
{
    // Completes in LexCompletionWindow
    public default void lexComplete(String completion)
    {
        ListView<?> listView = lookup(".lex-completion-listview").query();
        int itemCount = TestUtil.fx(() -> listView.getItems().size());
        for (int i = TestUtil.fx(() -> listView.getSelectionModel().getSelectedIndex()); i < itemCount; i++)
        {
            push(KeyCode.DOWN);
            if (Objects.equals(TestUtil.<@Nullable String>fx(() -> Utility.onNullable(listView.getSelectionModel().getSelectedItem(), x -> x.toString())), completion))
            {
                push(KeyCode.ENTER);
                return;
            }
        }
    }

    public default void autoComplete(String completion, boolean useTab)
    {
        ListView<Completion> listView = lookup(".autocomplete").query();
        int itemCount = TestUtil.fx(() -> listView.getItems().size());
        for (int i = TestUtil.fx(() -> listView.getSelectionModel().getSelectedIndex()); i < itemCount; i++)
        {
            push(KeyCode.DOWN);
            if (Objects.equals(TestUtil.<@Nullable String>fx(() -> Utility.onNullable(listView.getSelectionModel().getSelectedItem(), x -> x._test_getContent())), completion))
            {
                push(useTab ? KeyCode.TAB : KeyCode.ENTER);
                return;
            }
        }
        Assert.fail("Could not find item: " + completion);
    }
}
