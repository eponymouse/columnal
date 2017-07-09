package records.gui.stf;

import com.google.common.collect.ImmutableList;
import records.gui.stf.StructuredTextField.Item;
import records.gui.stf.StructuredTextField.Suggestion;
import utility.Pair;
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
        for (Component<?> component : getChildComponents())
        {
            int delta = component.delete(startIncl, endExcl);
            startIncl += delta;
            endExcl += delta;
        }
        return startIncl;
    }

    @Override
    public final InsertState insert(int beforeIndex, ImmutableList<Integer> codepoints)
    {
        // Important to use indexed loop here and keep calling getChildComponents() as some
        // insertions will change the components (tagged components, or lists)
        for (int i = 0; i < getChildComponents().size(); i++)
        {
            Component<?> component = getChildComponents().get(i);
            InsertState state = component.insert(beforeIndex, codepoints);
            beforeIndex = state.cursorPos;
            codepoints = state.remainingCharactersToInsert;
        }
        return new InsertState(beforeIndex, codepoints);
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
