package records.gui;

import javafx.scene.input.KeyCode;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.fxmisc.wellbehaved.event.EventPattern;
import org.fxmisc.wellbehaved.event.InputMap;
import org.fxmisc.wellbehaved.event.Nodes;
import records.data.TableId;
import records.data.TableManager;
import records.grammar.GrammarUtility;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.FXPlatformRunnable;
import xyz.columnal.utility.TranslationUtility;
import utility.gui.ErrorableTextField;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A name for entering/editing a table name.  Used to name the actual table,
 * not to look up existing tables (which is done via other controls).
 */
public class TableNameTextField extends ErrorableTextField<TableId>
{    
    @OnThread(Tag.FXPlatform)
    @SuppressWarnings("identifier")
    public TableNameTextField(@Nullable TableManager tableManager, final @Nullable TableId editingId, boolean blankAllowed, FXPlatformRunnable defocus)
    {
        // We automatically remove leading/trailing whitespace, rather than complaining about it.
        // We also convert any whitespace (including multiple chars) into a single space
        super(s -> {
            s = GrammarUtility.collapseSpaces(s);
            if (s.isEmpty())
            {
                if (blankAllowed)
                    return ConversionResult.success(new TableId(s));
                else
                    return ConversionResult.<@NonNull TableId>error(TranslationUtility.getStyledString("table.name.error.missing"));
            }
            TableId tableId = new TableId(s);
            //System.err.println("Comparing \"" + s + "\" with " + Utility.listToString(Utility.mapList(tableManager.getAllTables(), t -> "\"" + t.getId().getRaw() + "\"")));
            List<TableId> similar = new ArrayList<>();
            // If we match the beginning ID, that is OK, otherwise the ID must be free:
            if (tableId.equals(editingId) || (tableManager == null || tableManager.isFreeId(tableId, similar)))
            {
                if (similar.isEmpty())
                    return ConversionResult.success(tableId);
                else
                    return ConversionResult.success(tableId, TranslationUtility.getString("table.name.similar", similar.stream().map(t -> "\"" + t.getOutput() + "\"").collect(Collectors.joining(", "))));
            }
            else
            {
                return ConversionResult.<TableId>error(TranslationUtility.getStyledString("table.name.exists", s));
            }
        });
        getStyleClass().add("table-name-text-field");
        if (editingId != null)
            setText(editingId.getRaw());
        setPromptText(TranslationUtility.getString("table.name.prompt.auto"));

        Nodes.addInputMap(getNode(), InputMap.consume(EventPattern.keyPressed(KeyCode.ENTER), e -> {
            defocus.run();
        }));
    }
}
