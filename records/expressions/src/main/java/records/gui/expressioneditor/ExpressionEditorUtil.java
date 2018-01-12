package records.gui.expressioneditor;

import javafx.scene.control.*;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.VBox;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.jetbrains.annotations.NotNull;
import records.transformations.expression.ErrorAndTypeRecorder.QuickFix;
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
        ExpressionInfoDisplay errorShower = installErrorShower(vBox, typeLabel, textField);
        return new Pair<>(vBox, new ErrorDisplayer()
        {
            @Override
            public boolean isShowingError()
            {
                return errorShower.isShowingError();
            }

            @Override
            public void showError(String s, List<QuickFix> q)
            {
                setError(vBox, s);
                errorShower.setMessageAndFixes(new Pair<>(s, q));
            }

            @Override
            public void showType(String type)
            {
                errorShower.setType(type);
            }
        });
    }

    public static ExpressionInfoDisplay installErrorShower(VBox vBox, Label topLabel, TextField textField)
    {
        return new ExpressionInfoDisplay(vBox, topLabel, textField);
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
