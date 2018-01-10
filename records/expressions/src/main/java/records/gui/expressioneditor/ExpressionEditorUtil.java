package records.gui.expressioneditor;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Bounds;
import javafx.scene.control.*;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;
import org.controlsfx.control.PopOver;
import org.jetbrains.annotations.NotNull;
import records.transformations.expression.ErrorAndTypeRecorder;
import records.transformations.expression.ErrorAndTypeRecorder.QuickFix;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
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
    protected static <E> Pair<VBox, ErrorDisplayer> withLabelAbove(TextField textField, String cssClass, String label, @Nullable @UnknownInitialization ConsecutiveChild<E> surrounding, Stream<String> parentStyles)
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
        return new Pair<>(vBox, new ErrorDisplayer()
        {
            @Override
            public void showError(String s, List<QuickFix> q)
            {
                setError(vBox, s);
                errorShower.setMessageAndFixes(new Pair<>(s, q));
            }

            @Override
            public void showType(String type)
            {
                showTypeAsTooltip(type, typeLabel);
            }
        });
    }

    static void showTypeAsTooltip(String type, Label typeLabel)
    {
        typeLabel.setTooltip(new Tooltip("Type: " + type));
    }

    // Needs to listen to in the message, the focus of the text field, and changes
    // in the mouse position
    public static class ErrorUpdater
    {
        private final TextField textField;
        private final SimpleStringProperty message = new SimpleStringProperty("");
        private final ObservableList<ErrorAndTypeRecorder.QuickFix> quickFixes = FXCollections.observableArrayList();
        private final VBox vBox;
        private @Nullable PopOver popup = null;
        private boolean focused = false;
        private boolean hovering = false;

        @SuppressWarnings("initialization")
        public ErrorUpdater(VBox vBox, TextField textField)
        {
            this.vBox = vBox;
            this.textField = textField;
            FXUtility.addChangeListenerPlatformNN(textField.focusedProperty(), this::textFieldFocusChanged);
            FXUtility.addChangeListenerPlatformNN(vBox.hoverProperty(), this::mouseHoverStatusChanged);
        }

        private static class ErrorMessagePopup extends PopOver
        {
            private final BooleanBinding hasFixes;

            @SuppressWarnings("initialization")
            public ErrorMessagePopup(StringProperty msg, ObservableList<ErrorAndTypeRecorder.QuickFix> quickFixes)
            {
                Label errorLabel = new Label();
                errorLabel.getStyleClass().add("expression-error-popup");
                errorLabel.textProperty().bind(msg);

                ListView<ErrorAndTypeRecorder.QuickFix> fixList = new ListView<>(quickFixes);
                // Keep reference to prevent GC:
                hasFixes = Bindings.isEmpty(quickFixes).not();
                fixList.visibleProperty().bind(hasFixes);

                fixList.setCellFactory(lv -> new ListCell<ErrorAndTypeRecorder.QuickFix>() {
                    @Override
                    @OnThread(Tag.FX)
                    protected void updateItem(ErrorAndTypeRecorder.QuickFix item, boolean empty) {
                        super.updateItem(item, empty);
                        setText("");
                        setGraphic(empty ? null : new TextFlow(new Text(item.getTitle())));
                    }
                });

                VBox container = new VBox(errorLabel, fixList);

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
            if (vBox.getScene() != null)
            {
                Bounds screenBounds = vBox.localToScreen(vBox.getBoundsInLocal());
                popup = new ErrorMessagePopup(message, quickFixes);
                popup.show(vBox);
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

        public void setMessageAndFixes(@Nullable Pair<String, List<ErrorAndTypeRecorder.QuickFix>> newMsgAndFixes)
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
    protected static <E> Pair<VBox, ErrorDisplayer> keyword(String keyword, String cssClass, @Nullable @UnknownInitialization OperandNode<E> surrounding, Stream<String> parentStyles)
    {
        TextField t = new TextField(keyword);
        t.setEditable(false);
        t.setDisable(true);
        return withLabelAbove(t, cssClass, "", surrounding, parentStyles);
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
    public static <E> void enableDragFrom(Label dragSource, @UnknownInitialization ConsecutiveChild<E> src)
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
    public static <E> void enableSelection(Label typeLabel, @UnknownInitialization ConsecutiveChild<E> node)
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
