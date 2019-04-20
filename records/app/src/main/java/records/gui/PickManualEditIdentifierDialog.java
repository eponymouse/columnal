package records.gui;

import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.Column;
import records.data.ColumnId;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.gui.DimmableParent;
import utility.gui.ErrorableLightDialog;
import utility.gui.GUI;
import utility.gui.LightDialog;
import utility.gui.TranslationUtility;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Lets you pick a column, or select to use row numbers
 */
@OnThread(Tag.FXPlatform)
public class PickManualEditIdentifierDialog extends ErrorableLightDialog<Optional<ColumnId>>
{
    private final RadioButton byColumnRadio;
    private final TextField byColumnName;
    // If column exists, return it -- else return null
    private final Function<String, @Nullable ColumnId> columnIdExists;

    public PickManualEditIdentifierDialog(View parent, Optional<ColumnId> startingValue, Function<String, @Nullable ColumnId> columnIdExists)
    {
        super(parent, true);
        this.columnIdExists = columnIdExists;

        ToggleGroup group = new ToggleGroup();

        RadioButton byRowRadio = GUI.radioButton(group, "manual.edit.byrownum");
        byColumnRadio = GUI.radioButton(group, "manual.edit.bycolumn");
        byRowRadio.setSelected(!startingValue.isPresent());
        byColumnRadio.setSelected(startingValue.isPresent());
        byColumnName = new TextField(startingValue.map(c -> c.getRaw()).orElse(""));
        byColumnName.disableProperty().bind(byRowRadio.selectedProperty());
        getDialogPane().setContent(new VBox(
            new Label("Pick column to use to identify the edited rows if the source data changes:"),
                byRowRadio,
            new HBox(byColumnRadio, byColumnName)
        ));
    }

    @Override
    protected @OnThread(Tag.FXPlatform) Either<@Localized String, Optional<ColumnId>> calculateResult()
    {
        if (byColumnRadio.isSelected())
        {
            @Nullable ColumnId columnId = columnIdExists.apply(byColumnName.getText().trim());
            
            if (columnId != null)
                return Either.<@Localized String, Optional<ColumnId>>right(Optional.<ColumnId>of(columnId));
            else
                return Either.left(TranslationUtility.getString("manual.edit.column.not.found", byColumnName.getText().trim()));
        }
        else
        {
            return Either.<@Localized String, Optional<ColumnId>>right(Optional.<ColumnId>empty());
        }
    }
}
