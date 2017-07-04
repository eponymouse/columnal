package records.gui.stf;

import records.gui.stf.StructuredTextField.Component;
import records.gui.stf.StructuredTextField.ErrorFix;
import records.gui.stf.StructuredTextField.Item;
import records.gui.stf.StructuredTextField.ItemVariant;
import utility.Either;
import utility.Utility;

import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;

/**
 * Created by neil on 28/06/2017.
 */ // Takes a component and adds {+|-}HH:MM on the end for a timezone offset.
public class PlusMinusOffsetComponent<R, A> implements Component<R>
{
    private final int seconds;
    private final Component<A> a;
    private final BiFunction<A, ZoneOffset, R> combine;
    private int aLength;

    public PlusMinusOffsetComponent(Component<A> a, int seconds, BiFunction<A, ZoneOffset, R> combine)
    {
        this.a = a;
        this.seconds = seconds;
        this.combine = combine;
    }

    @Override
    public List<Item> getItems()
    {
        int hours = seconds / 3600;
        int minutes = (seconds - (hours * 3600)) / 60;
        List<Item> aItems = a.getItems();
        aLength = aItems.size();
        return Utility.concat(aItems, Arrays.asList(
            new Item(this, "", ItemVariant.TIMEZONE_PLUS_MINUS, "\u00B1"),
            new Item(this, Integer.toString(hours), ItemVariant.EDITABLE_OFFSET_HOUR, "Zone Hours"),
            new Item(this, ":"),
            new Item(this, Integer.toString(minutes), ItemVariant.EDITABLE_OFFSET_MINUTE, "Zone Minutes")));
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
