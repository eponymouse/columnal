package records.gui;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.TableId;
import records.data.TableManager;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Utility;
import utility.gui.TranslationUtility;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Collectors;

/**
 * A name for entering/editing a table name.  Used to name the actual table,
 * not to look up existing tables (which is done via other controls).
 * 
 * If the table name is blanked, the prompt text will show a default
 * table name to be used, and this will not be an error.  In this case,
 * Optional.empty() will be used as the value.
 */
public class TableNameTextField extends ErrorableTextField<Optional<TableId>>
{    
    @OnThread(Tag.FXPlatform)
    public TableNameTextField(TableManager tableManager, final @Nullable TableId editingId)
    {
        // We automatically remove leading/trailing whitespace, rather than complaining about it.
        // We also convert any whitespace (including multiple chars) into a single space
        super(s -> {
            s = Utility.collapseSpaces(s);
            // Empty is fine, just means auto-assign:
            if (s.isEmpty())
                return ConversionResult.<Optional<TableId>>success(Optional.empty());
            TableId tableId = new TableId(s);
            //System.err.println("Comparing \"" + s + "\" with " + Utility.listToString(Utility.mapList(tableManager.getAllTables(), t -> "\"" + t.getId().getRaw() + "\"")));
            List<TableId> similar = new ArrayList<>();
            // If we match the beginning ID, that is OK, otherwise the ID must be free:
            if (tableId.equals(editingId) || tableManager.isFreeId(tableId, similar))
            {
                if (similar.isEmpty())
                    return ConversionResult.success(Optional.of(tableId));
                else
                    return ConversionResult.success(Optional.of(tableId), TranslationUtility.getString("table.name.similar", similar.stream().map(t -> "\"" + t.getOutput() + "\"").collect(Collectors.joining(", "))));
            }
            else
            {
                return ConversionResult.<Optional<TableId>>error(TranslationUtility.getString("table.name.exists", s));
            }
        });
        getStyleClass().add("table-name-text-field");
        if (editingId != null)
            setText(editingId.getRaw());
        setPromptText(TranslationUtility.getString("table.name.prompt.auto"));
    }
}
