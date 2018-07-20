package records.gui;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import javafx.event.ActionEvent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Separator;
import javafx.scene.control.ToggleGroup;
import javafx.stage.Window;
import log.Log;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.ColumnId;
import records.data.TableManager;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeUtility;
import records.error.InternalException;
import records.gui.EditImmediateColumnDialog.ColumnDetails;
import records.gui.expressioneditor.TypeEditor;
import records.gui.stf.Component;
import records.gui.stf.StructuredTextField;
import records.gui.stf.StructuredTextField.EditorKit;
import records.gui.stf.TableDisplayUtility;
import records.transformations.expression.type.UnfinishedTypeExpression;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.Pair;
import utility.Utility;
import utility.gui.ErrorableLightDialog;
import utility.gui.FXUtility;
import utility.gui.GUI;
import utility.gui.LabelledGrid;
import utility.gui.LightDialog;
import utility.gui.TranslationUtility;

/**
 * Edits an immediate column, which has a name, type, and default value
 */
@OnThread(Tag.FXPlatform)
public class EditImmediateColumnDialog extends ErrorableLightDialog<ColumnDetails>
{
    private @Nullable DataType customDataType = null;
    private final ColumnNameTextField columnNameTextField;

    public static class ColumnDetails
    {
        public final ColumnId columnId;
        public final DataType dataType;
        public final @Value Object defaultValue;

        public ColumnDetails(ColumnId columnId, DataType dataType, @Value Object defaultValue)
        {
            this.columnId = columnId;
            this.dataType = dataType;
            this.defaultValue = defaultValue;
        }
    }
    
    private @Nullable @Value Object defaultValue;
    
    @OnThread(Tag.FXPlatform)
    public EditImmediateColumnDialog(Window parent, TableManager tableManager, @Nullable ColumnId initial, boolean creatingNewTable)
    {
        super(parent);

        LabelledGrid content = new LabelledGrid();

        columnNameTextField = new ColumnNameTextField(initial);
        content.addRow(GUI.labelledGridRow("edit.column.name", "column-name", columnNameTextField.getNode()));
        
        StructuredTextField defaultValueField = new StructuredTextField();
        defaultValueField.getStyleClass().add("default-value");
        TypeEditor typeEditor = new TypeEditor(tableManager, new UnfinishedTypeExpression(""), t -> {
            customDataType = t;
            updateType(defaultValueField, customDataType);
            Scene scene = getDialogPane().getScene();
            if (scene != null && scene.getWindow() != null)
                scene.getWindow().sizeToScene();
        });
        content.addRow(GUI.labelledGridRow("edit.column.type", "column-type", typeEditor.getContainer()));
        content.addRow(GUI.labelledGridRow("edit.column.defaultValue", "column-defaultValue", defaultValueField));
        
        Label explanation = new Label(
            (creatingNewTable ? (TranslationUtility.getString("newcolumn.newTableExplanation") + "  ") : Utility.universal(""))
                + TranslationUtility.getString("newcolumn.newColumnExplanation")
        );
        explanation.setWrapText(true);
        
        getDialogPane().setContent(GUI.vbox("",
            explanation,
            new Separator(),
            content,
            getErrorLabel()
        ));
        
        FXUtility.preventCloseOnEscape(getDialogPane());

        setOnShown(e -> {
            // Have to use runAfter to combat ButtonBarSkin grabbing focus:
            FXUtility.runAfter(columnNameTextField::requestFocusWhenInScene);
            //org.scenicview.ScenicView.show(getDialogPane().getScene());
        });
    }

    @Override
    protected @OnThread(Tag.FXPlatform) Either<@Localized String, ColumnDetails> calculateResult()
    {
        // Check whether some conditions are fulfilled
        DataType dataType = customDataType;

        @Nullable ColumnId columnId = columnNameTextField.valueProperty().getValue();
        
        //Log.debug("Col: " + columnId + " type: " + dataType + " default: " + defaultValue);
        
        if (columnId == null)
            return Either.left(TranslationUtility.getString("edit.column.invalid.column.name"));
        if (dataType == null)
            return Either.left(TranslationUtility.getString("edit.column.invalid.column.type"));
        if (defaultValue == null)
            return Either.left(TranslationUtility.getString("edit.column.invalid.column.defaultValue"));
        
        return Either.right(new ColumnDetails(columnId, dataType, defaultValue));
    }

    @OnThread(Tag.FXPlatform)
    private void updateType(@UnknownInitialization(LightDialog.class)EditImmediateColumnDialog this, StructuredTextField structuredTextField, @Nullable DataType t)
    {
        if (t == null)
            structuredTextField.setDisable(true);
        else
        {
            try
            {
                structuredTextField.resetContent(makeEditorKit(t));
                structuredTextField.setDisable(false);
            }
            catch (InternalException e)
            {
                Log.log(e);
                structuredTextField.setDisable(true);
            }
        }
    }

    private EditorKit<?> makeEditorKit(@UnknownInitialization(LightDialog.class)EditImmediateColumnDialog this, DataType dataType) throws InternalException
    {
        defaultValue = DataTypeUtility.makeDefaultValue(dataType);
        return fieldFromComponent(TableDisplayUtility.component(ImmutableList.of(), dataType, defaultValue), TableDisplayUtility.stfStylesFor(dataType));
    }

    private <@NonNull @Value T extends @NonNull @Value Object> EditorKit<T> fieldFromComponent(@UnknownInitialization(LightDialog.class)EditImmediateColumnDialog this, Component<T> component, ImmutableList<String> stfStyles) throws InternalException
    {
        return new EditorKit<T>(component, (Pair<String, @NonNull @Value T> v) -> {defaultValue = v.getSecond();}, () -> getDialogPane().lookupButton(ButtonType.OK).requestFocus(), stfStyles);
    }
}
