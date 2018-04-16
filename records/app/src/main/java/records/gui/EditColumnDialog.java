package records.gui;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import javafx.event.ActionEvent;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.stage.Window;
import log.Log;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.ColumnId;
import records.data.TableManager;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeUtility;
import records.error.InternalException;
import records.gui.EditColumnDialog.ColumnDetails;
import records.gui.expressioneditor.TypeEditor;
import records.gui.stf.Component;
import records.gui.stf.StructuredTextField;
import records.gui.stf.StructuredTextField.EditorKit;
import records.gui.stf.TableDisplayUtility;
import records.transformations.expression.type.UnfinishedTypeExpression;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
import utility.gui.FXUtility;
import utility.gui.GUI;
import utility.gui.LightDialog;

@OnThread(Tag.FXPlatform)
public class EditColumnDialog extends LightDialog<ColumnDetails>
{
    private @Nullable DataType customDataType = null;

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
    public EditColumnDialog(Window parent, TableManager tableManager, @Nullable ColumnId initial)
    {
        super(parent);

        ColumnNameTextField columnNameTextField = new ColumnNameTextField(initial);
        ToggleGroup toggleGroup = new ToggleGroup();
        RadioButton radioNumber = GUI.radioButton(toggleGroup, "type.number.plain", "radio-type-number");
        RadioButton radioText = GUI.radioButton(toggleGroup, "type.text", "radio-type-text");
        RadioButton radioCustom = GUI.radioButton(toggleGroup, "type.custom", "radio-type-custom");
        StructuredTextField structuredTextField = new StructuredTextField();
        structuredTextField.getStyleClass().add("default-value");
        TypeEditor typeEditor = new TypeEditor(tableManager, new UnfinishedTypeExpression(""), t -> {
            customDataType = t;
            if (!radioCustom.isSelected())
                radioCustom.setSelected(true); // This will call listener below anyway
            else
                updateType(structuredTextField, t);
        });
        FXUtility.addChangeListenerPlatform(toggleGroup.selectedToggleProperty(), sel -> {
            if (sel == radioNumber)
            {
                updateType(structuredTextField, DataType.NUMBER);
            }
            else if (sel == radioText)
            {
                updateType(structuredTextField, DataType.TEXT);
            }
            else if (sel == radioCustom)
            {
                updateType(structuredTextField, customDataType);
            }
            else
            {
                updateType(structuredTextField, null);
            }
        });
        
        getDialogPane().setContent(GUI.vbox("",
            new Label("Column name"),
            columnNameTextField.getNode(),
            new Label("Type"),
            radioNumber,
            radioText,
            GUI.hbox("", radioCustom, typeEditor.getContainer()),
            structuredTextField
        ));
        getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        final Button btOk = (Button) getDialogPane().lookupButton(ButtonType.OK);
        btOk.getStyleClass().add("ok-button");
        getDialogPane().lookupButton(ButtonType.CANCEL).getStyleClass().add("cancel-button");
        // From https://stackoverflow.com/questions/38696053/prevent-javafx-dialog-from-closing
        btOk.addEventFilter(
            ActionEvent.ACTION,
            event -> {
                setResult(null);
                // Check whether some conditions are fulfilled
                DataType dataType;
                if (radioNumber.isSelected())
                    dataType = DataType.NUMBER;
                else if (radioText.isSelected())
                    dataType = DataType.TEXT;
                else
                    dataType = customDataType;

                @Nullable ColumnId columnId = columnNameTextField.valueProperty().getValue();
                Log.debug("Col: " + columnId + " type: " + dataType + " default: " + defaultValue);
                if (columnId != null && dataType != null && defaultValue != null)
                    setResult(new ColumnDetails(columnId, dataType, defaultValue));
                if (getResult() == null)
                    event.consume();
            }
        );
        setResultConverter(bt -> {
            if (bt == ButtonType.OK && getResult() != null)
                return getResult();
            return null;
        });

        setOnShown(e -> {
            radioNumber.setSelected(true);
            // Have to use runAfter to combat ButtonBarSkin grabbing focus:
            FXUtility.runAfter(columnNameTextField::requestFocusWhenInScene);
            //org.scenicview.ScenicView.show(getDialogPane().getScene());
        });
    }

    @OnThread(Tag.FXPlatform)
    private  void updateType(@UnknownInitialization(LightDialog.class) EditColumnDialog this, StructuredTextField structuredTextField, @Nullable DataType t)
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

    private EditorKit<?> makeEditorKit(@UnknownInitialization(LightDialog.class) EditColumnDialog this, DataType dataType) throws InternalException
    {
        defaultValue = DataTypeUtility.makeDefaultValue(dataType);
        return fieldFromComponent(TableDisplayUtility.component(ImmutableList.of(), dataType, defaultValue), TableDisplayUtility.stfStylesFor(dataType));
    }

    private <@NonNull @Value T extends @NonNull @Value Object> EditorKit<T> fieldFromComponent(@UnknownInitialization(LightDialog.class) EditColumnDialog this, Component<T> component, ImmutableList<String> stfStyles) throws InternalException
    {
        return new EditorKit<T>(component, (Pair<String, @NonNull @Value T> v) -> {defaultValue = v.getSecond();}, () -> getDialogPane().lookupButton(ButtonType.OK).requestFocus(), stfStyles);
    }
}
