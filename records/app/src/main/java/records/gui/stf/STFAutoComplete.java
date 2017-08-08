package records.gui.stf;

import javafx.collections.FXCollections;
import javafx.scene.Node;
import javafx.scene.control.ListView;
import javafx.scene.control.PopupControl;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import org.fxmisc.richtext.model.NavigationActions.SelectionPolicy;
import org.fxmisc.wellbehaved.event.EventPattern;
import org.fxmisc.wellbehaved.event.InputMap;
import org.fxmisc.wellbehaved.event.Nodes;
import records.gui.stf.StructuredTextField.Suggestion;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformRunnable;
import utility.Pair;

import java.util.Arrays;
import java.util.List;

/**
 * Created by neil on 01/07/2017.
 */
@OnThread(Tag.FXPlatform)
public class STFAutoComplete extends PopupControl
{
    private final ListView<Suggestion> completions;
    private final StructuredTextField<?> parent;

    @SuppressWarnings("initialization") // For passing the cell the this reference
    public STFAutoComplete(StructuredTextField<?> parent, List<Suggestion> suggestions)
    {
        this.parent = parent;
        completions = new ListView<>(FXCollections.observableArrayList(suggestions));
        completions.getStyleClass().add("stf-autocomplete");
        completions.setCellFactory(lv -> {
            return new STFAutoCompleteCell(this);
        });
        setSkin(new Skin());
        setHideOnEscape(true);
        disableListViewKeybindings(parent, completions);
    }

    public void update()
    {
        List<Suggestion> suggestions = completions.getItems();
        boolean[] eligible = new boolean[suggestions.size()];
        for (int i = 0; i < suggestions.size(); i++)
        {
            Suggestion sugg = suggestions.get(i);
            String text = parent.getTextForItems(sugg.startIndexIncl.getFirst(), sugg.endIndexIncl.getFirst());
            eligible[i] = sugg.suggestion.toLowerCase().startsWith(text.toLowerCase());
        }
        int sel = completions.getSelectionModel().getSelectedIndex();
        if (sel < 0 || !eligible[sel])
        {
            for (int i = 0; i < eligible.length; i++)
            {
                if (eligible[i])
                {
                    completions.getSelectionModel().select(i);
                    break;
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    @OnThread(Tag.FXPlatform)
    private void disableListViewKeybindings(StructuredTextField<?> parent, ListView<Suggestion> listView)
    {
        // This is a list of default bindings on ListView, most of which we want to disable:
        /*
        LIST_VIEW_BINDINGS.add(new KeyBinding(HOME, "SelectFirstRow"));
        LIST_VIEW_BINDINGS.add(new KeyBinding(END, "SelectLastRow"));
        LIST_VIEW_BINDINGS.add(new KeyBinding(HOME, "SelectAllToFirstRow").shift());
        LIST_VIEW_BINDINGS.add(new KeyBinding(END, "SelectAllToLastRow").shift());
        LIST_VIEW_BINDINGS.add(new KeyBinding(PAGE_UP, "SelectAllPageUp").shift());
        LIST_VIEW_BINDINGS.add(new KeyBinding(PAGE_DOWN, "SelectAllPageDown").shift());

        LIST_VIEW_BINDINGS.add(new KeyBinding(SPACE, "SelectAllToFocus").shift());
        LIST_VIEW_BINDINGS.add(new KeyBinding(SPACE, "SelectAllToFocusAndSetAnchor").shortcut().shift());

        LIST_VIEW_BINDINGS.add(new KeyBinding(PAGE_UP, "ScrollUp"));
        LIST_VIEW_BINDINGS.add(new KeyBinding(PAGE_DOWN, "ScrollDown"));

        LIST_VIEW_BINDINGS.add(new KeyBinding(ENTER, "Activate"));
        LIST_VIEW_BINDINGS.add(new KeyBinding(SPACE, "Activate"));
        LIST_VIEW_BINDINGS.add(new KeyBinding(F2, "Activate"));
        LIST_VIEW_BINDINGS.add(new KeyBinding(ESCAPE, "CancelEdit"));

        LIST_VIEW_BINDINGS.add(new KeyBinding(A, "SelectAll").shortcut());
        LIST_VIEW_BINDINGS.add(new KeyBinding(HOME, "FocusFirstRow").shortcut());
        LIST_VIEW_BINDINGS.add(new KeyBinding(END, "FocusLastRow").shortcut());
        LIST_VIEW_BINDINGS.add(new KeyBinding(PAGE_UP, "FocusPageUp").shortcut());
        LIST_VIEW_BINDINGS.add(new KeyBinding(PAGE_DOWN, "FocusPageDown").shortcut());

        if (PlatformUtil.isMac()) {
            LIST_VIEW_BINDINGS.add(new KeyBinding(SPACE, "toggleFocusOwnerSelection").ctrl().shortcut());
        } else {
            LIST_VIEW_BINDINGS.add(new KeyBinding(SPACE, "toggleFocusOwnerSelection").ctrl());
        }

        // if listView is vertical...
        LIST_VIEW_BINDINGS.add(new ListViewKeyBinding(UP, "SelectPreviousRow").vertical());
        LIST_VIEW_BINDINGS.add(new ListViewKeyBinding(KP_UP, "SelectPreviousRow").vertical());
        LIST_VIEW_BINDINGS.add(new ListViewKeyBinding(DOWN, "SelectNextRow").vertical());
        LIST_VIEW_BINDINGS.add(new ListViewKeyBinding(KP_DOWN, "SelectNextRow").vertical());

        LIST_VIEW_BINDINGS.add(new ListViewKeyBinding(UP, "AlsoSelectPreviousRow").vertical().shift());
        LIST_VIEW_BINDINGS.add(new ListViewKeyBinding(KP_UP, "AlsoSelectPreviousRow").vertical().shift());
        LIST_VIEW_BINDINGS.add(new ListViewKeyBinding(DOWN, "AlsoSelectNextRow").vertical().shift());
        LIST_VIEW_BINDINGS.add(new ListViewKeyBinding(KP_DOWN, "AlsoSelectNextRow").vertical().shift());

        LIST_VIEW_BINDINGS.add(new ListViewKeyBinding(UP, "FocusPreviousRow").vertical().shortcut());
        LIST_VIEW_BINDINGS.add(new ListViewKeyBinding(DOWN, "FocusNextRow").vertical().shortcut());

        LIST_VIEW_BINDINGS.add(new ListViewKeyBinding(UP, "DiscontinuousSelectPreviousRow").vertical().shortcut().shift());
        LIST_VIEW_BINDINGS.add(new ListViewKeyBinding(DOWN, "DiscontinuousSelectNextRow").vertical().shortcut().shift());
        LIST_VIEW_BINDINGS.add(new ListViewKeyBinding(PAGE_UP, "DiscontinuousSelectPageUp").vertical().shortcut().shift());
        LIST_VIEW_BINDINGS.add(new ListViewKeyBinding(PAGE_DOWN, "DiscontinuousSelectPageDown").vertical().shortcut().shift());
        LIST_VIEW_BINDINGS.add(new ListViewKeyBinding(HOME, "DiscontinuousSelectAllToFirstRow").vertical().shortcut().shift());
        LIST_VIEW_BINDINGS.add(new ListViewKeyBinding(END, "DiscontinuousSelectAllToLastRow").vertical().shortcut().shift());
        // --- end of vertical
        LIST_VIEW_BINDINGS.add(new KeyBinding(BACK_SLASH, "ClearSelection").shortcut());
         */

        // We can't seem to ignore the keypresses so we must override them and perform the appropriate action:
        Nodes.addInputMap(listView, InputMap.sequence(
            Arrays.<Pair<KeyCombination, FXPlatformRunnable>>asList(
                new Pair<>(new KeyCodeCombination(KeyCode.HOME), () -> parent.lineStart(SelectionPolicy.CLEAR)),
                new Pair<>(new KeyCodeCombination(KeyCode.HOME, KeyCombination.SHIFT_DOWN), () -> parent.lineStart(SelectionPolicy.EXTEND)),
                new Pair<>(new KeyCodeCombination(KeyCode.END), () -> parent.lineEnd(SelectionPolicy.CLEAR)),
                new Pair<>(new KeyCodeCombination(KeyCode.END, KeyCombination.SHIFT_DOWN), () -> parent.lineEnd(SelectionPolicy.EXTEND)),
                new Pair<>(new KeyCodeCombination(KeyCode.A, KeyCombination.SHORTCUT_DOWN), () -> parent.selectAll()),
                new Pair<>(new KeyCodeCombination(KeyCode.TAB), () -> fireSelected())
            ).stream().map(k -> InputMap.consume(EventPattern.keyPressed(k.getFirst()), ev -> k.getSecond().run())).toArray(InputMap[]::new)
        ));
    }

    public void fire(Suggestion item)
    {
        parent.fireSuggestion(item);
    }

    public void fireSelected()
    {
        Suggestion selectedItem = completions.getSelectionModel().getSelectedItem();
        if (selectedItem != null)
            fire(selectedItem);
    }

    private class Skin implements javafx.scene.control.Skin<STFAutoComplete>
    {
        @Override
        @OnThread(Tag.FX)
        public STFAutoComplete getSkinnable()
        {
            return STFAutoComplete.this;
        }

        @Override
        @OnThread(Tag.FX)
        public Node getNode()
        {
            return completions;
        }

        @Override
        @OnThread(Tag.FX)
        public void dispose()
        {
        }
    }
}
