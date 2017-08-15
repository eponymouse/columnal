package records.gui.expressioneditor;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import org.checkerframework.checker.i18n.qual.LocalizableKey;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.interning.qual.Interned;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.qual.Pure;
import org.jetbrains.annotations.NotNull;
import records.data.datatype.DataType;
import records.error.InternalException;
import records.error.UserException;
import records.gui.expressioneditor.ExpressionEditorUtil.CopiedItems;
import records.gui.expressioneditor.GeneralExpressionEntry.Status;
import records.transformations.expression.*;
import records.transformations.expression.AddSubtractExpression.Op;
import utility.FXPlatformConsumer;
import utility.Pair;
import utility.Utility;
import utility.gui.FXUtility;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Consecutive implements all the methods of OperandNode but deliberately
 * does not extend it because Consecutive by itself is not a valid
 * operand.  For that, use BracketedExpression.
 */
public @Interned abstract class ConsecutiveBase<EXPRESSION extends @NonNull Object, SEMANTIC_PARENT> extends DeepNodeTree implements EEDisplayNodeParent, EEDisplayNode, ErrorDisplayer
{
    protected final OperandOps<EXPRESSION, SEMANTIC_PARENT> operations;

    protected final String style;
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
    protected final ObservableList<OperandNode<@NonNull EXPRESSION>> operands;
    protected final ObservableList<OperatorEntry<@NonNull EXPRESSION, SEMANTIC_PARENT>> operators;
    private final @Nullable Node prefixNode;
    private final @Nullable Node suffixNode;
    private @Nullable String prompt = null;

    @SuppressWarnings("initialization")
    public ConsecutiveBase(OperandOps<EXPRESSION, SEMANTIC_PARENT> operations, @Nullable Node prefixNode, @Nullable Node suffixNode, String style)
    {
        this.operations = operations;
        this.style = style;
        operands = FXCollections.observableArrayList();
        operators = FXCollections.observableArrayList();

        this.prefixNode = prefixNode;
        this.suffixNode = suffixNode;
        listenToNodeRelevantList(operands);
        listenToNodeRelevantList(operators);
        FXUtility.listen(operands, c -> {
            if (!atomicEdit.get())
                selfChanged();
        });
        FXUtility.listen(operators, c -> {
            if (!atomicEdit.get())
                selfChanged();
        });
        FXUtility.addChangeListenerPlatformNN(atomicEdit, changing -> {
            if (!changing)
                selfChanged();
        });
    }

    @Override
    protected Stream<Node> calculateNodes()
    {
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
        return childrenNodes.stream();
    }

    @Override
    protected Stream<EEDisplayNode> calculateChildren()
    {
        return Stream.concat(operators.stream(), operands.stream());
    }

    @NotNull
    protected OperatorEntry<EXPRESSION, SEMANTIC_PARENT> makeBlankOperator()
    {
        return new OperatorEntry<>(operations.getOperandClass(), this);
    }

    @NotNull
    protected OperandNode<@NonNull EXPRESSION> makeBlankOperand()
    {
        return operations.makeGeneral(this, getThisAsSemanticParent(), null);
    }

    // Get us as a semantic parent.  Do not get OUR parent.
    protected abstract SEMANTIC_PARENT getThisAsSemanticParent();

    protected abstract void selfChanged();

    // Call after children have changed
    protected void updateDisplay()
    {
        updatePrompt();
    }

    private void updatePrompt()
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
    public void focus(Focus side)
    {
        if (side == Focus.LEFT)
            operands.get(0).focus(side);
        else
            operands.get(operands.size() - 1).focus(side);
    }

    public void replace(OperandNode<@NonNull EXPRESSION> oldNode, @Nullable OperandNode<@NonNull EXPRESSION> newNode)
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

    public static enum OperatorOutcome { KEEP, BLANK }

    public OperatorOutcome addOperandToRight(@UnknownInitialization OperatorEntry<EXPRESSION, SEMANTIC_PARENT> rightOf, String operatorEntered, String initialContent, boolean focus)
    {
        // Must add operand and operator
        int index = Utility.indexOfRef(operators, rightOf);
        if (index != -1)
        {
            atomicEdit.set(true);
            operators.add(index+1, makeBlankOperator());
            OperandNode<EXPRESSION> operandNode = operations.makeGeneral(this, getThisAsSemanticParent(), initialContent);
            if (focus)
                operandNode.focusWhenShown();
            operands.add(index+1, operandNode);
            atomicEdit.set(false);
            return OperatorOutcome.KEEP;
        }
        // If we can't find it, I guess blank:
        return OperatorOutcome.BLANK;
    }


    public void setOperatorToRight(@UnknownInitialization OperandNode<@NonNull EXPRESSION> rightOf, String operator)
    {
        int index = getOperandIndex(rightOf);
        if (index != -1)
        {
            if (index >= operators.size() || !operators.get(index).fromBlankTo(operator))
            {
                // Add new operator and new operand:
                atomicEdit.set(true);
                operators.add(index, new OperatorEntry<>(operations.getOperandClass(), operator, true, this));
                OperandNode<@NonNull EXPRESSION> blankOperand = makeBlankOperand();
                blankOperand.focusWhenShown();
                operands.add(index+1, blankOperand);
                atomicEdit.set(false);
            }
        }
    }

    private int getOperandIndex(@UnknownInitialization OperandNode<@NonNull EXPRESSION> operand)
    {
        int index = Utility.indexOfRef(operands, operand);
        if (index == -1)
            Utility.logStackTrace("Asked for index but " + operand + " not a child of parent " + this);
        return index;
    }


    //protected abstract List<Pair<DataType,List<String>>> getSuggestedParentContext() throws UserException, InternalException;

    @Override
    public void changed(@UnknownInitialization(EEDisplayNode.class) EEDisplayNode child)
    {
        if (!atomicEdit.get())
            selfChanged();
    }

    @SuppressWarnings("unchecked")
    @Override
    public void focusRightOf(@UnknownInitialization EEDisplayNode child)
    {
        // Cast is safe because of instanceof, and the knowledge that
        // all our children have EXPRESSION as inner type:
        if (child instanceof OperandNode && Utility.containsRef(operands, (OperandNode<@NonNull EXPRESSION>)child))
        {
            int index = getOperandIndex((OperandNode<@NonNull EXPRESSION>)child);
            if (index >= operators.size())
            {
                operators.add(makeBlankOperator());
            }
            operators.get(index).focus(Focus.LEFT);
        }
        else
        {
            int index = Utility.indexOfRef(operators, (OperatorEntry<EXPRESSION, SEMANTIC_PARENT>)child);
            if (index < operators.size() - 1)
                operands.get(index + 1).focus(Focus.LEFT);
            else
                parentFocusRightOfThis();
        }
    }

    protected abstract void parentFocusRightOfThis();

    @SuppressWarnings("unchecked")
    @Override
    public void focusLeftOf(@UnknownInitialization EEDisplayNode child)
    {
        if (child instanceof OperandNode && Utility.containsRef(operands, (OperandNode<@NonNull EXPRESSION>)child))
        {
            int index = getOperandIndex((OperandNode<@NonNull EXPRESSION>) child);
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
        else if (child instanceof OperatorEntry && Utility.containsRef(operators, (OperatorEntry<EXPRESSION, SEMANTIC_PARENT>)child))
        {
            int index = Utility.indexOfRef(operators, (OperatorEntry<EXPRESSION, SEMANTIC_PARENT>)child);
            if (index != -1)
                operands.get(index).focus(Focus.RIGHT);
        }
    }

    private void addBlankAtLeft()
    {
        atomicEdit.set(true);
        OperandNode<@NonNull EXPRESSION> blankOperand = makeBlankOperand();
        blankOperand.focusWhenShown();
        operands.add(0, blankOperand);
        operators.add(0, makeBlankOperator());
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

    public void focusWhenShown()
    {
        operands.get(0).focusWhenShown();
    }

    public void prompt(String value)
    {
        prompt = value;
        updatePrompt();
    }

    // If an expression is valid, it will not have an operator last, e.g.
    // 1 + 2 + 3 +
    // If has an operator last, there's a missing operand
    // This method returns true, operators if last is not an operator (i.e. if expression is valid), or false, operators if last is an operator
    private Pair<Boolean, List<String>> getOperators(int firstIndex, int lastIndex)
    {
        boolean lastOp = operators.size() == operands.size();
        return new Pair<>(!lastOp, Utility.<OperatorEntry<EXPRESSION, SEMANTIC_PARENT>, String>mapList(operators, op -> op.get()));
    }

    public EXPRESSION save(ErrorDisplayerRecord errorDisplayers, FXPlatformConsumer<Object> onError)
    {
        return save(errorDisplayers, onError, operands.get(0), operands.get(operands.size() - 1));
    }

    public EXPRESSION save(ErrorDisplayerRecord errorDisplayers, FXPlatformConsumer<Object> onError, OperandNode<@NonNull EXPRESSION> first, OperandNode<@NonNull EXPRESSION> last)
    {
        int firstIndex = operands.indexOf(first);
        int lastIndex = operands.indexOf(last);
        boolean roundBracketed = false;
        if (firstIndex == -1 || lastIndex == -1 || lastIndex < firstIndex)
        {
            firstIndex = 0;
            lastIndex = operands.size() - 1;
        }
        // May be because it was -1, or just those values were passed directly:
        if (firstIndex == 0 && lastIndex == operands.size() - 1)
        {
            // Bit of a hack:
            roundBracketed = this instanceof BracketedExpression;
        }

        List<@NonNull EXPRESSION> expressionExps = Utility.<OperandNode<@NonNull EXPRESSION>, @NonNull EXPRESSION>mapList(operands.subList(firstIndex, lastIndex + 1), (OperandNode<@NonNull EXPRESSION> n) -> n.save(errorDisplayers, onError));
        Pair<Boolean, List<String>> opsValid = getOperators(firstIndex, lastIndex);

        if (!opsValid.getFirst())
        {
            // Add a dummy unfinished expression beyond last operator:
            expressionExps.add(errorDisplayers.record(this, operations.makeUnfinished("")));
        }

        List<String> ops = opsValid.getSecond();

        if (ops.isEmpty()) // Must be valid in this case
        {
            // Only one operand:
            return expressionExps.get(0);
        }

        return operations.makeExpression(this, errorDisplayers, expressionExps, ops, roundBracketed);
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
    public List<ConsecutiveChild<EXPRESSION>> getChildrenFromTo(ConsecutiveChild<EXPRESSION> start, ConsecutiveChild<EXPRESSION> end)
    {
        List<ConsecutiveChild<@NonNull EXPRESSION>> allChildren = getAllChildren();
        int a = allChildren.indexOf(start);
        int b = allChildren.indexOf(end);
        if (a == -1 || b == -1 || a > b)
            return Collections.emptyList();
        return allChildren.subList(a, b + 1);
    }

    private List<ConsecutiveChild<@NonNull EXPRESSION>> getAllChildren()
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

    private static <EXPRESSION extends @NonNull Object, SEMANTIC_PARENT> List<ConsecutiveChild<EXPRESSION>> interleaveOperandsAndOperators(List<OperandNode<EXPRESSION>> operands, List<OperatorEntry<EXPRESSION, SEMANTIC_PARENT>> operators)
    {
        return new AbstractList<ConsecutiveChild<EXPRESSION>>()
        {
            @Override
            public ConsecutiveChild<EXPRESSION> get(int index)
            {
                return ((index & 1) == 0) ? operands.get(index >> 1) : operators.get(index >> 1);
            }

            @Override
            public int size()
            {
                return operands.size() + operators.size();
            }

            @Override
            public ConsecutiveChild<EXPRESSION> remove(int index)
            {
                if ((index & 1) == 0)
                    return operands.remove(index >> 1);
                else
                    return operators.remove(index >> 1);
            }

            // We presume that they intend to add operatorentry at odd indexes
            // and operator at even indexes
            @SuppressWarnings("unchecked")
            @Override
            public void add(int index, ConsecutiveChild<EXPRESSION> element)
            {
                if ((index & 1) == 0)
                    operands.add(index >> 1, (OperandNode<EXPRESSION>) element);
                else
                    operators.add(index >> 1, (OperatorEntry<EXPRESSION, SEMANTIC_PARENT>) element);
            }
        };
    }

    public void markSelection(ConsecutiveChild<EXPRESSION> from, ConsecutiveChild<EXPRESSION> to, boolean selected)
    {
        for (ConsecutiveChild<EXPRESSION> n : getChildrenFromTo(from, to))
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
    protected <C> @Nullable Pair<ConsecutiveChild<? extends C>, Double> findClosestDrop(Point2D loc, Class<C> forType)
    {
        return Utility.filterOutNulls(Stream.<ConsecutiveChild<EXPRESSION>>concat(operands.stream(), operators.stream()).<@Nullable Pair<ConsecutiveChild<? extends C>, Double>>map(n -> n.findClosestDrop(loc, forType))).min(Comparator.comparing(p -> p.getSecond())).get();
    }

    @SuppressWarnings("unchecked")
    public @Nullable CopiedItems copyItems(ConsecutiveChild<EXPRESSION> start, ConsecutiveChild<EXPRESSION> end)
    {
        List<ConsecutiveChild<@NonNull EXPRESSION>> all = getAllChildren();
        boolean startIsOperator = start instanceof OperatorEntry;
        int startIndex = all.indexOf(start);
        int endIndex = all.indexOf(end);

        if (startIndex == -1 || endIndex == -1)
            // Problem:
            return null;

        return new CopiedItems(
            Utility.<ConsecutiveChild<EXPRESSION>, String>mapList(all.subList(startIndex, endIndex + 1), child -> {
                if (child instanceof OperatorEntry)
                    return ((OperatorEntry<EXPRESSION, SEMANTIC_PARENT>)child).get();
                else
                    return operations.save(((OperandNode<EXPRESSION>)child).save(new ErrorDisplayerRecord(), o -> {}));
            }), startIsOperator);
    }

    public boolean insertBefore(ConsecutiveChild insertBefore, CopiedItems itemsToInsert)
    {
        // At the beginning and at the end, we may get a match (e.g. inserting an operator
        // after an operand), or mismatch (inserting an operator after an operator)
        // In the case of a mismatch, we must insert a blank of the other type to get it right.
        atomicEdit.set(true);

        @Nullable Pair<List<OperandNode<EXPRESSION>>, List<OperatorEntry<EXPRESSION, SEMANTIC_PARENT>>> loaded = loadItems(itemsToInsert);
        if (loaded == null)
            return false;
        List<OperandNode<EXPRESSION>> newOperands = loaded.getFirst();
        List<OperatorEntry<EXPRESSION, SEMANTIC_PARENT>> newOperators = loaded.getSecond();

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
                newOperands.add(0, makeBlankOperand());
            }
            // We are inserting before an operand, so the end is messy if the inserted content
            // didn't end with an operator
            if (!endsWithOperator)
            {
                newOperators.add(makeBlankOperator());
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
                newOperators.add(0, makeBlankOperator());
            }
            // Inserting before operator, so we need to end with operand:
            if (endsWithOperator)
            {
                newOperands.add(makeBlankOperand());
            }

            // Now we are ok to insert at index for operators, but must adjust for operands:
            operators.addAll(index, newOperators);
            operands.addAll(index + 1, newOperands);
        }

        removeBlanks(operands, operators, false, null);

        atomicEdit.set(false);
        return true;
    }

    private @Nullable Pair<List<OperandNode<EXPRESSION>>, List<OperatorEntry<EXPRESSION, SEMANTIC_PARENT>>> loadItems(CopiedItems copiedItems)
    {
        List<OperandNode<EXPRESSION>> operands = new ArrayList<>();
        List<OperatorEntry<EXPRESSION, SEMANTIC_PARENT>> operators = new ArrayList<>();
        try
        {
            for (int i = 0; i < copiedItems.items.size(); i++)
            {
                // First: is it even?  If it's even and we start on operator then it's an operator
                // Equally, if it's odd and we didn't start on operator, then it's an operator.
                String curItem = copiedItems.items.get(i);
                if (((i & 1) == 0) == copiedItems.startsOnOperator)
                {
                    operators.add(new OperatorEntry<>(operations.getOperandClass(), curItem, false, this));
                }
                else
                {
                    operands.add(operations.loadOperand(curItem, this));
                }
            }
        }
        catch (UserException | InternalException e)
        {
            return null;
        }
        return new Pair<>(operands, operators);
    }

    public void removeItems(ConsecutiveChild<EXPRESSION> start, ConsecutiveChild<EXPRESSION> end)
    {
        atomicEdit.set(true);
        int startIndex;
        int endIndex;
        List<ConsecutiveChild<@NonNull EXPRESSION>> all = interleaveOperandsAndOperators(operands, operators);
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
            all.add(startIndex, makeBlankOperator());
        }
        else if (!(start instanceof OperatorEntry) && !(end instanceof OperatorEntry))
        {
            all.add(startIndex, makeBlankOperand());
        }
        removeBlanks(operands, operators, true, null);
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

    public abstract ImmutableSet<Character> terminatedByChars();

    public void focusChanged()
    {
        removeBlanks(operands, operators, true, atomicEdit);

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
    private static <EXPRESSION extends @NonNull Object, SEMANTIC_PARENT> void removeBlanks(List<OperandNode<EXPRESSION>> operands, List<OperatorEntry<EXPRESSION, SEMANTIC_PARENT>> operators, boolean accountForFocus, @Nullable BooleanProperty atomicEdit)
    {
        // Note on atomicEdit: we set to true if we modify, and set to false once at the end,
        // which will do nothing if we never edited

        List<ConsecutiveChild<EXPRESSION>> all = interleaveOperandsAndOperators(operands, operators);

        // We check for blanks on the second of the pair, as it makes the index checks easier
        // Hence we only need start at 1:
        int index = 1;
        while (index < all.size())
        {
            if (all.get(index - 1).isBlank() && (!accountForFocus || !all.get(index - 1).isFocused()) &&
                all.get(index).isBlank() && (!accountForFocus || !all.get(index).isFocused()))
            {
                if (atomicEdit != null)
                    atomicEdit.set(true);
                // Both are blank, so remove:
                // Important to remove later one first so as to not mess with the indexing:
                all.remove(index);
                if (index - 1 > 0 || all.size() > 1)
                {
                    all.remove(index - 1);
                }
                // Else we don't want to change index, as we want to assess the next
                // pair
            }
            else
                index += 1; // Only if they weren't removed do we advance
        }

        // Remove final operand or operator if blank&unfocused:
        if (!all.isEmpty())
        {
            ConsecutiveChild last = all.get(all.size() - 1);
            if (last.isBlank() && (!accountForFocus || !last.isFocused()) && all.size() > 1)
            {
                if (atomicEdit != null)
                    atomicEdit.set(true);
                all.remove(all.size() - 1);
            }
        }

        if (atomicEdit != null)
            atomicEdit.set(false);
    }

    // We deliberately don't directly override OperandNode's isFocused,
    // because then it would be too easily to forget to override it a subclass
    // children, which may have other fields which could be focused
    protected boolean childIsFocused()
    {
        return getAllChildren().stream().anyMatch(c -> c.isFocused());
    }

    @Override
    public void showError(String error, List<ErrorRecorder.QuickFix> quickFixes)
    {
        operands.get(0).showError(error, quickFixes);
    }

    public static interface OperandOps<EXPRESSION extends @NonNull Object, SEMANTIC_PARENT>
    {
        public OperandNode<EXPRESSION> makeGeneral(ConsecutiveBase<EXPRESSION, SEMANTIC_PARENT> parent, SEMANTIC_PARENT semanticParent, @Nullable String initialContent);

        public ImmutableList<Pair<String, @Localized String>> getValidOperators(SEMANTIC_PARENT semanticParent);

        public boolean isOperatorAlphabet(char character, SEMANTIC_PARENT semanticParent);

        public Class<EXPRESSION> getOperandClass();

        @NonNull EXPRESSION makeUnfinished(String s);

        EXPRESSION makeExpression(ErrorDisplayer displayer, ErrorDisplayerRecord errorDisplayers, List<EXPRESSION> expressionExps, List<String> ops, boolean directlyRoundBracketed);

        String save(EXPRESSION expression);

        OperandNode<EXPRESSION> loadOperand(String src, ConsecutiveBase<EXPRESSION, SEMANTIC_PARENT> parent) throws UserException, InternalException;
    }

    private static Pair<String, @LocalizableKey String> opD(String op, @LocalizableKey String key)
    {
        return new Pair<>(op, key);
    }

    private static class ExpressionOps implements OperandOps<Expression, ExpressionNodeParent>
    {
        private final static ImmutableList<Pair<String, @LocalizableKey String>> OPERATORS = ImmutableList.<Pair<String, @LocalizableKey String>>copyOf(Arrays.<Pair<String, @LocalizableKey String>>asList(
            opD("=", "op.equal"),
            opD("<>", "op.notEqual"),
            opD("+", "op.plus"),
            opD("-", "op.minus"),
            opD("*", "op.times"),
            opD("/", "op.divide"),
            opD("&", "op.and"),
            opD("|", "op.or"),
            opD("<", "op.lessThan"),
            opD("<=", "op.lessThanOrEqual"),
            opD(">", "op.greaterThan"),
            opD(">=", "op.greaterThanOrEqual"),
            opD("^", "op.raise"),
            opD(",", "op.separator"),
            opD("~", "op.matches"),
            opD("\u00B1", "op.plusminus")
        ));
        private final Set<Integer> ALPHABET = OPERATORS.stream().map(p -> p.getFirst()).flatMapToInt(String::codePoints).boxed().collect(Collectors.<@NonNull Integer>toSet());

        @Override
        public ImmutableList<Pair<String, @Localized String>> getValidOperators(ExpressionNodeParent parent)
        {
            return Utility.concatI(OPERATORS, parent.operatorKeywords());
        }

        public boolean isOperatorAlphabet(char character, ExpressionNodeParent expressionNodeParent)
        {
            return ALPHABET.contains((Integer)(int)character) || expressionNodeParent.operatorKeywords().stream().flatMapToInt(k -> k.getFirst().codePoints()).anyMatch(c -> c == (int)character);
        }

        @Override
        public OperandNode<Expression> makeGeneral(ConsecutiveBase<Expression, ExpressionNodeParent> parent, ExpressionNodeParent semanticParent, @Nullable String initialContent)
        {
            return new GeneralExpressionEntry(initialContent == null ? "" : initialContent, Status.UNFINISHED, parent, semanticParent);
        }

        @Override
        public Class<Expression> getOperandClass()
        {
            return Expression.class;
        }

        @Override
        public Expression makeUnfinished(String s)
        {
            return new UnfinishedExpression(s);
        }

        public Expression makeExpression(ErrorDisplayer displayer, ErrorDisplayerRecord errorDisplayers, List<Expression> expressionExps, List<String> ops, boolean directlyRoundBracketed)
        {


            if (expressionExps.get(expressionExps.size() - 1) instanceof UnfinishedExpression && ((UnfinishedExpression)expressionExps.get(expressionExps.size() - 1)).getText().trim().isEmpty()
                && ops.get(ops.size() - 1).trim().isEmpty())
            {
                // Make copy for editing:
                expressionExps = new ArrayList<>(expressionExps);
                ops = new ArrayList<>(ops);
                expressionExps.remove(expressionExps.size() - 1);
                ops.remove(ops.size() - 1);
            }

            if (ops.isEmpty())
            {
                return expressionExps.get(0);
            }
            else if (ops.stream().allMatch(op -> op.equals("+") || op.equals("-")))
            {
                return errorDisplayers.record(displayer, new AddSubtractExpression(expressionExps, Utility.<String, Op>mapList(ops, op -> op.equals("+") ? Op.ADD : Op.SUBTRACT)));
            }
            else if (ops.stream().allMatch(op -> op.equals("*")))
            {
                return errorDisplayers.record(displayer, new TimesExpression(expressionExps));
            }
            else if (ops.stream().allMatch(op -> op.equals("/")))
            {
                if (expressionExps.size() == 2)
                    return errorDisplayers.record(displayer, new DivideExpression(expressionExps.get(0), expressionExps.get(1)));
            }
            else if (ops.stream().allMatch(op -> op.equals("=")))
            {
                if (expressionExps.size() == 2)
                    return errorDisplayers.record(displayer, new EqualExpression(expressionExps.get(0), expressionExps.get(1)));
            }
            else if (ops.stream().allMatch(op -> op.equals(",")))
            {
                if (directlyRoundBracketed)
                    return errorDisplayers.record(displayer, new TupleExpression(ImmutableList.copyOf(expressionExps)));
                // TODO offer fix to bracket this?
            }

            return errorDisplayers.record(displayer, new InvalidOperatorExpression(expressionExps, ops));
        }

        @Override
        public String save(Expression child)
        {
            return child.save(false);
        }

        @Override
        public OperandNode<Expression> loadOperand(String curItem, ConsecutiveBase<Expression, ExpressionNodeParent> consecutiveBase) throws UserException, InternalException
        {
            return Expression.parse(null, curItem, consecutiveBase.getEditor().getTypeManager()).loadAsSingle().load(consecutiveBase, consecutiveBase.getThisAsSemanticParent());
        }
    }

    private static class UnitExpressionOps implements OperandOps<UnitExpression, UnitNodeParent>
    {
        private final static ImmutableList<Pair<String, @LocalizableKey String>> OPERATORS = ImmutableList.<Pair<String, @LocalizableKey String>>copyOf(Arrays.<Pair<String, @LocalizableKey String>>asList(
            opD("*", "op.times"),
            opD("/", "op.divide"),
            opD("^", "op.raise")
        ));
        private final Set<Integer> ALPHABET = OPERATORS.stream().map(p -> p.getFirst()).flatMapToInt(String::codePoints).boxed().collect(Collectors.<@NonNull Integer>toSet());

        @Override
        public OperandNode<UnitExpression> makeGeneral(ConsecutiveBase<UnitExpression, UnitNodeParent> parent, UnitNodeParent semanticParent, @Nullable String initialContent)
        {
            return new UnitEntry(parent, initialContent == null ? "" : initialContent);
        }

        @Override
        public ImmutableList<Pair<String, @Localized String>> getValidOperators(UnitNodeParent parent)
        {
            return OPERATORS;
        }

        @Override
        public boolean isOperatorAlphabet(char character, UnitNodeParent parent)
        {
            return ALPHABET.contains((int)character);
        }

        @Override
        public Class<UnitExpression> getOperandClass()
        {
            return UnitExpression.class;
        }

        @Override
        public UnitExpression makeUnfinished(String s)
        {
            return new SingleUnitExpression(s);
        }

        @Override
        public UnitExpression makeExpression(ErrorDisplayer displayer, ErrorDisplayerRecord errorDisplayers, List<UnitExpression> operands, List<String> ops, boolean directlyRoundBracketed)
        {
            if (operands.size() == 2 && ops.size() == 1 && ops.get(0).equals("/"))
            {
                return errorDisplayers.record(displayer, new UnitDivideExpression(operands.get(0), operands.get(1)));
            }
            else if (ops.stream().allMatch(o -> o.equals(" ") || o.equals("*")))
            {
                return errorDisplayers.record(displayer, new UnitTimesExpression(ImmutableList.copyOf(operands), ops.stream().map(o -> o.equals("*") ? UnitTimesExpression.Op.STAR : UnitTimesExpression.Op.SPACE).collect(ImmutableList.toImmutableList())));
            }

            return new InvalidOperatorUnitExpression();
        }

        @Override
        public String save(UnitExpression unitExpression)
        {
            return unitExpression.save(true);
        }

        @Override
        public OperandNode<UnitExpression> loadOperand(String src, ConsecutiveBase<UnitExpression, UnitNodeParent> parent) throws UserException, InternalException
        {
            return UnitExpression.load(src).edit(parent, false);
        }
    }

    public static final OperandOps<Expression, ExpressionNodeParent> EXPRESSION_OPS = new ExpressionOps();
    public static final OperandOps<UnitExpression, UnitNodeParent> UNIT_OPS = new UnitExpressionOps();
}
