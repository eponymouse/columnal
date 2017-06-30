package records.gui.stf;

import org.checkerframework.checker.i18n.qual.LocalizableKey;
import records.error.InternalException;
import records.gui.stf.StructuredTextField.Component;
import records.gui.stf.StructuredTextField.ErrorFix;
import records.gui.stf.StructuredTextField.Item;
import records.gui.stf.StructuredTextField.ItemVariant;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.gui.TranslationUtility;

import java.time.DateTimeException;
import java.time.Month;
import java.time.Year;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Created by neil on 28/06/2017.
 */
@OnThread(Tag.FXPlatform)
public class YM implements Component<YearMonth>
{
    private final TemporalAccessor value;

    public YM(TemporalAccessor value) throws InternalException
    {
        this.value = value;
    }

    public List<Item> getItems()
    {
        return Arrays.asList(
            new Item(Integer.toString(value.get(ChronoField.MONTH_OF_YEAR)), ItemVariant.EDITABLE_MONTH, TranslationUtility.getString("entry.prompt.month")),
            new Item("/"),
            new Item(Integer.toString(value.get(ChronoField.YEAR)), ItemVariant.EDITABLE_YEAR, TranslationUtility.getString("entry.prompt.year")));
    }

    private static int adjustYear2To4(int month, String originalYearText, final int year)
    {
        if (year < 100 && originalYearText.length() < 4)
        {
            // Apply 80/20 rule (20 years into future, or 80 years into past):
            int fourYear = Year.now().getValue() - (Year.now().getValue() % 100) + year;
            if (fourYear - Year.now().getValue() > 20)
                fourYear -= 100;

            try
            {
                YearMonth.of(fourYear, month);
                // Only if valid:
                return fourYear;
            } catch (DateTimeException e)
            {
                // Not valid if we change year to four digits, may be another fault, so we wait
            }
        }
        return year;
    }

    @Override
    @OnThread(Tag.FXPlatform)
    public Either<List<ErrorFix>, YearMonth> endEdit(StructuredTextField<?> field, List<Item> endResult)
    {
        List<ErrorFix> fixes = new ArrayList<>();
        field.revertEditFix().ifPresent(fixes::add);
        int standardFixes = fixes.size();

        try
        {
            String yearText = getItem(endResult, ItemVariant.EDITABLE_YEAR);
            int year;
            int month;
            try
            {
                year = Integer.parseInt(yearText);
                month = parseMonth(getItem(endResult, ItemVariant.EDITABLE_MONTH));
            } catch (NumberFormatException e)
            {
                // If this throws, we'll fall out to the outer catch block
                // Try swapping month and year:
                month = parseMonth(yearText);
                year = Integer.parseInt(getItem(endResult, ItemVariant.EDITABLE_MONTH));
            }


            // For fixes, we always use fourYear.  If they really want a two digit year, they should enter the leading zeroes
            if (month <= 0)
            {
                clampFix(field, fixes, 1, yearText, year);
            } else if (month >= 13)
            {
                clampFix(field, fixes, 12, yearText, year);
            }

            if (fixes.size() == standardFixes)
            {
                int adjYear = adjustYear2To4(month, yearText, year);
                field.setItem(ItemVariant.EDITABLE_MONTH, Integer.toString(month));
                field.setItem(ItemVariant.EDITABLE_YEAR, String.format("%04d", adjYear));
                return Either.right(YearMonth.of(adjYear, month));
            }
        } catch (NumberFormatException | DateTimeException e)
        {
        }
        return Either.left(fixes);
    }

    private static int parseMonth(String item) throws NumberFormatException
    {
        try
        {
            return Integer.parseInt(item);
        } catch (NumberFormatException e)
        {
            // Try as month name...
        }

        Set<Integer> possibles = new HashSet<>();
        for (int i = 1; i <= 12; i++)
        {
            for (TextStyle textStyle : TextStyle.values())
            {
                if (Month.of(i).getDisplayName(textStyle, Locale.getDefault()).toLowerCase().startsWith(item.toLowerCase()))
                    possibles.add(i);
            }

        }
        if (possibles.size() == 1)
            return possibles.iterator().next();

        throw new NumberFormatException();
    }

    public void clampFix(StructuredTextField<?> field, List<ErrorFix> fixes, int month, String yearText, int year)
    {
        fix(field, fixes, "entry.fix.clamp", month, yearText, year);
    }

    public void fix(StructuredTextField<?> field, List<ErrorFix> fixes, @LocalizableKey String labelKey, int month, String yearText, int year)
    {
        // For fixes, we always use fourYear.  If they really want a two digit year, they should enter the leading zeroes
        year = adjustYear2To4(month, yearText, year);
        try
        {
            YearMonth.of(year, month);
        } catch (DateTimeException e)
        {
            // Don't add a fix if the destination date isn't valid either!
            return;
        }

        final String value = month + "/" + year;
        fixes.add(new ErrorFix(TranslationUtility.getString(labelKey, value))
        {
            @Override
            @OnThread(Tag.FXPlatform)
            public void performFix()
            {
                field.setValue(value);
            }
        });
    }

}
