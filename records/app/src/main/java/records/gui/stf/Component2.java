package records.gui.stf;

import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.gui.stf.StructuredTextField.ErrorFix;
import records.gui.stf.StructuredTextField.Suggestion;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.Utility;

import java.util.Arrays;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Created by neil on 28/06/2017.
 */
@OnThread(Tag.FXPlatform)
public class Component2<R, A, B> extends ParentComponent<R>
{
    private final Component<A> a;
    private final Component<B> b;
    private final BiFunction<A, B, R> combine;
    private final @Nullable DividerComponent divider;
    private int aLength;

    public Component2(ImmutableList<Component<?>> parents, Function<ImmutableList<Component<?>>, Component<A>> a, @Nullable String divider, Function<ImmutableList<Component<?>>, Component<B>> b, BiFunction<A, B, R> combine)
    {
        super(parents);
        this.a = a.apply(getItemParents());
        this.b = b.apply(getItemParents());
        this.divider = divider == null ? null : new DividerComponent(getItemParents(), divider);
        this.combine = combine;
    }

    @Override
    public List<Component<?>> getChildComponents()
    {
        return divider == null ? Arrays.asList(a, b) : Arrays.asList(a, divider, b);
    }

    @Override
    public Either<List<ErrorFix>, R> endEdit(StructuredTextField<?> field)
    {
        int aLength = a.getItems().size();
        Either<List<ErrorFix>, A> ax = a.endEdit(field);
        Either<List<ErrorFix>, B> bx = b.endEdit(field);
        return Either.combineConcatError(ax, bx, combine);
    }
}
