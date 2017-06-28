package records.gui.stf;

import records.gui.stf.StructuredTextField.Component;
import records.gui.stf.StructuredTextField.ErrorFix;
import records.gui.stf.StructuredTextField.Item;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.Utility;

import java.util.Arrays;
import java.util.List;
import java.util.function.BiFunction;

/**
 * Created by neil on 28/06/2017.
 */
@OnThread(Tag.FXPlatform)
public class Component2<R, A, B> implements Component<R>
{
    private final Component<A> a;
    private final Component<B> b;
    private final BiFunction<A, B, R> combine;
    private final String divider;

    public Component2(Component<A> a, String divider, Component<B> b, BiFunction<A, B, R> combine)
    {
        this.a = a;
        this.b = b;
        this.divider = divider;
        this.combine = combine;
    }

    @Override
    public List<Item> getInitialItems()
    {
        return Utility.concat(a.getInitialItems(), Arrays.asList(new Item(divider)), b.getInitialItems());
    }

    @Override
    public Either<List<ErrorFix>, R> endEdit(StructuredTextField<?> field)
    {
        Either<List<ErrorFix>, A> ax = a.endEdit(field);
        Either<List<ErrorFix>, B> bx = b.endEdit(field);
        return ax.either(ea -> bx.either(eb -> Either.left(Utility.concat(ea, eb)), rb -> Either.left(ea)),
            ra -> bx.either(eb -> Either.left(eb), rb -> Either.right(combine.apply(ra, rb))));
    }
}
