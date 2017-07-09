package records.gui.stf;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.error.InternalException;
import records.gui.stf.StructuredTextField.CharEntryResult;
import records.gui.stf.StructuredTextField.ErrorFix;
import records.gui.stf.StructuredTextField.Item;
import records.gui.stf.StructuredTextField.ItemVariant;
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
import java.util.Optional;
import java.util.function.Function;

/**
 * Created by neil on 28/06/2017.
 */
@OnThread(Tag.FXPlatform)
public abstract class VariableLengthComponentList<R, T> extends ParentComponent<R>
{
    private final ArrayList<Component<? extends T>> contentComponents;
    private ImmutableList<Component<?>> allComponents;
    private final Function<List<T>, R> combine;
    private final String divider;
    private final @Nullable String prefix;
    private final @Nullable String suffix;

    // Same as above but allows throwing an internal exception, and re-orders parameters to avoid having same erasure
    public VariableLengthComponentList(ImmutableList<Component<?>> parents, @Nullable String prefix, String divider, List<FXPlatformFunctionInt<ImmutableList<Component<?>>, Component<? extends T>>> components, @Nullable String suffix, Function<List<T>, R> combine) throws InternalException
    {
        super(parents);
        this.contentComponents = new ArrayList<>();
        for (FXPlatformFunctionInt<ImmutableList<Component<?>>, Component<? extends T>> f : components)
        {
            this.contentComponents.add(f.apply(getItemParents()));
        }
        this.divider = divider;
        this.combine = combine;
        this.prefix = prefix;
        this.suffix = suffix;
        this.allComponents = makeAllComponents(getItemParents(), prefix, contentComponents, divider, suffix);
    }

    @Override
    protected List<Component<?>> getChildComponents()
    {
        return allComponents;
    }

    private static <T> ImmutableList<Component<?>> makeAllComponents(ImmutableList<Component<?>> itemParents, @Nullable String prefix, List<Component<? extends T>> content, String divider, @Nullable String suffix)
    {
        Builder<Component<?>> build = ImmutableList.builder();
        if (prefix != null)
            build.add(new DividerComponent(itemParents, prefix));
        for (int i = 0; i < content.size(); i++)
        {
            build.add(content.get(i));
            if (i < content.size() - 1)
                build.add(new DividerComponent(itemParents, divider));
        }
        if (suffix != null)
            build.add(new DividerComponent(itemParents, suffix));
        return build.build();
    }

    @Override
    public Either<List<ErrorFix>, R> endEdit(StructuredTextField<?> field)
    {
        // This is a bit iffy as it threads a mutable array list through the
        // immutable-looking Either instances, but it is safe:
        Either<List<ErrorFix>, ArrayList<T>> result = contentComponents.get(0).endEdit(field).map(x -> new ArrayList<>(Collections.singletonList(x)));
        for (int i = 1; i < contentComponents.size(); i++)
            result = Either.combineConcatError(result, contentComponents.get(i).endEdit(field), (a, x) -> {a.add(x); return a;});
        return result.map(combine);
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
}
