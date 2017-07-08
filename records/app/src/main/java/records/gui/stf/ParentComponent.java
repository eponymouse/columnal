package records.gui.stf;

import com.google.common.collect.ImmutableList;
import records.gui.stf.StructuredTextField.Item;
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
        return getChildComponents().stream().flatMap(c -> getItems().stream()).collect(Collectors.toList());
    }

    @Override
    public Pair<List<Item>, Integer> delete(int startIncl, int endExcl)
    {
        List<Item> allItems = new ArrayList<>();
        for (Component<?> component : getChildComponents())
        {
            Pair<List<Item>, Integer> after = component.delete(startIncl, endExcl);
            startIncl += after.getSecond();
            endExcl += after.getSecond();
            allItems.addAll(after.getFirst());
        }
        return new Pair<>(allItems, startIncl);
    }

    @Override
    public InsertState insert(int beforeIndex, ImmutableList<Integer> codepoints)
    {
        List<Item> allItems = new ArrayList<>();
        for (Component<?> component : getChildComponents())
        {
            InsertState state = component.insert(beforeIndex, codepoints);
            beforeIndex = state.cursorPos;
            codepoints = state.remainingCharactersToInsert;
            allItems.addAll(state.items);
        }
        return new InsertState(allItems, beforeIndex, codepoints);
    }
}
