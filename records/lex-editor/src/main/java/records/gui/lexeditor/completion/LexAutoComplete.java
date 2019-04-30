package records.gui.lexeditor.completion;

import annotation.units.CanonicalLocation;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.binding.ObjectExpression;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableMap;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.scene.input.MouseButton;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Region;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.util.Duration;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.gui.lexeditor.EditorDisplay;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformConsumer;
import utility.Pair;
import utility.Utility;
import utility.gui.FXUtility;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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
