package records.gui.expressioneditor;

import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.PopupControl;
import javafx.scene.control.Skin;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.function.FunctionDefinition;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.ExFunction;
import utility.Pair;
import utility.Utility;

import java.util.List;

/**
 * Created by neil on 17/12/2016.
 */
@OnThread(Tag.FXPlatform)
public class AutoComplete extends PopupControl
{
    private final TextField textField;
    private final ExFunction<String, List<Completion>> calculateCompletions;
    private final ListView<Completion> completions;

    @SuppressWarnings("initialization")
    public AutoComplete(TextField textField, ExFunction<String, List<Completion>> calculateCompletions)
    {
        this.textField = textField;
        this.completions = new ListView<>();
        this.calculateCompletions = calculateCompletions;

        completions.setCellFactory(lv -> {
            return new CompleteCell();
        });

        setSkin(new Skin<AutoComplete>()
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
                return completions;
            }

            @Override
            @OnThread(Tag.FX)
            public void dispose()
            {
            }
        });

        Utility.addChangeListenerPlatformNN(textField.focusedProperty(), focused -> {
            if (focused)
            {
                Pair<Double, Double> pos = calculatePosition();
                updateCompletions(calculateCompletions, textField.getText());
                show(textField, pos.getFirst(), pos.getSecond());
            }
            else
                hide();
        });

        Utility.addChangeListenerPlatformNN(textField.localToSceneTransformProperty(), t -> updatePosition());
        Utility.addChangeListenerPlatformNN(textField.layoutXProperty(), t -> updatePosition());
        Utility.addChangeListenerPlatformNN(textField.layoutYProperty(), t -> updatePosition());
        Utility.addChangeListenerPlatformNN(textField.heightProperty(), t -> updatePosition());

        Utility.addChangeListenerPlatformNN(textField.textProperty(), text -> {
            updatePosition(); // Just in case
            updateCompletions(calculateCompletions, text);
        });
        textField.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ESCAPE)
            {
                hide();
            }
        });
    }

    private void updateCompletions(ExFunction<String, List<Completion>> calculateCompletions, String text)
    {
        try
        {
            this.completions.getItems().setAll(calculateCompletions.apply(text));
        }
        catch (InternalException | UserException e)
        {
            Utility.log(e);
            this.completions.getItems().clear();
        }
    }

    @OnThread(Tag.FXPlatform)
    private @Nullable Pair<Double, Double> calculatePosition()
    {
        @Nullable Point2D textToScene = textField.localToScene(0, textField.getHeight());
        if (textToScene == null || textField.getScene() == null || textField.getScene().getWindow() == null)
            return null;
        return new Pair<>(
            textToScene.getX() + textField.getScene().getX() + textField.getScene().getWindow().getX(),
            textToScene.getY() + textField.getScene().getY() + textField.getScene().getWindow().getY()
        );
    }

    private void updatePosition()
    {
        if (isShowing())
        {
            @Nullable Pair<Double, Double> pos = calculatePosition();
            if (pos != null)
            {
                setAnchorX(pos.getFirst());
                setAnchorY(pos.getSecond());
            }
        }
    }

    public abstract static class Completion
    {
        @OnThread(Tag.FXPlatform)
        abstract Pair<@Nullable Node, String> getDisplay();
        abstract boolean shouldShow(String input);
    }

    public static class SimpleCompletion extends Completion
    {
        private final String text;

        public SimpleCompletion(String text)
        {
            this.text = text;
        }

        @Override
        Pair<@Nullable Node, String> getDisplay()
        {
            return new Pair<>(null, text);
        }

        @Override
        boolean shouldShow(String input)
        {
            return text.startsWith(input);
        }
    }

    public static class KeyShortcutCompletion extends Completion
    {
        private final String shortcut;
        private final String title;

        public KeyShortcutCompletion(String shortcut, String title)
        {
            this.shortcut = shortcut;
            this.title = title;
        }

        @Override
        Pair<@Nullable Node, String> getDisplay()
        {
            return new Pair<>(new Label(" " + shortcut + " "), title);
        }

        @Override
        boolean shouldShow(String input)
        {
            return input.isEmpty();
        }
    }

    public static class FunctionCompletion extends Completion
    {
        private final FunctionDefinition function;

        public FunctionCompletion(FunctionDefinition function)
        {
            this.function = function;
        }

        @Override
        Pair<@Nullable Node, String> getDisplay()
        {
            return new Pair<>(null, function.getName());
        }

        @Override
        boolean shouldShow(String input)
        {
            return function.getName().startsWith(input);
        }
    }

    private class CompleteCell extends ListCell<Completion>
    {
        @Override
        @OnThread(value = Tag.FXPlatform, ignoreParent = true)
        protected void updateItem(Completion item, boolean empty)
        {
            if (empty)
            {
                setGraphic(null);
                setText("");
            }
            else
            {
                Pair<@Nullable Node, String> p = item.getDisplay();
                setGraphic(p.getFirst());
                setText(p.getSecond());
            }
            super.updateItem(item, empty);
        }
    }
}
