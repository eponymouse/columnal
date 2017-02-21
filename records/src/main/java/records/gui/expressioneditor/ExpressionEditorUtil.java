package records.gui.expressioneditor;

import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.VBox;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.jetbrains.annotations.NotNull;
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
    @NotNull
    protected static VBox withLabelAbove(TextField textField, String cssClass, String label, @UnknownInitialization OperandNode surrounding)
    {
        FXUtility.sizeToFit(textField, 10.0, 10.0);
        textField.getStyleClass().addAll(cssClass + "-name", "labelled-name");
        Label typeLabel = new Label(label);
        typeLabel.getStyleClass().addAll(cssClass + "-top", "labelled-top");
        enableSelection(typeLabel, surrounding);
        enableDragFrom(typeLabel, surrounding);
        VBox vBox = new VBox(typeLabel, textField);
        vBox.getStyleClass().add(cssClass);
        return vBox;
    }

    @NotNull
    protected static VBox keyword(String keyword, String cssClass, @UnknownInitialization OperandNode surrounding)
    {
        TextField t = new TextField(keyword);
        t.setEditable(false);
        t.setDisable(true);
        return withLabelAbove(t, cssClass, "", surrounding);
    }

    public static void setStyles(Label topLabel, Stream<String> parentStyles)
    {
        topLabel.getStyleClass().add(parentStyles.collect(Collectors.joining("-")) + "-child");
    }

    public static class CopiedItems implements Serializable
    {
        private static final long serialVersionUID = 3245083225504039668L;
        public final List<String> operands; // Expressions saved to string
        public final List<String> operators; // Operators as raw string
        public final boolean startsOnOperator;

        public CopiedItems(List<String> operands, List<String> operators, boolean startsOnOperator)
        {
            this.operands = operands;
            this.operators = operators;
            this.startsOnOperator = startsOnOperator;
        }
    }

    @SuppressWarnings("initialization")
    public static void enableDragFrom(Label dragSource, @UnknownInitialization OperandNode src)
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
    public static void enableSelection(Label typeLabel, @UnknownInitialization OperandNode node)
    {
        typeLabel.setOnMouseClicked(e -> {
            if (e.isDragDetect() || !e.isStillSincePress())
                return;

            if (e.isShiftDown())
                node.getParent().getEditor().extendSelectionTo(node);
            else
                node.getParent().getEditor().selectOnly(node);
            e.consume();
        });
    }
}
