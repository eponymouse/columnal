package records.gui.expressioneditor;

import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import org.checkerframework.checker.interning.qual.UnknownInterned;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.UnknownKeyFor;
import records.data.ColumnId;
import records.data.datatype.DataType;
import utility.Utility;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by neil on 17/12/2016.
 */
public class Consecutive extends ExpressionNode implements ExpressionParent
{
    private final ObservableList<Node> nodes;
    private final ObservableList<ExpressionNode> children;

    public Consecutive(List<ExpressionNode> initial, ExpressionParent parent)
    {
        super(parent);
        nodes = FXCollections.observableArrayList();
        children = FXCollections.observableArrayList();
        children.addListener((ListChangeListener<? super @UnknownInterned @UnknownKeyFor ExpressionNode>) c -> {
            nodes.setAll(children.stream().flatMap(e -> e.nodes().stream()).collect(Collectors.<Node>toList()));
        });
        children.setAll(initial);
    }

    @Override
    public ObservableList<Node> nodes()
    {
        return nodes;
    }

    @Override
    public void replace(ExpressionNode oldNode, @Nullable ExpressionNode newNode)
    {
        int index = getChildIndex(oldNode);
        if (index != -1)
        {
            Utility.logStackTrace("Removing " + oldNode + " from " + this);
            if (newNode != null)
                children.set(index, newNode);
            else
                children.remove(index);
        }
        //TODO: merge consecutive general entries
    }

    @Override
    public void addToRight(@Nullable ExpressionNode rightOf, ExpressionNode... add)
    {
        //Utility.logStackTrace("Adding " + add[0] + " to " + this);
        if (rightOf == null)
        {
            children.addAll(add);
        }
        else
        {
            int index = getChildIndex(rightOf);
            if (index != -1)
                children.addAll(index + 1, Arrays.asList(add));
        }
    }

    private int getChildIndex(@Nullable ExpressionNode rightOf)
    {
        int index = children.indexOf(rightOf);
        if (index == -1)
            Utility.logStackTrace("Asked for index but " + rightOf + " not a child of parent " + this);
        return index;
    }

    @Override
    public @Nullable DataType getType(ExpressionNode child)
    {
        return null;
    }

    @Override
    public List<ColumnId> getAvailableColumns()
    {
        return parent.getAvailableColumns();
    }

}
