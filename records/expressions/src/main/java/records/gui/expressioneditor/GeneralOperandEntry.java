package records.gui.expressioneditor;

import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.NonNull;
import records.transformations.expression.ErrorAndTypeRecorder.QuickFix;
import utility.Pair;
import utility.gui.FXUtility;

import java.util.List;

/**
 * A helper class that implements various methods when you
 * have a single text field as a ConsecutiveChild
 *
 */
abstract class GeneralOperandEntry<EXPRESSION extends @NonNull Object, SEMANTIC_PARENT> extends EntryNode<EXPRESSION, SEMANTIC_PARENT> implements ConsecutiveChild<EXPRESSION>, ErrorDisplayer
{
    /**
     * A label to the left of the text-field, used for displaying things like the
     * arrows on column reference
     */
    protected final Label prefix;

    /**
     * The label which sits at the top describing the type
     */
    protected final Label typeLabel;

    /**
     * The outermost container for the whole thing:
     */
    protected final VBox container;

    private final ExpressionInfoDisplay expressionInfoDisplay;

    protected GeneralOperandEntry(Class<EXPRESSION> operandClass, ConsecutiveBase<EXPRESSION, SEMANTIC_PARENT> parent)
    {
        super(parent, operandClass);
        textField.getStyleClass().add("entry-field");
        parent.getEditor().registerFocusable(textField);

        FXUtility.sizeToFit(textField, null, null);
        typeLabel = new Label();
        typeLabel.getStyleClass().addAll("entry-type", "labelled-top");
        ExpressionEditorUtil.enableSelection(typeLabel, this);
        ExpressionEditorUtil.enableDragFrom(typeLabel, this);
        prefix = new Label();
        container = new VBox(typeLabel, new HBox(prefix, textField));
        container.getStyleClass().add("entry");
        this.expressionInfoDisplay = ExpressionEditorUtil.installErrorShower(container, typeLabel, textField);
        ExpressionEditorUtil.setStyles(typeLabel, parent.getParentStyles());
    }

    @Override
    public boolean isBlank()
    {
        return textField.getText().trim().isEmpty();
    }

    @Override
    public void setSelected(boolean selected)
    {
        FXUtility.setPseudoclass(container, "exp-selected", selected);
    }

    public void changed(@UnknownInitialization(EEDisplayNode.class) EEDisplayNode child)
    {
        parent.changed(this);
    }

    @Override
    public void showError(String error, List<QuickFix> quickFixes)
    {
        ExpressionEditorUtil.setError(container, error);
        expressionInfoDisplay.setMessageAndFixes(new Pair<>(error, quickFixes));
    }

    @Override
    public void showType(String type)
    {
        expressionInfoDisplay.setType(type);
    }
}
