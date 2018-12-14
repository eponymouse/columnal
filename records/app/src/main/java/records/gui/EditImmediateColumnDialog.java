package records.gui;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import javafx.scene.Scene;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.stage.Window;
import log.Log;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.ColumnId;
import records.data.TableId;
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
import records.transformations.expression.type.IdentTypeExpression;
import records.transformations.expression.type.InvalidIdentTypeExpression;
import records.transformations.expression.type.TypeExpression;
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
    private final TypeEditor typeEditor;
    private @Nullable DataType latestType;
    private final @Nullable TableNameTextField tableNameTextField;

    public static class ColumnDetails
    {
        // Null if not creating table, or if blank
        public final @Nullable TableId tableId;
        public final ColumnId columnId;
        public final DataType dataType;
        public final @Value Object defaultValue;

        public ColumnDetails(@Nullable TableId tableId, ColumnId columnId, DataType dataType, @Value Object defaultValue)
        {
            this.tableId = tableId;
            this.columnId = columnId;
            this.dataType = dataType;
            this.defaultValue = defaultValue;
        }
    }
    
    private @Nullable @Value Object defaultValue;
    
    @OnThread(Tag.FXPlatform)
    public EditImmediateColumnDialog(Window parent, TableManager tableManager, @Nullable ColumnId initial, @Nullable DataType dataType, boolean creatingNewTable)
    {
        super(parent, true);

        LabelledGrid content = new LabelledGrid();
        content.getStyleClass().add("edit-column-details");

        if (creatingNewTable)
        {
            tableNameTextField = new TableNameTextField(tableManager, null, true);
            tableNameTextField.setPromptText(TranslationUtility.getString("table.name.prompt.auto"));
            content.addRow(GUI.labelledGridRow("edit.table.name", "edit-column/table-name", tableNameTextField.getNode()));
        }
        else
        {
            tableNameTextField = null;
        }
        
        columnNameTextField = new ColumnNameTextField(initial);
        content.addRow(GUI.labelledGridRow("edit.column.name", "edit-column/column-name", columnNameTextField.getNode()));
        
        StructuredTextField defaultValueField = new StructuredTextField();
        defaultValueField.getStyleClass().add("default-value");
        TypeExpression typeExpression = new InvalidIdentTypeExpression("");
        if (dataType != null)
        {
            try
            {
                typeExpression = TypeExpression.fromDataType(dataType);
            }
            catch (InternalException e)
            {
                Log.log(e);
            }
        }
        typeEditor = new TypeEditor(tableManager.getTypeManager(), typeExpression, t -> {
            customDataType = t.toDataType(tableManager.getTypeManager());
            updateType(defaultValueField, customDataType);
            Scene scene = getDialogPane().getScene();
            if (scene != null && scene.getWindow() != null)
                scene.getWindow().sizeToScene();
        });
        content.addRow(GUI.labelledGridRow("edit.column.type", "edit-column/column-type", typeEditor.getContainer()));
        content.addRow(GUI.labelledGridRow("edit.column.defaultValue", "edit-column/column-defaultValue", defaultValueField));
        
        Label explanation = new Label(
            (creatingNewTable ? (TranslationUtility.getString("newcolumn.newTableExplanation") + "  ") : Utility.universal(""))
                + TranslationUtility.getString("newcolumn.newColumnExplanation")
        );
        explanation.setWrapText(true);
        
        getDialogPane().setContent(GUI.vbox("edit-column-content",
            explanation,
            content,
            getErrorLabel()
        ));
        
        FXUtility.preventCloseOnEscape(getDialogPane());

        setOnShown(e -> {
            // Have to use runAfter to combat ButtonBarSkin grabbing focus:
            FXUtility.runAfter(tableNameTextField != null ? tableNameTextField::requestFocusWhenInScene : columnNameTextField::requestFocusWhenInScene);
            //org.scenicview.ScenicView.show(getDialogPane().getScene());
        });
    }

    @Override
    protected @OnThread(Tag.FXPlatform) Either<@Localized String, ColumnDetails> calculateResult()
    {
        // Check whether some conditions are fulfilled
        DataType dataType = customDataType;
        
        @Nullable TableId tableId = null;
        if (tableNameTextField != null)
        {
            tableId = tableNameTextField.valueProperty().get();
            // Ok to be null if field is blank
            if (tableId == null)
                return Either.left(TranslationUtility.getString("edit.column.invalid.table.name"));
        }

        @Nullable ColumnId columnId = columnNameTextField.valueProperty().getValue();
        
        //Log.debug("Col: " + columnId + " type: " + dataType + " default: " + defaultValue);
        
        if (columnId == null)
            return Either.left(TranslationUtility.getString("edit.column.invalid.column.name"));
        if (dataType == null)
            return Either.left(TranslationUtility.getString("edit.column.invalid.column.type"));
        if (defaultValue == null)
            return Either.left(TranslationUtility.getString("edit.column.invalid.column.defaultValue"));
        
        return Either.right(new ColumnDetails(tableId, columnId, dataType, defaultValue));
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
                if (!t.equals(latestType))
                {
                    structuredTextField.resetContent(makeEditorKit(t));
                    latestType = t;
                }
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
