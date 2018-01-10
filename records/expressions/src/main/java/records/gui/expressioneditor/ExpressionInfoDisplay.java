package records.gui.expressioneditor;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;
import org.controlsfx.control.PopOver;
import records.transformations.expression.ErrorAndTypeRecorder.QuickFix;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
import utility.gui.FXUtility;

import java.util.List;

/**
 * Manages the display of errors, quick fixes and type information in the expression editor.
 * 
 * The rule for when it displays is either:
 *  - The user hovers over the label at the top of an expression item (regardless of error state), OR
 *  - (The user hovers over the main part or focuses the main part) AND there is an error.
 * Bindings to do this will get too intricate, so we just keep track of the properties involved
 *  
 */
public class ExpressionInfoDisplay
{
    private final SimpleStringProperty type = new SimpleStringProperty("");
    private final SimpleStringProperty message = new SimpleStringProperty("");
    private final ObservableList<QuickFix> quickFixes = FXCollections.observableArrayList();
    private final VBox expressionNode;
    private @Nullable PopOver popup = null;
    private boolean focused = false;
    private boolean hoveringOverall = false;
    private boolean hoveringTop = false;

    @SuppressWarnings("initialization")
    public ExpressionInfoDisplay(VBox expressionNode, Label topLabel, TextField textField)
    {
        this.expressionNode = expressionNode;
        FXUtility.addChangeListenerPlatformNN(textField.focusedProperty(), this::textFieldFocusChanged);
        FXUtility.addChangeListenerPlatformNN(expressionNode.hoverProperty(), b -> mouseHoverStatusChanged(expressionNode.isHover(), topLabel.isHover()));
        FXUtility.addChangeListenerPlatformNN(topLabel.hoverProperty(), b -> mouseHoverStatusChanged(expressionNode.isHover(), topLabel.isHover()));
        FXUtility.addChangeListenerPlatformNN(message, s -> updateShowHide());
    }

    private class ErrorMessagePopup extends PopOver
    {
        private final BooleanBinding hasFixes;

        public ErrorMessagePopup()
        {
            getStyleClass().add("expression-info-popup");
            setArrowLocation(ArrowLocation.BOTTOM_CENTER);
            // If we let the position vary to fit on screen, we end up with the popup bouncing in and out
            // as the mouse hovers on item then on popup then hides.  Better to let the item be off-screen
            // and let the user realise they need to move things about a bit:
            setAutoFix(false);
            // It's the skin that binds the height, so we must unbind after the skin is set:
            FXUtility.onceNotNull(skinProperty(), skin -> {
                // By default, the min width and height are the same, to allow for arrow + corners.
                // But we know arrow is on bottom, so we don't need such a large min height:
                getRoot().minHeightProperty().unbind();
                getRoot().setMinHeight(20.0);
            });
            
            
            Label errorLabel = new Label();
            errorLabel.getStyleClass().add("expression-info-error");
            errorLabel.textProperty().bind(message);
            errorLabel.visibleProperty().bind(errorLabel.textProperty().isNotEmpty());
            errorLabel.managedProperty().bind(errorLabel.visibleProperty());

            ListView<QuickFix> fixList = new ListView<>(quickFixes);
            fixList.setMaxHeight(150.0);
            // Keep reference to prevent GC:
            hasFixes = Bindings.isEmpty(quickFixes).not();
            fixList.visibleProperty().bind(hasFixes);
            fixList.managedProperty().bind(fixList.visibleProperty());

            fixList.setCellFactory(lv -> new ListCell<QuickFix>() {
                @Override
                @OnThread(Tag.FX)
                protected void updateItem(@Nullable QuickFix item, boolean empty) {
                    super.updateItem(item, empty);
                    setText("");
                    setGraphic(empty || item == null ? null : new TextFlow(new Text(item.getTitle())));
                }
            });

            Label typeLabel = new Label();
            typeLabel.getStyleClass().add("expression-info-type");
            typeLabel.textProperty().bind(type);
            BorderPane container = new BorderPane(errorLabel, typeLabel, null, fixList, null);

            setContentNode(container);
        }
    }

    private void show()
    {
        // Shouldn't be non-null already, but just in case:
        if (popup != null)
        {
            hide();
        }
        if (expressionNode.getScene() != null)
        {
            popup = new ErrorMessagePopup();
            popup.show(expressionNode);
        }
    }

    @RequiresNonNull("popup")
    // Can't have an ensuresnull check
    private void hide()
    {
        popup.hide();
        popup = null;
    }

    private void mouseHoverStatusChanged(boolean hoverOverall, boolean hoverTop)
    {
        this.hoveringOverall = hoverOverall;
        this.hoveringTop = hoverTop;
        updateShowHide();
    }

    private void textFieldFocusChanged(boolean newFocused)
    {
        this.focused = newFocused;
        updateShowHide();
    }


    private void updateShowHide()
    {
        if (hoveringTop || ((hoveringOverall || focused) && !message.get().isEmpty()))
        {
            if (popup == null)
                show();
        }
        else
        {
            if (popup != null)
                hide();
        }
    }

    public void setMessageAndFixes(@Nullable Pair<String, List<QuickFix>> newMsgAndFixes)
    {
        if (newMsgAndFixes == null)
        {
            message.setValue("");
            quickFixes.clear();
            // Hide the popup:
            if (popup != null)
            {
                hide();
            }
        }
        else
        {
            message.set(newMsgAndFixes.getFirst());
            quickFixes.setAll(newMsgAndFixes.getSecond());
        }
    }
    
    public void setType(String type)
    {
        this.type.set(type);
    }
}
