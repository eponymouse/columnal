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
import utility.Utility;
import utility.gui.FXUtility;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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
            Utility.later(this).updateWindowPosition(Utility.later(this).window.listView.getItems());
        }));
    }

    public void show(ImmutableList<LexCompletion> completions)
    {
        window.setCompletions(completions);
        FXUtility.runAfterNextLayout(() -> updateWindowPosition(completions));
        updatePosition.playFromStart();
    }

    private void updateWindowPosition(List<LexCompletion> completions)
    {
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
        int sel = window.listView.getSelectedIndex();
        if (sel + 1 < window.listView.getItems().size())
            window.listView.select(sel + 1);
    }

    public void up()
    {
        int sel = window.listView.getSelectedIndex();
        if (sel - 1 >= 0)
            window.listView.select(sel - 1);
        else
            window.listView.select(-1);
    }

    public Optional<LexCompletion> getSelectedCompletion()
    {
        return Optional.ofNullable(window.listView.getSelectedItem());
    }

    /**
     * Check if the source snippet is a stem of any (space-separated) word in the completion text.  If so, return a
     * corresponding completion, else return empty.
     * 
     * e.g. matchWordStart("te", 34, "from text to")
     * would return Optional.of(new LexCompletion(34, "from text to"))
     * 
     * @param src the source content.  If null, automatically match
     * @param startPos the start position to feed to the completion constructor
     * @param completionText The completion text
     * @return
     */
    public static Optional<LexCompletion> matchWordStart(@Nullable String src, @CanonicalLocation int startPos, String completionText)
    {
        if (src == null)
            return Optional.of(new LexCompletion(startPos, completionText));
        
        int curCompletionStart = 0;
        do
        {
            if (Utility.startsWithIgnoreCase(completionText, src, curCompletionStart))
            {
                return Optional.of(new LexCompletion(startPos, completionText));
            }
            curCompletionStart = completionText.indexOf(' ', curCompletionStart);
            if (curCompletionStart >= 0)
                curCompletionStart += 1;
        }
        while (curCompletionStart >= 0);
        
        return Optional.empty();
    }
    
    public enum LexSelectionBehaviour
    {
        SELECT_IF_ONLY,
        SELECT_IF_TOP,
        NO_AUTO_SELECT;
    }

}
