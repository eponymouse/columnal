package records.gui;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.controlsfx.control.PopOver.ArrowLocation;
import xyz.columnal.data.ColumnId;
import records.grammar.GrammarUtility;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.TranslationUtility;
import xyz.columnal.utility.gui.ErrorableTextField;

/**
 * Created by neil on 30/04/2017.
 */
@OnThread(Tag.FXPlatform)
public class ColumnNameTextField extends ErrorableTextField<ColumnId>
{
    @OnThread(Tag.FXPlatform)
    @SuppressWarnings("identifier")
    public ColumnNameTextField(@Nullable ColumnId initial)
    {
        // We automatically remove leading/trailing whitespace, rather than complaining about it.
        // We also convert any whitespace (including multiple chars) into a single space
        super(s -> {
            s = GrammarUtility.collapseSpaces(s);
            if (s.isEmpty())
                return ConversionResult.<@NonNull ColumnId>error(TranslationUtility.getStyledString("column.name.error.missing"));
            return checkAlphabet(s, ColumnId::validCharacter, ColumnId::new);
        });
        getStyleClass().add("column-name-text-field");
        if (initial != null)
            setText(initial.getRaw());
    }

    // Overridden to change return type to be more specific:
    @Override
    public ColumnNameTextField withArrowLocation(ArrowLocation location)
    {
        super.withArrowLocation(location);
        return this;
    }
}
