package records.gui;

import org.checkerframework.checker.nullness.qual.NonNull;
import records.data.TableId;
import records.data.TableManager;
import utility.gui.TranslationUtility;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by neil on 30/04/2017.
 */
public class TableNameTextField extends ErrorableTextField<TableId>
{
    public TableNameTextField(TableManager tableManager)
    {
        // We automatically remove leading/trailing whitespace, rather than complaining about it.
        // We also convert any whitespace (including multiple chars) into a single space
        super(s -> {
            s = s.trim().replaceAll("(?U)\\s+", " ");
            if (s.isEmpty())
                return ConversionResult.<@NonNull TableId>error(TranslationUtility.getString("table.name.cannotBeBlank"));
            TableId tableId = new TableId(s);
            List<TableId> similar = new ArrayList<>();
            if (tableManager.isFreeId(tableId, similar))
            {
                if (similar.isEmpty())
                    return ConversionResult.success(tableId);
                else
                    return ConversionResult.success(tableId, TranslationUtility.getString("table.name.similar", similar.stream().map(t -> "\"" + t.getOutput() + "\"").collect(Collectors.joining(", "))));
            }
            else
            {
                return ConversionResult.<@NonNull TableId>error(TranslationUtility.getString("table.exists", s));
            }
        });
    }
}
