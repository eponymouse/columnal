package records.gui.expressioneditor;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.binding.ListBinding;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.PopupWindow.AnchorLocation;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.EnsuresNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;
import org.jetbrains.annotations.NotNull;
import records.transformations.expression.ErrorRecorder;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformConsumer;
import utility.Pair;
import utility.Utility;
import utility.gui.FXUtility;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by neil on 21/01/2017.
 */
public class ExpressionEditorUtil
{
    /**
     * Returns
     * @param textField
     * @param cssClass
     * @param label
     * @param surrounding
     * @param parentStyles
     * @return A pair of the VBox to display, and an action which can be used to show/clear an error on it (clear by passing null)
     */
    @NotNull
    protected static Pair<VBox, ErrorDisplayer> withLabelAbove(TextField textField, String cssClass, String label, @Nullable @UnknownInitialization ConsecutiveChild surrounding, Stream<String> parentStyles)
    {
        FXUtility.sizeToFit(textField, 10.0, 10.0);
        textField.getStyleClass().addAll(cssClass + "-name", "labelled-name");
        Label typeLabel = new Label(label);
        typeLabel.getStyleClass().addAll(cssClass + "-top", "labelled-top");
        if (surrounding != null)
        {
            enableSelection(typeLabel, surrounding);
            enableDragFrom(typeLabel, surrounding);
        }
        setStyles(typeLabel, parentStyles);
        VBox vBox = new VBox(typeLabel, textField);
        vBox.getStyleClass().add(cssClass);
        ErrorUpdater errorShower = installErrorShower(vBox, textField);
        return new Pair<>(vBox, (String s, List<ErrorRecorder.QuickFix> q) -> {
            setError(vBox, s);
            errorShower.setMessageAndFixes(new Pair<>(s, q));
        });
    }

    // Needs to listen to in the message, the focus of the text field, and changes
    // in the mouse position
    public static class ErrorUpdater
    {
        private final TextField textField;
        private final SimpleStringProperty message = new SimpleStringProperty("");
        private final ObservableList<ErrorRecorder.QuickFix> quickFixes = FXCollections.observableArrayList();
        private final VBox vBox;
        private @Nullable PopupControl popup = null;
        private boolean focused = false;
        private boolean hovering = false;

        @SuppressWarnings("initialization")
        public ErrorUpdater(VBox vBox, TextField textField)
        {
            this.vBox = vBox;
            this.textField = textField;
            Utility.addChangeListenerPlatformNN(textField.focusedProperty(), this::textFieldFocusChanged);
            Utility.addChangeListenerPlatformNN(vBox.hoverProperty(), this::mouseHoverStatusChanged);
        }

        private static class ErrorMessagePopup extends PopupControl
        {
            private final BooleanBinding hasFixes;

            @SuppressWarnings("initialization")
            public ErrorMessagePopup(StringProperty msg, ObservableList<ErrorRecorder.QuickFix> quickFixes)
            {
                Label errorLabel = new Label();
                errorLabel.getStyleClass().add("expression-error-popup");
                errorLabel.textProperty().bind(msg);

                ListView<ErrorRecorder.QuickFix> fixList = new ListView<>(quickFixes);
                // Keep reference to prevent GC:
                hasFixes = Bindings.isEmpty(quickFixes).not();
                fixList.visibleProperty().bind(hasFixes);

                fixList.setCellFactory(lv -> new ListCell<ErrorRecorder.QuickFix>() {
                    @Override
                    @OnThread(Tag.FX)
                    protected void updateItem(ErrorRecorder.QuickFix item, boolean empty) {
                        super.updateItem(item, empty);
                        setText("");
                        setGraphic(empty ? null : new TextFlow(new Text(item.getTitle())));
                    }
                });

                VBox container = new VBox(errorLabel, fixList);

                setSkin(new Skin<ErrorMessagePopup>()
                {
                    @Override
                    @OnThread(Tag.FX)
                    public ErrorMessagePopup getSkinnable()
                    {
                        return ErrorMessagePopup.this;
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
                });
            }
        }

        private void show()
        {
            // Shouldn't be non-null already, but just in case:
            if (popup != null)
            {
                hide();
            }
            if (vBox.getScene() != null)
            {
                Bounds screenBounds = vBox.localToScreen(vBox.getBoundsInLocal());
                popup = new ErrorMessagePopup(message, quickFixes);
                popup.setAnchorLocation(AnchorLocation.CONTENT_BOTTOM_LEFT);
                popup.show(vBox, screenBounds.getMinX(), screenBounds.getMinY());
            }
        }

        @RequiresNonNull("popup")
        // Can't have an ensuresnull check
        private void hide()
        {
            popup.hide();
            popup = null;
        }

        public void mouseHoverStatusChanged(boolean newHovering)
        {
            if (newHovering)
            {
                if (popup == null && !message.get().isEmpty())
                {
                    show();
                }
            }
            else
            {
                // If mouse leaves, then we hide only if not focused:
                if (!focused && popup != null)
                {
                    hide();
                }
            }
            this.hovering = newHovering;
        }

        public void textFieldFocusChanged(boolean newFocused)
        {
            if (newFocused)
            {
                System.out.println("Focused, message is " + message.get() + " popup " + popup);
                if (!message.get().isEmpty())
                {
                    show();
                }
            }
            else
            {
                // If focus leaves, then even if you are still hovering, we hide:
                if (popup != null)
                {
                    hide();
                }
            }
            this.focused = newFocused;
        }

        public void setMessageAndFixes(@Nullable Pair<String, List<ErrorRecorder.QuickFix>> newMsgAndFixes)
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
                // If we are focused or hovering already, now need to show message
                if (focused || hovering)
                {
                    show();
                }
            }
        }
    }

    public static ErrorUpdater installErrorShower(VBox vBox, TextField textField)
    {
        return new ErrorUpdater(vBox, textField);
    }

    public static void setError(VBox vBox, @Nullable String s)
    {
        FXUtility.setPseudoclass(vBox, "exp-error", s != null);
    }

    @NotNull
    protected static VBox keyword(String keyword, String cssClass, @Nullable @UnknownInitialization OperandNode surrounding, Stream<String> parentStyles)
    {
        TextField t = new TextField(keyword);
        t.setEditable(false);
        t.setDisable(true);
        return withLabelAbove(t, cssClass, "", surrounding, parentStyles).getFirst();
    }

    public static void setStyles(Label topLabel, Stream<String> parentStyles)
    {
        topLabel.getStyleClass().add(parentStyles.collect(Collectors.joining("-")) + "-child");
    }

    public static class CopiedItems implements Serializable
    {
        private static final long serialVersionUID = 3245083225504039668L;
        /**
         * Expressions are saved to string, operators are there as the raw string
         * They strictly alternate (operand-operator-operand etc) and the boolean
         * tracks whether first one was an operator (otherwise: operand)
         */
        public final List<String> items;
        public final boolean startsOnOperator;

        public CopiedItems(List<String> items, boolean startsOnOperator)
        {
            this.items = items;
            this.startsOnOperator = startsOnOperator;
        }
    }

    @SuppressWarnings("initialization")
    public static void enableDragFrom(Label dragSource, @UnknownInitialization ConsecutiveChild src)
    {
        ExpressionEditor editor = src.getParent().getEditor();
        dragSource.setOnDragDetected(e -> {
            editor.ensureSelectionIncludes(src);
            @Nullable CopiedItems selection = editor.getSelection();
            if (selection != null)
            {
                editor.setSelectionLocked(true);
                Dragboard db = dragSource.startDragAndDrop(TransferMode.MOVE);
                db.setContent(Collections.singletonMap(FXUtility.getTextDataFormat("Expression"), selection));
            }
            e.consume();
        });
        dragSource.setOnDragDone(e -> {
            editor.setSelectionLocked(false);
            if (e.getTransferMode() != null)
            {
                editor.removeSelectedItems();
            }
            e.consume();
        });
    }

    @SuppressWarnings("initialization")
    public static void enableSelection(Label typeLabel, @UnknownInitialization ConsecutiveChild node)
    {
        typeLabel.setOnMouseClicked(e -> {
            if (!e.isStillSincePress())
                return;

            if (e.isShiftDown())
                node.getParent().getEditor().extendSelectionTo(node);
            else
                node.getParent().getEditor().selectOnly(node);
            e.consume();
        });
    }
}
