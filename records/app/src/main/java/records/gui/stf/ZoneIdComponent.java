package records.gui.stf;

import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.gui.stf.StructuredTextField.ErrorFix;
import records.gui.stf.StructuredTextField.Item;
import records.gui.stf.StructuredTextField.ItemVariant;
import records.gui.stf.StructuredTextField.Suggestion;
import utility.Either;
import utility.Pair;
import utility.gui.TranslationUtility;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.zone.ZoneRulesException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by neil on 11/07/2017.
 */
public class ZoneIdComponent extends TerminalComponent<ZoneId>
{
    public ZoneIdComponent(ImmutableList<Component<?>> componentParents, @Nullable ZoneId initialZoneId)
    {
        super(componentParents);
        items.add(new Item(getItemParents(), initialZoneId == null ? "" : initialZoneId.getId(), ItemVariant.EDITABLE_ZONEID, TranslationUtility.getString("entry.prompt.zone")));
    }

    @Override
    public Either<List<ErrorFix>, ZoneId> endEdit(StructuredTextField<?> field)
    {
        try
        {
            return Either.right(ZoneId.of(getItem(ItemVariant.EDITABLE_ZONEID)));
        }
        catch (DateTimeException e)
        {
            return Either.left(Collections.emptyList());
        }
    }

    @Override
    public List<Suggestion> getSuggestions()
    {
        // TODO also add +00:00 style completions
        return ZoneId.getAvailableZoneIds().stream().map(s -> new Suggestion(getItems().get(0), s)).collect(Collectors.toList());
    }
}
