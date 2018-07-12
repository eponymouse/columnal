package records.gui.stf;

import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.dataflow.qual.Pure;
import records.gui.stf.StructuredTextField.ErrorFix;
import records.gui.stf.StructuredTextField.Item;
import records.gui.stf.StructuredTextField.ItemVariant;
import records.gui.stf.StructuredTextField.Suggestion;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;

import java.util.List;

/**
 * Created by neil on 08/07/2017.
 */
@OnThread(Tag.FXPlatform)
public abstract class Component<T>
{
    private final ImmutableList<Component<?>> componentParentsAndSelf;

    @SuppressWarnings("initialization") // Due to adding "this" to list
    protected Component(ImmutableList<Component<?>> componentParents)
    {
        this.componentParentsAndSelf = ImmutableList.<Component<?>>builder().addAll(componentParents).add(this).build();
    }

    public abstract List<Item> getItems();

    public ImmutableList<Suggestion> getSuggestions()
    {
        return ImmutableList.of();
    }

    // Gets a list of parents, *including this node* to be passed as parent for an item.
    @SuppressWarnings("nullness") // Not sure why checker can't see that field cannot be null
    @Pure
    protected final ImmutableList<Component<?>> getItemParents(@UnknownInitialization(Component.class)Component<T>this)
    {
        return componentParentsAndSelf;
    }

    public abstract Either<List<ErrorFix>, T> endEdit(StructuredTextField field);

    protected final String getItem(ItemVariant item)
    {
        return getItems().stream().filter(ss -> ss.getType() == item).findFirst().map(ss -> ss.getValue()).orElse("");
    }

    // Gives back an integer delta corresponding to how much the start
    // of the deleted region has moved.
    public abstract DeleteState delete(int startIncl, int endExcl);

    // Return true if components changed
    public abstract boolean selectionChanged(int startIncl, int endIncl);

    // Essentially, could it be removed if it's the only item in a list and focus leaves the list?
    public abstract boolean hasNoData();

    public abstract void focusChanged(boolean focused);

    public static class DeleteState
    {
        // An amount that the start position of the deleted region has moved:
        public final int startDelta;

        // Suggests whether the entirety of the current item makes sense to remove (e.g.
        public final boolean couldDeleteItem;

        public DeleteState(int startDelta, boolean couldDeleteItem)
        {
            this.startDelta = startDelta;
            this.couldDeleteItem = couldDeleteItem;
        }
    }

    // The characters are integer codepoints.
    public abstract InsertState insert(InsertState state);

    public boolean hasOuterBrackets()
    {
        return false;
    }

    // State after an insertion
    public static class InsertState
    {
        // The length of the items before this one.  Used to adjust cursorPos to be relative to the current item.
        public final int lenSoFar;
        // The cursor pos, relative to the start of the whole field (lenSoFar is used to adjust)
        public final int cursorPos;
        // The codepoints left to insert at the cursor position.
        public final ImmutableList<Integer> remainingCodepointsToInsert;

        public InsertState(int lenSoFar, int cursorPos, ImmutableList<Integer> remainingCodepointsToInsert)
        {
            this.lenSoFar = lenSoFar;
            this.cursorPos = cursorPos;
            this.remainingCodepointsToInsert = remainingCodepointsToInsert;
        }
    }
}
