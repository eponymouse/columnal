package records.gui.lexeditor;

import annotation.units.SourceLocation;
import com.google.common.collect.ImmutableList;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.PopupControl;
import javafx.scene.control.Skin;
import javafx.scene.control.Skinnable;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.Nullable;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformRunnable;
import utility.Pair;
import utility.Utility;
import utility.gui.FXUtility;

import java.util.List;
import java.util.Optional;

@OnThread(Tag.FXPlatform)
public class LexAutoComplete
{
    private final LexAutoCompleteWindow window = new LexAutoCompleteWindow();
    private final EditorDisplay editor;
    private final Timeline updatePosition;
    private final FXPlatformRunnable triggerCompletion;

    public LexAutoComplete(@UnknownInitialization EditorDisplay editor, FXPlatformRunnable triggerCompletion)
    {
        this.editor = Utility.later(editor);
        this.triggerCompletion = triggerCompletion;
        this.updatePosition = new Timeline(new KeyFrame(Duration.millis(250), e -> {
            Utility.later(this).updateWindowPosition(Utility.later(this).window.listView.getItems());
        }));
    }

    public void show(List<LexCompletion> completions)
    {
        window.setCompletions(completions);
        updateWindowPosition(completions);
        FXUtility.runAfterNextLayout(() -> updateWindowPosition(completions));
        updatePosition.playFromStart();
    }

    private void updateWindowPosition(List<LexCompletion> completions)
    {
        Point2D caretBottom = editor.getCaretBottomOnScreen(completions.stream().mapToInt(c -> c.startPos).min().orElse(editor.getCaretPosition()));
        ListCell listCell = (ListCell) window.listView.lookup(".list-cell");
        double labelPad = listCell == null ? 0 : listCell.getPadding().getLeft();
        if (caretBottom != null && !completions.isEmpty())
            window.show(editor, caretBottom.getX() - labelPad - 1 /* window border */, caretBottom.getY());
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
        int sel = window.listView.getSelectionModel().getSelectedIndex();
        if (sel + 1 < window.listView.getItems().size())
            window.listView.getSelectionModel().select(sel + 1);
    }

    public void up()
    {
        int sel = window.listView.getSelectionModel().getSelectedIndex();
        if (sel - 1 >= 0)
            window.listView.getSelectionModel().select(sel - 1);
        else
            window.listView.getSelectionModel().clearSelection();
    }

    public Optional<LexCompletion> selectCompletion()
    {
        return Optional.ofNullable(window.listView.getSelectionModel().getSelectedItem());
    }
    
    public enum LexSelectionBehaviour
    {
        SELECT_IF_ONLY,
        SELECT_IF_TOP,
        NO_AUTO_SELECT;
    }

    public static class LexCompletion
    {
        public final @SourceLocation int startPos;
        public final String content;
        public final int relativeCaretPos;
        public final LexSelectionBehaviour selectionBehaviour;

        public LexCompletion(@SourceLocation int startPos, String content, int relativeCaretPos, LexSelectionBehaviour selectionBehaviour)
        {
            this.startPos = startPos;
            this.content = content;
            this.relativeCaretPos = relativeCaretPos;
            this.selectionBehaviour = selectionBehaviour;
        }

        public LexCompletion(@SourceLocation int startPos, String content, int relativeCaretPos)
        {
            this(startPos, content, relativeCaretPos, LexSelectionBehaviour.NO_AUTO_SELECT);
        }

        public LexCompletion(@SourceLocation int startPos, String content)
        {
            this(startPos, content, content.length());
        }

        public LexCompletion(@SourceLocation int startPos, String content, LexSelectionBehaviour selectionBehaviour)
        {
            this(startPos, content, content.length(), selectionBehaviour);
        }

        // Used by ListView to display content:
        @Override
        public String toString()
        {
            return content;
        }
    }

    @OnThread(Tag.FXPlatform)
    public class LexAutoCompleteWindow extends PopupControl
    {
        private final Pane pane;
        private final ListView<LexCompletion> listView;
        
        public LexAutoCompleteWindow()
        {
            this.listView = new ListView<LexCompletion>() {
                @Override
                @OnThread(value = Tag.FXPlatform, ignoreParent = true)
                public void requestFocus()
                {
                    // Can't be focused
                }
            };
            listView.setOnMouseClicked(e -> {
                if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2)
                {
                    @Nullable LexCompletion selected = listView.getSelectionModel().getSelectedItem();
                    if (selected != null)
                    {
                        triggerCompletion.run();
                    }
                }
            });
            this.pane = new BorderPane(listView);
            setAutoFix(false);
            setAutoHide(false);
            setHideOnEscape(false);
            setSkin(new LexAutoCompleteSkin());
            if (getScene() != null)
            {
                getScene().addEventFilter(MouseEvent.MOUSE_CLICKED, e -> {
                    if (e.getButton() == MouseButton.MIDDLE)
                    {
                        hide();
                        e.consume();
                    }
                });
            }
        }

        public void setCompletions(List<LexCompletion> completions)
        {
            this.listView.getItems().setAll(completions);
            if ((completions.size() == 1 && completions.get(0).selectionBehaviour == LexSelectionBehaviour.SELECT_IF_ONLY)
                || (completions.size() >= 1 && completions.get(0).selectionBehaviour == LexSelectionBehaviour.SELECT_IF_TOP))
            {
                listView.getSelectionModel().select(0);
            }
        }

        @OnThread(Tag.FXPlatform)
        public @Nullable String _test_getSelectedContent()
        {
            return Utility.onNullable(listView.getSelectionModel().getSelectedItem(), l -> l.content);
        }

        public List<LexCompletion> _test_getShowing()
        {
            return listView.getItems();
        }

        @OnThread(Tag.FX)
        private class LexAutoCompleteSkin implements Skin<LexAutoCompleteWindow>
        {
            @Override
            public LexAutoCompleteWindow getSkinnable()
            {
                return LexAutoCompleteWindow.this;
            }

            @Override
            public Node getNode()
            {
                return pane;
            }

            @Override
            public void dispose()
            {

            }
        }
    }

    
}
