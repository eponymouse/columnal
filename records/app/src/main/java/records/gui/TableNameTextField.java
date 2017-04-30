package records.gui;

import org.checkerframework.checker.nullness.qual.NonNull;
import records.data.TableId;
import records.data.TableManager;
import utility.gui.TranslationUtility;

/**
 * Created by neil on 30/04/2017.
 */
public class TableNameTextField extends ErrorableTextField<TableId>
{
    public TableNameTextField(TableManager tableManager)
    {
        super(s -> {
            TableId tableId = new TableId(s);
            if (tableManager.isFreeId(tableId))
            {
                return ConversionResult.success(tableId);
            }
            else
            {
                return ConversionResult.<@NonNull TableId>error(TranslationUtility.getString("table.exists", s));
            }
        });
    }
}
