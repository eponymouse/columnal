package records.gui.expressioneditor;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.ColumnId;
import records.data.datatype.DataType;
import records.data.datatype.DataType.TagType;
import records.data.datatype.TypeId;
import records.transformations.expression.Expression;
import records.transformations.expression.TagExpression;
import utility.FXPlatformConsumer;
import utility.Pair;

import java.util.List;

/**
 * Created by neil on 21/01/2017.
 */
public class TagExpressionNode implements ExpressionParent, OperandNode
{
    private final TextField tagNameField;
    private final ExpressionParent parent;
    private final TypeId typeName;
    private final TagType<DataType> tagType;
    private final @Nullable Consecutive inner;
    private final ObservableList<Node> nodes;
    private final VBox labelledField;

    @SuppressWarnings("initialization")
    public TagExpressionNode(ExpressionParent parent, TypeId typeName, TagType<DataType> tagType)
    {
        this.parent = parent;
        this.typeName = typeName;
        this.tagType = tagType;
        this.tagNameField = new LeaveableTextField(this, this);
        tagNameField.setText(tagType.getName());
        labelledField = ExpressionEditorUtil.withLabelAbove(tagNameField, "tag", "tag " + typeName.getRaw() + ":");
        if (tagType.getInner() == null)
            inner = null;
        else
            inner = new Consecutive(this, c -> labelledField, null);

        if (inner == null)
            nodes = FXCollections.observableArrayList(labelledField);
        else
            nodes = inner.nodes();
    }

    @Override
    public List<ColumnId> getAvailableColumns()
    {
        return parent.getAvailableColumns();
    }

    @Override
    public List<String> getAvailableVariables(ExpressionNode child)
    {
        return parent.getAvailableVariables(this);
    }

    @Override
    public List<DataType> getAvailableTaggedTypes()
    {
        return parent.getAvailableTaggedTypes();
    }

    @Override
    public boolean isTopLevel()
    {
        return false;
    }

    @Override
    public void changed(ExpressionNode child)
    {
        parent.changed(this);
    }

    @Override
    public void focusRightOf(ExpressionNode child)
    {
        parent.focusRightOf(this);
    }

    @Override
    public void focusLeftOf(ExpressionNode child)
    {
        tagNameField.positionCaret(tagNameField.getLength());
        tagNameField.requestFocus();
    }

    @Override
    public @Nullable DataType inferType()
    {
        return null;
    }

    @Override
    public OperandNode prompt(String prompt)
    {
        return this;
    }

    @Override
    public @Nullable Expression toExpression(FXPlatformConsumer<Object> onError)
    {
        Expression innerExp;
        if (inner == null)
            innerExp = null;
        else
        {
            innerExp = inner.toExpression(onError);
            if (innerExp == null)
                return null;
        }
        return new TagExpression(new Pair<>(typeName.getRaw(), tagType.getName()), innerExp);
    }

    @Override
    public OperandNode focusWhenShown()
    {
        if (inner != null)
            inner.focus(Focus.LEFT);
        return this;
    }

    @Override
    public ObservableList<Node> nodes()
    {
        return nodes;
    }

    @Override
    public void focus(Focus side)
    {
        if (side == Focus.LEFT)
        {
            tagNameField.positionCaret(0);
            tagNameField.requestFocus();
        }
        else
        {
            if (inner != null)
            {
                inner.focus(Focus.RIGHT);
            }
            else
            {
                tagNameField.positionCaret(tagNameField.getLength());
                tagNameField.requestFocus();
            }
        }
    }
}
