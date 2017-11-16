package records.gui;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;
import records.data.ColumnId;
import records.data.TableManager;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeUtility;
import records.error.InternalException;
import records.gui.NewColumnDialog.NewColumnDetails;
import records.gui.stf.Component;
import records.gui.stf.StructuredTextField;
import records.gui.stf.StructuredTextField.EditorKit;
import records.gui.stf.TableDisplayUtility;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.Pair;
import utility.Utility;
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
    private final StructuredTextField defaultValueEditor;
    private @Value Object defaultValue;

    @OnThread(Tag.FXPlatform)
    public NewColumnDialog(TableManager tableManager) throws InternalException
    {
        setTitle(TranslationUtility.getString("newcolumn.title"));
        contents = new VBox();
        contents.getStyleClass().add("new-column-content");
        name = new TextField();
        name.getStyleClass().add("new-column-name");
        typeSelectionPane = new TypeSelectionPane(tableManager.getTypeManager());
        defaultValue = DataTypeUtility.value((Integer)0);
        defaultValueEditor = new StructuredTextField(makeEditorKit(DataType.NUMBER));
        defaultValueEditor.getStyleClass().add("new-column-value");
        Label nameLabel = new Label(TranslationUtility.getString("newcolumn.name"));
        BorderPane defaultValueEditorWrapper = new BorderPane(defaultValueEditor);
        contents.getChildren().addAll(
                new Row(nameLabel, name),
                new Separator(),
                typeSelectionPane.getNode(),
                new Separator(),
                new HBox(new Label(TranslationUtility.getString("newcolumn.defaultvalue")), defaultValueEditorWrapper),
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

        FXUtility.addChangeListenerPlatform(typeSelectionPane.selectedType(), optDataType -> {
            if (optDataType != null && optDataType.isPresent())
            {
                @NonNull DataType dataType = optDataType.get();
                Utility.alertOnErrorFX_(() ->
                {
                    defaultValueEditor.resetContent(makeEditorKit(dataType));
                    defaultValueEditor.getStyleClass().add("new-column-value");
                    defaultValueEditorWrapper.setCenter(defaultValueEditor);
                    getDialogPane().layout();
                });
            }
        });
    }

    private EditorKit<?> makeEditorKit(@UnknownInitialization(Object.class) NewColumnDialog this, DataType dataType) throws InternalException
    {
        defaultValue = DataTypeUtility.makeDefaultValue(dataType);
        return fieldFromComponent(TableDisplayUtility.component(ImmutableList.of(), dataType, defaultValue));
    }

    private <@NonNull @Value T extends @NonNull @Value Object> EditorKit<T> fieldFromComponent(@UnknownInitialization(Object.class) NewColumnDialog this, Component<T> component) throws InternalException
    {
        return new EditorKit<T>(component, (Pair<String, @NonNull @Value T> v) -> {defaultValue = v.getSecond();}, () -> getDialogPane().lookupButton(ButtonType.OK).requestFocus());
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
            return Either.right(new NewColumnDetails(new ColumnId(name.getText()), selectedType, defaultValue));
        }
    }

    public static class NewColumnDetails
    {
        public final ColumnId name;
        public final DataType type;
        public final @Value Object defaultValue;

        public NewColumnDetails(ColumnId name, DataType type, @Value Object defaultValue)
        {
            this.name = name;
            this.type = type;
            this.defaultValue = defaultValue;
        }
    }


}
