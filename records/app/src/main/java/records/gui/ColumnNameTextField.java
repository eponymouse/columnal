package records.gui;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.ColumnId;
import records.data.TableId;
import records.data.TableManager;
import utility.Utility;
import utility.gui.TranslationUtility;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by neil on 30/04/2017.
 */
public class ColumnNameTextField extends ErrorableTextField<ColumnId>
{
    public ColumnNameTextField(@Nullable ColumnId initial)
    {
        // We automatically remove leading/trailing whitespace, rather than complaining about it.
        // We also convert any whitespace (including multiple chars) into a single space
        super(s -> {
            s = Utility.collapseSpaces(s);
            if (s.isEmpty())
                return ConversionResult.<@NonNull ColumnId>error(TranslationUtility.getString("column.name.error.missing"));
            return ConversionResult.success(new ColumnId(s));
        });
    }

}
