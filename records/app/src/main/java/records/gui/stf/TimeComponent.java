package records.gui.stf;

import records.gui.stf.StructuredTextField.Component;
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
public class TimeComponent implements Component<LocalTime>
{
    private final TemporalAccessor value;

    public TimeComponent(TemporalAccessor value)
    {
        this.value = value;
    }

    @Override
    public List<Item> getInitialItems()
    {
        return Arrays.asList(
            new Item(Integer.toString(value.get(ChronoField.HOUR_OF_DAY)), ItemVariant.EDITABLE_HOUR, TranslationUtility.getString("entry.prompt.hour")),
            new Item(":"),
            new Item(Integer.toString(value.get(ChronoField.MINUTE_OF_HOUR)), ItemVariant.EDITABLE_MINUTE, TranslationUtility.getString("entry.prompt.minute")),
            new Item(":"),
            new Item(Integer.toString(value.get(ChronoField.SECOND_OF_MINUTE)), ItemVariant.EDITABLE_SECOND, TranslationUtility.getString("entry.prompt.second")));
    }

    @Override
    public Either<List<ErrorFix>, LocalTime> endEdit(StructuredTextField<?> field)
    {
        List<ErrorFix> fixes = new ArrayList<>();
        field.revertEditFix().ifPresent(fixes::add);
        try
        {
            int hour = Integer.parseInt(field.getItem(ItemVariant.EDITABLE_HOUR));
            int minute = Integer.parseInt(field.getItem(ItemVariant.EDITABLE_MINUTE));
            int second;
            int nano;
            String secondText = field.getItem(ItemVariant.EDITABLE_SECOND);
            if (secondText.contains("."))
            {
                second = Integer.parseInt(secondText.substring(0, secondText.indexOf('.')));
                String nanoText = secondText.substring(secondText.indexOf('.') + 1);
                while (nanoText.length() < 9)
                    nanoText += "0";
                nano = Integer.parseInt(nanoText);
            } else
            {
                second = Integer.parseInt(secondText);
                nano = 0;
            }

            if (hour >= 0 && hour <= 23 && minute >= 0 && minute <= 59 && second >= 0 && second <= 59)
            {
                return Either.right(LocalTime.of(hour, minute, second, nano));
            }
        } catch (NumberFormatException e)
        {

        }
        return Either.left(fixes);
    }
}
