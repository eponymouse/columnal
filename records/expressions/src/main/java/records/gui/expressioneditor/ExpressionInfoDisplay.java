package records.gui.expressioneditor;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.util.Duration;
import org.checkerframework.checker.nullness.qual.EnsuresNonNull;
import org.checkerframework.checker.nullness.qual.NonNull;
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
 * Additionally, when we should hide, but there is an error showing, we hide slowly, so the user gets
 * time to mouse-over us to access the fixes and so on.  If the user mouses-over us, we cancel any current
 * hide.
 *  
 */
public class ExpressionInfoDisplay
{
    private final SimpleStringProperty type = new SimpleStringProperty("");
    private final SimpleStringProperty errorMessage = new SimpleStringProperty("");
    private final ObservableList<QuickFix> quickFixes = FXCollections.observableArrayList();
    private final VBox expressionNode;
    private @Nullable PopOver popup = null;
    private boolean focused = false;
    private boolean hoveringAttached = false;
    private boolean hoveringTopOfAttached = false;
    private boolean hoveringPopup = false;
    private @Nullable Animation hidingAnimation;

    @SuppressWarnings("initialization")
    public ExpressionInfoDisplay(VBox expressionNode, Label topLabel, TextField textField)
    {
        this.expressionNode = expressionNode;
        FXUtility.addChangeListenerPlatformNN(textField.focusedProperty(), this::textFieldFocusChanged);
        FXUtility.addChangeListenerPlatformNN(expressionNode.hoverProperty(), b -> {
            hoveringAttached = b;
            mouseHoverStatusChanged();
        });
        FXUtility.addChangeListenerPlatformNN(topLabel.hoverProperty(), b -> {
            hoveringTopOfAttached = b;
            mouseHoverStatusChanged();
        });
        FXUtility.addChangeListenerPlatformNN(errorMessage, s -> updateShowHide(true));
    }

    private class ErrorMessagePopup extends PopOver
    {
        private final BooleanBinding hasFixes;

        public ErrorMessagePopup()
        {
            setDetachable(true);
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
            errorLabel.textProperty().bind(errorMessage);
            errorLabel.visibleProperty().bind(errorLabel.textProperty().isNotEmpty());
            errorLabel.managedProperty().bind(errorLabel.visibleProperty());

            ListView<QuickFix> fixList = new ListView<>(quickFixes);
            fixList.setMaxHeight(150.0);
            // Keep reference to prevent GC:
            hasFixes = Bindings.isNotEmpty(quickFixes);
            fixList.visibleProperty().bind(hasFixes);
            fixList.managedProperty().bind(fixList.visibleProperty());

            fixList.setCellFactory(lv -> new ListCell<QuickFix>() {
                @Override
                @OnThread(Tag.FX)
                protected void updateItem(@Nullable QuickFix item, boolean empty) {
                    super.updateItem(item, empty);
                    setText("");
                    setGraphic((empty || item == null) ? null : new TextFlow(new Text(item.getTitle())));
                }
            });

            Label typeLabel = new Label();
            typeLabel.getStyleClass().add("expression-info-type");
            typeLabel.textProperty().bind(type);
            BorderPane container = new BorderPane(errorLabel, typeLabel, null, fixList, null);

            setContentNode(container);
            FXUtility.addChangeListenerPlatformNN(getRoot().hoverProperty(), b -> {
                hoveringPopup = b;
                mouseHoverStatusChanged();
            });
            FXUtility.addChangeListenerPlatformNN(detachedProperty(), b -> updateShowHide(false));
        }
    }

    private void show()
    {
        // Shouldn't be non-null already, but just in case:
        if (popup != null)
        {
            hide(true);
        }
        if (expressionNode.getScene() != null)
        {
            popup = new ErrorMessagePopup();
            popup.show(expressionNode);
        }
    }

    @RequiresNonNull("popup")
    // Can't have an ensuresnull check
    private void hide(boolean immediately)
    {
        @NonNull PopOver popupFinal = popup;
        // Whether we hide immediately or not, stop any current animation:
        cancelHideAnimation();

        if (immediately)
        {
            popupFinal.hide();
            popup = null;
        }
        else
        {
            
            hidingAnimation = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(popupFinal.opacityProperty(), 1.0)),
                new KeyFrame(Duration.millis(2000), new KeyValue(popupFinal.opacityProperty(), 0.0))
            );
            hidingAnimation.setOnFinished(e -> {
                if (popup != null)
                {
                    popup.hide();
                    popup = null;
                }
            });
            hidingAnimation.playFromStart();
        }
    }

    private void mouseHoverStatusChanged()
    {
        updateShowHide(false);
    }

    private void textFieldFocusChanged(boolean newFocused)
    {
        this.focused = newFocused;
        updateShowHide(true);
    }


    private void updateShowHide(boolean hideImmediately)
    {
        if (hoveringPopup || hoveringTopOfAttached || ((hoveringAttached || focused) && !errorMessage.get().isEmpty()) || (popup != null && popup.isDetached()))
        {
            if (popup == null)
            {
                show();
            }
            else
            {
                // Make sure to cancel any hide animation:
                cancelHideAnimation();
            }
        }
        else
        {
            if (popup != null)
                hide(hideImmediately);
        }
    }

    //@EnsuresNull("hidingAnimation")
    private void cancelHideAnimation()
    {        
        if (hidingAnimation != null)
        {
            hidingAnimation.stop();
            hidingAnimation = null;
        }
        if (popup != null)
        {
            popup.setOpacity(1.0);
        }
    }

    public void setMessageAndFixes(@Nullable Pair<String, List<QuickFix>> newMsgAndFixes)
    {
        if (newMsgAndFixes == null)
        {
            errorMessage.setValue("");
            quickFixes.clear();
            // Hide the popup:
            if (popup != null)
            {
                hide(true);
            }
        }
        else
        {
            errorMessage.set(newMsgAndFixes.getFirst());
            quickFixes.setAll(newMsgAndFixes.getSecond());
        }
    }
    
    public boolean isShowingError()
    {
        return !errorMessage.get().isEmpty();
    }
    
    public void setType(String type)
    {
        this.type.set(type);
    }
}
