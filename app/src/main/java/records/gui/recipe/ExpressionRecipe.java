package records.gui.recipe;

import javafx.stage.Window;
import org.checkerframework.checker.i18n.qual.LocalizableKey;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.gui.lexeditor.ExpressionEditor.ColumnPicker;
import records.transformations.expression.Expression;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.FXPlatformSupplier;
import xyz.columnal.utility.TranslationUtility;

@OnThread(Tag.FXPlatform)
public abstract class ExpressionRecipe
{
    private final @Localized String title;

    public ExpressionRecipe(@LocalizableKey String titleKey)
    {
        this.title = TranslationUtility.getString(titleKey);
    }

    public @Localized String getTitle()
    {
        return title;
    }

    public abstract @Nullable Expression makeExpression(Window parentWindow, ColumnPicker columnPicker);
}
