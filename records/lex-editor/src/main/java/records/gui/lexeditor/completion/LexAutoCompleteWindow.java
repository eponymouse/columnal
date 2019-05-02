package records.gui.lexeditor.completion;

import com.google.common.collect.ImmutableList;
import javafx.scene.Node;
import javafx.scene.control.PopupControl;
import javafx.scene.control.Skin;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.web.WebView;
import log.Log;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.gui.lexeditor.completion.LexCompletionList;
import records.gui.lexeditor.completion.LexAutoComplete.LexSelectionBehaviour;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformConsumer;
import utility.Pair;
import utility.Utility;
import utility.gui.FXUtility;

import java.net.URL;
import java.util.List;


// public for testing purposes
@OnThread(Tag.FXPlatform)
public class LexAutoCompleteWindow extends PopupControl
{
    private final HBox pane;
    final LexCompletionList listView;
    
    public LexAutoCompleteWindow(FXPlatformConsumer<LexCompletion> triggerCompletion)
    {
        this.listView = new LexCompletionList(triggerCompletion);
        WebView webView = new WebView();
        webView.setFocusTraversable(false);
        webView.setPrefWidth(400.0);
        webView.setVisible(false);
        listView.setMaxHeight(400.0);
        webView.setMaxHeight(400.0);
        this.pane = new HBox(listView, webView);
        pane.setFillHeight(false);
        pane.setFocusTraversable(false);
        
        setAutoFix(false);
        setAutoHide(false);
        setHideOnEscape(false);
        setSkin(new LexAutoCompleteSkin());
        pane.getStylesheets().add(FXUtility.getStylesheet("autocomplete.css"));
        pane.getStyleClass().add("lex-complete-root");
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
        FXUtility.addChangeListenerPlatform(listView.selectedItemProperty(), selected -> {
            if (selected != null)
            {
                @Nullable Pair<String, @Nullable String> fileNameAndAnchor = selected.furtherDetailsURL;
                if (fileNameAndAnchor != null)
                {
                    URL url = getClass().getResource("/" + fileNameAndAnchor.getFirst());
                    if (url != null)
                    {
                        webView.getEngine().load(url.toExternalForm() + (fileNameAndAnchor.getSecond() != null ? "#" + fileNameAndAnchor.getSecond() : ""));
                        webView.setVisible(true);
                    }
                    else
                    {
                        Log.error("Missing file: " + fileNameAndAnchor.getFirst());
                        webView.setVisible(false);
                    }
                }
                else
                    webView.setVisible(false);
            }
            else
                webView.setVisible(false);
        });
    }

    public void setCompletions(ImmutableList<LexCompletionGroup> groups)
    {
        this.listView.setCompletions(groups);
        if ((groups.size() >= 1 && groups.get(0).completions.size() == 1 && groups.get(0).completions.get(0).selectionBehaviour == LexSelectionBehaviour.SELECT_IF_ONLY)
            || (groups.size() >= 1 && groups.get(0).completions.size() >= 1 && groups.get(0).completions.get(0).selectionBehaviour == LexSelectionBehaviour.SELECT_IF_TOP))
        {
            listView.select(new Pair<>(0, 0));
        }
    }

    @OnThread(Tag.FXPlatform)
    public @Nullable String _test_getSelectedContent()
    {
        LexCompletion selectedItem = listView.getSelectedItem();
        if (selectedItem == null)
            return null;
        else
            return selectedItem.content;
    }

    public ImmutableList<LexCompletion> _test_getShowing()
    {
        return listView.getItems().collect(ImmutableList.<LexCompletion>toImmutableList());
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
