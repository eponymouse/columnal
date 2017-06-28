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
import java.time.LocalDate;
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
public class YMD implements Component<LocalDate>
{
    private final TemporalAccessor value;

    public YMD(TemporalAccessor value) throws InternalException
    {
        this.value = value;
    }

    public List<Item> getInitialItems()
    {
        return Arrays.asList(new Item(Integer.toString(value.get(ChronoField.DAY_OF_MONTH)), ItemVariant.EDITABLE_DAY, TranslationUtility.getString("entry.prompt.day")),
            new Item("/"),
            new Item(Integer.toString(value.get(ChronoField.MONTH_OF_YEAR)), ItemVariant.EDITABLE_MONTH, TranslationUtility.getString("entry.prompt.month")),
            new Item("/"),
            new Item(Integer.toString(value.get(ChronoField.YEAR)), ItemVariant.EDITABLE_YEAR, TranslationUtility.getString("entry.prompt.year")));
    }

    private static int adjustYear2To4(int day, int month, String originalYearText, final int year)
    {
        if (year < 100 && originalYearText.length() < 4)
        {
            // Apply 80/20 rule (20 years into future, or 80 years into past):
            int fourYear = Year.now().getValue() - (Year.now().getValue() % 100) + year;
            if (fourYear - Year.now().getValue() > 20)
                fourYear -= 100;

            try
            {
                LocalDate.of(fourYear, month, day);
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
    public Either<List<ErrorFix>, LocalDate> endEdit(StructuredTextField<?> field)
    {
        List<ErrorFix> fixes = new ArrayList<>();
        field.revertEditFix().ifPresent(fixes::add);
        int standardFixes = fixes.size();

        try
        {
            String dayText = field.getItem(ItemVariant.EDITABLE_DAY);
            int day, month;
            try
            {
                day = Integer.parseInt(dayText);
                month = parseMonth(field.getItem(ItemVariant.EDITABLE_MONTH));
            } catch (NumberFormatException e)
            {
                // If this throws, we'll fall out to the outer catch block
                // Try swapping day and month:
                month = parseMonth(dayText);
                day = Integer.parseInt(field.getItem(ItemVariant.EDITABLE_MONTH));
                dayText = field.getItem(ItemVariant.EDITABLE_MONTH);
            }


            String yearText = field.getItem(ItemVariant.EDITABLE_YEAR);
            int year = Integer.parseInt(yearText);
            // For fixes, we always use fourYear.  If they really want a two digit year, they should enter the leading zeroes
            if (day <= 0)
            {
                clampFix(field, fixes, 1, month, yearText, year);
            }
            try
            {
                int adjYear = adjustYear2To4(1, month, yearText, year);
                int monthLength = YearMonth.of(adjYear, month).lengthOfMonth();
                if (day > monthLength)
                {
                    clampFix(field, fixes, monthLength, month, yearText, year);
                    // If it's like 31st September, suggest 1st October:
                    if (day <= 31 && day - 1 == monthLength)
                    {
                        // Can't happen for December, so don't need to worry about year roll-over:
                        fix(field, fixes, "entry.fix.adjustByDay", 1, month + 1, yearText, year);
                    }

                    if (day >= 32 && year <= monthLength)
                    {
                        // They may have entered year, month, day, so swap day and year:
                        clampFix(field, fixes, year, month, dayText, day);
                    }
                }
            } catch (DateTimeException e)
            {
                // Not a valid year-month anyway, so don't suggest
            }

            if (month <= 0)
            {
                clampFix(field, fixes, day, 1, yearText, year);
            } else if (month >= 13)
            {
                clampFix(field, fixes, day, 12, yearText, year);
                if (1 <= day && day <= 12)
                {
                    // Possible day-month transposition:
                    fix(field, fixes, "entry.fix.dayMonthSwap", month, day, yearText, year);
                }
            }

            if (fixes.size() == standardFixes)
            {
                int adjYear = adjustYear2To4(day, month, yearText, year);
                field.setItem(ItemVariant.EDITABLE_DAY, Integer.toString(day));
                field.setItem(ItemVariant.EDITABLE_MONTH, Integer.toString(month));
                field.setItem(ItemVariant.EDITABLE_YEAR, String.format("%04d", adjYear));
                return Either.right(LocalDate.of(adjYear, month, day));
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

    public void clampFix(StructuredTextField<?> field, List<ErrorFix> fixes, int day, int month, String yearText, int year)
    {
        fix(field, fixes, "entry.fix.clamp", day, month, yearText, year);
    }

    public void fix(StructuredTextField<?> field, List<ErrorFix> fixes, @LocalizableKey String labelKey, int day, int month, String yearText, int year)
    {
        // For fixes, we always use fourYear.  If they really want a two digit year, they should enter the leading zeroes
        year = adjustYear2To4(day, month, yearText, year);
        try
        {
            LocalDate.of(year, month, day);
        } catch (DateTimeException e)
        {
            // Don't add a fix if the destination date isn't valid either!
            return;
        }

        final String value = day + "/" + month + "/" + year;
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
