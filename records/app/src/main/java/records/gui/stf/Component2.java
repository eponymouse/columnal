package records.gui.stf;

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
    private int aLength;

    public Component2(Component<A> a, String divider, Component<B> b, BiFunction<A, B, R> combine)
    {
        this.a = a;
        this.b = b;
        this.divider = divider;
        this.combine = combine;
    }

    @Override
    public List<Item> getItems()
    {
        List<Item> aItems = a.getItems();
        aLength = aItems.size();
        return Utility.concat(aItems, Arrays.asList(new Item(this, divider)), b.getItems());
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
