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
import records.transformations.expression.TimesExpression;
import records.transformations.expression.UnfinishedExpression;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformConsumer;
import utility.Pair;
import utility.Utility;
import utility.gui.FXUtility;

import java.util.AbstractList;
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
    /**
     * The operands and operators are always exactly interleaved.
     * The first item is operands.get(0), followed by operators.get(0),
     * followed by operands.get(1), etc.
     *
     * If X stands for any operand and ? for any operator, you can have:
     *   X
     *   X ?
     *   X ? X
     *   X ? X ?
     *   and so on.  So operators is always same size as operands, or one smaller.
     *
     * There can be blanks in the middle, but there should only be blanks on the end
     * if that item is focused.  So if X* means blank and ?* means blank, you're allowed:
     *    X* ?* X
     * if the focus is in the first operand or first operator.  But as soon as it is in
     * the first non-blank, the previous items should be removed.  Similarly at the end:
     *    X ?*
     * is allowed if the focus is in the last slot.  The following operand is only added
     * once the user has filled in the operator, giving:
     *    X ? X*
     * In this case, the last operator is left even when focus is lost, because it's guaranteed
     * the expression is invalid without ther operand being filled in.  Same logic applies to:
     *    X* ? X
     */
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
        for (int i = 0; i < Math.max(operands.size(), operators.size()); i++)
        {
            if (i < operands.size())
                childrenNodes.addAll(operands.get(i).nodes());
            if (i < operators.size())
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
            {
                // index is zero.  If we are blank then we do go to parent's left
                // If we aren't blank, we make a new blank before us:
                if (operands.get(0).isBlank())
                    parentFocusLeftOfThis();
                else
                {
                    addBlankAtLeft();
                }
            }
        }
        else if (child instanceof OperatorEntry && Utility.containsRef(operators, (OperatorEntry)child))
        {
            int index = Utility.indexOfRef(operators, (OperatorEntry)child);
            if (index != -1)
                operands.get(index).focus(Focus.RIGHT);
        }
    }

    private void addBlankAtLeft()
    {
        atomicEdit.set(true);
        operands.add(0, new GeneralEntry("", Status.UNFINISHED, this)
            .focusWhenShown());
        operators.add(0, new OperatorEntry(this));
        atomicEdit.set(false);
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
        boolean lastBlank = !operators.isEmpty() && operators.get(operators.size() - 1).get().isEmpty();
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

    private List<ConsecutiveChild> getAllChildren(@UnknownInitialization(ConsecutiveBase.class) ConsecutiveBase this)
    {
        return interleaveOperandsAndOperators(operands, operators);
    }

    /**
     * Produces a list which interleaves the operands and operators together.  Note that
     * the returned list is a *live mirror* of the lists.  Any removals from the returned list
     * will affect the originals.  Additions are not permitted.
     *
     * If you let the lists get into an inconsistent state (e.g. remove one operand from the middle)
     * then you'll get undefined behaviour.
     *
     * @param operands
     * @param operators
     * @return
     */

    private static List<ConsecutiveChild> interleaveOperandsAndOperators(List<OperandNode> operands, List<OperatorEntry> operators)
    {
        return new AbstractList<ConsecutiveChild>()
        {
            @Override
            public ConsecutiveChild get(int index)
            {
                return ((index & 1) == 0) ? operands.get(index >> 1) : operators.get(index >> 1);
            }

            @Override
            public int size()
            {
                return operands.size() + operators.size();
            }

            @Override
            public ConsecutiveChild remove(int index)
            {
                if ((index & 1) == 0)
                    return operands.remove(index >> 1);
                else
                    return operators.remove(index >> 1);
            }

            // We presume that they intend to add operatorentry at odd indexes
            // and operator at even indexes
            @Override
            public void add(int index, ConsecutiveChild element)
            {
                if ((index & 1) == 0)
                    operands.add(index >> 1, (OperandNode) element);
                else
                    operators.add(index >> 1, (OperatorEntry) element);
            }
        };
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
        List<ConsecutiveChild> all = getAllChildren();
        boolean startIsOperator = start instanceof OperatorEntry;
        int startIndex = all.indexOf(start);
        int endIndex = all.indexOf(end);

        if (startIndex == -1 || endIndex == -1)
            // Problem:
            return null;

        return new CopiedItems(
            Utility.<ConsecutiveChild, String>mapList(all.subList(startIndex, endIndex + 1), child -> {
                if (child instanceof OperatorEntry)
                    return ((OperatorEntry)child).get();
                else
                    return ((OperandNode)child).toExpression(o -> {}).save(false);
            }), startIsOperator);
    }

    public boolean insertBefore(ConsecutiveChild insertBefore, CopiedItems itemsToInsert)
    {
        // At the beginning and at the end, we may get a match (e.g. inserting an operator
        // after an operand), or mismatch (inserting an operator after an operator)
        // In the case of a mismatch, we must insert a blank of the other type to get it right.
        atomicEdit.set(true);

        @Nullable Pair<List<OperandNode>, List<OperatorEntry>> loaded = loadItems(itemsToInsert);
        if (loaded == null)
            return false;
        List<OperandNode> newOperands = loaded.getFirst();
        List<OperatorEntry> newOperators = loaded.getSecond();

        boolean endsWithOperator;
        if (itemsToInsert.startsOnOperator)
            endsWithOperator = newOperators.size() == newOperands.size() + 1;
        else
            endsWithOperator = newOperators.size() == newOperands.size();

        // If it starts with an operator and you're inserting before an operand, add an extra blank operand

        if (insertBefore instanceof OperandNode)
        {
            int index = operands.indexOf(insertBefore);
            if (index == -1)
                return false;

            // If it starts with an operator and you're inserting before an operand, add an extra blank operand
            if (itemsToInsert.startsOnOperator)
            {
                newOperands.add(0, new GeneralEntry("", Status.UNFINISHED, this));
            }
            // We are inserting before an operand, so the end is messy if the inserted content
            // didn't end with an operator
            if (!endsWithOperator)
            {
                newOperators.add(new OperatorEntry(this));
            }

            // We will have an operand first, that one goes at index:
            operands.addAll(index, newOperands);
            // Operator at index follows operand at index:
            operators.addAll(index, newOperators);

        }
        else
        {
            int index = operators.indexOf(insertBefore);
            if (index == -1)
                return false;

            // Inserting before operator, so to match we need an operator first to take its place:
            if (!itemsToInsert.startsOnOperator)
            {
                newOperators.add(0, new OperatorEntry(this));
            }
            // Inserting before operator, so we need to end with operand:
            if (endsWithOperator)
            {
                newOperands.add(new GeneralEntry("", Status.UNFINISHED, this));
            }

            // Now we are ok to insert at index for operators, but must adjust for operands:
            operators.addAll(index, newOperators);
            operands.addAll(index + 1, newOperands);
        }

        removeBlanks(operands, operators, false);

        atomicEdit.set(false);
        return true;
    }

    private @Nullable Pair<List<OperandNode>, List<OperatorEntry>> loadItems(CopiedItems copiedItems)
    {
        List<OperandNode> operands = new ArrayList<>();
        List<OperatorEntry> operators = new ArrayList<>();
        try
        {
            for (int i = 0; i < copiedItems.items.size(); i++)
            {
                // First: is it even?  If it's even and we start on operator then it's an operator
                // Equally, if it's odd and we didn't start on operator, then it's an operator.
                if (((i & 1) == 0) == copiedItems.startsOnOperator)
                {
                    operators.add(new OperatorEntry(copiedItems.items.get(i), false, this));
                }
                else
                {
                    operands.add(Expression.parse(null, copiedItems.items.get(i), getEditor().getTypeManager()).loadAsSingle().apply(this));
                }
            }
        }
        catch (UserException | InternalException e)
        {
            return null;
        }
        return new Pair<>(operands, operators);
    }

    public void removeItems(ConsecutiveChild start, ConsecutiveChild end)
    {
        atomicEdit.set(true);
        int startIndex;
        int endIndex;
        List<ConsecutiveChild> all = interleaveOperandsAndOperators(operands, operators);
        startIndex = all.indexOf(start);
        endIndex = all.indexOf(end);
        if (startIndex != -1 && endIndex != -1)
        {
            // Important to go backwards so that the indexes don't get screwed up:
            for (int i = endIndex; i >= startIndex; i--)
                all.remove(i);
        }
        
        // There's three cases:
        // - We begin and end with operator; need to add a new blank one after
        // - We begin and end with operand; need to add a new blank one after
        // - We begin and end with different; will be complete whichever way round it is
        // However: if next to the blank operator/operand there is a blank operand/operator
        // we can cancel the two out instead and remove them both.
        if (start instanceof OperatorEntry && end instanceof OperatorEntry)
        {
            all.add(startIndex, new OperatorEntry(this));
        }
        else if (!(start instanceof OperatorEntry) && !(end instanceof OperatorEntry))
        {
            all.add(startIndex, new GeneralEntry("", Status.UNFINISHED, this));
        }
        removeBlanks(operands, operators, true);
        atomicEdit.set(false);
    }

    public void setSelected(boolean selected)
    {
        if (prefixNode != null)
            FXUtility.setPseudoclass(prefixNode, "exp-selected", selected);
        if (suffixNode != null)
            FXUtility.setPseudoclass(suffixNode, "exp-selected", selected);
        for (ConsecutiveChild consecutiveChild : getAllChildren())
        {
            consecutiveChild.setSelected(selected);
        }
    }

    /**
     * Focuses a blank slot on the left of the expression, either an existing
     * blank, or a new specially created blank
     */
    public void focusBlankAtLeft()
    {
        if (operands.get(0).isBlank())
            operands.get(0).focus(Focus.LEFT);
        else
            addBlankAtLeft();
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

    public void focusChanged(@UnknownInitialization(ConsecutiveBase.class) ConsecutiveBase this)
    {
        atomicEdit.set(true);
        removeBlanks(operands, operators, true);
        atomicEdit.set(false);

        // Must also tell remaining children to update (shouldn't interact with above calculation
        // because updating should not make a field return isBlank==true, that should only be returned
        // by unstructured/leaf operands, and only structured/branch operands should respond to this):
        for (ConsecutiveChild consecutiveChild : getAllChildren())
        {
            consecutiveChild.focusChanged();
        }
    }

    /**
     * This method goes through the given list of operands and operators, and modifies them
     * *in place* to remove unnecessary harmless blanks.  It's static to avoid accidentally
     * modifying members.
     *
     * The rules are: any consecutive pair of operator and operand will get removed (it doesn't really
     * matter which ones if there's > 2 consecutive blanks; if there's an odd number you'll always
     * be left with one, and if even, all will be removed anyway).  And finally, a single blank item
     * on the end by itself will get removed.  (A blank pair or more on the end would already have been
     * removed by the first check.)
     *
     * @param operands
     * @param operators
     * @param accountForFocus If they are focused, should they be kept in (true: yes, keep; false: no, remove)
     */
    private static void removeBlanks(List<OperandNode> operands, List<OperatorEntry> operators, boolean accountForFocus)
    {
        List<ConsecutiveChild> all = interleaveOperandsAndOperators(operands, operators);

        // We check for blanks on the second of the pair, as it makes the index checks easier
        // Hence we only need start at 1:
        int index = 1;
        while (index < all.size())
        {
            if (all.get(index - 1).isBlank() && (!accountForFocus || !all.get(index - 1).isFocused()) &&
                all.get(index).isBlank() && (!accountForFocus || !all.get(index).isFocused()))
            {
                // Both are blank, so remove:
                // Important to remove later one first so as to not mess with the indexing:
                all.remove(index);
                all.remove(index - 1);
                // In this case we don't want to change index, as we want to assess the next
                // pair
            }
            else
                index += 1; // Only if they weren't removed do we advance
        }

        // Remove final operand or operator if blank&unfocused:
        if (!all.isEmpty())
        {
            ConsecutiveChild last = all.get(all.size() - 1);
            if (last.isBlank() && (!accountForFocus || !last.isFocused()))
            {
                all.remove(all.size() - 1);
            }
        }
    }

    // We deliberately don't directly override OperandNode's isFocused,
    // because then it would be too easily to forget to override it a subclass
    // children, which may have other fields which could be focused
    protected boolean childIsFocused()
    {
        return getAllChildren().stream().anyMatch(c -> c.isFocused());
    }
}
