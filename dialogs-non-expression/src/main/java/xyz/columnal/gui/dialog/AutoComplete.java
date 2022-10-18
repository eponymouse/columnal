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

package xyz.columnal.gui.dialog;

import com.google.common.collect.ImmutableList;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.NumberBinding;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableStringValue;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Point2D;
import javafx.geometry.VerticalDirection;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.PopupControl;
import javafx.scene.control.Skin;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.skin.TextFieldSkin;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.web.WebView;
import javafx.stage.Window;
import xyz.columnal.log.Log;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.interning.qual.Interned;
import org.checkerframework.checker.nullness.qual.EnsuresNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.gui.dialog.AutoComplete.Completion;
import xyz.columnal.gui.dialog.AutoComplete.Completion.CompletionContent;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.function.fx.FXPlatformConsumer;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.Utility;
import xyz.columnal.utility.gui.FXUtility;
import xyz.columnal.utility.gui.GUI;
import xyz.columnal.utility.gui.Instruction;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by neil on 17/12/2016.
 */
@OnThread(Tag.FXPlatform)
public class AutoComplete<C extends Completion>
{
    private static final double CELL_HEIGHT = 30.0;
    private final TextField textField;
    private final CompletionListener<C> onSelect;
    private @Nullable AutoCompleteWindow window;
    
    private boolean settingContentDirectly = false;

    public static enum WhitespacePolicy
    {
        ALLOW_ANYWHERE,
        ALLOW_ONE_ANYWHERE_TRIM,
        DISALLOW
    }
    
    public interface CompletionCalculator<C extends Completion>
    {
        Stream<C> calculateCompletions(String textFieldContent) throws InternalException, UserException; 
    }

    /**
     *
     * @param textField
     * @param calculateCompletions Gets completes given the input string and current state
     * @param onSelect The action to take when a completion is selected.
     *                 Should not set the text
     *                 for the text field, but instead return the new text to be set.
     * @param showOnFocus Should we show as soon as we get focused?  If false, we show when
     *                    a character has been typed.
     * @param inNextAlphabet The alphabet of a slot is the set of characters *usually*
     *                       featured.  E.g. for operators it's any characters that
     *                       feature in an operator. For general entry it's the inverse
     *                       of operators.  This predicate checks if the character is in
     *                       the alphabet of the following slot.  If it is, and there's
     *                       no available completions with this character then we pick
     *                       the top one and move to next slot.
     */
    @OnThread(Tag.FXPlatform)
    public AutoComplete(TextField textField, CompletionCalculator<C> calculateCompletions, CompletionListener<C> onSelect, WhitespacePolicy whitespacePolicy)
    {
        this.textField = textField;
        this.onSelect = onSelect;
        

        textField.getStylesheets().add(FXUtility.getStylesheet("autocomplete.css"));
        

        FXUtility.addChangeListenerPlatformNN(textField.focusedProperty(), focused -> {
            if (focused)
            {
                AutoComplete<C> usInit = FXUtility.mouse(this);
                usInit.createWindow();
                usInit.window.updateCompletions(calculateCompletions, textField.getText(), textField.getCaretPosition());
                // We use runAfter because completing previous field can focus us before it has its text
                // in place, so the showOnFocus call returns the wrong value.  The user won't notice
                // big difference if auto complete appears a fraction later, so we don't lose anything:
                FXUtility.runAfter(() -> {
                    Pair<Double, Double> pos = calculatePosition();
                    if (textField.isFocused() && !isShowing() && pos != null)
                    {
                        //Point2D screenTopLeft = textField.localToScreen(new Point2D(0, -1));
                        // TODO see if we can find a useful place to show this:
                        //instruction.show(textField, screenTopLeft.getX(), screenTopLeft.getY());
                        usInit.show(textField, pos.getFirst(), pos.getSecond());
                    }
                });
            }
            else
            {
                // Focus leaving and we are showing:
                if (isShowing() && window != null)
                {
                    // Update completions in case it was recently changed
                    // e.g. they pressed closing bracket and that has been removed and
                    // is causing us to move focus:
                    window.updateCompletions(calculateCompletions, textField.getText(), textField.getCaretPosition());
                    hide();
                }
            }
        });
        
        textField.setOnMouseClicked(e -> {
            // Because the autocomplete is a popup which is technically a different window, there seems to be an annoying
            // JavaFX behaviour.  A click back into the text field (i.e. not the popup window) doesn't delivery the mouse
            // press event to the main window.  But it does deliver the click event, so we watch for a click, and if that
            // finds an unfocused text field, we focus it.  The complication is that by default a click without a press on
            // a text field will select the whole text, so after focusing, we must fix up the caret position to be correct:
            if (!textField.isFocused())
            {
                // TODO in Java 9, move to public hit-test API:
                textField.requestFocus();
                textField.positionCaret(((TextFieldSkin)textField.getSkin()).getIndex(e.getX(), e.getY()).getInsertionIndex());
            }
        });

        FXPlatformConsumer<Object> updatePos = t -> {
            if (window != null)
                window.updatePosition();
        };
        FXUtility.addChangeListenerPlatformNN(textField.localToSceneTransformProperty(), updatePos);
        FXUtility.addChangeListenerPlatformNN(textField.layoutXProperty(), updatePos);
        FXUtility.addChangeListenerPlatformNN(textField.layoutYProperty(), updatePos);
        FXUtility.addChangeListenerPlatformNN(textField.heightProperty(), updatePos);
        
        textField.sceneProperty().addListener(new ChangeListener<@Nullable Scene>()
        {
            // This listens to scene all the time.
            // While scene is non-null, positionListener listens to scene's X/Y position.
            // (Scene X/Y position is position within window, which seems unlikely to change [window decoration etc]
            // but seems correct to listen to it)
            
            // While scene is non-null, the inner listener listens to window:
            private final ChangeListener<@Nullable Window> windowListener = new WindowChangeListener();
            private final ChangeListener<Number> positionListener = new NumberChangeListener();
            
            {
                // Set up initial state:
                Utility.later(this).changed(textField.sceneProperty(), null, textField.getScene());
            }
            
            @Override
            @OnThread(value = Tag.FXPlatform, ignoreParent = true)
            public void changed(ObservableValue<? extends @Nullable Scene> observable, @Nullable Scene oldValue, @Nullable Scene newValue)
            {
                if (oldValue != null)
                {
                    oldValue.xProperty().removeListener(positionListener);
                    oldValue.yProperty().removeListener(positionListener);
                    oldValue.windowProperty().removeListener(windowListener);
                    windowListener.changed(oldValue.windowProperty(), oldValue.getWindow(), null);
                }
                if (newValue != null)
                {
                    newValue.xProperty().addListener(positionListener);
                    newValue.yProperty().addListener(positionListener);
                    newValue.windowProperty().addListener(windowListener);
                    windowListener.changed(newValue.windowProperty(), null, newValue.getWindow());
                }
                // If scene/window has changed, we should update position even if X/Y hasn't changed:
                updatePos.consume("");
            }
        });

        textField.setTextFormatter(new TextFormatter<String>(change -> {
            // We're not interested in changes in just the selection:
            if (!change.isContentChange())
                return change;
            String text = change.getControlNewText();
            
            // Trim everything except single trailing space:
            text = text.trim() + (text.endsWith(" ") ? " " : "");

            int[] codepoints = text.codePoints().toArray();
            updatePos.consume(""); // Just in case
            List<C> available = window == null ? ImmutableList.of() : window.updateCompletions(calculateCompletions, text.trim(), Math.min(text.trim().length(), textField.getCaretPosition()));

            // Show if not already showing:
            Pair<Double, Double> pos = calculatePosition();
            if (textField.isFocused() && !isShowing() && pos != null)
            {
                FXUtility.keyboard(this).show(textField, pos.getFirst(), pos.getSecond());
            }
            
            // Trim spaces:
            text = text.trim();
            
            if (whitespacePolicy == WhitespacePolicy.DISALLOW)
            {
                String originalChangeText = change.getText();
                String withoutWhitespace = originalChangeText.replaceAll("\\s", "");
                change.setText(withoutWhitespace);
                Log.debug("Change pos: " + change.getCaretPosition() + " orig: \"" + originalChangeText + "\" [" + originalChangeText.length() + "] without white: \"" + withoutWhitespace + "\" [" + withoutWhitespace.length() + "]");
                int prospectivePos = change.getCaretPosition() - (originalChangeText.length() - withoutWhitespace.length());
                if (prospectivePos >= 0 && prospectivePos <= change.getControlNewText().length())
                    change.setCaretPosition(prospectivePos);
                return change;
            }
            
            return change;
        }));

        textField.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, e -> {
            if (window != null)
                window.textFieldKeyPressed(e);
        });
    }

    @EnsuresNonNull("window")
    private void show(TextField textField, double x, double y)
    {
        createWindow();
        window.show(textField, x, y);
    }

    @EnsuresNonNull("window")
    private void createWindow()
    {
        if (window == null)
        {
            window = new AutoCompleteWindow();
        }
    }

    public void hide(@UnknownInitialization AutoComplete<C> this)
    {
        final @Nullable AutoCompleteWindow w = window;
        if (w != null)
        {
            w.instruction.hide();
            w.hide();
            window = null;
        }
    }

    private boolean isShowing(@UnknownInitialization AutoComplete<C> this)
    {
        return window != null && window.isShowing();
    }

    public void setContentDirect(String s, boolean moveCaretToEnd)
    {
        settingContentDirectly = true;
        textField.setText(s);
        if (moveCaretToEnd)
            textField.positionCaret(s.length());
        settingContentDirectly = false;
    }

    @OnThread(Tag.FXPlatform)
    @RequiresNonNull({"textField"})
    private @Nullable Pair<Double, Double> calculatePosition(@UnknownInitialization(Object.class) AutoComplete<C> this)
    {
        @Nullable Point2D textToScene = textField.localToScene(0, textField.getHeight());
        @Nullable Scene textFieldScene = textField.getScene();
        if (textToScene == null || textFieldScene == null || textFieldScene == null)
            return null;
        @Nullable Window window = textFieldScene.getWindow();
        if (window == null)
            return null;
        return new Pair<>(
            textToScene.getX() + textFieldScene.getX() + window.getX(),
            textToScene.getY() + textFieldScene.getY() + window.getY()
        );
    }
    
    public abstract static @Interned class Completion
    {
        @OnThread(Tag.FXPlatform)
        protected final class CompletionContent
        {
            private final ObservableStringValue completion;
            private final @Localized String description;

            public CompletionContent(ObservableStringValue completion, @Nullable @Localized String description)
            {
                this.completion = completion;
                this.description = description == null ? Utility.universal("") : description;
            }

            public CompletionContent(String completion, @Nullable @Localized String description)
            {
                this(new ReadOnlyStringWrapper(completion), description);
            }
            
            // Slight hack: use Pair to have an overload that comes pre-localised:
            public CompletionContent(Pair<String, @Localized String> completionAndDescription)
            {
                this.completion = new ReadOnlyStringWrapper(completionAndDescription.getFirst());
                this.description = completionAndDescription.getSecond();
            }
        }
        
        /**
         * Given a property with the latest text, what graphical node and text property
         * should we show for the item?
         */
        @OnThread(Tag.FXPlatform)
        public abstract CompletionContent makeDisplay(ObservableStringValue currentText);

        @OnThread(Tag.FXPlatform)
        public String _test_getContent()
        {
            return makeDisplay(new ReadOnlyStringWrapper("")).completion.get();
        }
        
        /**
         * How should we sort this item?  For functions, leave off brackets.
         * 
         * @param text The current user-entered text (independent of this item)
         */
        @OnThread(Tag.FXPlatform)
        public String getDisplaySortKey(String text)
        {
            return makeDisplay(new ReadOnlyStringWrapper(text)).completion.get();
        }

        /**
         * Gets the URL of the details to show to the right of the list.  If null, nothing
         * is shown to the right.
         */
        @OnThread(Tag.FXPlatform)
        public @Nullable String getFurtherDetailsURL()
        {
            return null;
        }
    }
    
    public static class SimpleCompletion extends Completion
    {
        protected final String completion;
        private final @Nullable @Localized String description;

        public SimpleCompletion(String completion, @Nullable @Localized String description)
        {
            this.completion = completion;
            this.description = description;
        }

        @Override
        public CompletionContent makeDisplay(ObservableStringValue currentText)
        {
            return new CompletionContent(completion, description);
        }

        @Override
        public @Nullable String getFurtherDetailsURL()
        {
            return super.getFurtherDetailsURL();
        }
    }

    private class CompleteCell extends ListCell<C>
    {
        public CompleteCell()
        {
            setMinHeight(CELL_HEIGHT);
            setPrefHeight(CELL_HEIGHT);
            setMaxHeight(CELL_HEIGHT);
            getStyleClass().add("completion-cell");
        }

        @Override
        @OnThread(value = Tag.FXPlatform, ignoreParent = true)
        protected void updateItem(@Nullable C item, boolean empty)
        {
            textProperty().unbind();
            if (empty || item == null)
            {
                setGraphic(null);
                setText("");
            }
            else
            {
                CompletionContent cc = item.makeDisplay(textField.textProperty());
                setGraphic(GUI.labelRaw(cc.description, "completion-cell-description"));
                textProperty().bind(cc.completion);
            }
            super.updateItem(item, empty);
        }
    }

    public static interface CompletionListener<C extends Completion>
    {
        // Item was double-clicked in the list
        // Returns the new text for the textfield, or null if keep as-is
        @Nullable String doubleClick(String currentText, C selectedItem);

        // Enter or Tab used to select
        // Returns the new text for the textfield, or null if keep as-is
        @Nullable String keyboardSelect(String textBeforeCaret, String textAfterCaret, @Nullable C selectedItem, boolean wasTab);
    }

    // For reasons I'm not clear about, this listener needs to be its own class, not anonymous:
    private class NumberChangeListener implements ChangeListener<Number>
    {
        @OnThread(Tag.FXPlatform)
        public NumberChangeListener()
        {
        }

        @Override
        @OnThread(value = Tag.FXPlatform, ignoreParent = true)
        public void changed(ObservableValue<? extends Number> prop, Number oldVal, Number newVal)
        {
            if (window != null)
                window.updatePosition();
        }
    }

    private class WindowChangeListener implements ChangeListener<@Nullable Window>
    {
        @OnThread(Tag.FXPlatform)
        final ChangeListener<Number> positionListener;

        @OnThread(Tag.FXPlatform)
        private WindowChangeListener()
        {
            positionListener = new NumberChangeListener();
        }

        @Override
        @OnThread(value = Tag.FXPlatform, ignoreParent = true)
        public void changed(ObservableValue<? extends @Nullable Window> prop, @Nullable Window oldValue, @Nullable Window newValue)
        {
            if (oldValue != null)
            {
                oldValue.xProperty().removeListener(positionListener);
                oldValue.yProperty().removeListener(positionListener);
            }
            if (newValue != null)
            {
                newValue.xProperty().addListener(positionListener);
                newValue.yProperty().addListener(positionListener);
            }
            // If scene/window has changed, we should update position even if X/Y hasn't changed:
            if (window != null)
                window.updatePosition();
        }
    }
    
    // public for testing purposes only
    @OnThread(Tag.FXPlatform)
    public class AutoCompleteWindow extends PopupControl
    {
        private final ListView<C> completions;
        private final BorderPane container;
        private final Instruction instruction;
        private final NumberBinding webViewHeightBinding;
        
        // TO prevent memory leaks, it's important that this
        // class adds no bindings to external elements, as we want
        // to allow this class to be GCed once hidden.
        public AutoCompleteWindow()
        {
            // Disable autofix so that the popup doesn't get moved to cover up text field:
            setAutoFix(false);
            this.instruction = new Instruction("autocomplete.instruction", "autocomplete-instruction");
            this.completions = new ListView<C>() {
                @Override
                @OnThread(Tag.FX)
                public void requestFocus()
                {
                    // Can't be focused
                }
            };
            completions.getStyleClass().add("autocomplete");
            completions.setPrefWidth(400.0);
            container = new BorderPane(null, null, null, null, completions);
            container.addEventFilter(MouseEvent.MOUSE_CLICKED, e -> {
                if (e.getButton() == MouseButton.MIDDLE)
                {
                    hide();
                    instruction.hide();
                    e.consume();
                }
            });
            
            webViewHeightBinding = Bindings.max(300.0f, completions.heightProperty());
            

            FXUtility.listen(completions.getItems(), change -> {
                FXUtility.runAfter(() -> updateHeight(completions));
            });
            updateHeight(completions);

            completions.getStylesheets().add(FXUtility.getStylesheet("autocomplete.css"));

            completions.setCellFactory(lv -> {
                return new CompleteCell();
            });
            completions.setOnMouseClicked(e -> {
                @Nullable C selectedItem = completions.getSelectionModel().getSelectedItem();
                if (e.getClickCount() == 2 && e.getButton() == MouseButton.PRIMARY && selectedItem != null)
                {
                    @Nullable String newContent = onSelect.doubleClick(textField.getText(), selectedItem);
                    if (newContent != null)
                        setContentDirect(newContent, true);
                    hide();
                    instruction.hide();
                }
            });

            FXUtility.addChangeListenerPlatform(completions.getSelectionModel().selectedItemProperty(), selected -> {
                if (selected != null)
                {
                    @Nullable String url = selected.getFurtherDetailsURL();
                    if (url != null)
                    {
                        WebView webView = new WebView();
                        webView.setPrefWidth(400.0);
                        webView.prefHeightProperty().bind(webViewHeightBinding);
                        webView.getEngine().load(url);
                        container.setCenter(webView);
                    }
                    else
                    {
                        container.setCenter(null);
                    }
                }
                else
                {
                    container.setCenter(null);
                }
            });

            setSkin(new AutoCompleteSkin());

            // We do the hiding on escape and auto-hide:
            setHideOnEscape(false);
            setAutoHide(false);
            instruction.setHideOnEscape(false);
            instruction.setAutoHide(false);
        }

        public void textFieldKeyPressed(KeyEvent e)
        {
            if (e.getCode() == KeyCode.ESCAPE && isShowing())
            {
                hide();
                instruction.hide();
            }
            final int PAGE = 9;
            int oldSel = completions.getSelectionModel().getSelectedIndex();
            if (e.getCode() == KeyCode.UP || e.getCode() == KeyCode.PAGE_UP)
            {
                if (oldSel <= 0)
                {
                    completions.getSelectionModel().clearSelection();
                }
                else if (e.getCode() == KeyCode.UP)
                {
                    completions.getSelectionModel().select(oldSel - 1);
                    FXUtility.ensureSelectionInView(completions, VerticalDirection.UP);
                }
                else if (e.getCode() == KeyCode.PAGE_UP)
                {
                    completions.getSelectionModel().select(
                            Math.max(0, oldSel - PAGE)
                    );
                    FXUtility.ensureSelectionInView(completions, VerticalDirection.UP);
                }
                e.consume();
            }
            else if (e.getCode() == KeyCode.DOWN)
            {
                if (oldSel < completions.getItems().size() - 1)
                {
                    completions.getSelectionModel().select(oldSel + 1);
                    if (oldSel > 10)
                        oldSel = oldSel;
                    FXUtility.ensureSelectionInView(completions, VerticalDirection.DOWN);
                }
                e.consume();
            }
            else if (e.getCode() == KeyCode.PAGE_DOWN)
            {
                completions.getSelectionModel().select(
                        Math.min(completions.getItems().size() - 1, oldSel + PAGE)
                );
                FXUtility.ensureSelectionInView(completions, VerticalDirection.DOWN);
                e.consume();
            }

            if (e.getCode() == KeyCode.ENTER || e.getCode() == KeyCode.TAB)
            {
                C selectedItem = completions.getSelectionModel().getSelectedItem();
                if (isShowing())
                {
                    e.consume();
                    String curText = textField.getText();
                    @Nullable String newContent = onSelect.keyboardSelect(curText.substring(0, textField.getCaretPosition()), curText.substring(textField.getCaretPosition()), selectedItem, e.getCode() == KeyCode.TAB);
                    if (newContent != null)
                        setContentDirect(newContent, true);
                    hide();
                    instruction.hide();
                }
            }
        }

        private void updateHeight(@UnknownInitialization(Window.class) AutoCompleteWindow this, ListView<?> completions)
        {
            // Merging several answers from https://stackoverflow.com/questions/17429508/how-do-you-get-javafx-listview-to-be-the-height-of-its-items
            double itemHeight = CELL_HEIGHT;
            completions.setPrefHeight(Math.min(300.0, 2 + itemHeight * completions.getItems().size()));
            completions.setVisible(!completions.getItems().isEmpty());
            sizeToScene();
        }

        @OnThread(Tag.FXPlatform)
        private void updatePosition(@UnknownInitialization(PopupControl.class) AutoCompleteWindow this)
        {
            if (isShowing() && textField != null)
            {
                @Nullable Pair<Double, Double> pos = calculatePosition();
                if (pos != null)
                {
                    setAnchorX(pos.getFirst());
                    setAnchorY(pos.getSecond());
                }
            }
        }

        private List<C> updateCompletions(CompletionCalculator<C> calculateCompletions, String text, int caretPos)
        {
            try
            {
                List<C> calculated = calculateCompletions.calculateCompletions(text)
                        .sorted(Comparator.comparing((C c) -> c.getDisplaySortKey(text)))
                        .collect(Collectors.<C>toList());
                this.completions.getItems().setAll(calculated);
            }
            catch (InternalException | UserException e)
            {
                Log.log(e);
                this.completions.getItems().clear();
            }
            return this.completions.getItems();
        }
        
        @OnThread(Tag.FXPlatform)
        public @Nullable String _test_getSelectedContent()
        {
            C selected = completions.getSelectionModel().getSelectedItem();
            if (selected != null)
                return selected.makeDisplay(new ReadOnlyStringWrapper(textField.getText())).completion.get();
            else
                return null;
        }

        private class AutoCompleteSkin implements Skin<AutoCompleteWindow>
        {
            @Override
            @OnThread(Tag.FX)
            public AutoCompleteWindow getSkinnable()
            {
                return AutoCompleteWindow.this;
            }

            @Override
            @OnThread(Tag.FX)
            public Node getNode()
            {
                return container;
            }

            @Override
            @OnThread(Tag.FX)
            public void dispose()
            {
            }
        }
    }
}
