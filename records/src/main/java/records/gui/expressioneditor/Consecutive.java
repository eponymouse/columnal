package records.gui.expressioneditor;

import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import org.checkerframework.checker.interning.qual.Interned;
import org.checkerframework.checker.interning.qual.UnknownInterned;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.UnknownKeyFor;
import records.data.ColumnId;
import records.data.datatype.DataType;
import records.transformations.expression.Expression;
import records.transformations.expression.MatchExpression;
import records.transformations.expression.MatchExpression.PatternMatch;
import utility.FXPlatformConsumer;
import utility.Utility;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Consecutive implements all the methods of OperandNode but deliberately
 * does not extend it.  For that, use Bracketed.
 */
public @Interned class Consecutive implements ExpressionParent, ExpressionNode
{
    private final ObservableList<Node> nodes;
    // The boolean value is only used during updateListeners, will be true other times
    private final IdentityHashMap<ExpressionNode, Boolean> listeningTo = new IdentityHashMap<>();
    private final ListChangeListener<Node> childrenNodeListener;
    private final ObservableList<OperandNode> operands;
    private final ObservableList<OperatorEntry> operators;
    private final Node prefixNode;
    private final Node suffixNode;
    private final @Nullable ExpressionParent parent;
    private @Nullable String prompt;

    @SuppressWarnings("initialization")
    public Consecutive(ExpressionParent parent, @Nullable Node prefixNode, @Nullable Node suffixNode)
    {
        this.parent = parent;
        nodes = FXCollections.observableArrayList();
        operands = FXCollections.observableArrayList();
        operators = FXCollections.observableArrayList();
        this.prefixNode = prefixNode;
        this.suffixNode = suffixNode;
        this.childrenNodeListener = c -> {
            updateNodes();
        };
        operands.addListener((ListChangeListener<? super @UnknownInterned @UnknownKeyFor OperandNode>) c -> {
            updateNodes();
            updateListeners();
        });
        operators.addListener((ListChangeListener<? super @UnknownInterned @UnknownKeyFor OperatorEntry>) c -> {
            updateNodes();
            updateListeners();
        });
        // Must do operator first:
        operators.add(new OperatorEntry("", this));
        operands.add(new GeneralEntry("", this));

    }

    private void updateListeners()
    {
        // Make them all as old (false)
        listeningTo.replaceAll((e, b) -> false);
        // Merge new ones:

        Stream.<ExpressionNode>concat(operands.stream(), operators.stream()).forEach(child -> {
            // No need to listen again if already present as we're already listening
            if (listeningTo.get(child) == null)
                child.nodes().addListener(childrenNodeListener);
            listeningTo.put(child, true);
        });
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
        if (operands.size() == 1 && prompt != null)
            operands.get(0).prompt(prompt);
        else
        {
            for (OperandNode child : operands)
            {
                child.prompt("");
            }
        }

        List<Node> childrenNodes = new ArrayList<Node>();
        for (int i = 0; i < operands.size(); i++)
        {
            childrenNodes.addAll(operands.get(i).nodes());
            childrenNodes.addAll(operators.get(i).nodes());
        }
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
    public void focus(Focus side)
    {
        if (side == Focus.LEFT)
            operands.get(0).focus(side);
        else
            operands.get(operands.size() - 1).focus(side);
    }

    public void replace(OperandNode oldNode, @Nullable OperandNode newNode)
    {
        int index = getOperandIndex(oldNode);
        //System.err.println("Replacing " + oldNode + " with " + newNode + " index " + index);
        if (index != -1)
        {
            //Utility.logStackTrace("Removing " + oldNode + " from " + this);
            if (newNode != null)
                operands.set(index, newNode);
            else
                operands.remove(index);
        }
    }

    public void addOperandToRight(OperatorEntry rightOf, OperandNode operandNode)
    {
        // Must add operand and operator
        int index = operators.indexOf(rightOf);
        if (index != -1)
        {
            // Everything is keyed on operands size, so must add operator first:
            operators.add(index+1, new OperatorEntry("", this));
            operands.add(index+1, operandNode);
        }
    }


    public void setOperatorToRight(OperandNode rightOf, String operator)
    {
        int index = getOperandIndex(rightOf);
        if (index != -1)
        {
            if (!operators.get(index).fromBlankTo(operator))
            {
                // Add new operator and new operand:
                // Everything is keyed on operands size, so must add operator first:
                operators.add(index, new OperatorEntry(operator, this));
                operands.add(index+1, new GeneralEntry("", this).focusWhenShown());
            }
        }
    }

    private int getOperandIndex(OperandNode operand)
    {
        int index = operands.indexOf(operand);
        if (index == -1)
            Utility.logStackTrace("Asked for index but " + operand + " not a child of parent " + this);
        return index;
    }

    @Override
    public @Nullable DataType getType(ExpressionNode child)
    {
        //TODO: work it out from surrounding
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
    public List<String> getAvailableVariables(ExpressionNode child)
    {
        if (parent != null)
            return parent.getAvailableVariables(this);
        else
            return Collections.emptyList();
    }

    @Override
    public boolean isTopLevel()
    {
        return false;
    }

    @Override
    public void focusRightOf(ExpressionNode child)
    {
        if (child instanceof OperandNode && operands.contains(child))
        {
            int index = getOperandIndex((OperandNode)child);
            operators.get(index).focus(Focus.LEFT);
        }
        else
        {
            int index = operators.indexOf(child);
            if (index < operators.size() - 1)
                operands.get(index + 1).focus(Focus.LEFT);
            else if (parent != null)
                parent.focusRightOf(this);
        }
    }

    @Override
    public void focusLeftOf(ExpressionNode child)
    {
        if (child instanceof OperandNode && operands.contains(child))
        {
            int index = getOperandIndex((OperandNode) child);
            if (index > 0)
                operators.get(index - 1).focus(Focus.RIGHT);
            else if (parent != null)
                parent.focusLeftOf(this);
        }
        else
        {
            int index = operators.indexOf(child);
            if (index != -1)
                operands.get(index).focus(Focus.RIGHT);
        }
    }

    public List<String> getDeclaredVariables()
    {
        if (isValidAsMatchVariable(operands.get(0)))
        {
            return operands.get(0).getDeclaredVariables();
        }
        return Collections.emptyList();
    }

    private boolean isValidAsMatchVariable(OperandNode expressionNode)
    {
        return expressionNode == operands.get(0) && operands.size() <= 2 && parent instanceof ClauseNode && ((ClauseNode)parent).isMatchNode(this);
    }

    public Consecutive focusWhenShown()
    {
        operands.get(0).focusWhenShown();
        return this;
    }

    public Consecutive prompt(String value)
    {
        prompt = value;
        updateNodes();
        return this;
    }

    public @Nullable Expression toExpression(FXPlatformConsumer<Object> onError)
    {
        return null; //TODO
    }

    public @Nullable DataType inferType()
    {
        return null; //TODO
    }

    public @Nullable List<Expression> toArgs()
    {
        return null; // TODO
    }

    public @Nullable Function<MatchExpression, PatternMatch> toPattern()
    {
        return null;
    }
}
