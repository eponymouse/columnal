package records.gui.expressioneditor;

import javafx.scene.control.*;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.VBox;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.jetbrains.annotations.NotNull;
import records.data.datatype.DataType;
import records.gui.TypeDialog;
import records.transformations.expression.ErrorAndTypeRecorder.QuickFix;
import records.transformations.expression.Expression;
import records.transformations.expression.FixedTypeExpression;
import records.transformations.expression.LoadableExpression;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformConsumer;
import utility.Pair;
import utility.gui.FXUtility;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
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
    protected static <E extends LoadableExpression<E, P>, P> Pair<VBox, ErrorDisplayer<E>> withLabelAbove(TextField textField, String cssClass, String label, @Nullable @UnknownInitialization ConsecutiveChild<?, ?> surrounding, ExpressionEditor editor, FXPlatformConsumer<E> replaceWithFixed, Stream<String> parentStyles)
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
        return new Pair<>(vBox, new ErrorDisplayer<E>()
        {
            @Override
            public boolean isShowingError()
            {
                return errorShower.isShowingError();
            }

            @Override
            public void showError(String s, List<QuickFix<E>> q)
            {
                setError(vBox, s);
                errorShower.setMessageAndFixes(new Pair<>(s, q), editor.getWindow(), editor.getTableManager(), replaceWithFixed);
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
    protected static <E extends LoadableExpression<E, P>, P> Pair<VBox, ErrorDisplayer<E>> keyword(String keyword, String cssClass, @Nullable @UnknownInitialization OperandNode<?, ?> surrounding, ExpressionEditor expressionEditor, FXPlatformConsumer<E> replace, Stream<String> parentStyles)
    {
        TextField t = new TextField(keyword);
        t.setEditable(false);
        t.setDisable(true);
        return withLabelAbove(t, cssClass, "", surrounding, expressionEditor, replace, parentStyles);
    }

    public static void setStyles(Label topLabel, Stream<String> parentStyles)
    {
        topLabel.getStyleClass().add(parentStyles.collect(Collectors.joining("-")) + "-child");
    }

    @OnThread(Tag.Any)
    public static List<QuickFix<Expression>> quickFixesForTypeError(Expression src, @Nullable DataType fix)
    {
        List<QuickFix<Expression>> quickFixes = new ArrayList<>();
        quickFixes.add(new QuickFix<>("Set type...", params -> {
            TypeDialog typeDialog = new TypeDialog(params.parentWindow, params.tableManager.getTypeManager(), false);
            @Nullable DataType dataType = typeDialog.showAndWait().orElse(Optional.empty()).orElse(null);
            if (dataType != null)
            {
                return FixedTypeExpression.fixType(dataType, src);
            }
            else
            {
                return src;
            }
        }));
        if (fix != null)
        {
            @NonNull DataType fixFinal = fix;
            quickFixes.add(new QuickFix<>("Set type: " + fix, p -> FixedTypeExpression.fixType(fixFinal, src)));
        }
        return quickFixes;
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
    public static <E extends LoadableExpression<E, P>, P> void enableDragFrom(Label dragSource, @UnknownInitialization ConsecutiveChild<E, P> src)
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
    public static <E extends LoadableExpression<E, P>, P> void enableSelection(Label typeLabel, @UnknownInitialization ConsecutiveChild<E, P> node)
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
