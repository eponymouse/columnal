package utility.gui;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.ObjectExpression;
import javafx.scene.control.Label;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleButton;
import org.checkerframework.checker.i18n.qual.LocalizableKey;
import org.checkerframework.checker.i18n.qual.Localized;
import org.controlsfx.control.SegmentedButton;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;

import java.util.IdentityHashMap;

/**
 * An augmented version of SegmentedButton that allows you
 * to get the selected value from the buttons easily.
 */
@OnThread(Tag.FX)
public class SegmentedButtonValue<T> extends SegmentedButton
{
    private final IdentityHashMap<Toggle, T> buttons = new IdentityHashMap<>();
    private final ObjectExpression<T> valueProperty;

    @SuppressWarnings("nullness") // We know each button will have a mapped value in the binding
    @SafeVarargs
    public SegmentedButtonValue(Pair<@LocalizableKey String, T>... choices)
    {
        for (Pair<@LocalizableKey String, T> choice : choices)
        {
            ToggleButton button = new TickToggleButton(TranslationUtility.getString(choice.getFirst()));
            getButtons().add(button);
            buttons.put(button, choice.getSecond());
        }

        getButtons().get(0).setSelected(true);
        valueProperty = Bindings.createObjectBinding(() -> buttons.get(getToggleGroup().getSelectedToggle()), getToggleGroup().selectedToggleProperty());
    }

    public ObjectExpression<T> valueProperty()
    {
        return valueProperty;
    }

    @OnThread(Tag.FX)
    private static class TickToggleButton extends ToggleButton
    {
        public TickToggleButton(@Localized String text)
        {
            super(text);
            Label tick = new Label("\u2713");
            setGraphic(tick);
            tick.visibleProperty().bind(selectedProperty());
        }
    }
}
