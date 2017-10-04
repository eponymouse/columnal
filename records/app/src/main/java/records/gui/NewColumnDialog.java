package records.gui;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.initialization.qual.UnderInitialization;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;
import org.jetbrains.annotations.NotNull;
import records.data.TableManager;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeUtility;
import records.error.InternalException;
import records.gui.NewColumnDialog.NewColumnDetails;
import records.gui.expressioneditor.ExpressionEditor;
import records.gui.stf.Component;
import records.gui.stf.StructuredTextField;
import records.gui.stf.StructuredTextField.EditorKit;
import records.transformations.expression.NumericLiteral;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.Pair;
import utility.Utility;
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
    private StructuredTextField defaultValueEditor;
    private String defaultValueAsString = "";

    @OnThread(Tag.FXPlatform)
    public NewColumnDialog(TableManager tableManager) throws InternalException
    {
        setTitle(TranslationUtility.getString("newcolumn.title"));
        contents = new VBox();
        contents.getStyleClass().add("new-column-content");
        name = new TextField();
        name.getStyleClass().add("new-column-name");
        typeSelectionPane = new TypeSelectionPane(tableManager.getTypeManager());
        defaultValueEditor = makeField(DataType.NUMBER);
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
                    defaultValueEditor = makeField(dataType);
                    defaultValueEditor.getStyleClass().add("new-column-value");
                    defaultValueEditorWrapper.setCenter(defaultValueEditor);
                    getDialogPane().layout();
                });
            }
        });
    }

    private StructuredTextField makeField(@UnknownInitialization(Object.class) NewColumnDialog this, DataType dataType) throws InternalException
    {
        return fieldFromComponent(TableDisplayUtility.component(ImmutableList.of(), dataType, DataTypeUtility.makeDefaultValue(dataType)));
    }

    private <@NonNull @Value T extends @NonNull @Value Object> StructuredTextField fieldFromComponent(@UnknownInitialization(Object.class) NewColumnDialog this, Component<T> component) throws InternalException
    {
        return new StructuredTextField(() -> getDialogPane().lookupButton(ButtonType.OK).requestFocus(), new EditorKit<T>(component, (Pair<String, @NonNull @Value T> v) -> {defaultValueAsString = v.getFirst();}));
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
            return Either.right(new NewColumnDetails(name.getText(), selectedType, defaultValueAsString));
        }
    }

    public static class NewColumnDetails
    {
        public final String name;
        public final DataType type;
        public final String defaultValueUnparsed;

        public NewColumnDetails(String name, DataType type, String defaultValueUnparsed)
        {
            this.name = name;
            this.type = type;
            this.defaultValueUnparsed = defaultValueUnparsed;
        }
    }


}
