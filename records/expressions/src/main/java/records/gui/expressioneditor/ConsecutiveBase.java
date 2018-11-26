package records.gui.expressioneditor;

import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import javafx.beans.binding.BooleanExpression;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.DataFormat;
import javafx.util.Duration;
import log.Log;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.interning.qual.Interned;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.qual.Pure;
import records.data.datatype.DataType;
import records.gui.expressioneditor.ExpressionEditorUtil.ErrorTop;
import records.transformations.expression.BracketedStatus;
import records.transformations.expression.Expression;
import records.transformations.expression.LoadableExpression.SingleLoader;
import records.transformations.expression.QuickFix;
import records.transformations.expression.UnitExpression;
import records.transformations.expression.type.TypeExpression;
import records.transformations.expression.type.TypeSaver;
import styled.StyledShowable;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformConsumer;
import utility.FXPlatformRunnable;
import utility.Pair;
import utility.Utility;
import utility.gui.FXUtility;
import utility.gui.GUI;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Consecutive implements all the methods of OperandNode but deliberately
 * does not extend it because Consecutive by itself is not a valid
 * operand.  For that, use BracketedExpression.
 */
public @Interned abstract class ConsecutiveBase<EXPRESSION extends StyledShowable, SAVER extends ClipboardSaver> extends DeepNodeTree implements EEDisplayNodeParent, EEDisplayNode, Locatable
{
    protected final OperandOps<EXPRESSION, SAVER> operations;
    
    protected final String style;
    protected final ObservableList<ConsecutiveChild<@NonNull EXPRESSION, SAVER>> children;
    // The container node to use, and the label to focus on selection
    protected final @Nullable Pair<ErrorTop, Label> prefixNode;
    protected final @Nullable Pair<ErrorTop, Label> suffixNode;
    private @Nullable String prompt = null;
    private boolean removingBlanks;

    private BooleanProperty disabledProperty = new SimpleBooleanProperty(false);
    
    protected @MonotonicNonNull EXPRESSION mostRecentSave;

    protected void listenToNodeRelevantList(@UnknownInitialization(ConsecutiveBase.class) ConsecutiveBase<EXPRESSION, SAVER> this, ObservableList<?> children)
    {
        FXUtility.listen(children,c ->
        {
            Utility.later(this).updateNodes();
            Utility.later(this).updateListeners();
        });
    }

    public abstract DataFormat getClipboardType();

    protected final static class PrefixSuffix
    {
        private final @Nullable String prefixText;
        private final @Nullable String suffixText;
        private final @UnknownInitialization ConsecutiveChild<?, ?> selectable;

        public PrefixSuffix(@Nullable String prefixText, @Nullable String suffixText, @UnknownInitialization ConsecutiveChild<?, ?> selectable)
        {
            this.prefixText = prefixText;
            this.suffixText = suffixText;
            this.selectable = selectable;
        }
    }

    public ConsecutiveBase(OperandOps<EXPRESSION, SAVER> operations, @Nullable PrefixSuffix prefixSuffix, String style)
    {
        this.operations = operations;
        this.style = style;
        children = FXCollections.observableArrayList();
        
        this.prefixNode = prefixSuffix == null ? null : makePrefixSuffixNode(prefixSuffix.prefixText, prefixSuffix.selectable);
        this.suffixNode = prefixSuffix == null ? null : makePrefixSuffixNode(prefixSuffix.suffixText, prefixSuffix.selectable);
        listenToNodeRelevantList(children);
        FXUtility.listen(children, c -> {
            //Utility.logStackTrace("Operands size: " + operands.size());
            if (!atomicEdit.get())
                Utility.later(this).selfChanged();
        });
        FXUtility.addChangeListenerPlatformNN(atomicEdit, changing -> {
            if (!changing)
                Utility.later(this).selfChanged();
        });
    }

    private static @Nullable Pair<ErrorTop, Label> makePrefixSuffixNode(@Nullable String content, @UnknownInitialization ConsecutiveChild<?,? > selectable)
    {
        if (content == null)
            return null;
        
        TextField field = new TextField(content) {
            @Override
            @OnThread(Tag.FX)
            public void requestFocus()
            {
            }
        };
        field.getStyleClass().add("entry-field");
        FXUtility.setPseudoclass(field, "ps-keyword", true);
        field.setEditable(false);
        FXUtility.sizeToFit(field, null, null);
        Label topLabel = GUI.label(null, "labelled-top");
        ExpressionEditorUtil.enableSelection(topLabel, selectable, field);
        ErrorTop container = new ErrorTop(topLabel, field);
        container.getStyleClass().add("entry");
        return new Pair<>(container, topLabel);
    }

    @Override
    protected Stream<Node> calculateNodes(@UnknownInitialization(DeepNodeTree.class) ConsecutiveBase<EXPRESSION, SAVER> this)
    {
        List<Node> childrenNodes = new ArrayList<Node>();
        if (children != null)
        {
            for (ConsecutiveChild<EXPRESSION, SAVER> child : children)
            {
                childrenNodes.addAll(child.nodes());
            }
        }
        if (this.prefixNode != null)
            childrenNodes.add(0, this.prefixNode.getFirst());
        if (this.suffixNode != null)
            childrenNodes.add(this.suffixNode.getFirst());
        return childrenNodes.stream();
    }

    @Override
    protected Stream<EEDisplayNode> calculateChildren(@UnknownInitialization(DeepNodeTree.class) ConsecutiveBase<EXPRESSION, SAVER> this)
    {
        return children == null ? Stream.empty() : children.stream().map(c -> c);
    }

    @NonNull
    protected EntryNode<EXPRESSION, SAVER> makeBlankChild()
    {
        //Log.logStackTrace("Make blank child");
        return operations.makeGeneral(this, null);
    }

    public void setDisable(boolean disabled)
    {
        this.disabledProperty.set(disabled);
    }

    public void bindDisable(BooleanExpression bindTo)
    {
        this.disabledProperty.bind(bindTo);
    }

    // Make sure to call if you override
    protected void selfChanged()
    {
        removeBlanksLater();
        for (ConsecutiveChild<EXPRESSION, SAVER> child : children)
        {
            child.bindDisable(disabledProperty);
        }
    }

    private void removeBlanksLater()
    {
        if (!removingBlanks)
        {
            FXUtility.runAfterDelay(Duration.millis(200), () -> {
                removeBlanks();
            });
        }
    }

    public void removeBlanks()
    {
        //Log.debug("Remove blanks from: " + this);
        for (ConsecutiveChild<EXPRESSION, SAVER> child : children)
        {
            child.removeNestedBlanks();
        }
        
        Predicate<ConsecutiveChild<@NonNull EXPRESSION, SAVER>> isRemovable = c -> c.isBlank() && !c.isFocused() && !c.isFocusPending();
        
        // If we don't need to remove blanks, don't trigger the listeners for a no-op change:
        if (children.stream().noneMatch(isRemovable) || children.size() == 1)
            return;
        
        removingBlanks = true;
        //Log.debug("Removing blanks, was size " + children.size());
        atomicEdit.set(true);
        
        children.removeIf(isRemovable);
        if (children.isEmpty())
            children.add(makeBlankChild());
        atomicEdit.set(false);
        //Log.debug("  Now size " + children.size());
        removingBlanks = false;
        
        updatePrompts();
    }

    // Overridden by TopLevelEditor 
    protected void updatePrompts()
    {
    }

    @Override
    public void focus(Focus side)
    {
        int targetIndex = side == Focus.LEFT ? 0 : children.size() - 1;
        // Empty shouldn't happen, but better to fix than complain:
        if (children.isEmpty())
        {
            children.add(makeBlankChild());
        }
        children.get(targetIndex).focus(side);
        
        
        if (!children.get(targetIndex).isFocused())
        {
            int addIndex = side == Focus.LEFT ? 0 : children.size();
            
            children.add(addIndex, focusWhenShown(makeBlankChild()));
        }
    }
    
    protected void replaceSubExpression(EXPRESSION target, EXPRESSION replacement)
    {
        if (mostRecentSave != null)
        {
            atomicEdit.set(true);
            children.setAll(operations.replaceAndLoad(mostRecentSave, target, replacement, getChildrenBracketedStatus()).map(l -> l.load(this)).collect(Collectors.toList()));
            atomicEdit.set(false);
        }
    }

    protected abstract boolean hasImplicitRoundBrackets();

    // Replaces the whole operator-expression that operator was part of, with the new expression
    /*
    protected void replaceWholeLoad(EntryNode<EXPRESSION, SAVER> oldOperator, @UnknownIfRecorded LoadableExpression<EXPRESSION, SAVER> e)
    {
        if (children.contains(oldOperator))
        {
            Pair<List<SingleLoader<EXPRESSION, SAVER, OperandNode<EXPRESSION, SAVER>>>, List<SingleLoader<EXPRESSION, SAVER, OperatorEntry<EXPRESSION, SAVER>>>> loaded = e.loadAsConsecutive(hasImplicitRoundBrackets());
            List<OperandNode<EXPRESSION, SAVER>> startingOperands = Utility.mapList(loaded.getFirst(), operand -> operand.load(this, getThisAsSemanticParent()));
            List<OperatorEntry<EXPRESSION, SAVER>> startingOperators = Utility.mapList(loaded.getSecond(), operator -> operator.load(this, getThisAsSemanticParent()));
            atomicEdit.set(true);
            operands.forEach(EEDisplayNode::cleanup);
            operators.forEach(EEDisplayNode::cleanup);
            operands.setAll(startingOperands);
            operators.setAll(startingOperators);
            atomicEdit.set(false);
            // Get rid of anything which would go if you got focus and lost it again:
            focusChanged();
        }
    }
    */

    public void replace(ConsecutiveChild<@NonNull EXPRESSION, SAVER> oldNode, @Nullable ConsecutiveChild<@NonNull EXPRESSION, SAVER> newNode)
    {
        int index = getOperandIndex(oldNode);
        //System.err.println("Replacing " + oldNode + " with " + newNode + " index " + index);
        if (index != -1)
        {
            //Utility.logStackTrace("Removing " + oldNode + " from " + this);
            if (newNode != null)
            {
                children.get(index).cleanup();
                children.set(index, newNode);
            }
            else
                children.remove(index).cleanup();
        }
    }

    public void replace(ConsecutiveChild<@NonNull EXPRESSION, SAVER> oldNode, Stream<SingleLoader<EXPRESSION, SAVER>> items)
    {
        int index = getOperandIndex(oldNode);
        //System.err.println("Replacing " + oldNode + " with " + newNode + " index " + index);
        if (index != -1)
        {
            atomicEdit.set(true);
            //Utility.logStackTrace("Removing " + oldNode + " from " + this);
            children.remove(index).cleanup();
            children.addAll(index, items.map(l -> l.load(this)).collect(Collectors.toList()));
            atomicEdit.set(false);
        }
    }

    // Is this node equal to then given one, or does it contain the given one?
    @Override
    public boolean isOrContains(EEDisplayNode child)
    {
        return this == child || getAllChildren().stream().anyMatch(n -> n.isOrContains(child));
    }

    public Stream<Pair<Label, Boolean>> _test_getHeaders()
    {
        return getAllChildren().stream().flatMap(o -> o._test_getHeaders());
    }

    /**
     * If the operand to the right of rightOf does NOT pass the given test (or the operator between is non-blank),
     * use the supplier to make one and insert it with blank operator between.
     */
    public void ensureOperandToRight(@UnknownInitialization EntryNode<EXPRESSION, SAVER> rightOf, Predicate<ConsecutiveChild<EXPRESSION, SAVER>> isAcceptable, Supplier<Stream<SingleLoader<EXPRESSION, SAVER>>> makeNew)
    {
        int index = Utility.indexOfRef(children, rightOf);
        if (index + 1 < children.size() && isAcceptable.test(children.get(index + 1)) && children.get(index).isBlank())
            return; // Nothing to do; already acceptable
        // Must add:
        atomicEdit.set(true);
        children.addAll(index + 1, makeNew.get().map(l -> l.load(this)).collect(Collectors.toList()));
        atomicEdit.set(false);
    }

    protected final void save(SAVER saver)
    {
        clearAllErrors();
        for (ConsecutiveChild<EXPRESSION, SAVER> child : children)
        {
            child.save(saver);
        }
    }

    public void flushFocusRequest()
    {
        // Take a copy in case focus causes blank removal:
        for (ConsecutiveChild<EXPRESSION, SAVER> child : new ArrayList<>(children))
        {
            child.flushFocusRequest();
        }
    }

    protected void deleteRangeIncl(ConsecutiveChild<?, ?> start, ConsecutiveChild<?, ?> end)
    {
        boolean inRange = false;
        atomicEdit.set(true);
        for (Iterator<ConsecutiveChild<EXPRESSION, SAVER>> iterator = children.iterator(); iterator.hasNext(); )
        {
            ConsecutiveChild<EXPRESSION, SAVER> child = iterator.next();
            // Ordering important here: we remove begin/end inclusive:
            if (child == start)
                inRange = true;
            if (inRange)
                iterator.remove();
            if (child == end)
                inRange = false;
        }
        if (children.isEmpty())
            children.add(focusWhenShown(makeBlankChild()));
        atomicEdit.set(false);
    }

    public static enum BracketBalanceType
    {
        ROUND, SQUARE;
    }

    public boolean balancedBrackets(BracketBalanceType  bracketBalanceType)
    {
        int open = 0;
        for (ConsecutiveChild<EXPRESSION, SAVER> child : children)
        {
            if (child.opensBracket(bracketBalanceType))
                open++;
            else if (child.closesBracket(bracketBalanceType))
                open--;
        }
        return open == 0;
    }


    public static enum OperatorOutcome { KEEP, BLANK }
    
    public void addOperandToRight(@UnknownInitialization ConsecutiveChild<EXPRESSION, SAVER> rightOf, String initialContent)
    {
        // if coming from blank, must create blank
        withChildIndex(rightOf, index -> {
            @NonNull final EntryNode<EXPRESSION, SAVER> child;
            children.add(index + 1, focusWhenShown(child = makeBlankChild()));
            // Set content after, in case it triggers
            // completion and moves slot:
            child.setText(initialContent);
        });
    }

    private int getOperandIndex(@UnknownInitialization ConsecutiveChild<@NonNull EXPRESSION, SAVER> operand)
    {
        int index = Utility.indexOfRef(children, operand);
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
    public void focusRightOf(EEDisplayNode child, Focus side, boolean becauseOfTab)
    {
        // Cast is safe because of instanceof, and the knowledge that
        // all our children have EXPRESSION as inner type:
        withChildIndex(child, index ->
        {
            boolean leavingBlank = ((ConsecutiveChild<EXPRESSION, SAVER>) child).isBlank();
            if (index + 1 < children.size())
            {
                if (leavingBlank)
                {
                    if (children.get(index + 1).availableForFocus())
                        children.get(index + 1).focus(side);
                    else
                        children.add(index + 2, focusWhenShown(makeBlankChild()));
                }
                else
                    children.add(index + 1, focusWhenShown(makeBlankChild()));
            }
            else
            {
                if (leavingBlank)
                    parentFocusRightOfThis(side, becauseOfTab);
                else
                    children.add(index + 1, focusWhenShown(makeBlankChild()));
            }
        });
    }

    protected abstract void parentFocusRightOfThis(Focus side, boolean becauseOfTab);

    protected static <T extends EEDisplayNode> T focusWhenShown(T node)
    {
        node.focusWhenShown();
        return node;
    }
    
    @SuppressWarnings("unchecked")
    @Override
    public void focusLeftOf(EEDisplayNode child)
    {
        withChildIndex(child, index ->
        {   
            // There's two cases:
            //   A) If we are leaving a blank, we try to focus the item to the left.
            //      If that isn't focusable, we add a blank to its left.
            //   B) If we are leaving a non-blank, we add a blank to its left.
            boolean leavingBlank = ((ConsecutiveChild<EXPRESSION, SAVER>) child).isBlank();
            
            if (index > 0)
            {
                if (leavingBlank)
                {
                    if (children.get(index - 1).availableForFocus())
                        children.get(index - 1).focus(Focus.RIGHT);
                    else
                        children.add(index - 1, focusWhenShown(makeBlankChild()));
                }
                else
                    children.add(index, focusWhenShown(makeBlankChild()));
            }
            else
            {
                // index is zero.  If we are blank then we do go to parent's left
                // If we aren't blank, we make a new blank before us:
                if (leavingBlank)
                    parentFocusLeftOfThis();
                else
                {
                    addBlankAtLeft();
                }
            }
        });
    }

    private void addBlankAtLeft()
    {
        Log.debug("Adding blank at left");
        atomicEdit.set(true);
        EntryNode<@NonNull EXPRESSION, SAVER> blankOperand = makeBlankChild();
        blankOperand.focusWhenShown();
        children.add(0, blankOperand);
        atomicEdit.set(false);
    }

    protected abstract void parentFocusLeftOfThis();

    public void focusWhenShown()
    {
        if (children.isEmpty())
            children.add(makeBlankChild());
        
        children.get(0).focusWhenShown();
    }
    
    // If item is a child of us, run the given lambda and return true.  If not, just return false.
    @SuppressWarnings("unchecked") // It's not actually unchecked...
    private void withChildIndex(@UnknownInitialization EEDisplayNode child, FXPlatformConsumer<Integer> withIndex)
    {
        if (child instanceof ConsecutiveChild && Utility.containsRef(children, (@UnknownInitialization ConsecutiveChild<@NonNull EXPRESSION, SAVER>)child))
        {
            int index = getOperandIndex((ConsecutiveChild<@NonNull EXPRESSION, SAVER>) child);
            withIndex.consume(index);
        }
    }

    @Override
    public void deleteRightOf(EEDisplayNode child)
    {
        withChildIndex(child, index -> {
            if (index + 1 < children.size())
            {
                if (!children.get(index + 1).availableForFocus() || !children.get(index + 1).deleteFirst())
                    children.remove(index + 1);
            }
        });
    }

    @Override
    public void deleteLeftOf(EEDisplayNode child)
    {
        withChildIndex(child, index -> {
            if (index > 0)
            {
                if (!children.get(index - 1).availableForFocus() || !children.get(index - 1).deleteLast())
                    children.remove(index - 1);
            }
        });
    }

    /*
        public @UnknownIfRecorded EXPRESSION saveUnrecorded(ErrorDisplayerRecord errorDisplayers, ErrorAndTypeRecorder onError)
        {
            if (children.isEmpty())
                return operations.makeExpression(errorDisplayers, ImmutableList.of(), ImmutableList.of(), getChildrenBracketedStatus());
            else
                return save(errorDisplayers, onError, children.get(0), children.get(children.size() - 1));
        }
    */
    protected BracketedStatus getChildrenBracketedStatus()
    {
        return BracketedStatus.MISC;
    }
/*
    public @UnknownIfRecorded EXPRESSION save(ErrorDisplayerRecord errorDisplayers, ErrorAndTypeRecorder onError, ConsecutiveChild<@NonNull EXPRESSION, SAVER> first, ConsecutiveChild<@NonNull EXPRESSION, SAVER> last)
    {
        int firstIndex = children.indexOf(first);
        int lastIndex = children.indexOf(last);
        BracketedStatus bracketedStatus = BracketedStatus.MISC;
        if (firstIndex == -1 || lastIndex == -1 || lastIndex < firstIndex)
        {
            firstIndex = 0;
            lastIndex = children.size() - 1;
        }
        // May be because it was -1, or just those values were passed directly:
        if (firstIndex == 0 && lastIndex == children.size() - 1)
        {
            bracketedStatus = getChildrenBracketedStatus();
        }

        return operations.makeExpression(errorDisplayers, children.subList(firstIndex, lastIndex + 1), bracketedStatus);
    }
*/
    public @Nullable DataType inferType()
    {
        return null; //TODO
    }

    /**
     * Gets all the operands between start and end (inclusive).  Returns the empty list
     * if there any problems (start or end not found, or end before start)
     */
    @Pure
    public List<ConsecutiveChild<EXPRESSION, SAVER>> getChildrenFromTo(ConsecutiveChild<EXPRESSION, SAVER> start, ConsecutiveChild<EXPRESSION, SAVER> end)
    {
        List<ConsecutiveChild<@NonNull EXPRESSION, SAVER>> allChildren = getAllChildren();
        int a = allChildren.indexOf(start);
        int b = allChildren.indexOf(end);
        if (a == -1 || b == -1 || a > b)
            return Collections.emptyList();
        return allChildren.subList(a, b + 1);
    }

    @Pure
    protected ImmutableList<ConsecutiveChild<@NonNull EXPRESSION, SAVER>> getAllChildren()
    {
        return ImmutableList.copyOf(children);
    }

    /**
     * Change the selection state of all children in range from/to
     * (inclusive) to the given boolean, and select the given
     * optional focus item (plus run the action once focus is lost)
     */
    public final void markSelection(ConsecutiveChild<EXPRESSION, SAVER> from, ConsecutiveChild<EXPRESSION, SAVER> to, boolean selected, @Nullable Pair<ConsecutiveChild<EXPRESSION, SAVER>, FXPlatformRunnable> focus)
    {
        for (ConsecutiveChild<EXPRESSION, SAVER> n : getChildrenFromTo(from, to))
        {
            n.setSelected(selected, focus != null && n == focus.getFirst(), focus == null ? null : focus.getSecond());
        }
    }

    @Override
    public void visitLocatable(LocatableVisitor visitor)
    {
        children.forEach(o -> o.visitLocatable(visitor));
    }

    @SuppressWarnings("unchecked")
    public @Nullable Map<DataFormat, Object> copyItems(ConsecutiveChild<EXPRESSION, SAVER> start, ConsecutiveChild<EXPRESSION, SAVER> end)
    {
        List<ConsecutiveChild<@NonNull EXPRESSION, SAVER>> all = getAllChildren();
        int startIndex = all.indexOf(start);
        int endIndex = all.indexOf(end);

        if (startIndex == -1 || endIndex == -1)
            // Problem:
            return null;

        final SAVER saver = operations.saveToClipboard(this);
        for (ConsecutiveChild<EXPRESSION, SAVER> child : all.subList(startIndex, endIndex + 1))
        {
            child.save(saver);
        }
        
        return saver.finishClipboard();
    }

    /*
    public boolean insertBefore(ConsecutiveChild insertBefore, CopiedItems itemsToInsert)
    {
        // At the beginning and at the end, we may get a match (e.g. inserting an operator
        // after an operand), or mismatch (inserting an operator after an operator)
        // In the case of a mismatch, we must insert a blank of the other type to get it right.
        atomicEdit.set(true);

        @Nullable Pair<List<OperandNode<EXPRESSION, SAVER>>, List<OperatorEntry<EXPRESSION, SAVER>>> loaded = loadItems(itemsToInsert);
        if (loaded == null)
            return false;
        List<OperandNode<EXPRESSION, SAVER>> newOperands = loaded.getFirst();
        List<OperatorEntry<EXPRESSION, SAVER>> newOperators = loaded.getSecond();

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

        removeBlanks(operands, operators, c -> c.isBlank(), c -> c.isFocused(), EEDisplayNode::cleanup, false, null);

        atomicEdit.set(false);
        return true;
    }
    */

    /*
    private @Nullable List<EntryNode<EXPRESSION, SAVER>> loadItems(CopiedItems copiedItems)
    {
        List<EntryNode<EXPRESSION, SAVER>> loaded = new ArrayList<>();
        try
        {
            for (int i = 0; i < copiedItems.items.size(); i++)
            {
                String curItem = copiedItems.items.get(i);
                loaded.add(operations.loadOperand(curItem, this));
            }
        }
        catch (UserException | InternalException e)
        {
            return null;
        }
        return loaded;
    }
    */

    public void removeItems(ConsecutiveChild<EXPRESSION, SAVER> start, ConsecutiveChild<EXPRESSION, SAVER> end)
    {
        atomicEdit.set(true);
        int startIndex;
        int endIndex;
        startIndex = children.indexOf(start);
        endIndex = children.indexOf(end);
        if (startIndex != -1 && endIndex != -1)
        {
            // Important to go backwards so that the indexes don't get screwed up:
            for (int i = endIndex; i >= startIndex; i--)
                children.remove(i).cleanup();
        }
        
        //removeBlanks(children, c -> c.isBlank(), c -> c.isFocused(), EEDisplayNode::cleanup, true, null);
        atomicEdit.set(false);
    }

    /*
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
    */

    /**
     * Focuses a blank slot on the left of the expression, either an existing
     * blank, or a new specially created blank
     */
    public void focusBlankAtLeft()
    {
        if (children.get(0).isBlank())
            children.get(0).focus(Focus.LEFT);
        else
            addBlankAtLeft();
    }

    protected void unmaskErrors()
    {
        for (ConsecutiveChild<EXPRESSION, SAVER> child : children)
        {
            child.unmaskErrors();
        }
    }

    public void focusChanged()
    {
        //Log.debug("Removing blanks, focus owner: " + nodes().get(0).getScene().getFocusOwner() + " items: " + nodes().stream().map(Object::toString).collect(Collectors.joining(", ")));
        //removeBlanks(children, c -> c.isBlank(), c -> c.isFocused(), EEDisplayNode::cleanup, true, atomicEdit);
        removeBlanksLater();

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
    /*
    static <ITEM> void removeBlanks(List<ITEM> operands, Predicate<ITEM> isBlank, Predicate<ITEM> isFocused, Consumer<ITEM> withRemoved, boolean accountForFocus, @Nullable BooleanProperty atomicEdit)
    {
        // Note on atomicEdit: we set to true if we modify, and set to false once at the end,
        // which will do nothing if we never edited

        List<I> all = ConsecutiveBase.<COMMON, OPERAND, OPERATOR>interleaveOperandsAndOperators(operands, operators);

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
                if (all.get(index) instanceof EEDisplayNode)
                    Log.logStackTrace("Removed blank " + all.get(index - 1) + " and " + all.get(index) + " at " + index + " " + accountForFocus);
                // Both are blank, so remove:
                // Important to remove later one first so as to not mess with the indexing:
                all.remove(index);
                if (index - 1 > 0 || all.size() > 1)
                {
                    withRemoved.accept(all.remove(index - 1));
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
                withRemoved.accept(all.remove(all.size() - 1));
            }
        }

        if (atomicEdit != null)
            atomicEdit.set(false);
    }
    */

    // We deliberately don't directly override OperandNode's isFocused,
    // because then it would be too easily to forget to override it a subclass
    // children, which may have other fields which could be focused
    protected boolean childIsFocused()
    {
        return getAllChildren().stream().anyMatch(c -> c.isFocused());
    }

    public void addErrorAndFixes(@Nullable ConsecutiveChild<EXPRESSION, SAVER> start, @Nullable ConsecutiveChild<EXPRESSION, SAVER> end, StyledString error, List<QuickFix<EXPRESSION, SAVER>> quickFixes)
    {
        Log.debug("Showing " + error.toPlain() + " from " + start + " to " + end + "; " + quickFixes.size() + " " + quickFixes.stream().map(q -> q.getTitle().toPlain()).collect(Collectors.joining("//")) + "\n\n\n");
        boolean inSpan = start == null;
        for (ConsecutiveChild<EXPRESSION, SAVER> child : children)
        {
            if (child == start)
                inSpan = true;
            
            if (inSpan)
                child.addErrorAndFixes(error, quickFixes);
            
            if (child == end)
                inSpan = false;
        }
    }

    public void clearAllErrors()
    {
        children.forEach(op -> op.clearAllErrors());
    }

    public boolean isShowingError()
    {
        return children.get(0).isShowingError();
    }

    public void showType(String type)
    {
    }

    @Override
    public void cleanup()
    {
        children.forEach(EEDisplayNode::cleanup);
    }

    /**
     * Should we show autocomplete window for given child (which has just been focused), or not?
     * General rule is don't show if expression is currently complete, show if they need to type something.
     */
    public abstract boolean showCompletionImmediately(@UnknownInitialization ConsecutiveChild<EXPRESSION, SAVER> child);

    public static final OperandOps<Expression, ExpressionSaver> EXPRESSION_OPS = new ExpressionOps();
    public static final OperandOps<UnitExpression, UnitSaver> UNIT_OPS = new UnitExpressionOps();
    public static final OperandOps<TypeExpression, TypeSaver> TYPE_OPS = new TypeExpressionOps();
}
