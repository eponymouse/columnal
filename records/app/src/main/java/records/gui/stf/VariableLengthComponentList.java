package records.gui.stf;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import records.error.InternalException;
import records.error.UserException;
import records.gui.stf.StructuredTextField.ErrorFix;
import records.gui.stf.StructuredTextField.Item;
import records.gui.stf.StructuredTextField.ItemVariant;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.FXPlatformFunctionIntUser;
import utility.FXPlatformRunnable;
import utility.Utility;
import utility.gui.FXUtility;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Created by neil on 28/06/2017.
 */
@OnThread(Tag.FXPlatform)
public abstract class VariableLengthComponentList<R, T> extends Component<R>
{
    private final ObservableList<Component<? extends T>> contentComponents;
    private final boolean canBeAbsent;
    private final SimpleBooleanProperty isAbsent;
    private ImmutableList<Component<?>> allComponents;
    private final Function<List<T>, R> combine;
    private final int suffixCodepoint;
    private final int dividerCodepoint;

    // Same as above but allows throwing an internal exception, and re-orders parameters to avoid having same erasure
    public VariableLengthComponentList(ImmutableList<Component<?>> parents, String prefix, String divider, String suffix, Function<List<T>, R> combine) throws InternalException
    {
        super(parents);
        this.dividerCodepoint = divider.codePointAt(0);
        this.suffixCodepoint = suffix.codePointAt(0);
        this.combine = combine;
        Component<?> prefixComponent = new DividerComponent(getItemParents(), prefix);
        Component<?> suffixComponent = new DividerComponent(getItemParents(), suffix);
        this.allComponents = ImmutableList.of(prefixComponent, suffixComponent);
        this.contentComponents = FXCollections.observableArrayList();
        // Nested lists can be completely removed:
        this.canBeAbsent = !parents.isEmpty() && (parents.get(parents.size() - 1) instanceof VariableLengthComponentList);
        this.isAbsent = new SimpleBooleanProperty(true);

        // Must listen before adding initial items:
        FXUtility.listen(contentComponents, change ->
        {
            Builder<Component<?>> r = ImmutableList.builder();
            r.add(prefixComponent);
            for (int i = 0; i < contentComponents.size(); i++)
            {
                if (i != 0)
                    r.add(new DividerComponent(getItemParents(), divider));
                r.add(contentComponents.get(i));
            }
            r.add(suffixComponent);
            allComponents = r.build();
        });
        FXUtility.addChangeListenerPlatformNN(isAbsent, absent -> {
            if (absent)
            {
                allComponents = ImmutableList.of(new EmptyListComponent(getItemParents()));
            }
            else
            {
                allComponents = ImmutableList.of(prefixComponent, suffixComponent);
            }
        });
    }

    public VariableLengthComponentList(ImmutableList<Component<?>> parents, String prefix, String divider, List<FXPlatformFunctionIntUser<ImmutableList<Component<?>>, Component<? extends T>>> components, String suffix, Function<List<T>, R> combine) throws InternalException, UserException
    {
        this(parents, prefix, divider, suffix, combine);
        isAbsent.set(false);
        for (FXPlatformFunctionIntUser<ImmutableList<Component<?>>, Component<? extends T>> f : components)
        {
            this.contentComponents.add(f.apply(getItemParents()));
        }
    }

    @Override
    public boolean hasNoData()
    {
        return isAbsent.get(); //return contentComponents.isEmpty() || (contentComponents.size() == 1 && contentComponents.get(0).hasNoData());
    }

    @Override
    public List<Item> getItems()
    {
        return allComponents.stream().flatMap(c -> c.getItems().stream()).collect(Collectors.toList());
    }

    @Override
    public Either<List<ErrorFix>, R> endEdit(StructuredTextField<?> field)
    {
        if (contentComponents.isEmpty())
            return Either.right(combine.apply(Collections.emptyList()));

        // This is a bit iffy as it threads a mutable array list through the
        // immutable-looking Either instances, but it is safe:
        Either<List<ErrorFix>, ArrayList<T>> result = contentComponents.get(0).endEdit(field).map(x -> new ArrayList<>(Collections.singletonList(x)));
        for (int i = 1; i < contentComponents.size(); i++)
            result = Either.combineConcatError(result, contentComponents.get(i).endEdit(field), (a, x) -> {a.add(x); return a;});
        return result.map(combine);
    }

    @Override
    public final DeleteState delete(int startIncl, int endExcl)
    {
        int lenSoFar = 0;
        int totalDelta = 0;
        boolean[] removedDividers = contentComponents.isEmpty() ? new boolean[0] : new boolean[contentComponents.size() - 1];
        boolean[] emptyItems = new boolean[contentComponents.size()];
        boolean removedStartOrEnd = false;
        for (int i = 0; i < allComponents.size(); i++)
        {
            Component<?> component = allComponents.get(i);
            int len = component.getItems().stream().mapToInt(Item::getScreenLength).sum();
            DeleteState innerDelete = component.delete(startIncl - lenSoFar, endExcl - lenSoFar);
            totalDelta += innerDelete.startDelta;
            if (innerDelete.couldDeleteItem)
            {
                if (i == 0 || i == allComponents.size() - 1)
                {
                    removedStartOrEnd = true;
                }
                else
                {
                    int indexWithoutPrefix = i - 1;
                    if ((indexWithoutPrefix % 2) == 0)
                    {
                        // Content item
                        emptyItems[indexWithoutPrefix / 2] = true;
                    }
                    else
                    {
                        removedDividers[indexWithoutPrefix / 2] = true;
                    }
                }
            }
            lenSoFar += len;
        }
        // Now we have to work out which list items to actually remove.  The key is that the divider must be removed.
        // If a divider is removed, we decide which adjacent item to remove.  We prefer the one that can be deleted,
        // and if neither matches, we prefer the one inside the range.  If both inside the range, arbitrary pick
        boolean removedItems[] = new boolean[emptyItems.length];
        for (int i = 0; i < removedDividers.length; i++)
        {
            if (removedDividers[i])
            {
                if (emptyItems[i] && !removedItems[i])
                {
                    // Remember that this alters allComponents:
                    contentComponents.remove(i);
                    removedItems[i] = true;
                    // TODO do we need to alter the delta?
                }
                else if (emptyItems[i + 1])
                {
                    // Remember that this alters allComponents:
                    contentComponents.remove(i + 1);
                    removedItems[i + 1] = true;
                }
                // TODO prefer the one not in the range
                else
                {
                    // Remember that this alters allComponents:
                    contentComponents.remove(i);
                    removedItems[i] = true;
                }
            }
        }

        boolean couldDeleteItem = canBeAbsent && removedStartOrEnd && allTrue(removedDividers) && allTrue(emptyItems);
        if (couldDeleteItem)
        {
            contentComponents.clear();
            isAbsent.set(true);
        }
        return new DeleteState(totalDelta, couldDeleteItem);
    }

    private boolean allTrue(boolean[] bs)
    {
        for (boolean b : bs)
        {
            if (b == false)
                return false;
        }
        return true;
    }

    @Override
    public final InsertState insert(InsertState state)
    {
        // Important to use indexed loop here as some
        // insertions will change the components (tagged components, or lists)
        for (int i = 0; i < allComponents.size(); i++)
        {
            // Special case: if currently empty (just open/close) and then type anything besides closing divider in the middle,
            // we add a new item.
            if (contentComponents.isEmpty() && i == 1 && !state.remainingCodepointsToInsert.isEmpty() && state.remainingCodepointsToInsert.get(0) != suffixCodepoint)
            {
                try
                {
                    contentComponents.add(makeNewEntry(getItemParents()));
                }
                catch (InternalException e)
                {
                    Utility.log(e);
                    // Just cancel any further insertions:
                    return new InsertState(state.lenSoFar, state.cursorPos, ImmutableList.of());
                }
            }

            // Deliberate fall-through, not an else:
            // Another special case: if we are at the end of an item (which we will be, if we are in the outer loop and i is odd, and they type the divider, add a new divider+item:
            if (i > 0 && state.cursorPos == state.lenSoFar && !state.remainingCodepointsToInsert.isEmpty() && state.remainingCodepointsToInsert.get(0) == dividerCodepoint)
            {
                /*
                    [23432,2343242]
                    01    23      4

                    If you are before A (in all components) and press comma, you get a new item at B (index in content components)
                    1: 0
                    2: 1
                    3: 1
                    4: 2

                    Formula: i / 2
                 */
                try
                {
                    contentComponents.add(i / 2, makeNewEntry(getItemParents()));
                }
                catch (InternalException e)
                {
                    Utility.log(e);
                    // Just cancel any further insertions:
                    return new InsertState(state.lenSoFar, state.cursorPos, ImmutableList.of());
                }
            }

            // Deliberate fall-through, not an else:
            Component<?> component = allComponents.get(i);
            state = component.insert(state);
        }
        return state;
    }

    /*TODO put back functionality for adding new item
    @Override
    public @Nullable CharEntryResult specialEntered(int character)
    {
        String s = new String(new int[] {character}, 0, 1);

        if (divider.equals(s) || !s.equals(suffix))
        {
            // Divider is ',', they entered ','
            // What we do is change content to ',' ready for valueChanged (this whole mechanism needs revamping anyway)
            return new CharEntryResult(s, true, true);
        }

        return null; // Use default behaviour
    }

    @Override
    public Optional<List<Item>> valueChanged(Item oldVal, Item newVal)
    {
        if (newVal.getType() == ItemVariant.DIVIDER_SPECIAL && !newVal.getValue().equals(suffix))
        {
            // Need to add a new item:
            try
            {
                contentComponents.add(makeNewEntry(getItemParents()));
                return Optional.of(getInitialItems());
            }
            catch (InternalException e)
            {
                Utility.log(e);
            }
        }
        return super.valueChanged(oldVal, newVal);
    }
    */
    protected abstract Component<? extends T> makeNewEntry(ImmutableList<Component<?>> subParents) throws InternalException;

    @Override
    public boolean selectionChanged(int startIncl, int endIncl)
    {
        if (contentComponents.size() == 1 && contentComponents.get(0).hasNoData())
        {
            int screenLength = allComponents.stream().flatMapToInt(c -> c.getItems().stream().mapToInt(Item::getScreenLength)).sum();
            if ((endIncl <= 0) || (startIncl >= screenLength))
            {
                contentComponents.remove(0);
                return true;
            }
        }
        return false;
    }

    private class EmptyListComponent extends TerminalComponent<Void>
    {
        public EmptyListComponent(ImmutableList<Component<?>> componentParents)
        {
            super(componentParents);
            items.add(new Item(getItemParents(), "", ItemVariant.EMPTY_LIST_PROMPT, "List"));
        }

        // Shouldn't happen because parent won't call endEdit on us:
        @Override
        public Either<List<ErrorFix>, Void> endEdit(StructuredTextField<?> field)
        {
            return Either.left(Collections.emptyList());
        }
    }
}
