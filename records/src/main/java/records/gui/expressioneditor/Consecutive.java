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
    public void deleteOneFromEnd()
    {
        // Ignore, I think (or, fold this one into outer one?)
    }

    @Override
    public void deleteOneFromBegin()
    {
        // Ignore, I think (or, fold this one into outer one?)
    }

    @Override
    public void replace(ExpressionNode oldNode, @Nullable ExpressionNode newNode)
    {
        int index = children.indexOf(oldNode);
        if (index != -1)
        {
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
        if (rightOf == null)
        {
            children.addAll(add);
        }
        else
        {
            int index = children.indexOf(rightOf);
            if (index != -1)
                children.addAll(index + 1, Arrays.asList(add));
        }
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

    @Override
    public void deleteOneLeftOf(ExpressionNode child)
    {
        int index = children.indexOf(child);
        if (index > 0)
            children.get(index - 1).deleteOneFromEnd();
    }

    @Override
    public void deleteOneRightOf(ExpressionNode child)
    {
        int index = children.indexOf(child);
        if (index != -1 && index != children.size() - 1)
            children.get(index + 1).deleteOneFromBegin();
    }
}
