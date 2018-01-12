package records.gui.expressioneditor;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableStringValue;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.PopupControl;
import javafx.scene.control.Skin;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.stage.Window;
import log.Log;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.interning.qual.Interned;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;
import records.error.InternalException;
import records.error.UserException;
import records.gui.expressioneditor.AutoComplete.Completion.CompletionAction;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.ExBiFunction;
import utility.Pair;
import utility.gui.FXUtility;

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

/**
 * Created by neil on 17/12/2016.
 */
@OnThread(Tag.FXPlatform)
public class AutoComplete extends PopupControl
{
    private final TextField textField;
    private final ListView<Completion> completions;
    private BorderPane container;

    public static enum CompletionQuery
    {
        // They've entered another char which doesn't fit, we're planning to leave the slot now:
        LEAVING_SLOT,
        // They've entered a char which does fit, so we're currently planning to stay in the slot:
        CONTINUED_ENTRY;
    }

    /**
     *
     * @param textField
     * @param calculateCompletions Gets completes given the input string and current state
     * @param onSelect The action to take when a completion is selected.
     *                 Should not set the text
     *                 for the text field, but instead return the new text to be set.
     * @param inNextAlphabet The alphabet of a slot is the set of characters *usually*
     *                       featured.  E.g. for operators it's any characters that
     *                       feature in an operator. For general entry it's the inverse
     *                       of operators.  This predicate checks if the character is in
     *                       the alphabet of the following slot.  If it is, and there's
     *                       no available completions with this character then we pick
     *                       the top one and move to next slot.
     */
    public AutoComplete(TextField textField, ExBiFunction<String, CompletionQuery, List<Completion>> calculateCompletions, CompletionListener onSelect, Predicate<Character> inNextAlphabet)
    {
        this.textField = textField;
        this.completions = new ListView<>();
        container = new BorderPane(null, null, null, null, completions);

        completions.getStylesheets().add(FXUtility.getStylesheet("autocomplete.css"));

        completions.setCellFactory(lv -> {
            return new CompleteCell();
        });
        completions.setOnMouseClicked(e -> {
            @Nullable Completion selectedItem = completions.getSelectionModel().getSelectedItem();
            if (e.getClickCount() == 2 && e.getButton() == MouseButton.PRIMARY && selectedItem != null)
            {
                textField.setText(onSelect.doubleClick(textField.getText(), selectedItem));
            }
        });

        FXUtility.addChangeListenerPlatform(completions.getSelectionModel().selectedItemProperty(), selected -> {
            if (selected != null)
            {
                @Nullable Node furtherDetails = selected.getFurtherDetails();
                container.setCenter(furtherDetails);
            }
            else
            {
                container.setCenter(null);
            }
        });

        setSkin(new AutoCompleteSkin());

        FXUtility.addChangeListenerPlatformNN(textField.focusedProperty(), focused -> {
            if (focused)
            {
                Pair<Double, Double> pos = calculatePosition();
                updateCompletions(calculateCompletions, textField.getText());
                if (!isShowing() && pos != null)
                    show(textField, pos.getFirst(), pos.getSecond());
            }
            else
            {
                if (isShowing())
                {
                    Completion completion = getCompletionIfFocusLeftNow();
                    if (completion != null)
                    {
                        textField.setText(onSelect.focusLeaving(textField.getText(), completion));
                    }
                    hide();
                }
            }
        });

        FXUtility.addChangeListenerPlatformNN(textField.localToSceneTransformProperty(), t -> updatePosition());
        FXUtility.addChangeListenerPlatformNN(textField.layoutXProperty(), t -> updatePosition());
        FXUtility.addChangeListenerPlatformNN(textField.layoutYProperty(), t -> updatePosition());
        FXUtility.addChangeListenerPlatformNN(textField.heightProperty(), t -> updatePosition());
        
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
                changed(textField.sceneProperty(), null, textField.getScene());
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
                updatePosition();
            }
        });

        textField.setTextFormatter(new TextFormatter<String>(change -> {
            // We're not interested in changes in just the selection:
            if (!change.isContentChange())
                return change;
            String text = change.getControlNewText();

            text = text.trim();
            updatePosition(); // Just in case
            List<Completion> available = updateCompletions(calculateCompletions, text);
            // If they type an operator or non-operator char, and there is
            // no completion containing such a char, finish with current and move
            // to next (e.g. user types "true&"; as long as there's no current completion
            // involving "&", take it as an operator and move to next slot (which
            // may also complete if that's the only operator featuring that char)
            // while selecting the best (top) selection for current, or leave as error if none
            if (text.length() >= 1 && inNextAlphabet.test(text.charAt(text.length() - 1)))
            {
                char last = text.charAt(text.length() - 1);
                String withoutLast = text.substring(0, text.length() - 1);
                try
                {
                    if (withoutLast != null && !available.stream().anyMatch(c -> c.features(withoutLast, last)))
                    {
                        // No completions feature the character and it is in the following alphabet, so
                        // complete the top one (if any are available) and move character to next slot
                        List<Completion> completionsWithoutLast = calculateCompletions.apply(withoutLast, CompletionQuery.LEAVING_SLOT);
                        @Nullable Completion completion = completionsWithoutLast.isEmpty() ? null : completionsWithoutLast.stream().filter(c -> c.completesOnExactly(withoutLast, true) == CompletionAction.COMPLETE_IMMEDIATELY).findFirst().orElse(completionsWithoutLast.get(0));
                        change.setText(onSelect.nonAlphabetCharacter(withoutLast, completion, "" + last));
                        change.setRange(0, textField.getLength());
                        return change;
                    }
                }
                catch (UserException | InternalException e)
                {
                    Log.log(e);
                }
            }
            // We want to select top one, not last one, so keep track of
            // whether we've already selected top one:
            boolean haveSelected = false;
            for (Completion completion : available)
            {
                CompletionAction completionAction = completion.completesOnExactly(text, available.size() == 1);
                if (completionAction == CompletionAction.COMPLETE_IMMEDIATELY)
                {
                    change.setText(onSelect.exactCompletion(text, completion));
                    change.setRange(0, textField.getLength());
                    hide();
                    return change;
                }
                else if (completionAction == CompletionAction.SELECT)
                {
                    // Select it, at least:
                    if (!haveSelected)
                    {
                        completions.getSelectionModel().select(completion);
                        haveSelected = true;
                    }
                }
            }

            if (!text.isEmpty() && !completions.getItems().isEmpty() && completions.getSelectionModel().isEmpty())
            {
                completions.getSelectionModel().select(0);
            }
            return change;
        }));
        setHideOnEscape(true);
        addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ESCAPE)
            {
                hide();
                e.consume();
            }
            if ((e.getCode() == KeyCode.UP || e.getCode() == KeyCode.PAGE_UP) && completions.getSelectionModel().getSelectedIndex() <= 0)
            {
                completions.getSelectionModel().clearSelection();
                e.consume();
            }
            if (e.getCode() == KeyCode.ENTER || e.getCode() == KeyCode.TAB)
            {
                Completion selectedItem = completions.getSelectionModel().getSelectedItem();
                if (selectedItem != null)
                {
                    e.consume();
                    textField.setText(onSelect.keyboardSelect(textField.getText(), selectedItem));
                    hide();
                }
            }
        });
    }

    @RequiresNonNull({"completions"})
    private List<Completion> updateCompletions(@UnknownInitialization(Object.class) AutoComplete this, ExBiFunction<String, CompletionQuery, List<Completion>> calculateCompletions, String text)
    {
        try
        {
            this.completions.getItems().setAll(calculateCompletions.apply(text, CompletionQuery.CONTINUED_ENTRY));
        }
        catch (InternalException | UserException e)
        {
            Log.log(e);
            this.completions.getItems().clear();
        }
        return this.completions.getItems();
    }

    @OnThread(Tag.FXPlatform)
    @RequiresNonNull({"textField"})
    private @Nullable Pair<Double, Double> calculatePosition(@UnknownInitialization(Object.class) AutoComplete this)
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

    @OnThread(Tag.FXPlatform)
    private void updatePosition(@UnknownInitialization(Object.class) AutoComplete this)
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

    @RequiresNonNull({"textField", "completions"})
    public @Nullable Completion getCompletionIfFocusLeftNow(@UnknownInitialization(Object.class) AutoComplete this)
    {
        List<Completion> availableCompletions = completions.getItems();
        for (Completion completion : availableCompletions)
        {
            CompletionAction completionAction = completion.completesOnExactly(textField.getText(), availableCompletions.size() == 1);
            if (completionAction != CompletionAction.NONE)
            {
                return completion;
            }
        }
        return null;
    }

    public abstract static @Interned class Completion
    {
        /**
         * Given a property with the latest text, what graphical node and text property
         * should we show for the item?
         */
        public abstract Pair<@Nullable Node, ObservableStringValue> getDisplay(ObservableStringValue currentText);

        /**
         * Given the current input, should we be showing this completion?
         *
         * TODO this shouldn't be here, it should be pushed down to a subclass, as it's
         * not called by AutoComplete itself
         */
        public abstract boolean shouldShow(String input);

        public static enum CompletionAction
        {
            COMPLETE_IMMEDIATELY,
            SELECT,
            NONE
        }

        /**
         * Given current input, and whether or not this is the only completion available,
         * should we complete it right now, select it at least, or do nothing?
         */
        public abstract CompletionAction completesOnExactly(String input, boolean onlyAvailableCompletion);

        /**
         * Does this completion feature the given character at all after
         * the current input?
         */
        public abstract boolean features(String curInput, char character);

        /**
         * Gets the details to show to the right of the list.  If null, nothing
         * is shown to the right.
         */
        public @Nullable Node getFurtherDetails()
        {
            return null;
        }
    }



    public static @Interned class KeyShortcutCompletion extends Completion
    {
        private final Character[] shortcuts;
        private final String title;

        public KeyShortcutCompletion(String title, Character... shortcuts)
        {
            this.shortcuts = shortcuts;
            this.title = title;
        }

        @Override
        public Pair<@Nullable Node, ObservableStringValue> getDisplay(ObservableStringValue currentText)
        {
            return new Pair<>(new Label(" " + shortcuts[0] + " "), new ReadOnlyStringWrapper(title));
        }

        @Override
        public boolean shouldShow(String input)
        {
            return input.isEmpty() || Arrays.stream(shortcuts).anyMatch(c -> input.equals(c.toString()));
        }

        @Override
        public CompletionAction completesOnExactly(String input, boolean onlyAvailable)
        {
            for (Character shortcut : shortcuts)
            {
                if (input.equals(shortcut.toString()))
                    return CompletionAction.COMPLETE_IMMEDIATELY;
            }
            return CompletionAction.NONE;
        }

        @Override
        public boolean features(String curInput, char character)
        {
            return Arrays.stream(shortcuts).anyMatch(c -> (char)c == character);
        }
    }

    private class CompleteCell extends ListCell<Completion>
    {
        @Override
        @OnThread(value = Tag.FXPlatform, ignoreParent = true)
        protected void updateItem(@Nullable Completion item, boolean empty)
        {
            textProperty().unbind();
            if (empty || item == null)
            {
                setGraphic(null);
                setText("");
            }
            else
            {
                Pair<@Nullable Node, ObservableStringValue> p = item.getDisplay(textField.textProperty());
                setGraphic(p.getFirst());
                textProperty().bind(p.getSecond());
            }
            super.updateItem(item, empty);
        }
    }

    public static interface CompletionListener
    {
        // Item was double-clicked in the list
        // Returns the new text for the textfield
        String doubleClick(String currentText, Completion selectedItem);

        // Moving on because non alphabet character entered
        // Returns the new text for the textfield
        String nonAlphabetCharacter(String textBefore, @Nullable Completion selectedItem, String textAfter);

        // Enter or Tab used to select
        // Returns the new text for the textfield
        String keyboardSelect(String currentText, Completion selectedItem);

        // Selected because completesOnExactly returned true
        // Returns the new text for the textfield
        String exactCompletion(String currentText, Completion selectedItem);
        
        // Leaving the slot.  selectedItem is only non-null if
        // completesOnExactly is true.
        // Returns the new text for the textfield
        String focusLeaving(String currentText, @Nullable Completion selectedItem);
    }

    public static abstract class SimpleCompletionListener implements CompletionListener
    {
        @Override
        public String doubleClick(String currentText, Completion selectedItem)
        {
            return selected(currentText, selectedItem, "");
        }

        @Override
        public String nonAlphabetCharacter(String textBefore, @Nullable Completion selectedItem, String textAfter)
        {
            return selected(textBefore, selectedItem, textAfter);
        }

        @Override
        public String keyboardSelect(String currentText, Completion selectedItem)
        {
            return selected(currentText, selectedItem, "");
        }

        @Override
        public String exactCompletion(String currentText, Completion selectedItem)
        {
            return selected(currentText, selectedItem, "");
        }

        protected abstract String selected(String currentText, @Nullable Completion c, String rest);
    }

    private class AutoCompleteSkin implements Skin<AutoComplete>
    {
        @Override
        @OnThread(Tag.FX)
        public AutoComplete getSkinnable()
        {
            return AutoComplete.this;
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

    // For reasons I'm not clear about, this listener needs to be its own class, not anonymous:
    private class NumberChangeListener implements ChangeListener<Number>
    {
        @Override
        @OnThread(value = Tag.FXPlatform, ignoreParent = true)
        public void changed(ObservableValue<? extends Number> prop, Number oldVal, Number newVal)
        {
            updatePosition();
        }
    }

    private class WindowChangeListener implements ChangeListener<@Nullable Window>
    {
        final ChangeListener<Number> positionListener = new NumberChangeListener();

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
            updatePosition();
        }
    }
}
