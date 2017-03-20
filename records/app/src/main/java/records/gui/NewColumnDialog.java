package records.gui;

import annotation.qual.Value;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import records.data.datatype.DataType;
import records.transformations.TransformationEditor;

/**
 * Created by neil on 20/03/2017.
 */
public class NewColumnDialog extends Dialog<NewColumnDialog.NewColumnDetails>
{
    private final TextField name;

    public NewColumnDialog()
    {
        VBox contents = new VBox();
        name = new TextField();
        contents.getChildren().add(new HBox(new Label(TransformationEditor.getString("newcolumn.name")), name));
        contents.getChildren().add(new Separator());
        ToggleGroup typeGroup = new ToggleGroup();
        RadioButton numberType = new RadioButton(TransformationEditor.getString("type.number"));
        numberType.setToggleGroup(typeGroup);
        contents.getChildren().add(new HBox(numberType, new Label(TransformationEditor.getString("newcolumn.number.units")), new TextField("")));
        RadioButton textType = new RadioButton(TransformationEditor.getString("type.text"));
        textType.setToggleGroup(typeGroup);
        contents.getChildren().add(textType);

        setResultConverter(bt -> {
            return new NewColumnDetails(name.getText(), typeGroup.getSelectedToggle() == numberType ? DataType.NUMBER : DataType.TEXT, "");
        });

        getDialogPane().setContent(contents);
        getDialogPane().getButtonTypes().setAll(ButtonType.CANCEL, ButtonType.OK);
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
