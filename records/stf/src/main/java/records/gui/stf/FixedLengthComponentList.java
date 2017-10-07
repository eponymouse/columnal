package records.gui.stf;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.error.InternalException;
import records.error.UserException;
import records.gui.stf.StructuredTextField.ErrorFix;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.FXPlatformFunctionInt;
import utility.FXPlatformFunctionIntUser;
import utility.Utility;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * Created by neil on 28/06/2017.
 */
@OnThread(Tag.FXPlatform)
public class FixedLengthComponentList<R, T> extends ParentComponent<R>
{
    private final ImmutableList<Component<? extends T>> contentComponents;
    private final Function<List<T>, R> combine;
    private final ImmutableList<Component<?>> allComponents;

    public FixedLengthComponentList(ImmutableList<Component<?>> parents, @Nullable String prefix, List<FXPlatformFunctionIntUser<ImmutableList<Component<?>>, Component<? extends T>>> components, String divider, @Nullable String suffix, Function<List<T>, R> combine) throws InternalException, UserException
    {
        super(parents);
        Builder<Component<? extends T>> componentBuilder = ImmutableList.builder();
        for (FXPlatformFunctionIntUser<ImmutableList<Component<?>>, Component<? extends T>> f : components)
        {
            componentBuilder.add(f.apply(getItemParents()));
        }
        this.contentComponents = componentBuilder.build();
        this.combine = combine;
        this.allComponents = makeAllComponents(getItemParents(), prefix, contentComponents, divider, suffix);
    }

    // Same as above but only allows throwing an internal exception, and re-orders parameters to avoid having same erasure
    public FixedLengthComponentList(ImmutableList<Component<?>> parents, @Nullable String prefix, String divider, List<FXPlatformFunctionInt<ImmutableList<Component<?>>, Component<? extends T>>> components, @Nullable String suffix, Function<List<T>, R> combine) throws InternalException
    {
        super(parents);
        Builder<Component<? extends T>> componentBuilder = ImmutableList.builder();
        for (FXPlatformFunctionInt<ImmutableList<Component<?>>, Component<? extends T>> f : components)
        {
            componentBuilder.add(f.apply(getItemParents()));
        }
        this.contentComponents = componentBuilder.build();
        this.combine = combine;
        this.allComponents = makeAllComponents(getItemParents(), prefix, contentComponents, divider, suffix);
    }

    @Override
    protected List<Component<?>> getChildComponents()
    {
        return allComponents;
    }

    private static <T> ImmutableList<Component<?>> makeAllComponents(ImmutableList<Component<?>> itemParents, @Nullable String prefix, ImmutableList<Component<? extends T>> content, String divider, @Nullable String suffix)
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
    public Either<List<ErrorFix>, R> endEdit(StructuredTextField field)
    {
        // This is a bit iffy as it threads a mutable array list through the
        // immutable-looking Either instances, but it is safe:
        Either<List<ErrorFix>, ArrayList<T>> result = contentComponents.get(0).endEdit(field).map(x -> new ArrayList<>(Collections.singletonList(x)));
        for (int i = 1; i < contentComponents.size(); i++)
            result = Either.combineConcatError(result, contentComponents.get(i).endEdit(field), (a, x) -> {a.add(x); return a;});
        return result.map(combine);
    }

    @Override
    public boolean hasOuterBrackets()
    {
        return !allComponents.isEmpty() && allComponents.get(0).hasOuterBrackets();
    }
}
