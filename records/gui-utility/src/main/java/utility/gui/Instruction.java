package utility.gui;

import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.PopupControl;
import javafx.scene.control.Skin;
import log.Log;
import org.checkerframework.checker.i18n.qual.LocalizableKey;
import threadchecker.OnThread;
import threadchecker.Tag;

@OnThread(Tag.FXPlatform)
public class Instruction extends PopupControl
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
