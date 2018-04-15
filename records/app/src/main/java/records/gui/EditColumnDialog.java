package records.gui;

import annotation.qual.Value;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.layout.HBox;
import javafx.stage.Window;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.ColumnId;
import records.data.TableManager;
import records.data.datatype.DataType;
import records.gui.EditColumnDialog.ColumnDetails;
import records.gui.expressioneditor.TypeEditor;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
import utility.gui.GUI;
import utility.gui.LightDialog;

@OnThread(Tag.FXPlatform)
public class EditColumnDialog extends LightDialog<ColumnDetails>
{
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
    
    
    @OnThread(Tag.FXPlatform)
    public EditColumnDialog(Window parent, TableManager tableManager, @Nullable ColumnId initial)
    {
        super(parent);
        
        getDialogPane().setContent(GUI.vbox("",
            new Label("Column name"),
            new ColumnNameTextField(initial).getNode(),
            new Label("Type"),
            new RadioButton("Number (no units"),
            new RadioButton("Text"),
            GUI.hbox("", new RadioButton("Custom"), new TypeEditor(tableManager).getContainer())
        ));
    }
}
