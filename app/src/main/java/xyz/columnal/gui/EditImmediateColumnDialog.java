/*
 * Columnal: Safer, smoother data table processing.
 * Copyright (c) Neil Brown, 2016-2020, 2022.
 *
 * This file is part of Columnal.
 *
 * Columnal is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 *
 * Columnal is distributed in the hope that it will be useful, but WITHOUT 
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or 
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for 
 * more details.
 *
 * You should have received a copy of the GNU General Public License along 
 * with Columnal. If not, see <https://www.gnu.org/licenses/>.
 */

package xyz.columnal.gui;

import annotation.qual.ImmediateValue;
import annotation.qual.Value;
import javafx.scene.Scene;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import xyz.columnal.gui.dialog.ColumnNameTextField;
import xyz.columnal.log.Log;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.id.ColumnId;
import xyz.columnal.id.TableId;
import xyz.columnal.data.TableManager;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.DataTypeUtility;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.gui.EditImmediateColumnDialog.ColumnDetails;
import xyz.columnal.gui.dtf.Document;
import xyz.columnal.gui.dtf.DocumentTextField;
import xyz.columnal.gui.dtf.Recogniser;
import xyz.columnal.gui.dtf.RecogniserDocument;
import xyz.columnal.gui.dtf.TableDisplayUtility;
import xyz.columnal.gui.dtf.TableDisplayUtility.RecogniserAndType;
import xyz.columnal.gui.lexeditor.TopLevelEditor.Focus;
import xyz.columnal.gui.lexeditor.TypeEditor;
import xyz.columnal.transformations.expression.type.TypeExpression;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.function.ExBiFunction;
import xyz.columnal.utility.function.fx.FXPlatformRunnable;
import xyz.columnal.utility.Utility;
import xyz.columnal.utility.gui.AlignedLabels;
import xyz.columnal.utility.gui.ErrorableLightDialog;
import xyz.columnal.utility.gui.FXUtility;
import xyz.columnal.utility.gui.GUI;
import xyz.columnal.utility.gui.LabelledGrid;
import xyz.columnal.utility.gui.LightDialog;
import xyz.columnal.utility.TranslationUtility;

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
    private @MonotonicNonNull RecogniserDocument<?> defaultValueDocument;

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
    
    public static enum InitialFocus { FOCUS_TABLE_NAME, FOCUS_COLUMN_NAME, FOCUS_TYPE }
    
    @OnThread(Tag.FXPlatform)
    public EditImmediateColumnDialog(View parent, TableManager tableManager, @Nullable ColumnId initial, @Nullable DataType dataType, boolean creatingNewTable, InitialFocus initialFocus)
    {
        super(parent, true);
        setResizable(true);

        AlignedLabels alignedLabels = new AlignedLabels();
        LabelledGrid content = new LabelledGrid();
        content.getStyleClass().add("edit-column-details");

        columnNameTextField = new ColumnNameTextField(initial);
        content.addRow(LabelledGrid.labelledGridRow(alignedLabels, "edit.column.name", "edit-column/column-name", columnNameTextField.getNode()));
        clearErrorLabelOnChange(columnNameTextField);
        
        DocumentTextField defaultValueField = new DocumentTextField(null) {
            @Override
            public @OnThread(Tag.FXPlatform) void documentChanged(Document document)
            {
                super.documentChanged(document);
                clearErrorLabel();
            }
        };
        defaultValueField.getStyleClass().add("default-value");
        TypeExpression typeExpression = null;
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
        typeEditor = new TypeEditor(tableManager.getTypeManager(), typeExpression, true, false, t -> {
            clearErrorLabel();
            customDataType = t.toDataType(tableManager.getTypeManager());
            updateType(defaultValueField, customDataType);
            Scene scene = getDialogPane().getScene();
            // Warning: this can make the dialog bigger than the screen!
            //if (scene != null && scene.getWindow() != null)
                //scene.getWindow().sizeToScene();
        }) {
            @Override
            protected void parentFocusRightOfThis(Either<Focus, Integer> side, boolean becauseOfTab)
            {
                defaultValueField.selectAll();
                defaultValueField.requestFocus();
            }
        };
        content.addRow(LabelledGrid.labelledGridRow(alignedLabels, "edit.column.type", "edit-column/column-type", typeEditor.getContainer()));
        content.addRow(LabelledGrid.labelledGridRow(alignedLabels, "edit.column.defaultValue", "edit-column/column-defaultValue", defaultValueField));
        
        Label explanation = new Label(
            (creatingNewTable ? (TranslationUtility.getString("newcolumn.newTableExplanation") + "  ") : Utility.universal(""))
                + TranslationUtility.getString("newcolumn.newColumnExplanation")
        );
        explanation.setWrapText(true);
        explanation.setPrefWidth(450.0);

        VBox vbox = GUI.vbox("edit-column-content",
                explanation,
                content,
                getErrorLabel()
        );

        if (creatingNewTable)
        {
            tableNameTextField = new TableNameTextField(tableManager, null, true, this::focusColumnNameField);
            tableNameTextField.setPromptText(TranslationUtility.getString("table.name.prompt.auto"));
            LabelledGrid topGrid = new LabelledGrid();
            topGrid.getStyleClass().add("edit-column-details");
            topGrid.addRow(LabelledGrid.labelledGridRow(alignedLabels, "edit.table.name", "edit-column/table-name", tableNameTextField.getNode()));
            vbox.getChildren().add(0, topGrid);
            clearErrorLabelOnChange(tableNameTextField);
        }
        else
        {
            tableNameTextField = null;
        }


        getDialogPane().setContent(vbox);
        
        FXUtility.preventCloseOnEscape(getDialogPane());

        setOnHiding(e -> {
            typeEditor.cleanup();
        });
        setOnShown(e -> {
            // Have to use runAfter to combat ButtonBarSkin grabbing focus:
            FXUtility.runAfter(() -> {
                switch (initialFocus)
                {
                    case FOCUS_TABLE_NAME:
                        if (tableNameTextField != null)
                        {
                            tableNameTextField.requestFocusWhenInScene();
                            break;
                        }
                        // else fall through and focus column:
                    case FOCUS_COLUMN_NAME:
                        columnNameTextField.requestFocusWhenInScene();
                        break;
                    case FOCUS_TYPE:
                        typeEditor.focus(Focus.RIGHT);
                        break;
                }
            });
            //org.scenicview.ScenicView.show(getDialogPane().getScene());
        });
    }

    private void focusColumnNameField(@UnknownInitialization(Object.class) EditImmediateColumnDialog this)
    {
        if (columnNameTextField != null)
            columnNameTextField.requestFocusWhenInScene();
    }

    @Override
    protected @OnThread(Tag.FXPlatform) Either<@Localized String, ColumnDetails> calculateResult()
    {
        typeEditor.showAllErrors();
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
            return Either.left(TranslationUtility.getString("edit.column.invalid.column.defaultValue", defaultValueDocument == null ? "" : defaultValueDocument.getLatestValue().<String>either(err -> err.error.toPlain(), o -> "")));
        
        return Either.right(new ColumnDetails(tableId, columnId, dataType, defaultValue));
    }

    @OnThread(Tag.FXPlatform)
    private void updateType(@UnknownInitialization(LightDialog.class)EditImmediateColumnDialog this, DocumentTextField textField, @Nullable DataType t)
    {
        if (t == null)
            textField.setDisable(true);
        else
        {
            try
            {
                if (!t.equals(latestType))
                {
                    defaultValueDocument = makeEditorKit(t);
                    textField.setDocument(defaultValueDocument);
                    latestType = t;
                }
                textField.setDisable(false);
            }
            catch (InternalException e)
            {
                Log.log(e);
                textField.setDisable(true);
            }
        }
    }

    private RecogniserDocument<?> makeEditorKit(@UnknownInitialization(LightDialog.class)EditImmediateColumnDialog this, DataType dataType) throws InternalException
    {
        try
        {
            defaultValue = DataTypeUtility.makeDefaultValue(dataType);
            // Bit of a hack to work around thread checker:
            return makeEditorKit(((ExBiFunction<DataType, @Value Object, String>)EditImmediateColumnDialog::defaultAsString).apply(dataType, defaultValue), TableDisplayUtility.recogniser(dataType, true));
            //return fieldFromComponent(TableDisplayUtility.component(ImmutableList.of(), dataType, defaultValue), TableDisplayUtility.stfStylesFor(dataType));
        }
        catch (UserException e)
        {
            // If valueToString throws on makeDefaultValue, it's justifiable
            // to treat this as an internal error:
            throw new InternalException("Error loading default value", e);
        }
    }

    @OnThread(Tag.Simulation)
    private static String defaultAsString(DataType dataType, @Value Object defValue) throws UserException, InternalException
    {
        return DataTypeUtility.valueToString(defValue);
    }

    private <T extends @NonNull @ImmediateValue Object> RecogniserDocument<@Value T> makeEditorKit(@UnknownInitialization(LightDialog.class) EditImmediateColumnDialog this, String initialValue, RecogniserAndType<T> recogniser)
    {
        RecogniserDocument<@Value T> editorKit = new RecogniserDocument<@Value T>(initialValue, (Class<@Value T>)recogniser.itemClass, (Recogniser<@Value T>)recogniser.recogniser, null, (String s, @Value T v, FXPlatformRunnable reset) -> {defaultValue = v;}, k -> getDialogPane().lookupButton(ButtonType.OK).requestFocus(), null);
        defaultValue = editorKit.getLatestValue().leftToNull();
        return editorKit;
    }

}
