package records.gui.expressioneditor;

import com.google.common.collect.ImmutableList;
import com.sun.javafx.scene.control.skin.TextFieldSkin;
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
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.web.WebView;
import javafx.stage.Window;
import log.Log;
import org.checkerframework.checker.i18n.qual.LocalizableKey;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.interning.qual.Interned;
import org.checkerframework.checker.nullness.qual.EnsuresNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;
import records.error.InternalException;
import records.error.UserException;
import records.gui.expressioneditor.AutoComplete.Completion;
import records.gui.expressioneditor.AutoComplete.Completion.CompletionContent;
import records.gui.expressioneditor.AutoComplete.Completion.ShowStatus;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.ExBiFunction;
import utility.FXPlatformConsumer;
import utility.FXPlatformRunnable;
import utility.FXPlatformSupplier;
import utility.Pair;
import utility.Utility;
import utility.gui.FXUtility;
import utility.gui.GUI;
import utility.gui.Instruction;
import utility.gui.TranslationUtility;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.OptionalInt;
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
    private final AlphabetCheck alphabetCheck;
    private @Nullable AutoCompleteWindow window;
    
    private boolean settingContentDirectly = false;
    private OptionalInt prospectiveCaret = OptionalInt.empty();

    public static enum WhitespacePolicy
    {
        ALLOW_ANYWHERE,
        ALLOW_ONE_ANYWHERE_TRIM,
        DISALLOW
    }
    
    public static enum CompletionQuery
    {
        // They've entered another char which doesn't fit, we're planning to leave the slot now:
        LEAVING_SLOT,
        // They've entered a char which does fit, so we're currently planning to stay in the slot:
        CONTINUED_ENTRY;
    }
    
    public static interface AlphabetCheck
    {
        /**
         * Returns true if the new character (codepoint) is a different alphabet
         * to the existing String.  That is, the new character cannot possibly continue
         * the current item and must be part of a new entry.  This is the case e.g.
         * for ("xyz", '+'), ("54", '{'), ("+", 'a') and so on.
         * @param existing The string so far.  Will not be empty.
         * @param newCodepoint The new codepoint.
         * @return
         */
        public boolean requiresNewSlot(String existing, int newCodepoint);
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
    public AutoComplete(TextField textField, ExBiFunction<String, CompletionQuery, Stream<C>> calculateCompletions, CompletionListener<C> onSelect, FXPlatformSupplier<Boolean> showOnFocus, WhitespacePolicy whitespacePolicy, AlphabetCheck inNextAlphabet)
    {
        this.textField = textField;
        this.onSelect = onSelect;
        this.alphabetCheck = inNextAlphabet;
        

        textField.getStylesheets().add(FXUtility.getStylesheet("autocomplete.css"));
        

        FXUtility.addChangeListenerPlatformNN(textField.focusedProperty(), focused -> {
            if (focused)
            {
                AutoComplete<C> usInit = FXUtility.mouse(this);
                usInit.createWindow();
                usInit.window.updateCompletions(calculateCompletions, textField.getText());
                // We use runAfter because completing previous field can focus us before it has its text
                // in place, so the showOnFocus call returns the wrong value.  The user won't notice
                // big difference if auto complete appears a fraction later, so we don't lose anything:
                FXUtility.runAfter(() -> {
                    Pair<Double, Double> pos = calculatePosition();
                    if (textField.isFocused() && !isShowing() && pos != null && showOnFocus.get())
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
                    window.updateCompletions(calculateCompletions, textField.getText());
                    C completion = getCompletionIfFocusLeftNow();
                    if (completion != null)
                    {
                        //#error TODO I think setting the text isn't enough to clear the error state, we also need to set the status or something?
                        @Nullable String newContent = onSelect.focusLeaving(textField.getText(), completion);
                        if (newContent != null)
                            FXUtility.keyboard(this).setContentDirect(newContent);
                    }
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
            List<C> available = window == null ? ImmutableList.of() : window.updateCompletions(calculateCompletions, text.trim());

            // Show if not already showing:
            Pair<Double, Double> pos = calculatePosition();
            if (textField.isFocused() && !isShowing() && pos != null)
            {
                FXUtility.keyboard(this).show(textField, pos.getFirst(), pos.getSecond());
            }
            
            // If they type an operator or non-operator char, and there is
            // no completion containing such a char, finish with current and move
            // to next (e.g. user types "true&"; as long as there's no current completion
            // involving "&", take it as an operator and move to next slot (which
            // may also complete if that's the only operator featuring that char)
            // while selecting the best (top) selection for current, or leave as error if none
            Log.debug("Checking alphabet: [[" + text + "]]");
            for (int codepointIndex = 1; codepointIndex < codepoints.length; codepointIndex++)
            {
                String prefix = new String(codepoints, 0, codepointIndex);
                int cur = codepoints[codepointIndex];
                String suffix = new String(codepoints, codepointIndex + 1, codepoints.length - codepointIndex - 1);

                ImmutableList<C> completionWithoutLast = ImmutableList.of();
                try
                {
                    completionWithoutLast = calculateCompletions.apply(prefix, CompletionQuery.CONTINUED_ENTRY).collect(ImmutableList.<C>toImmutableList());
                }
                catch (InternalException | UserException e)
                {
                    Log.log(e);
                }
                if (codepoints.length >= 1 &&
                        (inNextAlphabet.requiresNewSlot(prefix, cur)
                                || (completionWithoutLast.stream().allMatch(c -> !c.features(prefix, cur))
                                && completionWithoutLast.stream().anyMatch(c -> c.shouldShow(prefix) == ShowStatus.DIRECT_MATCH))
                        ))
                {
                    try
                    {
                        if (!available.stream().anyMatch(c -> c.features(prefix, cur)))
                        {
                            // No completions feature the character and it is in the following alphabet, so
                            // complete the top one (if any are available) and move character to next slot
                            List<C> completionsWithoutLast = calculateCompletions.apply(prefix, CompletionQuery.LEAVING_SLOT).collect(Collectors.<C>toList());
                            @Nullable C completion = completionsWithoutLast.isEmpty() ? null : completionsWithoutLast.stream().filter(c -> c.shouldShow(prefix).viableNow()).findFirst().orElse(completionsWithoutLast.get(0));
                            int caretPos = prospectiveCaret.orElse(change.getCaretPosition());
                            OptionalInt position = caretPos > prefix.length() && textField.isFocused() ? OptionalInt.of(caretPos - prefix.length()) : OptionalInt.empty();
                            @Nullable String newContent = onSelect.nonAlphabetCharacter(prefix, completion, Utility.codePointToString(cur) + suffix, position);
                            if (newContent == null)
                                newContent = prefix;
                            if (newContent != null)
                            {
                                change.setText(newContent);
                                change.setRange(0, textField.getLength());
                            }
                            return change;
                        }
                    }
                    catch (UserException | InternalException e)
                    {
                        Log.log(e);
                    }
                }
            }
            // Trim after alphabet check:
            text = text.trim();
            
            if (window != null)
            {
                ListView<C> completions = window.completions;
                
                // We want to select top one, not last one, so keep track of
                // whether we've already selected top one:
                boolean haveSelected = false;
                for (C completion : available)
                {
                    ShowStatus completionAction = completion.shouldShow(text);
                    //Log.debug("Completion for \"" + text + "\": " + completionAction);
                    // TODO check if we are actually a single completion
                    if (completionAction == ShowStatus.DIRECT_MATCH && !settingContentDirectly && completion.completesWhenSingleDirect())
                    {
                        completions.getSelectionModel().select(completion);

                        @Nullable String newContent = onSelect.exactCompletion(text, completion);
                        if (newContent != null)
                        {
                            change.setText(newContent);
                            change.setRange(0, textField.getLength());
                        }
                        hide();
                        return change;
                    }
                    else if (completionAction == ShowStatus.START_DIRECT_MATCH || completionAction == ShowStatus.PHANTOM || completionAction == ShowStatus.DIRECT_MATCH)
                    {
                        // Select it, at least:
                        if (!haveSelected)
                        {
                            completions.getSelectionModel().select(completion);
                            FXUtility.ensureSelectionInView(completions, null);
                            haveSelected = true;
                        }
                    }
                }

                if (!text.isEmpty() && !completions.getItems().isEmpty() && completions.getSelectionModel().isEmpty())
                {
                    completions.getSelectionModel().select(0);
                    FXUtility.ensureSelectionInView(completions, null);
                }
            }
            
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

    public void setContentDirect(String s)
    {
        settingContentDirectly = true;
        textField.setText(s);
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

    @RequiresNonNull({"textField"})
    public @Nullable C getCompletionIfFocusLeftNow(@UnknownInitialization(Object.class) AutoComplete<C> this)
    {
        if (window == null)
            return null;
        
        List<C> availableCompletions = window.completions.getItems();
        for (C completion : availableCompletions)
        {
            // Say it's the only completion, because otherwise e.g. column completions
            // won't fire because there are always two of them:
            if (completion.shouldShow(textField.getText()).viableNow())
            {
                return completion;
            }
        }
        return null;
    }


    public boolean matchingAlphabets(String lhs, String rhs)
    {
        return lhs.isEmpty() || rhs.isEmpty() || 
            !alphabetCheck.requiresNewSlot(lhs, rhs.codePoints().findFirst().orElse(0));
    }


    public void withProspectiveCaret(int caret, FXPlatformRunnable action)
    {
        prospectiveCaret = OptionalInt.of(caret);
        action.run();
        prospectiveCaret = OptionalInt.empty();
        // If we've lost focus, don't override caret position:
        if (textField.isFocused())
            textField.positionCaret(caret);
    }

    public abstract static @Interned class Completion
    {

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
        public abstract CompletionContent makeDisplay(ObservableStringValue currentText);

        /**
         * How should we sort this item?  For functions, leave off brackets.
         * 
         * @param text The current user-entered text (independent of this item)
         */
        public String getDisplaySortKey(String text)
        {
            return makeDisplay(new ReadOnlyStringWrapper(text)).completion.get();
        }

        public static enum ShowStatus
        {
            /** An exact match as it stands right now,
             *  e.g. you type @if and it matches @if */
            DIRECT_MATCH,
            /** A dummy completion, e.g. numeric literal
             *  during entry. */
            PHANTOM,
            /**
             * Could be a direct match if you keep typing,
             * e.g. you've typed @mat but there is @match
             */
            START_DIRECT_MATCH,

            /** Doesn't match directly but we know it
             *  may relate due to a typo or synonym, e.g. you
             *  type @mach but there is @match
             */
            RELATED_MATCH,
            /**
             * Totally unrelated.  You type "dog" and the
             * item is @match.
             */
            NO_MATCH;

            public boolean viableNow()
            {
                return this == DIRECT_MATCH || this == PHANTOM;
            }
        }
        
        /**
         * Given the current input, what is the relation
         * of this completion?
         */
        public abstract ShowStatus shouldShow(String input);

        /**
         * Given current input, if there is one DIRECT_MATCH
         * and no PHANTOM or START_DIRECT_MATCH
         * should we complete it right now, or do nothing?
         * 
         * By default, don't complete (generally this is only a good idea for keywords and operators)
         */
        public boolean completesWhenSingleDirect()
        {
            return false;
        }

        /**
         * Does this completion feature the given character at all after
         * the current input?
         */
        public abstract boolean features(String curInput, int character);

        /**
         * Gets the URL of the details to show to the right of the list.  If null, nothing
         * is shown to the right.
         */
        public @Nullable String getFurtherDetailsURL()
        {
            return null;
        }
    }



    public static @Interned class KeyShortcutCompletion extends Completion
    {
        private final Character[] shortcuts;
        private final @LocalizableKey String titleKey;

        public KeyShortcutCompletion(@LocalizableKey String titleKey, Character... shortcuts)
        {
            this.shortcuts = shortcuts;
            this.titleKey = titleKey;
        }

        @Override
        public CompletionContent makeDisplay(ObservableStringValue currentText)
        {
            return new CompletionContent("" + shortcuts[0], TranslationUtility.getString(titleKey));
        }

        @Override
        public ShowStatus shouldShow(String input)
        {
            if (input.isEmpty())
                return ShowStatus.START_DIRECT_MATCH;
            else if (Arrays.stream(shortcuts).anyMatch(c -> input.equals(c.toString())))
                return ShowStatus.DIRECT_MATCH;
            else
                return ShowStatus.NO_MATCH;
        }

        @Override
        public boolean completesWhenSingleDirect()
        {
            return true;
        }

        @Override
        public boolean features(String curInput, int character)
        {
            return Arrays.stream(shortcuts).anyMatch(c -> c.charValue() == character);
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
        public ShowStatus shouldShow(String input)
        {
            if (input.equals(completion))
                return ShowStatus.DIRECT_MATCH;
            else if (completion.startsWith(input))
                return ShowStatus.START_DIRECT_MATCH;
            else
                return ShowStatus.NO_MATCH;
        }

        @Override
        public boolean completesWhenSingleDirect()
        {
            // Default is don't complete when we get it right for names:
            return false;
        }

        @Override
        public boolean features(String curInput, int character)
        {
            if (completion.startsWith(curInput))
                return Utility.containsCodepoint(completion.substring(curInput.length()), character);
            else
                return false;
        }

        @Override
        public @Nullable String getFurtherDetailsURL()
        {
            return super.getFurtherDetailsURL();
        }
    }

    public static class EndCompletion extends SimpleCompletion
    {
        public EndCompletion(String ending)
        {
            super(ending, null);
        }
        @Override
        public boolean completesWhenSingleDirect()
        {
            return true;
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

        // Moving on because non alphabet character entered
        // Returns the new text for the textfield, or null if keep as-is
        // positionCaret is blank for no focus change, or an index
        // of how many characters after the current to move the focus to.
        @Nullable String nonAlphabetCharacter(String textBefore, @Nullable C selectedItem, String textAfter, OptionalInt positionCaret);

        // Enter or Tab used to select
        // Returns the new text for the textfield, or null if keep as-is
        @Nullable String keyboardSelect(String currentText, C selectedItem);

        // Selected because completesOnExactly returned true
        // Returns the new text for the textfield, or null if keep as-is
        @Nullable String exactCompletion(String currentText, C selectedItem);
        
        // Leaving the slot.  selectedItem is only non-null if
        // completesOnExactly is true.
        // Returns the new text for the textfield, or null if keep as-is
        @Nullable String focusLeaving(String currentText, @Nullable C selectedItem);

        // Tab has been pressed when we have no reason to handle it: 
        void tabPressed();
    }

    public static abstract class SimpleCompletionListener<C extends Completion> implements CompletionListener<C>
    {
        @Override
        public @Nullable String doubleClick(String currentText, C selectedItem)
        {
            return selected(currentText, selectedItem, "", isFocused() ? OptionalInt.of(0) : OptionalInt.empty());
        }

        @Override
        public @Nullable String nonAlphabetCharacter(String textBefore, @Nullable C selectedItem, String textAfter, OptionalInt positionCaret)
        {
            return selected(textBefore, selectedItem != null && selectedItem.shouldShow(textBefore).viableNow() ? selectedItem : null, textAfter, positionCaret);
        }

        @Override
        public @Nullable String keyboardSelect(String currentText, C selectedItem)
        {
            return selected(currentText, selectedItem, "", isFocused() ? OptionalInt.of(0) : OptionalInt.empty());
        }

        @Override
        public @Nullable String exactCompletion(String currentText, C selectedItem)
        {
            return selected(currentText, selectedItem, "", isFocused() ? OptionalInt.of(0) : OptionalInt.empty());
        }

        protected abstract @Nullable String selected(String currentText, @Nullable C c, String rest, OptionalInt positionCaret);
        
        protected abstract boolean isFocused();
    }

    // For reasons I'm not clear about, this listener needs to be its own class, not anonymous:
    private class NumberChangeListener implements ChangeListener<Number>
    {
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
        private final WebView webView;
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
            this.webView = new WebView();
            this.webView.setPrefWidth(400.0);
            webViewHeightBinding = Bindings.max(300.0f, completions.heightProperty());
            this.webView.prefHeightProperty().bind(webViewHeightBinding);

            FXUtility.listen(completions.getItems(), change -> {
                FXUtility.runAfter(() -> updateHeight(completions));
            });

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
                        setContentDirect(newContent);
                }
            });

            FXUtility.addChangeListenerPlatform(completions.getSelectionModel().selectedItemProperty(), selected -> {
                if (selected != null)
                {
                    @Nullable String url = selected.getFurtherDetailsURL();
                    if (url != null)
                    {
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
                if (isShowing() && selectedItem != null)
                {
                    e.consume();
                    @Nullable String newContent = onSelect.keyboardSelect(textField.getText(), selectedItem);
                    if (newContent != null)
                        setContentDirect(newContent);
                    hide();
                    instruction.hide();
                }
                else if (selectedItem == null && e.getCode() == KeyCode.TAB)
                {
                    onSelect.tabPressed();
                }
            }
        }

        private void updateHeight(@UnknownInitialization(Window.class) AutoCompleteWindow this, ListView<?> completions)
        {
            // Merging several answers from https://stackoverflow.com/questions/17429508/how-do-you-get-javafx-listview-to-be-the-height-of-its-items
            double itemHeight = CELL_HEIGHT;
            completions.setPrefHeight(Math.min(300.0, 2 + itemHeight * completions.getItems().size()));
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

        private List<C> updateCompletions(ExBiFunction<String, CompletionQuery, Stream<C>> calculateCompletions, String text)
        {
            try
            {
                List<C> calculated = calculateCompletions.apply(text, CompletionQuery.CONTINUED_ENTRY)
                        .sorted(Comparator.comparing((C c) -> c.shouldShow(text)).thenComparing((C c) -> c.getDisplaySortKey(text)))
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
