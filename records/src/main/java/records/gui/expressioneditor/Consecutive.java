package records.gui.expressioneditor;

import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ListChangeListener.Change;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.interning.qual.Interned;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.Column;
import records.data.datatype.DataType;
import records.data.datatype.TypeManager;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.expression.AddSubtractExpression;
import records.transformations.expression.AddSubtractExpression.Op;
import records.transformations.expression.DivideExpression;
import records.transformations.expression.EqualExpression;
import records.transformations.expression.Expression;
import records.transformations.expression.MatchExpression;
import records.transformations.expression.MatchExpression.PatternMatch;
import records.transformations.expression.TimesExpression;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformConsumer;
import utility.Pair;
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
 * does not extend it because Consecutive by itself is not a valid
 * operand.  For that, use Bracketed.
 */
public @Interned class Consecutive implements ExpressionParent, ExpressionNode
{
    private final ObservableList<Node> nodes;
    // The boolean value is only used during updateListeners, will be true other times
    private final IdentityHashMap<ExpressionNode, Boolean> listeningTo = new IdentityHashMap<>();
    private @MonotonicNonNull ListChangeListener<Node> childrenNodeListener;
    private final ObservableList<OperandNode> operands;
    private final ObservableList<OperatorEntry> operators;
    private final @Nullable Node prefixNode;
    private final @Nullable Node suffixNode;
    private final @Nullable ExpressionParent parent;
    private @Nullable String prompt = null;

    public Consecutive(@Nullable ExpressionParent parent, @Nullable Node prefixNode, @Nullable Node suffixNode)
    {
        this.parent = parent;
        nodes = FXCollections.observableArrayList();
        operands = FXCollections.observableArrayList();
        operators = FXCollections.observableArrayList();

        this.prefixNode = prefixNode;
        this.suffixNode = suffixNode;
        Utility.listen(operands, c -> {
            updateNodes();
            updateListeners();
        });
        Utility.listen(operators, c -> {
            updateNodes();
            updateListeners();
        });
        initializeContent();
    }

    private ListChangeListener<Node> getChildrenNodeListener(@UnknownInitialization(Consecutive.class) Consecutive this)
    {
        if (childrenNodeListener == null)
        {
            this.childrenNodeListener = c ->
            {
                updateNodes();
            };
        }
        return childrenNodeListener;
    }

    @SuppressWarnings("initialization")
    private void initializeContent(@UnknownInitialization(Consecutive.class) Consecutive this)
    {
        // Must do operator first:
        operators.add(new OperatorEntry("", this));
        operands.add(new GeneralEntry("", this));
    }

    private void updateListeners(@UnknownInitialization(Consecutive.class) Consecutive this)
    {
        // Make them all as old (false)
        listeningTo.replaceAll((e, b) -> false);
        // Merge new ones:

        Stream.<ExpressionNode>concat(operands.stream(), operators.stream()).forEach(child -> {
            // No need to listen again if already present as we're already listening
            if (listeningTo.get(child) == null)
                child.nodes().addListener(getChildrenNodeListener());
            listeningTo.put(child, true);
        });
        // Stop listening to old:
        for (Iterator<Entry<ExpressionNode, Boolean>> iterator = listeningTo.entrySet().iterator(); iterator.hasNext(); )
        {
            Entry<ExpressionNode, Boolean> e = iterator.next();
            if (e.getValue() == false)
            {
                e.getKey().nodes().removeListener(getChildrenNodeListener());
                iterator.remove();
            }
        }

        selfChanged();
    }

    protected void selfChanged(@UnknownInitialization(Consecutive.class) Consecutive this)
    {
        if (parent != null)
            parent.changed(this);
    }

    private void updateNodes(@UnknownInitialization(Consecutive.class) Consecutive this)
    {
        updatePrompt();

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

    private void updatePrompt(@UnknownInitialization(Consecutive.class) Consecutive this)
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
    }

    @Override
    public final ObservableList<Node> nodes(@UnknownInitialization(Consecutive.class) Consecutive this)
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

    public void addOperandToRight(@UnknownInitialization OperatorEntry rightOf, OperandNode operandNode)
    {
        // Must add operand and operator
        int index = Utility.indexOfRef(operators, rightOf);
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

    private int getOperandIndex(@UnknownInitialization OperandNode operand)
    {
        int index = Utility.indexOfRef(operands, operand);
        if (index == -1)
            Utility.logStackTrace("Asked for index but " + operand + " not a child of parent " + this);
        return index;
    }

    @Override
    public List<Pair<DataType, List<String>>> getSuggestedContext(ExpressionNode child) throws InternalException, UserException
    {
        List<Pair<DataType, List<String>>> r = new ArrayList<>();

        int childIndex = operands.indexOf(child);
        if (childIndex == -1)
            throw new InternalException("Asking for suggestions from non-child");

        if (parent != null)
        {
            // We can ask parent for our context, then it's a matter of translating
            // it to our children.  If we have multiple children with operators
            // (except ","), we say there's no context.  If we have multiple children
            // and all-comma operators, we can decompose a tuple suggestion.
            List<Pair<DataType, List<String>>> contextFromParent = parent.getSuggestedContext(this);

            if (operators.isEmpty())
            {
                r.addAll(contextFromParent);
            }
            /* We can't decompose the string so this won't work...
            else if (operators.stream().allMatch(op -> op.get().equals(",")))
            {

                r.addAll(contextFromParent.stream().filter(p ->
                {
                    try
                    {
                        return p.getFirst().isTuple() && p.getFirst().getMemberType().size() == operands.size();
                    }
                    catch (InternalException e)
                    {
                        Utility.log(e);
                        return false;
                    }
                }).map().collect(Collectors.toList()));
            }
            */
        }

        // We can look for comparison expressions with columns and make suggestions:
        if (operators.size() == 1 && (operators.get(0).equals("=") || operators.get(0).equals("<>")))
        {
            // If childIndex is 1, we want to look at 0 and vice versa:
            OperandNode comparator = operands.get(1 - childIndex);
            if (comparator instanceof GeneralEntry)
            {
                @Nullable Column column = ((GeneralEntry)comparator).getColumn();
                if (column != null)
                {
                    // TODO get most frequent values (may need a callback!)
                }
            }
        }
        // TODO also <, >=, etc
        return r;
    }

    @Override
    public List<Column> getAvailableColumns()
    {
        if (parent != null)
            return parent.getAvailableColumns();
        else
            return Collections.emptyList();
    }

    @Override
    public List<Pair<String, @Nullable DataType>> getAvailableVariables(ExpressionNode child)
    {
        if (parent != null)
            return parent.getAvailableVariables(this);
        else
            return Collections.emptyList();
    }

    @Override
    public TypeManager getTypeManager() throws InternalException
    {
        if (parent != null)
            return parent.getTypeManager();
        throw new InternalException("Consecutive with no parent; should be overridden");
    }

    @Override
    public boolean isTopLevel(@UnknownInitialization(Consecutive.class) Consecutive this)
    {
        return false;
    }

    @Override
    public void changed(@UnknownInitialization(ExpressionNode.class) ExpressionNode child)
    {
        selfChanged();
    }

    @Override
    public void focusRightOf(@UnknownInitialization ExpressionNode child)
    {
        if (child instanceof OperandNode && Utility.containsRef(operands, (OperandNode)child))
        {
            int index = getOperandIndex((OperandNode)child);
            operators.get(index).focus(Focus.LEFT);
        }
        else
        {
            int index = Utility.indexOfRef(operators, (OperatorEntry)child);
            if (index < operators.size() - 1)
                operands.get(index + 1).focus(Focus.LEFT);
            else if (parent != null)
                parent.focusRightOf(this);
        }
    }

    @Override
    public void focusLeftOf(@UnknownInitialization ExpressionNode child)
    {
        if (child instanceof OperandNode && Utility.containsRef(operands, (OperandNode)child))
        {
            int index = getOperandIndex((OperandNode) child);
            if (index > 0)
                operators.get(index - 1).focus(Focus.RIGHT);
            else if (parent != null)
                parent.focusLeftOf(this);
        }
        else
        {
            int index = Utility.indexOfRef(operators, (OperatorEntry)child);
            if (index != -1)
                operands.get(index).focus(Focus.RIGHT);
        }
    }

    public List<Pair<String, @Nullable DataType>> getDeclaredVariables()
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
        updatePrompt();
        return this;
    }

    private @Nullable List<String> getOperators(@UnknownInitialization(Consecutive.class) Consecutive this)
    {
        // If last operator not blank then can't be valid expression:
        if (!operators.get(operators.size() - 1).get().isEmpty())
            return null;
        // Knock off the last operator:
        return Utility.<OperatorEntry, String>mapList(operators.subList(0, operators.size() - 1), op -> op.get());
    }

    public @Nullable Expression toExpression(@UnknownInitialization(Consecutive.class) Consecutive this, FXPlatformConsumer<Object> onError)
    {
        List<Expression> operandExps = new ArrayList<>();
        this.operands.forEach(n -> {
            Expression e = n.toExpression(onError);
            if (e != null)
                operandExps.add(e);
        });
        if (this.operands.size() != operandExps.size())
            return null;
        @Nullable List<String> ops = getOperators();
        if (ops == null)
            return null;
        if (ops.isEmpty())
        {
            // Only one operand:
            return operandExps.get(0);
        }
        if (ops.stream().allMatch(op -> op.equals("+") || op.equals("-")))
        {
            return new AddSubtractExpression(operandExps, Utility.<String, Op>mapList(ops, op -> op.equals("+") ? Op.ADD : Op.SUBTRACT));
        }
        else if (ops.stream().allMatch(op -> op.equals("*")))
        {
            return new TimesExpression(operandExps);
        }
        else if (ops.stream().allMatch(op -> op.equals("/")))
        {
            if (operandExps.size() == 2)
                return new DivideExpression(operandExps.get(0), operandExps.get(1));
        }
        else if (ops.stream().allMatch(op -> op.equals("=")))
        {
            if (operandExps.size() == 2)
                return new EqualExpression(operandExps.get(0), operandExps.get(1));
        }

        return null; //TODO record errors, suggest fixes
    }

    public @Nullable DataType inferType()
    {
        return null; //TODO
    }


    public @Nullable Function<MatchExpression, PatternMatch> toPattern()
    {
        return null;
    }

    // Done as an inner class to satisfy initialization checker
    private static class UpdateNodesAndListeners<T> implements FXPlatformConsumer<Change<? extends T>>
    {
        private final @UnknownInitialization(Consecutive.class) Consecutive consecutive;

        private UpdateNodesAndListeners(@UnknownInitialization(Consecutive.class) Consecutive consecutive)
        {
            this.consecutive = consecutive;
        }

        @Override
        public @OnThread(Tag.FXPlatform) void consume(Change<? extends T> c)
        {
            consecutive.updateNodes();
            consecutive.updateListeners();
        }
    }
}
