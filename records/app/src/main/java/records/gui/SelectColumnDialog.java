package records.gui;

import annotation.identifier.qual.ExpressionIdentifier;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.stage.Modality;
import javafx.stage.Window;
import log.Log;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.Column;
import records.data.ColumnId;
import records.data.Table;
import records.error.InternalException;
import records.error.UserException;
import records.gui.AutoComplete.CompletionListener;
import records.gui.AutoComplete.SimpleCompletion;
import records.gui.AutoComplete.WhitespacePolicy;
import records.gui.lexeditor.ExpressionEditor.ColumnPicker;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.IdentifierUtility;
import utility.TranslationUtility;
import utility.Utility;
import utility.gui.ErrorableLightDialog;
import utility.gui.FXUtility;

import java.util.function.Predicate;
import java.util.stream.Stream;

@OnThread(Tag.FXPlatform)
public class SelectColumnDialog extends ErrorableLightDialog<ColumnId>
{
    private final TextField columnField = new TextField();
    private final AutoComplete<ColumnCompletion> autoComplete;
    private long lastEditTimeMillis = -1;

    public SelectColumnDialog(Window parent, @Nullable Table srcTable, ColumnPicker columnPicker, Predicate<Column> filterColumn)
    {
        super(d -> parent, true);
        initOwner(parent);
        initModality(Modality.NONE);

        BorderPane.setMargin(columnField, new Insets(0, 2, 2, 5));
        autoComplete = new AutoComplete<ColumnCompletion>(columnField,
                s -> Utility.streamNullable(srcTable).flatMap(t -> {
                    try
                    {
                        return t.getData().getColumns().stream();
                    }
                    catch (UserException | InternalException e)
                    {
                        Log.log(e);
                        return Stream.empty();
                    }
                }).filter(c -> filterColumn.test(c) && c.getName().getOutput().contains(s)).map(ColumnCompletion::new),
                getListener(), WhitespacePolicy.ALLOW_ONE_ANYWHERE_TRIM);
        FXUtility.addChangeListenerPlatformNN(columnField.focusedProperty(), focus -> {
            // Update whether focus is arriving or leaving:
            lastEditTimeMillis = System.currentTimeMillis();
        });
        Label label = new Label("Type column name or click on column");
        label.visibleProperty().bind(columnField.focusedProperty());
        getDialogPane().setContent(new BorderPane(columnField, label, null, null, null));
        setOnShown(e -> {
            columnPicker.enableColumnPickingMode(null, p -> p.getFirst() == srcTable, p -> {
                columnField.setText(p.getSecond().getRaw());
            });
        });
        setOnHidden(e -> {
            columnPicker.disablePickingMode();
        });
    }

    @OnThread(Tag.FXPlatform)
    @Override
    protected Either<@Localized String, ColumnId> calculateResult()
    {
        @Nullable @ExpressionIdentifier String ident =  IdentifierUtility.asExpressionIdentifier(columnField.getText().trim());
        if (ident != null)
            return Either.right(new ColumnId(ident));
        else
            return Either.left(TranslationUtility.getString("edit.column.invalid.column.name"));
    }

    private CompletionListener<ColumnCompletion> getListener(@UnknownInitialization SelectColumnDialog this)
    {
        return new CompletionListener<ColumnCompletion>()
        {
            @Override
            public String doubleClick(String currentText, ColumnCompletion selectedItem)
            {
                return selectedItem.c.getName().getOutput();
            }

            @Override
            public @Nullable String keyboardSelect(String textBefore, String textAfter, @Nullable ColumnCompletion selectedItem, boolean tabPressed)
            {
                if (selectedItem != null)
                    return selectedItem.c.getName().getOutput();
                else
                    return null;
            }
        };
    }

    private static class ColumnCompletion extends SimpleCompletion
    {
        private final Column c;

        private ColumnCompletion(Column c)
        {
            super(c.getName().getRaw(), null);
            this.c = c;
        }
    }
}
