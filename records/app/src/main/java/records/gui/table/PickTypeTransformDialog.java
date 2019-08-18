package records.gui.table;

import com.google.common.collect.ImmutableList;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ListView;
import javafx.scene.layout.BorderPane;
import javafx.stage.Modality;
import records.data.datatype.DataType;
import records.gui.table.PickTypeTransformDialog.TypeTransform;
import records.transformations.expression.Expression;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.gui.DialogPaneWithSideButtons;
import utility.gui.DimmableParent;
import utility.gui.FXUtility;
import utility.gui.GUI;
import utility.gui.LightDialog;

@OnThread(Tag.FXPlatform)
public class PickTypeTransformDialog extends LightDialog<TypeTransform>
{
    public static class TypeTransform
    {
        public final Expression transformed;
        private final DataType destinationType;

        public TypeTransform(Expression transformed, DataType destinationType)
        {
            this.transformed = transformed;
            this.destinationType = destinationType;
        }

        @Override
        public String toString()
        {
            return destinationType.toString();
        }
    }

    public PickTypeTransformDialog(DimmableParent parent, ImmutableList<TypeTransform> pickFrom)
    {
        super(parent, new DialogPaneWithSideButtons());
        initModality(Modality.APPLICATION_MODAL);
        ListView<TypeTransform> listView = new ListView<>();
        listView.getItems().setAll(pickFrom);
        listView.getSelectionModel().selectFirst();
        listView.setPrefHeight(150);
        BorderPane.setMargin(listView, new Insets(10, 0, 0, 0));
        getDialogPane().setContent(GUI.borderTopCenter(
            GUI.label("transformType.header"),
            listView
        ));
        getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        setResultConverter(bt -> bt == ButtonType.OK ? listView.getSelectionModel().getSelectedItem() : null);
        setOnShown(e -> FXUtility.runAfter(listView::requestFocus));
        
    }
}
