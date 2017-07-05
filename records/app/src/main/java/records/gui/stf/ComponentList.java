package records.gui.stf;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.error.InternalException;
import records.gui.stf.StructuredTextField.Component;
import records.gui.stf.StructuredTextField.ErrorFix;
import records.gui.stf.StructuredTextField.Item;
import records.gui.stf.StructuredTextField.Suggestion;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.FXPlatformFunctionInt;
import utility.Pair;
import utility.Utility;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * Created by neil on 28/06/2017.
 */
@OnThread(Tag.FXPlatform)
public class ComponentList<R, T> extends Component<R>
{
    private final ImmutableList<Component<? extends T>> components;
    private final Function<List<T>, R> combine;
    private final String divider;
    private final @Nullable String prefix;
    private final @Nullable String suffix;
    private List<Pair<Integer, Integer>> subLists = new ArrayList<>();

    public ComponentList(ImmutableList<Component<?>> parents, @Nullable String prefix, List<Function<ImmutableList<Component<?>>, Component<? extends T>>> components, String divider, @Nullable String suffix, Function<List<T>, R> combine)
    {
        super(parents);
        this.components = ImmutableList.copyOf(Utility.<Function<ImmutableList<Component<?>>, Component<? extends T>>, Component<? extends T>>mapList(components, f -> f.apply(getItemParents())));
        this.divider = divider;
        this.combine = combine;
        this.prefix = prefix;
        this.suffix = suffix;
    }

    // Same as above but allows throwing an internal exception, and re-orders parameters to avoid having same erasure
    public ComponentList(ImmutableList<Component<?>> parents, @Nullable String prefix, String divider, List<FXPlatformFunctionInt<ImmutableList<Component<?>>, Component<? extends T>>> components, @Nullable String suffix, Function<List<T>, R> combine) throws InternalException
    {
        super(parents);
        Builder<Component<? extends T>> componentBuilder = ImmutableList.builder();
        for (FXPlatformFunctionInt<ImmutableList<Component<?>>, Component<? extends T>> f : components)
        {
            componentBuilder.add(f.apply(getItemParents()));
        }
        this.components = componentBuilder.build();
        this.divider = divider;
        this.combine = combine;
        this.prefix = prefix;
        this.suffix = suffix;
    }

    @Override
    public List<Item> getInitialItems()
    {
        List<Item> r = new ArrayList<>();
        if (prefix != null)
            r.add(new Item(getItemParents(), prefix));
        for (int i = 0; i < components.size(); i++)
        {
            List<Item> items = components.get(i).getInitialItems();
            subLists.add(new Pair<>(r.size(), r.size() + items.size()));
            r.addAll(items);
            if (i < components.size() - 1)
                r.add(new Item(getItemParents(), divider));
        }
        if (suffix != null)
            r.add(new Item(getItemParents(), suffix));
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

    @Override
    public List<Suggestion> getSuggestions()
    {
        List<Suggestion> r = new ArrayList<>();
        for (int i = 0; i < components.size(); i++)
        {
            int offset = subLists.get(i).getFirst();
            r.addAll(Utility.mapList(components.get(i).getSuggestions(), s -> s.offsetBy(offset)));
        }
        return r;
    }
}
