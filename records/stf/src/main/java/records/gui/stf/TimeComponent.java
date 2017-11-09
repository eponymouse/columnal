package records.gui.stf;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.gui.stf.StructuredTextField.ErrorFix;
import records.gui.stf.StructuredTextField.Item;
import records.gui.stf.StructuredTextField.ItemVariant;
import utility.Either;
import utility.gui.TranslationUtility;

import java.time.LocalTime;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by neil on 28/06/2017.
 */
public class TimeComponent extends TerminalComponent<@Value TemporalAccessor/*LocalTime*/>
{

    public TimeComponent(ImmutableList<Component<?>> parents, @Nullable TemporalAccessor value)
    {
        super(parents);
        String initialHour;
        String initialMinute;
        String initialSecond;
        if (value != null)
        {
            initialHour = Integer.toString(value.get(ChronoField.HOUR_OF_DAY));
            initialMinute = Integer.toString(value.get(ChronoField.MINUTE_OF_HOUR));
            initialSecond = Integer.toString(value.get(ChronoField.SECOND_OF_MINUTE));
            int nano = value.get(ChronoField.NANO_OF_SECOND);
            if (nano != 0)
            {
                initialSecond += "." + String.format("%09d", nano).replaceAll("0+$", "");
            }
        }
        else
        {
            initialHour = "";
            initialMinute = "";
            initialSecond = "";
        }

        items.addAll(Arrays.asList(
            new Item(getItemParents(), initialHour, ItemVariant.EDITABLE_HOUR, TranslationUtility.getString("entry.prompt.hour")),
            new Item(getItemParents(), ":"),
            new Item(getItemParents(), initialMinute, ItemVariant.EDITABLE_MINUTE, TranslationUtility.getString("entry.prompt.minute")),
            new Item(getItemParents(), ":"),
            new Item(getItemParents(), initialSecond, ItemVariant.EDITABLE_SECOND, TranslationUtility.getString("entry.prompt.second"))));
    }

    @Override
    public Either<List<ErrorFix>, @Value TemporalAccessor> endEdit(StructuredTextField field)
    {
        List<ErrorFix> fixes = new ArrayList<>();
        field.revertEditFix().ifPresent(f -> fixes.add(f));
        try
        {
            int hour = Integer.parseInt(getItem(ItemVariant.EDITABLE_HOUR));
            int minute = Integer.parseInt(getItem(ItemVariant.EDITABLE_MINUTE));
            int second;
            int nano;
            String secondText = getItem(ItemVariant.EDITABLE_SECOND);
            if (secondText.contains("."))
            {
                second = Integer.parseInt(secondText.substring(0, secondText.indexOf('.')));
                String nanoText = secondText.substring(secondText.indexOf('.') + 1);
                while (nanoText.length() < 9)
                    nanoText += "0";
                nano = Integer.parseInt(nanoText);
            }
            else
            {
                second = Integer.parseInt(secondText);
                nano = 0;
            }

            if (hour >= 0 && hour <= 23 && minute >= 0 && minute <= 59 && second >= 0 && second <= 59)
            {
                items.set(0, items.get(0).replaceContent(String.format("%02d", hour)));
                items.set(2, items.get(2).replaceContent(String.format("%02d", minute)));
                String canonicalSecondText = String.format("%02d", second);
                if (nano != 0)
                {
                    canonicalSecondText += "." + String.format("%09d", nano).replaceAll("0+$", "");
                }
                items.set(4, items.get(4).replaceContent(canonicalSecondText));
                return Either.right(LocalTime.of(hour, minute, second, nano));
            }
        } catch (NumberFormatException e)
        {

        }
        return Either.left(fixes);
    }
}
