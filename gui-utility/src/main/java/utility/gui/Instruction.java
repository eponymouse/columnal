package utility.gui;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.PopupControl;
import javafx.scene.control.Skin;
import javafx.scene.control.TextField;
import javafx.stage.Window;
import xyz.columnal.log.Log;
import org.checkerframework.checker.i18n.qual.LocalizableKey;
import threadchecker.OnThread;
import threadchecker.Tag;

@OnThread(Tag.FXPlatform)
public class Instruction extends PopupControl implements ChangeListener<Object>
{
    private final Label label;

    public Instruction(@LocalizableKey String instructionKey, String... styleClasses)
    {
        this.label = GUI.label(instructionKey, "instruction-label");
        setSkin(new InstructionSkin());
        getStyleClass().add("instruction");
        
        setAutoFix(false);
        setAnchorLocation(AnchorLocation.WINDOW_BOTTOM_LEFT);
    }
    
    public void showAboveWhenFocused(TextField textField)
    {
        FXUtility.addChangeListenerPlatformNN(textField.focusedProperty(), focus -> {
            Point2D screenTopLeft = textField.localToScreen(new Point2D(0, 1));
            Scene scene = textField.getScene();
            Window window = scene == null ? null : scene.getWindow();
            if (focus)
            {
                this.show(textField, screenTopLeft.getX(), screenTopLeft.getY());
                textField.localToSceneTransformProperty().addListener(this);
                if (window != null)
                {
                    window.xProperty().addListener(this);
                    window.yProperty().addListener(this);
                }
            }
            else
            {
                this.hide();
                textField.localToSceneTransformProperty().removeListener(this);
                if (window != null)
                {
                    window.xProperty().removeListener(this);
                    window.yProperty().removeListener(this);
                }
            }
        });
    }

    @Override
    @OnThread(value = Tag.FXPlatform, ignoreParent = true)
    public void changed(ObservableValue<?> observable, Object oldValue, Object newValue)
    {
        if (isShowing())
        {
            Point2D screenTopLeft = getOwnerNode().localToScreen(new Point2D(0, 1));
            this.show(getOwnerNode(), screenTopLeft.getX(), screenTopLeft.getY());
        }
    }

    @OnThread(Tag.FX)
    private class InstructionSkin implements Skin<Instruction>
    {
        @Override
        public Instruction getSkinnable()
        {
            return Instruction.this;
        }

        @Override
        public Node getNode()
        {
            return label;
        }

        @Override
        public void dispose()
        {

        }
    }
}
