package records.gui.stf;

import com.google.common.collect.ImmutableList;
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
public class TimeComponent extends Component<LocalTime>
{
    private final TemporalAccessor value;

    public TimeComponent(ImmutableList<Component<?>> parents, TemporalAccessor value)
    {
        super(parents);
        this.value = value;
    }

    @Override
    public List<Item> getInitialItems()
    {
        return Arrays.asList(
            new Item(getItemParents(), Integer.toString(value.get(ChronoField.HOUR_OF_DAY)), ItemVariant.EDITABLE_HOUR, TranslationUtility.getString("entry.prompt.hour")),
            new Item(getItemParents(), ":"),
            new Item(getItemParents(), Integer.toString(value.get(ChronoField.MINUTE_OF_HOUR)), ItemVariant.EDITABLE_MINUTE, TranslationUtility.getString("entry.prompt.minute")),
            new Item(getItemParents(), ":"),
            new Item(getItemParents(), Integer.toString(value.get(ChronoField.SECOND_OF_MINUTE)), ItemVariant.EDITABLE_SECOND, TranslationUtility.getString("entry.prompt.second")));
    }

    @Override
    public Either<List<ErrorFix>, LocalTime> endEdit(StructuredTextField<?> field, List<Item> endResult)
    {
        List<ErrorFix> fixes = new ArrayList<>();
        field.revertEditFix().ifPresent(fixes::add);
        try
        {
            int hour = Integer.parseInt(getItem(endResult, ItemVariant.EDITABLE_HOUR));
            int minute = Integer.parseInt(getItem(endResult, ItemVariant.EDITABLE_MINUTE));
            int second;
            int nano;
            String secondText = getItem(endResult, ItemVariant.EDITABLE_SECOND);
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
