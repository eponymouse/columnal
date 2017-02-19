package records.gui.expressioneditor;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ListChangeListener.Change;
import javafx.collections.ObservableList;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.interning.qual.Interned;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.qual.Pure;
import records.data.Column;
import records.data.datatype.DataType;
import records.data.datatype.TypeManager;
import records.error.InternalException;
import records.error.UserException;
import records.gui.expressioneditor.ExpressionEditorUtil.CopiedItems;
import records.gui.expressioneditor.GeneralEntry.Status;
import records.transformations.expression.AddSubtractExpression;
import records.transformations.expression.AddSubtractExpression.Op;
import records.transformations.expression.DivideExpression;
import records.transformations.expression.EqualExpression;
import records.transformations.expression.Expression;
import records.transformations.expression.InvalidOperatorExpression;
import records.transformations.expression.MatchExpression;
import records.transformations.expression.MatchExpression.PatternMatch;
import records.transformations.expression.TimesExpression;
import records.transformations.expression.UnfinishedExpression;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformConsumer;
import utility.Pair;
import utility.Utility;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Consecutive implements all the methods of OperandNode but deliberately
 * does not extend it because Consecutive by itself is not a valid
 * operand.  For that, use Bracketed.
 */
public @Interned abstract class ConsecutiveBase implements ExpressionParent, ExpressionNode
{
    private final ObservableList<Node> nodes;
    // The boolean value is only used during updateListeners, will be true other times
    private final IdentityHashMap<ExpressionNode, Boolean> listeningTo = new IdentityHashMap<>();
    protected final String style;
    private @MonotonicNonNull ListChangeListener<Node> childrenNodeListener;
    // The operands and operators are always exactly interleaved.
    // The first item is operands.get(0), followed by operators.get(0),
    // followed by operands.get(1), etc.
    protected final ObservableList<OperandNode> operands;
    protected final ObservableList<OperatorEntry> operators;
    private final @Nullable Node prefixNode;
    private final @Nullable Node suffixNode;
    private @Nullable String prompt = null;
    protected final BooleanProperty atomicEdit;

    public ConsecutiveBase(@Nullable Node prefixNode, @Nullable Node suffixNode, String style)
    {
        this.style = style;
        atomicEdit = new SimpleBooleanProperty(false);
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
        Utility.addChangeListenerPlatformNN(atomicEdit, inProgress -> {
            if (!inProgress)
            {
                // At end of edit:
                updateNodes();
                updateListeners();
            }
        });
    }

    private ListChangeListener<Node> getChildrenNodeListener(@UnknownInitialization(ConsecutiveBase.class)ConsecutiveBase this)
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

    // Can be overridden in subclasses
    @SuppressWarnings("initialization")
    protected void initializeContent(@UnknownInitialization(ConsecutiveBase.class)ConsecutiveBase this)
    {
        atomicEdit.set(true);
        operators.add(new OperatorEntry(this));
        operands.add(new GeneralEntry("", Status.UNFINISHED, this));
        atomicEdit.set(false);
    }

    private void updateListeners(@UnknownInitialization(ConsecutiveBase.class)ConsecutiveBase this)
    {
        if (atomicEdit.get())
            return;

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

    protected abstract void selfChanged(@UnknownInitialization(ConsecutiveBase.class) ConsecutiveBase this);

    private void updateNodes(@UnknownInitialization(ConsecutiveBase.class)ConsecutiveBase this)
    {
        if (atomicEdit.get())
            return;

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

        updateDisplay();
    }

    // Call after children have changed
    protected void updateDisplay(@UnknownInitialization(ConsecutiveBase.class)ConsecutiveBase this)
    {
    }

    private void updatePrompt(@UnknownInitialization(ConsecutiveBase.class)ConsecutiveBase this)
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
    public final ObservableList<Node> nodes(@UnknownInitialization(ConsecutiveBase.class)ConsecutiveBase this)
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
            atomicEdit.set(true);
            operators.add(index+1, new OperatorEntry(this));
            operands.add(index+1, operandNode);
            atomicEdit.set(false);
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
                atomicEdit.set(true);
                operators.add(index, new OperatorEntry(operator, true, this));
                operands.add(index+1, new GeneralEntry("", Status.UNFINISHED, this).focusWhenShown());
                atomicEdit.set(false);
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


        // We can ask parent for our context, then it's a matter of translating
        // it to our children.  If we have multiple children with operators
        // (except ","), we say there's no context.  If we have multiple children
        // and all-comma operators, we can decompose a tuple suggestion.
        List<Pair<DataType, List<String>>> contextFromParent = getSuggestedParentContext();

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

    protected abstract List<Pair<DataType,List<String>>> getSuggestedParentContext() throws UserException, InternalException;

    @Override
    public boolean isTopLevel(@UnknownInitialization(ConsecutiveBase.class)ConsecutiveBase this)
    {
        return false;
    }

    @Override
    public void changed(@UnknownInitialization(ExpressionNode.class) ExpressionNode child)
    {
        if (!atomicEdit.get())
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
            else
                parentFocusRightOfThis();
        }
    }

    protected abstract void parentFocusRightOfThis();

    @Override
    public void focusLeftOf(@UnknownInitialization ExpressionNode child)
    {
        if (child instanceof OperandNode && Utility.containsRef(operands, (OperandNode)child))
        {
            int index = getOperandIndex((OperandNode) child);
            if (index > 0)
                operators.get(index - 1).focus(Focus.RIGHT);
            else
                parentFocusLeftOfThis();
        }
        else
        {
            int index = Utility.indexOfRef(operators, (OperatorEntry)child);
            if (index != -1)
                operands.get(index).focus(Focus.RIGHT);
        }
    }

    protected abstract void parentFocusLeftOfThis();

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
        return expressionNode == operands.get(0) && operands.size() <= 2 && isMatchNode();
    }

    protected abstract boolean isMatchNode();

    public ConsecutiveBase focusWhenShown()
    {
        operands.get(0).focusWhenShown();
        return this;
    }

    public ConsecutiveBase prompt(String value)
    {
        prompt = value;
        updatePrompt();
        return this;
    }

    // If an expression is valid, it will always have a blank operator last, e.g.
    // 1 + 2 + 3 <blankop>
    // If has a non-blank operator last, there's a field beyond it that needs filling in
    // (and once it is filled, there will be a blank operator beyond it), e.g.
    // 1 + 2 + 3 * <blankfield>
    // This method returns true, operators if valid, or false, operators if not
    private Pair<Boolean, List<String>> getOperators(@UnknownInitialization(ConsecutiveBase.class)ConsecutiveBase this, int firstIndex, int lastIndex)
    {
        // If last operator not blank then can't be valid expression:
        boolean lastBlank = operators.get(operators.size() - 1).get().isEmpty();
        // Knock off the last operator:
        return new Pair<>(lastBlank, Utility.<OperatorEntry, String>mapList(lastBlank ? operators.subList(0, operators.size() - 1) : operators, op -> op.get()));
    }

    public Expression toExpression(@UnknownInitialization(ConsecutiveBase.class)ConsecutiveBase this, FXPlatformConsumer<Object> onError)
    {
        return toExpression(onError, operands.get(0), operands.get(operands.size() - 1));
    }

    public Expression toExpression(@UnknownInitialization(ConsecutiveBase.class)ConsecutiveBase this, FXPlatformConsumer<Object> onError, OperandNode first, OperandNode last)
    {
        int firstIndex = operands.indexOf(first);
        int lastIndex = operands.indexOf(last);
        if (firstIndex == -1 || lastIndex == -1 || lastIndex < firstIndex)
        {
            firstIndex = 0;
            lastIndex = operands.size() - 1;
        }
        List<Expression> operandExps = Utility.mapList(operands.subList(firstIndex, lastIndex + 1), n -> n.toExpression(onError));
        Pair<Boolean, List<String>> opsValid = getOperators(firstIndex, lastIndex);

        if (!opsValid.getFirst())
        {
            // Add a dummy unfinished expression beyond last operator:
            operandExps.add(new UnfinishedExpression(""));
        }

        List<String> ops = opsValid.getSecond();

        if (ops.isEmpty()) // Must be valid in this case
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

        return new InvalidOperatorExpression(operandExps, ops);
    }

    public @Nullable DataType inferType()
    {
        return null; //TODO
    }


    public Function<MatchExpression, PatternMatch> toPattern()
    {
        throw new RuntimeException("TODO");
    }

    /**
     * Gets all the operands between start and end (inclusive).  Returns the empty list
     * if there any problems (start or end not found, or end before start)
     */
    @Pure
    public List<ConsecutiveChild> getChildrenFromTo(ConsecutiveChild start, ConsecutiveChild end)
    {
        List<ConsecutiveChild> allChildren = getAllChildren();
        int a = allChildren.indexOf(start);
        int b = allChildren.indexOf(end);
        if (a == -1 || b == -1 || a > b)
            return Collections.emptyList();
        return allChildren.subList(a, b + 1);
    }

    private List<ConsecutiveChild> getAllChildren()
    {
        List<ConsecutiveChild> r = new ArrayList<>();
        for (int i = 0; i < operands.size(); i++)
        {
            r.add(operands.get(i));
            r.add(operators.get(i));
        }
        return r;
    }

    public void markSelection(ConsecutiveChild from, ConsecutiveChild to, boolean selected)
    {
        for (ConsecutiveChild n : getChildrenFromTo(from, to))
        {
            n.setSelected(selected);
        }
    }

    /**
     * Finds the nearest location to the given point in the scene where you could
     * drop a child.
     *
     * Returns the item before which you would insert, and the distance.
     */
    protected Pair<ConsecutiveChild, Double> findClosestDrop(Point2D loc)
    {
        return Stream.concat(operands.stream(), operators.stream()).map(n -> n.findClosestDrop(loc)).min(Comparator.comparing(Pair::getSecond)).get();
    }

    public @Nullable CopiedItems copyItems(ConsecutiveChild start, ConsecutiveChild end)
    {
        boolean startIsOp = false;
        int startIndex = operands.indexOf(start);
        if (startIndex == -1)
        {
            startIsOp = true;
            startIndex = operators.indexOf(start);
        }
        boolean endIsOp = false;
        int endIndex = operands.indexOf(end);
        if (endIndex == -1)
        {
            endIndex = operators.indexOf(end);
            endIsOp = false;
        }

        if (startIndex == -1 || endIndex == -1)
            // Problem:
            return null;

        return new CopiedItems(
            Utility.<OperandNode, String>mapList(operands.subList(startIsOp ? startIndex + 1 : startIndex, endIndex + 1), n -> n.toExpression(o -> {}).save(false)),
            Utility.<OperatorEntry, String>mapList(operators.subList(startIndex, endIsOp ? endIndex + 1 : endIndex), o -> o.get()),
            startIsOp);
    }

    public boolean insertBefore(ConsecutiveChild insertBefore, CopiedItems itemsToInsert)
    {
        // At the beginning and at the end, we may get a match (e.g. inserting an operator
        // after an operand), or mismatch (inserting an operator after an operator)
        // In the case of a mismatch, we must insert a blank of the other type to get it right.

        atomicEdit.set(true);

        @Nullable List<OperandNode> newOperands = loadOperands(itemsToInsert.operands);
        List<OperatorEntry> newOperators = Utility.mapList(itemsToInsert.operators, o -> new OperatorEntry(o, false, this));
        if (newOperands == null || Math.abs(newOperands.size() - newOperators.size()) > 1)
            return false;

        boolean endsWithOperator;
        if (itemsToInsert.startsOnOperator)
            endsWithOperator = itemsToInsert.operators.size() == itemsToInsert.operands.size() + 1;
        else
            endsWithOperator = itemsToInsert.operators.size() == itemsToInsert.operands.size();

        if (insertBefore instanceof OperandNode)
        {
            int index = operands.indexOf(insertBefore);
            if (index == -1)
                return false;

            // Inserting before operand, so to match we need operand first to take its place:
            if (itemsToInsert.startsOnOperator)
            {
                // Mismatch!  Add an empty operand:
                operands.add(index, new GeneralEntry("", Status.UNFINISHED, this));
                // Adjust as we want to insert after:
                index += 1;
            }
            // Now we are ok to insert at index:
            operands.addAll(index, newOperands);
            operators.addAll(index, newOperators);
            // Tidy up the end, if needed.
            // We inserted before an operand, so the end is messy if the inserted content
            // didn't end with an operator
            if (!endsWithOperator)
            {
                operators.add(index + newOperators.size(), new OperatorEntry(this));
                checkForDoubleBlank(index + newOperators.size() + 1);
            }
        }
        else
        {
            int index = operators.indexOf(insertBefore);
            if (index == -1)
                return false;
            // Inserting before operator, so to match we need an operator first to take its place:
            if (!itemsToInsert.startsOnOperator)
            {
                operators.add(index, new OperatorEntry(this));
                // Adjust as we want to insert after:
                index += 1;
            }

            // Now we are ok to insert at index for operators, adjusting for operands:
            operands.addAll(index + 1, newOperands);
            operators.addAll(index, newOperators);

            // Tidy up the end, if needed.
            // We inserted before an operator, so the end is messy if the inserted content
            // didn't end with an operand
            if (endsWithOperator)
            {
                operands.add(index + 1 + newOperands.size(), new GeneralEntry("", Status.UNFINISHED, this));
                checkForDoubleBlank(index + 1 + newOperands.size() + 1);
            }
        }

        atomicEdit.set(false);
        return true;
    }

    private @Nullable List<OperandNode> loadOperands(List<String> savedOperands)
    {
        try
        {
            return Utility.mapListEx(savedOperands, raw -> Expression.parse(null, raw, getEditor().getTypeManager()).loadAsSingle().apply(this));
        }
        catch (UserException | InternalException e)
        {
            return null;
        }
    }

    public void removeItems(ConsecutiveChild start, ConsecutiveChild end)
    {
        atomicEdit.set(true);
        int startOperandIndex, startOperatorIndex;
        int endOperandIndex, endOperatorIndex;
        if (start instanceof OperatorEntry)
        {
            startOperatorIndex = operators.indexOf(start);
            startOperandIndex = startOperatorIndex + 1;
        }
        else
        {
            startOperandIndex = operands.indexOf(start);
            startOperatorIndex = startOperandIndex;
        }
        if (end instanceof OperatorEntry)
        {
            endOperatorIndex = operators.indexOf(end);
            endOperandIndex = endOperatorIndex;
        }
        else
        {
            endOperandIndex = operands.indexOf(end);
            endOperatorIndex = endOperandIndex - 1;
        }
        
        operators.remove(startOperatorIndex, endOperatorIndex + 1);
        operands.remove(startOperandIndex, endOperandIndex + 1);
        
        // There's three cases:
        // - We begin and end with operator; need to add a new blank one after
        // - We begin and end with operand; need to add a new blank one after
        // - We begin and end with different; will be complete whichever way round it is
        // However: if next to the blank operator/operand there is a blank operand/operator
        // we can cancel the two out instead and remove them both.
        if (start instanceof OperatorEntry && end instanceof OperatorEntry)
        {
            operators.add(startOperatorIndex, new OperatorEntry(this));
            if (!checkForDoubleBlank(startOperatorIndex))
                checkForDoubleBlank(startOperatorIndex + 1);
        }
        else if (!(start instanceof OperatorEntry) && !(end instanceof OperatorEntry))
        {
            operands.add(startOperandIndex, new GeneralEntry("", Status.UNFINISHED, this));
            checkForDoubleBlank(startOperandIndex);
        }
        atomicEdit.set(false);
    }

    // Checks if operandIndex, and the operator before or after it, are blank
    // If so, removes them and returns true.  If no double blank found, returns false
    private boolean checkForDoubleBlank(int operandIndex)
    {
        if (operands.get(operandIndex).isBlank())
        {
            if (operandIndex > 0 && operators.get(operandIndex - 1).isBlank())
            {
                operands.remove(operandIndex);
                operators.remove(operandIndex - 1);
                return true;
            }
            else if (operandIndex < operators.size() && operators.get(operandIndex).isBlank())
            {
                operands.remove(operandIndex);
                operators.remove(operandIndex);
                return true;
            }
        }
        return false;
    }

    // Done as an inner class to satisfy initialization checker
    private static class UpdateNodesAndListeners<T> implements FXPlatformConsumer<Change<? extends T>>
    {
        private final @UnknownInitialization(ConsecutiveBase.class) ConsecutiveBase consecutive;

        private UpdateNodesAndListeners(@UnknownInitialization(ConsecutiveBase.class) ConsecutiveBase consecutive)
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
