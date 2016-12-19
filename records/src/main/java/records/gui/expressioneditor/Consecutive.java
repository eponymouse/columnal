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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Created by neil on 17/12/2016.
 */
public class Consecutive extends ExpressionNode implements ExpressionParent
{
    private final ObservableList<Node> nodes;
    // The boolean value is only used during updateListeners, will be true other times
    private final IdentityHashMap<ExpressionNode, Boolean> listeningTo = new IdentityHashMap<>();
    private final ListChangeListener<Node> childrenNodeListener;
    private final ObservableList<ExpressionNode> children;
    private final Node prefixNode;
    private final Node suffixNode;
    private final @Nullable ExpressionParent parent;

    @SuppressWarnings("initialization")
    public Consecutive(List<Function<ExpressionParent, ExpressionNode>> initial, ExpressionParent parent, @Nullable Node prefixNode, @Nullable Node suffixNode)
    {
        this.parent = parent;
        nodes = FXCollections.observableArrayList();
        children = FXCollections.observableArrayList();
        this.prefixNode = prefixNode;
        this.suffixNode = suffixNode;
        this.childrenNodeListener = c -> {
            updateNodes();
        };
        children.addListener((ListChangeListener<? super @UnknownInterned @UnknownKeyFor ExpressionNode>) c -> {
            updateNodes();
            updateListeners();
        });
        children.setAll(Utility.<Function<ExpressionParent, ExpressionNode>, ExpressionNode>mapList(initial, f -> f.apply(this)));
        if (!(children.get(children.size() - 1) instanceof OperatorEntry))
            children.add(new OperatorEntry("", this));
    }

    private void updateListeners()
    {
        // Make them all as old (false)
        listeningTo.replaceAll((e, b) -> false);
        // Merge new ones:
        for (ExpressionNode child : children)
        {
            // No need to listen again if already present as we're already listening
            if (listeningTo.get(child) == null)
                child.nodes().addListener(childrenNodeListener);
            listeningTo.put(child, true);
        }
        // Stop listening to old:
        for (Iterator<Entry<ExpressionNode, Boolean>> iterator = listeningTo.entrySet().iterator(); iterator.hasNext(); )
        {
            Entry<ExpressionNode, Boolean> e = iterator.next();
            if (e.getValue() == false)
            {
                e.getKey().nodes().removeListener(childrenNodeListener);
                iterator.remove();
            }
        }

    }

    private void updateNodes()
    {
        List<Node> childrenNodes = new ArrayList<Node>(children.stream().flatMap(e -> e.nodes().stream()).collect(Collectors.<Node>toList()));
        if (this.prefixNode != null)
            childrenNodes.add(0, this.prefixNode);
        if (this.suffixNode != null)
            childrenNodes.add(this.suffixNode);
        nodes.setAll(childrenNodes);
    }

    @Override
    public ObservableList<Node> nodes()
    {
        return nodes;
    }

    @Override
    public void focus()
    {
        children.get(0).focus();
    }

    @Override
    public void replace(ExpressionNode oldNode, @Nullable ExpressionNode newNode)
    {
        int index = getChildIndex(oldNode);
        System.err.println("Replacing " + oldNode + " with " + newNode + " index " + index);
        if (index != -1)
        {
            //Utility.logStackTrace("Removing " + oldNode + " from " + this);
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
        if (parent != null)
            return parent.getAvailableColumns();
        else
            return Collections.emptyList();
    }

    @Override
    public List<String> getAvailableVariables()
    {
        if (parent != null)
            return parent.getAvailableVariables();
        else
            return Collections.emptyList();
    }

    @Override
    public boolean isTopLevel()
    {
        return false;
    }

    @Override
    public void focusRightOfSelf()
    {
        if (parent != null)
            parent.focusRightOf(this);
    }

    @Override
    public void focusRightOf(ExpressionNode child)
    {
        int index = getChildIndex(child);
        if (index < children.size() - 1)
            children.get(index + 1).focus();
        else
            focusRightOfSelf();
    }
}
