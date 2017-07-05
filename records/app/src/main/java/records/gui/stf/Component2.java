package records.gui.stf;

import com.google.common.collect.ImmutableList;
import records.gui.stf.StructuredTextField.Component;
import records.gui.stf.StructuredTextField.ErrorFix;
import records.gui.stf.StructuredTextField.Item;
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
public class Component2<R, A, B> extends Component<R>
{
    private final Component<A> a;
    private final Component<B> b;
    private final BiFunction<A, B, R> combine;
    private final String divider;
    private int aLength;

    public Component2(ImmutableList<Component<?>> parents, Function<ImmutableList<Component<?>>, Component<A>> a, String divider, Function<ImmutableList<Component<?>>, Component<B>> b, BiFunction<A, B, R> combine)
    {
        super(parents);
        this.a = a.apply(getItemParents());
        this.b = b.apply(getItemParents());
        this.divider = divider;
        this.combine = combine;
    }

    @Override
    public List<Item> getInitialItems()
    {
        List<Item> aItems = a.getInitialItems();
        aLength = aItems.size();
        return Utility.concat(aItems, Arrays.asList(new Item(getItemParents(), divider)), b.getInitialItems());
    }

    @Override
    public Either<List<ErrorFix>, R> endEdit(StructuredTextField<?> field, List<Item> endResult)
    {
        Either<List<ErrorFix>, A> ax = a.endEdit(field, endResult.subList(0, aLength));
        Either<List<ErrorFix>, B> bx = b.endEdit(field, endResult.subList(aLength + 1, endResult.size()));
        return Either.combineConcatError(ax, bx, combine);
    }

    @Override
    public List<Suggestion> getSuggestions()
    {
        List<Suggestion> suggA = a.getSuggestions();
        List<Suggestion> suggB = b.getSuggestions();
        return Utility.<Suggestion>concat(suggA, Utility.<Suggestion, Suggestion>mapList(suggB, s -> s.offsetBy(aLength + 1)));
    }
}
