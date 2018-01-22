package records.gui.expressioneditor;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import javafx.beans.property.BooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import log.Log;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.interning.qual.Interned;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.qual.Pure;
import org.jetbrains.annotations.NotNull;
import records.data.datatype.DataType;
import records.error.InternalException;
import records.error.UserException;
import records.gui.expressioneditor.ExpressionEditorUtil.CopiedItems;
import records.transformations.expression.*;
import records.transformations.expression.ErrorAndTypeRecorder.QuickFix.ReplacementTarget;
import records.transformations.expression.LoadableExpression.SingleLoader;
import styled.StyledString;
import utility.FXPlatformConsumer;
import utility.Pair;
import utility.Utility;
import utility.gui.FXUtility;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Consecutive implements all the methods of OperandNode but deliberately
 * does not extend it because Consecutive by itself is not a valid
 * operand.  For that, use BracketedExpression.
 */
public @Interned abstract class ConsecutiveBase<EXPRESSION extends @NonNull LoadableExpression<EXPRESSION, SEMANTIC_PARENT>, SEMANTIC_PARENT> extends DeepNodeTree implements EEDisplayNodeParent, EEDisplayNode, ErrorDisplayer<EXPRESSION>
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
    protected final ObservableList<OperandNode<@NonNull EXPRESSION, SEMANTIC_PARENT>> operands;
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
            //Utility.logStackTrace("Operands size: " + operands.size());
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
    protected OperandNode<@NonNull EXPRESSION, SEMANTIC_PARENT> makeBlankOperand()
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
    
    protected void replaceLoad(OperandNode<@NonNull EXPRESSION, SEMANTIC_PARENT> oldNode, @NonNull Pair<ReplacementTarget, EXPRESSION> newNode)
    {
        if (newNode.getFirst() == ReplacementTarget.CURRENT)
        {
            replace(oldNode, newNode.getSecond().loadAsSingle().load(this, getThisAsSemanticParent()));
        }
        else
        {
            Pair<List<SingleLoader<EXPRESSION, SEMANTIC_PARENT, OperandNode<EXPRESSION, SEMANTIC_PARENT>>>, List<SingleLoader<EXPRESSION, SEMANTIC_PARENT, OperatorEntry<EXPRESSION, SEMANTIC_PARENT>>>> loaded = newNode.getSecond().loadAsConsecutive(hasImplicitRoundBrackets());

            List<OperandNode<EXPRESSION, SEMANTIC_PARENT>> startingOperands = Utility.mapList(loaded.getFirst(), operand -> operand.load(this, getThisAsSemanticParent()));
            List<OperatorEntry<EXPRESSION, SEMANTIC_PARENT>> startingOperators = Utility.mapList(loaded.getSecond(), operator -> operator.load(this, getThisAsSemanticParent()));
            
            atomicEdit.set(true);
            operands.setAll(startingOperands);
            operators.setAll(startingOperators);
            atomicEdit.set(false);
            // Get rid of anything which would go if you got focus and lost it again:
            focusChanged();
        }
    }

    protected abstract boolean hasImplicitRoundBrackets();

    // Replaces the whole operator-expression that operator was part of, with the new expression
    protected void replaceWholeLoad(OperatorEntry<EXPRESSION, SEMANTIC_PARENT> oldOperator, EXPRESSION e)
    {
        
    }

    public void replace(OperandNode<@NonNull EXPRESSION, SEMANTIC_PARENT> oldNode, @Nullable OperandNode<@NonNull EXPRESSION, SEMANTIC_PARENT> newNode)
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

    // Is this node equal to then given one, or does it contain the given one?
    @Override
    public boolean isOrContains(EEDisplayNode child)
    {
        return this == child || getAllChildren().stream().anyMatch(n -> n.isOrContains(child));
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
            OperandNode<EXPRESSION, SEMANTIC_PARENT> operandNode = operations.makeGeneral(this, getThisAsSemanticParent(), initialContent);
            if (focus)
                operandNode.focusWhenShown();
            operands.add(index+1, operandNode);
            atomicEdit.set(false);
            return OperatorOutcome.KEEP;
        }
        // If we can't find it, I guess blank:
        return OperatorOutcome.BLANK;
    }


    public void setOperatorToRight(@UnknownInitialization OperandNode<@NonNull EXPRESSION, SEMANTIC_PARENT> rightOf, String operator)
    {
        int index = getOperandIndex(rightOf);
        if (index != -1)
        {
            if (index >= operators.size() || !operators.get(index).fromBlankTo(operator))
            {
                // Add new operator and new operand:
                atomicEdit.set(true);
                operators.add(index, new OperatorEntry<>(operations.getOperandClass(), operator, true, this));
                OperandNode<@NonNull EXPRESSION, SEMANTIC_PARENT> blankOperand = makeBlankOperand();
                operands.add(index+1, blankOperand);
                atomicEdit.set(false);
            }
        }
    }

    private int getOperandIndex(@UnknownInitialization OperandNode<@NonNull EXPRESSION, SEMANTIC_PARENT> operand)
    {
        int index = Utility.indexOfRef(operands, operand);
        if (index == -1)
            Log.logStackTrace("Asked for index but " + operand + " not a child of parent " + this);
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
    public void focusRightOf(@UnknownInitialization EEDisplayNode child, Focus side)
    {
        // Cast is safe because of instanceof, and the knowledge that
        // all our children have EXPRESSION as inner type:
        if (child instanceof OperandNode && Utility.containsRef(operands, (OperandNode<@NonNull EXPRESSION, SEMANTIC_PARENT>)child))
        {
            int index = getOperandIndex((OperandNode<@NonNull EXPRESSION, SEMANTIC_PARENT>)child);
            if (index >= operators.size())
            {
                operators.add(makeBlankOperator());
            }
            operators.get(index).focus(side);
        }
        else if (child instanceof OperatorEntry)
        {
            int index = Utility.indexOfRef(operators, (OperatorEntry<EXPRESSION, SEMANTIC_PARENT>)child);
            if (index + 1 < operands.size())
                operands.get(index + 1).focus(side);
            else
                parentFocusRightOfThis(side);
        }
    }

    protected abstract void parentFocusRightOfThis(Focus side);

    @SuppressWarnings("unchecked")
    @Override
    public void focusLeftOf(@UnknownInitialization EEDisplayNode child)
    {
        if (child instanceof OperandNode && Utility.containsRef(operands, (OperandNode<@NonNull EXPRESSION, SEMANTIC_PARENT>)child))
        {
            int index = getOperandIndex((OperandNode<@NonNull EXPRESSION, SEMANTIC_PARENT>) child);
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
        OperandNode<@NonNull EXPRESSION, SEMANTIC_PARENT> blankOperand = makeBlankOperand();
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
    protected Pair<Boolean, List<String>> getOperators(int firstIndex, int lastIndex)
    {
        boolean lastOp = operators.size() == operands.size();
        return new Pair<>(!lastOp, Utility.<OperatorEntry<EXPRESSION, SEMANTIC_PARENT>, String>mapList(operators, op -> op.get()));
    }

    public EXPRESSION save(ErrorDisplayerRecord errorDisplayers, FXPlatformConsumer<Object> onError)
    {
        if (operands.isEmpty())
            return operations.makeExpression(this, errorDisplayers, ImmutableList.of(), ImmutableList.of(), getChildrenBracketedStatus());
        else
            return save(errorDisplayers, onError, operands.get(0), operands.get(operands.size() - 1));
    }

    protected BracketedStatus getChildrenBracketedStatus()
    {
        return BracketedStatus.MISC;
    }

    public EXPRESSION save(ErrorDisplayerRecord errorDisplayers, FXPlatformConsumer<Object> onError, OperandNode<@NonNull EXPRESSION, SEMANTIC_PARENT> first, OperandNode<@NonNull EXPRESSION, SEMANTIC_PARENT> last)
    {
        int firstIndex = operands.indexOf(first);
        int lastIndex = operands.indexOf(last);
        BracketedStatus bracketedStatus = BracketedStatus.MISC;
        if (firstIndex == -1 || lastIndex == -1 || lastIndex < firstIndex)
        {
            firstIndex = 0;
            lastIndex = operands.size() - 1;
        }
        // May be because it was -1, or just those values were passed directly:
        if (firstIndex == 0 && lastIndex == operands.size() - 1)
        {
            bracketedStatus = getChildrenBracketedStatus();
        }

        ImmutableList<@NonNull EXPRESSION> expressionExps = Utility.<OperandNode<@NonNull EXPRESSION, SEMANTIC_PARENT>, @NonNull EXPRESSION>mapListI(operands.subList(firstIndex, lastIndex + 1), (OperandNode<@NonNull EXPRESSION, SEMANTIC_PARENT> n) -> n.save(errorDisplayers, onError));
        Pair<Boolean, List<String>> opsValid = getOperators(firstIndex, lastIndex);
        List<String> ops = opsValid.getSecond();

        if (ops.isEmpty()) // Must be valid in this case
        {
            // Only one operand:
            return expressionExps.get(0);
        }

        return operations.makeExpression(this, errorDisplayers, expressionExps, ops, bracketedStatus);
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
    public List<ConsecutiveChild<EXPRESSION, SEMANTIC_PARENT>> getChildrenFromTo(ConsecutiveChild<EXPRESSION, SEMANTIC_PARENT> start, ConsecutiveChild<EXPRESSION, SEMANTIC_PARENT> end)
    {
        List<ConsecutiveChild<@NonNull EXPRESSION, SEMANTIC_PARENT>> allChildren = getAllChildren();
        int a = allChildren.indexOf(start);
        int b = allChildren.indexOf(end);
        if (a == -1 || b == -1 || a > b)
            return Collections.emptyList();
        return allChildren.subList(a, b + 1);
    }

    protected List<ConsecutiveChild<@NonNull EXPRESSION, SEMANTIC_PARENT>> getAllChildren()
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

    private static <COMMON, OPERAND extends COMMON, OPERATOR extends COMMON> List<COMMON> interleaveOperandsAndOperators(List<OPERAND> operands, List<OPERATOR> operators)
    {
        return new AbstractList<COMMON>()
        {
            @Override
            public COMMON get(int index)
            {
                return ((index & 1) == 0) ? operands.get(index >> 1) : operators.get(index >> 1);
            }

            @Override
            public int size()
            {
                return operands.size() + operators.size();
            }

            @Override
            public COMMON remove(int index)
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
            public void add(int index, COMMON element)
            {
                if ((index & 1) == 0)
                    operands.add(index >> 1, (OPERAND) element);
                else
                    operators.add(index >> 1, (OPERATOR) element);
            }
        };
    }

    public void markSelection(ConsecutiveChild<EXPRESSION, SEMANTIC_PARENT> from, ConsecutiveChild<EXPRESSION, SEMANTIC_PARENT> to, boolean selected)
    {
        for (ConsecutiveChild<EXPRESSION, SEMANTIC_PARENT> n : getChildrenFromTo(from, to))
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
    protected <C extends LoadableExpression<C, ?>> @Nullable Pair<ConsecutiveChild<? extends C, ?>, Double> findClosestDrop(Point2D loc, Class<C> forType)
    {
        return Utility.filterOutNulls(Stream.<ConsecutiveChild<EXPRESSION, SEMANTIC_PARENT>>concat(operands.stream(), operators.stream()).<@Nullable Pair<ConsecutiveChild<? extends C, ?>, Double>>map(n -> n.findClosestDrop(loc, forType))).min(Comparator.comparing(p -> p.getSecond())).get();
    }

    @SuppressWarnings("unchecked")
    public @Nullable CopiedItems copyItems(ConsecutiveChild<EXPRESSION, SEMANTIC_PARENT> start, ConsecutiveChild<EXPRESSION, SEMANTIC_PARENT> end)
    {
        List<ConsecutiveChild<@NonNull EXPRESSION, SEMANTIC_PARENT>> all = getAllChildren();
        boolean startIsOperator = start instanceof OperatorEntry;
        int startIndex = all.indexOf(start);
        int endIndex = all.indexOf(end);

        if (startIndex == -1 || endIndex == -1)
            // Problem:
            return null;

        return new CopiedItems(
            Utility.<ConsecutiveChild<EXPRESSION, SEMANTIC_PARENT>, String>mapList(all.subList(startIndex, endIndex + 1), child -> {
                if (child instanceof OperatorEntry)
                    return ((OperatorEntry<EXPRESSION, SEMANTIC_PARENT>)child).get();
                else
                    return operations.save(((OperandNode<EXPRESSION, SEMANTIC_PARENT>)child).save(new ErrorDisplayerRecord(), o -> {}));
            }), startIsOperator);
    }

    public boolean insertBefore(ConsecutiveChild insertBefore, CopiedItems itemsToInsert)
    {
        // At the beginning and at the end, we may get a match (e.g. inserting an operator
        // after an operand), or mismatch (inserting an operator after an operator)
        // In the case of a mismatch, we must insert a blank of the other type to get it right.
        atomicEdit.set(true);

        @Nullable Pair<List<OperandNode<EXPRESSION, SEMANTIC_PARENT>>, List<OperatorEntry<EXPRESSION, SEMANTIC_PARENT>>> loaded = loadItems(itemsToInsert);
        if (loaded == null)
            return false;
        List<OperandNode<EXPRESSION, SEMANTIC_PARENT>> newOperands = loaded.getFirst();
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

        removeBlanks(operands, operators, c -> c.isBlank(), c -> c.isFocused(), false, null);

        atomicEdit.set(false);
        return true;
    }

    private @Nullable Pair<List<OperandNode<EXPRESSION, SEMANTIC_PARENT>>, List<OperatorEntry<EXPRESSION, SEMANTIC_PARENT>>> loadItems(CopiedItems copiedItems)
    {
        List<OperandNode<EXPRESSION, SEMANTIC_PARENT>> operands = new ArrayList<>();
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

    public void removeItems(ConsecutiveChild<EXPRESSION, SEMANTIC_PARENT> start, ConsecutiveChild<EXPRESSION, SEMANTIC_PARENT> end)
    {
        atomicEdit.set(true);
        int startIndex;
        int endIndex;
        List<ConsecutiveChild<@NonNull EXPRESSION, SEMANTIC_PARENT>> all = interleaveOperandsAndOperators(operands, operators);
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
        removeBlanks(operands, operators, c -> c.isBlank(), c -> c.isFocused(), true, null);
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

    /**
     * A collection of characters which terminate this item, i.e. which you could press in the child
     * at the last position, and it should complete this ConsecutiveBase and move on.
     *
     * Note that while the returned collection is immutable, this method may return different values at
     * different times, e.g. because we are using the parent's set, which in turn has changed (like when
     * a clause node becomes/unbecomes the last item in a pattern match).
     */
    public abstract ImmutableSet<Character> terminatedByChars();

    public void focusChanged()
    {
        removeBlanks(operands, operators, c -> c.isBlank(), c -> c.isFocused(), true, atomicEdit);

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
    static <COMMON, OPERAND extends COMMON, OPERATOR extends COMMON> void removeBlanks(List<OPERAND> operands, List<OPERATOR> operators, Predicate<COMMON> isBlank, Predicate<COMMON> isFocused, boolean accountForFocus, @Nullable BooleanProperty atomicEdit)
    {
        // Note on atomicEdit: we set to true if we modify, and set to false once at the end,
        // which will do nothing if we never edited

        List<COMMON> all = ConsecutiveBase.<COMMON, OPERAND, OPERATOR>interleaveOperandsAndOperators(operands, operators);

        // We check for blanks on the second of the pair, as it makes the index checks easier
        // Hence we only need start at 1:
        int index = 1;
        while (index < all.size())
        {
            if (isBlank.test(all.get(index - 1)) && (!accountForFocus || !isFocused.test(all.get(index - 1))) &&
                isBlank.test(all.get(index)) && (!accountForFocus || !isFocused.test(all.get(index))))
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
            COMMON last = all.get(all.size() - 1);
            if (isBlank.test(last) && (!accountForFocus || !isFocused.test(last)) && all.size() > 1)
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
    public void showError(StyledString error, List<ErrorAndTypeRecorder.QuickFix<EXPRESSION>> quickFixes)
    {
        operands.get(0).showError(error, quickFixes);
    }

    @Override
    public boolean isShowingError()
    {
        return operands.get(0).isShowingError();
    }

    @Override
    public void showType(String type)
    {
        for (OperatorEntry<EXPRESSION, SEMANTIC_PARENT> operator : operators)
        {
            operator.showType(type);
        }
    }

    public static enum BracketedStatus
    {
        /** Direct round brackets, i.e. if there's only commas, this can be a tuple expression */
        DIRECT_ROUND_BRACKETED,
        /** Direct square brackets, i.e. this has to be an array expression */
        DIRECT_SQUARE_BRACKETED,
        /* Normal state: the others above don't apply */
        MISC;
    }


    public static final OperandOps<Expression, ExpressionNodeParent> EXPRESSION_OPS = new ExpressionOps();
    public static final OperandOps<UnitExpression, UnitNodeParent> UNIT_OPS = new UnitExpressionOps();
}
