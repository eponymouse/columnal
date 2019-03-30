package records.gui.lexeditor;

import com.google.common.collect.ImmutableList;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.control.ListView;
import javafx.scene.control.PopupControl;
import javafx.scene.control.Skin;
import javafx.scene.control.Skinnable;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.Nullable;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Utility;

@OnThread(Tag.FXPlatform)
public class LexAutoComplete
{
    private final LexAutoCompleteWindow window = new LexAutoCompleteWindow();
    private final EditorDisplay editor;

    public LexAutoComplete(@UnknownInitialization EditorDisplay editor)
    {
        this.editor = Utility.later(editor);
    }

    public void show(ImmutableList<LexCompletion> completions)
    {
        window.setCompletions(completions);
        Point2D caretBottom = editor.getCaretBottomOnScreen();
        window.show(editor, caretBottom.getX(), caretBottom.getY());
    }
    
    public void hide()
    {
        window.hide();
        window.setCompletions(ImmutableList.of());
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

    public static class LexCompletion
    {
        private final String content;

        public LexCompletion(String content)
        {
            this.content = content;
        }
    }

    @OnThread(Tag.FXPlatform)
    public class LexAutoCompleteWindow extends PopupControl
    {
        private final ListView<LexCompletion> listView;
        
        public LexAutoCompleteWindow()
        {
            this.listView = new ListView<>();
            setAutoFix(false);
            setHideOnEscape(false);
            setSkin(new LexAutoCompleteSkin());
        }

        public void setCompletions(ImmutableList<LexCompletion> completions)
        {
            this.listView.getItems().setAll(completions);
        }

        @OnThread(Tag.FXPlatform)
        public @Nullable String _test_getSelectedContent()
        {
            return Utility.onNullable(listView.getSelectionModel().getSelectedItem(), l -> l.content);
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
                return listView;
            }

            @Override
            public void dispose()
            {

            }
        }
    }

    
}
