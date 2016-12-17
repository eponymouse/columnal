package records.gui.expressioneditor;

import javafx.scene.Node;
import javafx.scene.layout.FlowPane;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.ColumnId;
import records.data.Table;
import records.data.datatype.DataType;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.expression.Expression;
import utility.Utility;

import java.util.Collections;
import java.util.List;

/**
 * Created by neil on 17/12/2016.
 */
public class ExpressionEditor implements ExpressionParent
{
    private final FlowPane container;
    private final @Nullable DataType type;
    private final @Nullable Table srcTable;
    private ExpressionNode root;

    @SuppressWarnings("initialization")
    public ExpressionEditor(@Nullable Expression startingValue, @Nullable Table srcTable, @Nullable DataType type)
    {
        this.container = new FlowPane();
        this.srcTable = srcTable;
        this.root = new GeneralEntry("", this);
        this.type = type;
        container.getChildren().setAll(root.nodes());
    }

    public Node getContainer()
    {
        return container;
    }

    @Override
    @SuppressWarnings("intern")
    public void replace(ExpressionNode oldNode, ExpressionNode newNode)
    {
        if (oldNode == root)
        {
            root = newNode;
            container.getChildren().setAll(root.nodes());
        }
        // else log internal issue
    }

    @Override
    public @Nullable DataType getType(ExpressionNode child)
    {
        return type;
    }

    @Override
    public List<ColumnId> getAvailableColumns()
    {
        if (srcTable == null)
            return Collections.emptyList();
        try
        {
            return srcTable.getData().getColumnIds();
        }
        catch (UserException e)
        {
            Utility.log(e);
            return Collections.emptyList();
        }
    }
}
