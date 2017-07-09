package records.gui.stf;

import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.gui.stf.StructuredTextField.ErrorFix;
import records.gui.stf.StructuredTextField.Item;
import records.gui.stf.StructuredTextField.ItemVariant;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;

import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The {+|-}HH:MM on the end for a timezone offset.
 */
public class PlusMinusOffsetComponent extends TerminalComponent<ZoneOffset>
{
    @OnThread(Tag.FXPlatform)
    public PlusMinusOffsetComponent(ImmutableList<Component<?>> parents, @Nullable Integer seconds)
    {
        super(parents);
        String initialMinutes;
        String initialHours;
        if (seconds != null)
        {
            int hours = seconds / 3600;
            initialHours = Integer.toString(hours);
            initialMinutes = Integer.toString((seconds - (hours * 3600)) / 60);
        }
        else
        {
            initialHours = "";
            initialMinutes = "";
        }
        items.addAll(Arrays.asList(
            new Item(getItemParents(), "", ItemVariant.TIMEZONE_PLUS_MINUS, "\u00B1"),
            new Item(getItemParents(), initialHours, ItemVariant.EDITABLE_OFFSET_HOUR, "Zone Hours"),
            new Item(getItemParents(), ":"),
            new Item(getItemParents(), initialMinutes, ItemVariant.EDITABLE_OFFSET_MINUTE, "Zone Minutes")));
    }

    @Override
    public Either<List<ErrorFix>, ZoneOffset> endEdit(StructuredTextField<?> field)
    {
        int sign = getItem(ItemVariant.TIMEZONE_PLUS_MINUS).equals("-") ? -1 : 1;

        int hour = sign * Integer.parseInt(getItem(ItemVariant.EDITABLE_OFFSET_HOUR));
        int minute = sign * Integer.parseInt(getItem(ItemVariant.EDITABLE_OFFSET_MINUTE));

        if (hour >= -18 && hour <= 18 && minute >= -59 && minute <= 59)
        {
            return Either.right(ZoneOffset.ofHoursMinutes(hour, minute));
        }
        return Either.left(Collections.emptyList() /*TODO*/);
    }
}
