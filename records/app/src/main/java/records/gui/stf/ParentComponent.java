package records.gui.stf;

import com.google.common.collect.ImmutableList;
import records.gui.stf.StructuredTextField.Item;
import records.gui.stf.StructuredTextField.Suggestion;
import utility.Utility;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by neil on 08/07/2017.
 */
public abstract class ParentComponent<T> extends Component<T>
{
    protected ParentComponent(ImmutableList<Component<?>> componentParents)
    {
        super(componentParents);
    }

    protected abstract List<Component<?>> getChildComponents();

    @Override
    public final List<Item> getItems()
    {
        return getChildComponents().stream().flatMap(c -> c.getItems().stream()).collect(Collectors.toList());
    }

    @Override
    public final boolean selectionChanged(int startIncl, int endIncl)
    {
        boolean anyChanged = false;
        int lenSoFar = 0;
        for (Component<?> component : getChildComponents())
        {
            int len = component.getItems().stream().mapToInt(Item::getScreenLength).sum();
            // Don't use short-circuit or here, want to call even if others have changed:
            boolean ch = component.selectionChanged(startIncl - lenSoFar, endIncl - lenSoFar);
            anyChanged = anyChanged | ch;
            lenSoFar += len;
        }
        return anyChanged;
    }

    @Override
    public boolean hasNoData()
    {
        return getChildComponents().stream().allMatch(Component::hasNoData);
    }

    @Override
    public final DeleteState delete(int startIncl, int endExcl)
    {
        boolean allCanBeDeleted = true;
        int lenSoFar = 0;
        int totalDelta = 0;
        for (Component<?> component : getChildComponents())
        {
            int len = component.getItems().stream().mapToInt(Item::getScreenLength).sum();
            DeleteState innerDelete = component.delete(startIncl - lenSoFar, endExcl - lenSoFar);
            totalDelta += innerDelete.startDelta;
            if (!innerDelete.couldDeleteItem)
                allCanBeDeleted = false;
            lenSoFar += len;
        }
        return new DeleteState(totalDelta, allCanBeDeleted);
    }

    @Override
    public final InsertState insert(InsertState state)
    {
        // Important to use indexed loop here and keep calling getChildComponents() as some
        // insertions will change the components (tagged components, or lists)
        for (int i = 0; i < getChildComponents().size(); i++)
        {
            Component<?> component = getChildComponents().get(i);
            state = component.insert(state);
        }
        return state;
    }

    @Override
    public final List<Suggestion> getSuggestions()
    {
        List<Suggestion> r = new ArrayList<>();
        for (int i = 0; i < getChildComponents().size(); i++)
        {
            int iFinal = i;
            r.addAll(Utility.mapList(getChildComponents().get(i).getSuggestions(), s -> s.offsetByNumItems(iFinal)));
        }
        return r;
    }
}
