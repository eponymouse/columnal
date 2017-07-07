package records.gui.stf;

import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.gui.stf.StructuredTextField.Component;
import records.gui.stf.StructuredTextField.ErrorFix;
import records.gui.stf.StructuredTextField.Item;
import records.gui.stf.StructuredTextField.ItemVariant;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.Utility;

import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Created by neil on 28/06/2017.
 */ // Takes a component and adds {+|-}HH:MM on the end for a timezone offset.
public class PlusMinusOffsetComponent<R, A> extends Component<R>
{
    private final Component<A> a;
    private final BiFunction<A, ZoneOffset, R> combine;
    private final String initialHours;
    private final String initialMinutes;
    private int aLength;

    @OnThread(Tag.FXPlatform)
    public PlusMinusOffsetComponent(ImmutableList<Component<?>> parents, Function<ImmutableList<Component<?>>, Component<A>> a, @Nullable Integer seconds, BiFunction<A, ZoneOffset, R> combine)
    {
        super(parents);
        this.a = a.apply(getItemParents());
        if (seconds != null)
        {
            int hours = seconds / 3600;
            this.initialHours = Integer.toString(hours);
            this.initialMinutes = Integer.toString((seconds - (hours * 3600)) / 60);
        }
        else
        {
            this.initialHours = "";
            this.initialMinutes = "";
        }
        this.combine = combine;
    }

    @Override
    public List<Item> getInitialItems()
    {

        List<Item> aItems = a.getInitialItems();
        aLength = aItems.size();
        return Utility.concat(aItems, Arrays.asList(
            new Item(getItemParents(), "", ItemVariant.TIMEZONE_PLUS_MINUS, "\u00B1"),
            new Item(getItemParents(), initialHours, ItemVariant.EDITABLE_OFFSET_HOUR, "Zone Hours"),
            new Item(getItemParents(), ":"),
            new Item(getItemParents(), initialMinutes, ItemVariant.EDITABLE_OFFSET_MINUTE, "Zone Minutes")));
    }

    @Override
    public Either<List<ErrorFix>, R> endEdit(StructuredTextField<?> field, List<Item> endResult)
    {
        Either<List<ErrorFix>, A> ea = a.endEdit(field, endResult.subList(0, aLength));

        int sign = getItem(endResult, ItemVariant.TIMEZONE_PLUS_MINUS).equals("-") ? -1 : 1;

        int hour = sign * Integer.parseInt(getItem(endResult, ItemVariant.EDITABLE_OFFSET_HOUR));
        int minute = sign * Integer.parseInt(getItem(endResult, ItemVariant.EDITABLE_OFFSET_MINUTE));

        if (hour >= -18 && hour <= 18 && minute >= -59 && minute <= 59)
        {
            return ea.either(err -> Either.left(err), ra -> Either.right(combine.apply(ra, ZoneOffset.ofHoursMinutes(hour, minute))));
        }
        return Either.left(Collections.emptyList() /*TODO*/);
    }
}
