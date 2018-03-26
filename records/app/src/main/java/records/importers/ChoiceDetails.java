package records.importers;

import annotation.help.qual.HelpKey;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.i18n.qual.LocalizableKey;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.nullness.qual.Nullable;
import utility.Either;

import java.util.function.Function;

/**
 * Contains information about a given choice, e.g. is free entry allowed,
 * get help on the item, etc).
 */
public class ChoiceDetails<C>
{
    private final @LocalizableKey String labelKey;
    private final @HelpKey String helpKey;
    public final ImmutableList<C> quickPicks;
    // If null, quick picks are the only options.  If non-null, offer an "Other" pick
    // in the combo box which shows a text field:
    public final @Nullable Function<String, Either<@Localized String, C>> stringEntry;

    public ChoiceDetails(@LocalizableKey String labelKey, @HelpKey String helpKey)
    {
        this.labelKey = labelKey;
        this.helpKey = helpKey;
    }

    public @LocalizableKey String getLabelKey()
    {
        return labelKey;
    }

    public @HelpKey String getHelpId()
    {
        return helpKey;
    }

    // Does this item have no possible choices?
    public boolean isEmpty()
    {
        return quickPicks.isEmpty() && stringEntry == null;
    }
}
