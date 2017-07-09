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
    public final int delete(int startIncl, int endExcl)
    {
        int lenSoFar = 0;
        int totalDelta = 0;
        for (Component<?> component : getChildComponents())
        {
            int len = component.getItems().stream().mapToInt(Item::getScreenLength).sum();
            totalDelta += component.delete(startIncl - lenSoFar, endExcl - lenSoFar);
            lenSoFar += len;
        }
        return totalDelta;
    }

    @Override
    public final InsertState insert(int lengthBeforeThisComponent, int insertBeforeIndex, ImmutableList<Integer> codepoints)
    {
        // Important to use indexed loop here and keep calling getChildComponents() as some
        // insertions will change the components (tagged components, or lists)
        for (int i = 0; i < getChildComponents().size(); i++)
        {
            Component<?> component = getChildComponents().get(i);
            InsertState state = component.insert(lengthBeforeThisComponent, insertBeforeIndex, codepoints);
            lengthBeforeThisComponent = state.lenSoFar;
            insertBeforeIndex = state.cursorPos;
            codepoints = state.remainingCharactersToInsert;
        }
        return new InsertState(lengthBeforeThisComponent, insertBeforeIndex, codepoints);
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
