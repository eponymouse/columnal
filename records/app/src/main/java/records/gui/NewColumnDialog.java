package records.gui;

import annotation.qual.Value;
import javafx.event.ActionEvent;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.initialization.qual.UnderInitialization;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;
import records.data.TableManager;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeUtility;
import records.gui.NewColumnDialog.NewColumnDetails;
import records.gui.expressioneditor.ExpressionEditor;
import records.transformations.expression.NumericLiteral;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.gui.ErrorLabel;
import utility.gui.ErrorableDialog;
import utility.gui.FXUtility;
import utility.gui.TranslationUtility;

import java.util.Optional;

/**
 * Created by neil on 20/03/2017.
 */
@OnThread(Tag.FXPlatform)
public class NewColumnDialog extends ErrorableDialog<NewColumnDetails>
{
    private final TextField name;
    private final VBox contents;
    private final TypeSelectionPane typeSelectionPane;
    private final ExpressionEditor defaultValueEditor;

    @OnThread(Tag.FXPlatform)
    public NewColumnDialog(TableManager tableManager)
    {
        setTitle(TranslationUtility.getString("newcolumn.title"));
        contents = new VBox();
        contents.getStyleClass().add("new-column-content");
        name = new TextField();
        name.getStyleClass().add("new-column-name");
        typeSelectionPane = new TypeSelectionPane(tableManager.getTypeManager());
        defaultValueEditor = new ExpressionEditor(new NumericLiteral(0, null), null, FXUtility.<@Nullable Optional<DataType>, @Nullable DataType>mapBindingEager(typeSelectionPane.selectedType(), o -> o == null ? null : o.orElse(null)), tableManager, e -> {});
        Label nameLabel = new Label(TranslationUtility.getString("newcolumn.name"));
        contents.getChildren().addAll(
                new Row(nameLabel, name),
                new Separator(),
                typeSelectionPane.getNode(),
                new Separator(),
                new HBox(new Label(TranslationUtility.getString("newcolumn.defaultvalue")), defaultValueEditor.getContainer()),
                getErrorLabel());

        setResizable(true);
        getDialogPane().getStylesheets().addAll(
                FXUtility.getStylesheet("general.css"),
                FXUtility.getStylesheet("dialogs.css"),
                FXUtility.getStylesheet("new-column-dialog.css")
        );
        getDialogPane().setContent(contents);

        initModality(Modality.NONE);
        setOnShown(e -> {
            // Have to use runAfter to combat ButtonBarSkin grabbing focus:
            FXUtility.runAfter(name::requestFocus);
            //org.scenicview.ScenicView.show(getDialogPane().getScene());
        });
    }

    @RequiresNonNull({"typeSelectionPane"})
    private @Nullable DataType getSelectedType(@UnknownInitialization(Object.class) NewColumnDialog this)
    {
        @Nullable Optional<DataType> maybeType = typeSelectionPane.selectedType().get();
        return maybeType == null ? null : maybeType.orElse(null);
    }

    protected Either<@Localized String, NewColumnDetails> calculateResult()
    {
        if (name.getText().trim().isEmpty())
        {
            return Either.left(TranslationUtility.getString("column.name.required"));
        }

        // Should getSelectedType return an Either, too?
        @Nullable DataType selectedType = getSelectedType();
        if (selectedType == null)
        {
            return Either.left(TranslationUtility.getString("column.type.invalid"));
        }
        else
        {
            return Either.right(new NewColumnDetails(name.getText(), selectedType, DataTypeUtility.value("")));
        }
    }

    public static class NewColumnDetails
    {
        public final String name;
        public final DataType type;
        public final @Value Object defaultValue;

        public NewColumnDetails(String name, DataType type, @Value Object defaultValue)
        {
            this.name = name;
            this.type = type;
            this.defaultValue = defaultValue;
        }
    }


}
