package records.gui.lexeditor.completion;

import com.google.common.collect.ImmutableList;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Point2D;
import javafx.util.Duration;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import records.gui.lexeditor.EditorDisplay;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformConsumer;
import utility.Utility;
import utility.gui.FXUtility;

import java.util.List;
import java.util.Optional;

@OnThread(Tag.FXPlatform)
public class LexAutoComplete
{
    private final LexAutoCompleteWindow window;
    private final EditorDisplay editor;
    private final Timeline updatePosition;

    public LexAutoComplete(@UnknownInitialization EditorDisplay editor, FXPlatformConsumer<LexCompletion> triggerCompletion)
    {
        this.window = new LexAutoCompleteWindow(triggerCompletion);
        this.editor = Utility.later(editor);
        this.updatePosition = new Timeline(new KeyFrame(Duration.millis(250), e -> {
            Utility.later(this).updateWindowPosition(Utility.later(this).window.listView.getItems().collect(ImmutableList.<LexCompletion>toImmutableList()));
        }));
    }

    public void show(ImmutableList<LexCompletionGroup> groups)
    {
        window.setCompletions(groups);
        FXUtility.runAfterNextLayout(() -> updateWindowPosition(window.listView.getItems().collect(ImmutableList.<LexCompletion>toImmutableList())));
        updatePosition.playFromStart();
    }

    private void updateWindowPosition(List<LexCompletion> completions)
    {
        @SuppressWarnings("units") // Because it passes through IntStream
        Point2D caretBottom = editor.getCaretBottomOnScreen(completions.stream().mapToInt(c -> c.startPos).min().orElse(editor.getCaretPosition()));
        double labelPad = window.listView.getTotalTextLeftPad() + 1;
        if (caretBottom != null && !completions.isEmpty())
            window.show(editor, caretBottom.getX() - labelPad, caretBottom.getY());
        else
            hide();
    }

    public void hide()
    {
        window.hide();
        window.setCompletions(ImmutableList.of());
        updatePosition.stop();
    }

    public boolean isShowing()
    {
        return window.isShowing();
    }

    public void down()
    {
        window.listView.down();
    }

    public void up()
    {
        window.listView.up();
    }

    public void pageDown()
    {
        window.listView.pageDown();
    }

    public void pageUp()
    {
        window.listView.pageUp();
    }

    public Optional<LexCompletion> getSelectedCompletion()
    {
        return Optional.ofNullable(window.listView.getSelectedItem());
    }

    public enum LexSelectionBehaviour
    {
        SELECT_IF_ONLY,
        SELECT_IF_TOP,
        NO_AUTO_SELECT;
    }

}
