/*
 * Columnal: Safer, smoother data table processing.
 * Copyright (c) Neil Brown, 2016-2020, 2022.
 *
 * This file is part of Columnal.
 *
 * Columnal is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 *
 * Columnal is distributed in the hope that it will be useful, but WITHOUT 
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or 
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for 
 * more details.
 *
 * You should have received a copy of the GNU General Public License along 
 * with Columnal. If not, see <https://www.gnu.org/licenses/>.
 */

package xyz.columnal.gui.lexeditor.completion;

import annotation.units.CanonicalLocation;
import com.google.common.collect.ImmutableList;
import javafx.scene.Node;
import javafx.scene.control.PopupControl;
import javafx.scene.control.Skin;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.web.WebView;
import javafx.stage.Screen;
import xyz.columnal.log.Log;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.w3c.dom.events.EventTarget;
import xyz.columnal.gui.lexeditor.completion.LexAutoComplete.LexSelectionBehaviour;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.function.fx.FXPlatformRunnable;
import xyz.columnal.utility.function.fx.FXPlatformSupplier;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.ResourceUtility;
import xyz.columnal.utility.gui.FXUtility;

import java.net.URL;
import java.util.ArrayList;
import java.util.function.Consumer;


// public for testing purposes
@OnThread(Tag.FXPlatform)
public final class LexAutoCompleteWindow extends PopupControl
{
    private final HBox pane;
    final LexCompletionList listView;
    private final ArrayList<FXPlatformRunnable> removeWebViewListeners = new ArrayList<>();
    private final WebView webView;

    public LexAutoCompleteWindow(LexCompletionListener triggerCompletion)
    {
        this.listView = new LexCompletionList(triggerCompletion);
        webView = FXUtility.makeOrReuseWebView();
        webView.setFocusTraversable(false);
        removeWebViewListeners.add(FXUtility.addChangeListenerPlatformNN(webView.visibleProperty(), vis -> {
            webView.managedProperty().set(vis);
            sizeToScene();
        }));
        webView.setPrefWidth(Screen.getPrimary().getBounds().getWidth() >= 1200 ? 500.0 : 350.0);
        webView.setVisible(false);
        listView.setMaxHeight(400.0);
        webView.setMaxHeight(400.0);
        BorderPane webViewWrapper = new BorderPane(webView);
        webViewWrapper.getStyleClass().add("lex-webview-wrapper");
        this.pane = new HBox(listView, webViewWrapper);
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
            if (selected != null && selected.furtherDetails != null)
            {
                selected.furtherDetails.either_(htmlContent -> {
                    webView.getEngine().loadContent(htmlContent);
                    webView.setVisible(true);
                }, new Consumer<Pair<String, @Nullable String>>()
                {
                    @Override
                    public void accept(Pair<String, @Nullable String> fileNameAndAnchor)
                    {
                        URL url = ResourceUtility.getResource(fileNameAndAnchor.getFirst());
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
                });
            }
            else
                webView.setVisible(false);
        });
        removeWebViewListeners.add(FXUtility.addChangeListenerPlatform(webView.getEngine().documentProperty(), webViewDoc -> enableInsertLinks(webViewDoc, triggerCompletion, () -> {
            LexCompletion lexCompletion = listView.getSelectedItem();
            if (lexCompletion != null)
            {
                return lexCompletion.startPos;
            }
            else
                return null;
        })));
    }
    
    public static void enableInsertLinks(@Nullable Document doc, InsertListener insertListener, FXPlatformSupplier<@Nullable @CanonicalLocation Integer> getInsertPosition)
    {
        if (doc != null)
        {
            if (doc.getDocumentElement() != null)
                doc.getDocumentElement().setAttribute("class", "autocomplete");
            
            // First find the anchors.
            NodeList spans = doc.getElementsByTagName("span");
            for (int i = 0; i < spans.getLength(); i++)
            {
                org.w3c.dom.Node span = spans.item(i);
                if (span == null || span.getAttributes() == null || !(span instanceof Element))
                    continue;

                Element element = (Element) span;
                String spanClass = element.getAttribute("class");
                String insert = element.getAttribute("data-insert");
                if (spanClass != null && spanClass.contains("insertable-expression"))
                {
                    element.setAttribute("title", "Click to insert into editor");
                    ((EventTarget) span).addEventListener("click", e ->
                    {
                        insertListener.insert(getInsertPosition.get(), insert);
                        e.stopPropagation();
                    }, true);
                }
            }
        }
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

    public void cleanup()
    {
        removeWebViewListeners.forEach(FXPlatformRunnable::run);
        FXUtility.returnWebViewForReuse(webView);
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
