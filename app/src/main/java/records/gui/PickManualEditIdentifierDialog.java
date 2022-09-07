package records.gui;

import annotation.identifier.qual.ExpressionIdentifier;
import com.google.common.collect.ImmutableList;
import javafx.beans.value.ObservableStringValue;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.ColumnId;
import records.gui.AutoComplete.Completion;
import records.gui.AutoComplete.CompletionListener;
import records.gui.AutoComplete.WhitespacePolicy;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.Either;
import xyz.columnal.utility.IdentifierUtility;
import xyz.columnal.utility.TranslationUtility;
import xyz.columnal.utility.gui.ErrorableLightDialog;
import xyz.columnal.utility.gui.FXUtility;
import xyz.columnal.utility.gui.GUI;

import java.util.Optional;

/**
 * Lets you pick a column, or select to use row numbers
 */
@OnThread(Tag.FXPlatform)
public class PickManualEditIdentifierDialog extends ErrorableLightDialog<Optional<ColumnId>>
{
    private final RadioButton byColumnRadio;
    private final TextField byColumnName;
    private final ImmutableList<ColumnId> srcTableColumns;

    public PickManualEditIdentifierDialog(View parent, Optional<Optional<ColumnId>> startingValue, ImmutableList<ColumnId> srcTableColumns)
    {
        super(parent, true);
        initModality(Modality.NONE);
        this.srcTableColumns = srcTableColumns;

        ToggleGroup group = new ToggleGroup();

        RadioButton byRowRadio = GUI.radioButton(group, "manual.edit.byrownum");
        byColumnRadio = GUI.radioButton(group, "manual.edit.bycolumn");
        byRowRadio.setSelected(startingValue.isPresent() && !startingValue.get().isPresent());
        byColumnRadio.setSelected(!startingValue.isPresent() || startingValue.get().isPresent());
        byColumnName = new TextField(!startingValue.isPresent() ? "" : startingValue.get().map(c -> c.getRaw()).orElse(""));
        byColumnName.disableProperty().bind(byRowRadio.selectedProperty());
        getDialogPane().setContent(new VBox(
            new Label("Pick column to use to identify the edited rows if the source data changes:"),
            new HBox(byColumnRadio, byColumnName),
            byRowRadio,
            getErrorLabel()
        ));
        
        new AutoComplete<ColumnCompletion>(byColumnName, content -> {
            return srcTableColumns.stream()
                .filter(columnId -> columnId.getRaw().startsWith(content))
                .map(columnId -> new ColumnCompletion(columnId));
        }, new CompletionListener<ColumnCompletion>()
        {
            @Override
            public @Nullable String doubleClick(String currentText, ColumnCompletion selectedItem)
            {
                return selectedItem.columnId.getRaw();
            }

            @Override
            public @Nullable String keyboardSelect(String textBeforeCaret, String textAfterCaret, @Nullable ColumnCompletion selectedItem, boolean wasTab)
            {
                return selectedItem != null ? selectedItem.columnId.getRaw() : null;
            }
        }, WhitespacePolicy.ALLOW_ONE_ANYWHERE_TRIM);
        
        setOnShowing(e -> {
            parent.enableColumnPickingMode(null, getDialogPane().sceneProperty(), p -> srcTableColumns.contains(p.getSecond()), p -> {
                byColumnRadio.setSelected(true);
                byColumnName.setText(p.getSecond().getRaw());
            });
            if (byColumnRadio.isSelected())
                FXUtility.runAfter(byColumnName::requestFocus);
        });
        setOnHiding(e -> {
            parent.disablePickingMode();
        });
    }

    @Override
    protected @OnThread(Tag.FXPlatform) Either<@Localized String, Optional<ColumnId>> calculateResult()
    {
        if (byColumnRadio.isSelected())
        {
            @Nullable @ExpressionIdentifier String id = IdentifierUtility.asExpressionIdentifier(byColumnName.getText().trim());
            
            if (id != null && srcTableColumns.contains(new ColumnId(id)))
                return Either.<@Localized String, Optional<ColumnId>>right(Optional.<ColumnId>of(new ColumnId(id)));
            else
                return Either.left(TranslationUtility.getString("manual.edit.column.not.found", byColumnName.getText().trim()));
        }
        else
        {
            return Either.<@Localized String, Optional<ColumnId>>right(Optional.<ColumnId>empty());
        }
    }

    private class ColumnCompletion extends Completion
    {
        private final ColumnId columnId;

        public ColumnCompletion(ColumnId columnId)
        {
            this.columnId = columnId;
        }

        @Override
        public @OnThread(Tag.FXPlatform) CompletionContent makeDisplay(ObservableStringValue currentText)
        {
            return new CompletionContent(columnId.getRaw(), null);
        }
    }
}