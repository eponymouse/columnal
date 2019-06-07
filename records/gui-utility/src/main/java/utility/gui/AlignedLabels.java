package utility.gui;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import org.checkerframework.checker.i18n.qual.LocalizableKey;
import org.checkerframework.checker.i18n.qual.Localized;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.TranslationUtility;

import java.util.ArrayList;

/**
 * We have dialogs where we want to have a set of labels with identical
 * widths, but the labels are not all in the same grid pane because of 
 * extra formatting or dividers between them.
 * 
 * To make the labels align we use a hack.  We make each label a stack pane
 * containing all the labels, but only the one we want to show there is 
 * visible.  That way all the stack panes have the same widths, and all
 * the labels appear to have the same width.
 * 
 * Note that we end up with N^2 labels for N actually-visible labels, but
 * since N is <10, it's not a big issue.
 */
@OnThread(Tag.FXPlatform)
public class AlignedLabels
{
    // One stack pane per visible row (N), each containing N labels 
    private final ArrayList<StackPane> stackPanes = new ArrayList<>();
    // The text items, same length as stackPanes.
    private final ArrayList<@Localized String> labelTexts = new ArrayList<>();

    /**
     * Add a new label with the given key, returns a StackPane that
     * may be modified to have more invisible labels by future addLabel
     * calls.
     */
    public StackPane addLabel(@LocalizableKey String labelKey)
    {
        // Add this text to existing panes:
        @Localized String labelText = TranslationUtility.getString(labelKey);
        for (StackPane existingPane : stackPanes)
        {
            Label l = new Label(labelText);
            StackPane.setAlignment(l, Pos.CENTER_RIGHT);
            l.setVisible(false);
            existingPane.getChildren().add(0, l);
        }
        // Now make a new pane with the existing and new:
        StackPane stackPane = new StackPane();
        for (String existingText : labelTexts)
        {
            Label l = new Label(existingText);
            StackPane.setAlignment(l, Pos.CENTER_RIGHT);
            l.setVisible(false);
            stackPane.getChildren().add(l);
        }
        Label l = new Label(labelText);
        StackPane.setAlignment(l, Pos.CENTER_RIGHT);
        stackPane.getChildren().add(l);
        
        labelTexts.add(labelText);
        stackPanes.add(stackPane);
        return stackPane;
    }
}
