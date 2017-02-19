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

import java.util.Collections;
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
        textField.getStyleClass().addAll(cssClass + "-name", "labelled-name");
        Label typeLabel = new Label(label);
        typeLabel.getStyleClass().addAll(cssClass + "-top", "labelled-top");
        enableSelection(typeLabel, surrounding);
        enableDragFrom(typeLabel, surrounding);
        VBox vBox = new VBox(typeLabel, textField);
        vBox.getStyleClass().add(cssClass);
        return vBox;
    }

    public static void setStyles(Label topLabel, Stream<String> parentStyles)
    {
        topLabel.getStyleClass().add(parentStyles.collect(Collectors.joining("-")) + "-child");
    }

    @SuppressWarnings("initialization")
    public static void enableDragFrom(Label dragSource, @UnknownInitialization OperandNode src)
    {
        ExpressionEditor editor = src.getParent().getEditor();
        dragSource.setOnDragDetected(e -> {
            @Nullable String selectionAsText = editor.getSelectionAsText();
            editor.ensureSelectionIncludes(src);
            if (selectionAsText != null)
            {
                Dragboard db = dragSource.startDragAndDrop(TransferMode.MOVE);
                db.setContent(Collections.singletonMap(FXUtility.getTextDataFormat("Expression"), selectionAsText));
            }
            e.consume();
        });
    }

    @SuppressWarnings("initialization")
    public static void enableSelection(Label typeLabel, @UnknownInitialization OperandNode node)
    {
        typeLabel.setOnMouseClicked(e -> {
            if (e.isShiftDown())
                node.getParent().getEditor().extendSelectionTo(node);
            else
                node.getParent().getEditor().selectOnly(node);
            e.consume();
        });
    }
}
