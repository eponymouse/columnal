package records.gui.expressioneditor;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.gui.expressioneditor.ExpressionEditorUtil.ErrorUpdater;
import records.transformations.expression.ErrorRecorder.QuickFix;
import utility.Pair;
import utility.gui.FXUtility;

import java.util.List;

/**
 * A helper class that implements various methods when you
 * have a single text field as a ConsecutiveChild
 *
 */
abstract class TextFieldEntry<EXPRESSION> extends ChildNode<EXPRESSION> implements ConsecutiveChild<EXPRESSION>, ErrorDisplayer
{
    protected final Class<EXPRESSION> operandClass;
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
     * The text field for user entry
     */
    protected final TextField textField;

    /**
     * Permanent reference to list of contained nodes (for ExpressionNode.nodes)
     */
    protected final ObservableList<Node> nodes = FXCollections.observableArrayList();

    /**
     * The outermost container for the whole thing:
     */
    protected final VBox container;

    private final ErrorUpdater errorUpdater;

    protected TextFieldEntry(Class<EXPRESSION> operandClass, ConsecutiveBase<EXPRESSION> parent)
    {
        super(parent);
        this.operandClass = operandClass;
        this.textField = createLeaveableTextField();
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
        this.errorUpdater = ExpressionEditorUtil.installErrorShower(container, textField);
        ExpressionEditorUtil.setStyles(typeLabel, parent.getParentStyles());
        this.nodes.setAll(container);

    }


    @Override
    public ObservableList<Node> nodes()
    {
        return nodes;
    }

    @Override
    public boolean isFocused()
    {
        return textField.isFocused();
    }

    @Override
    public boolean isBlank()
    {
        return textField.getText().trim().isEmpty();
    }

    @Override
    public void focusChanged()
    {
        // Nothing to do
    }

    @Override
    public void setSelected(boolean selected)
    {
        FXUtility.setPseudoclass(container, "exp-selected", selected);
    }

    @Override
    public void setHoverDropLeft(boolean selected)
    {
        FXUtility.setPseudoclass(container, "exp-hover-drop-left", selected);
    }

    @Override
    public <C> @Nullable Pair<ConsecutiveChild<? extends C>, Double> findClosestDrop(Point2D loc, Class<C> forType)
    {
        return ConsecutiveChild.closestDropSingle(this, operandClass, container, loc, forType);
    }

    @Override
    public void focus(Focus side)
    {
        textField.requestFocus();
        textField.positionCaret(side == Focus.LEFT ? 0 : textField.getLength());
    }

    public void changed(@UnknownInitialization(ExpressionNode.class) ExpressionNode child)
    {
        parent.changed(this);
    }

    @Override
    public void showError(String error, List<QuickFix> quickFixes)
    {
        ExpressionEditorUtil.setError(container, error);
        errorUpdater.setMessageAndFixes(new Pair<>(error, quickFixes));
    }
}
