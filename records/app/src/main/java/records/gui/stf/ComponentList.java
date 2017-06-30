package records.gui.stf;

import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.gui.stf.StructuredTextField.Component;
import records.gui.stf.StructuredTextField.ErrorFix;
import records.gui.stf.StructuredTextField.Item;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.Pair;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * Created by neil on 28/06/2017.
 */
@OnThread(Tag.FXPlatform)
public class ComponentList<R, T> implements Component<R>
{
    private final ImmutableList<Component<T>> components;
    private final Function<List<T>, R> combine;
    private final String divider;
    private final @Nullable String prefix;
    private final @Nullable String suffix;
    private List<Pair<Integer, Integer>> subLists = new ArrayList<>();

    public ComponentList(@Nullable String prefix, ImmutableList<Component<T>> components, String divider, @Nullable String suffix, Function<List<T>, R> combine)

    {
        this.components = components;
        this.divider = divider;
        this.combine = combine;
        this.prefix = prefix;
        this.suffix = suffix;
    }

    @Override
    public List<Item> getItems()
    {
        List<Item> r = new ArrayList<>();
        if (prefix != null)
            r.add(new Item(prefix));
        for (int i = 0; i < components.size(); i++)
        {
            List<Item> items = components.get(i).getItems();
            subLists.add(new Pair<>(r.size(), r.size() + items.size()));
            r.addAll(items);
            if (i < components.size() - 1)
                r.add(new Item(divider));
        }
        if (suffix != null)
            r.add(new Item(suffix));
        return r;
    }

    @Override
    public Either<List<ErrorFix>, R> endEdit(StructuredTextField<?> field, List<Item> endResult)
    {
        // This is a bit iffy as it threads a mutable array list through the
        // immutable-looking Either instances, but it is safe:
        Either<List<ErrorFix>, ArrayList<T>> result = components.get(0).endEdit(field, endResult.subList(subLists.get(0).getFirst(), subLists.get(0).getSecond())).map(x -> new ArrayList<>(Collections.singletonList(x)));
        for (int i = 1; i < components.size(); i++)
            result = Either.combineConcatError(result, components.get(i).endEdit(field, endResult.subList(subLists.get(i).getFirst(), subLists.get(i).getSecond())), (a, x) -> {a.add(x); return a;});
        return result.map(combine);
    }
}
