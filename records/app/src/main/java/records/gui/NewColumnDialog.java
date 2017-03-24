package records.gui;

import annotation.qual.Value;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.checkerframework.checker.initialization.qual.UnderInitialization;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;
import records.data.TableManager;
import records.data.datatype.DataType;
import records.gui.expressioneditor.ExpressionEditor;
import records.transformations.TransformationEditor;
import records.transformations.expression.NumericLiteral;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
import utility.Utility;

/**
 * Created by neil on 20/03/2017.
 */
@OnThread(Tag.FXPlatform)
public class NewColumnDialog extends Dialog<NewColumnDialog.NewColumnDetails>
{
    private final TextField name;
    private final VBox contents;
    private final TypeSelectionPane typeSelectionPane;
    private final ExpressionEditor defaultValueEditor;

    @OnThread(Tag.FXPlatform)
    public NewColumnDialog(TableManager tableManager)
    {
        contents = new VBox();
        name = new TextField();
        typeSelectionPane = new TypeSelectionPane(tableManager.getTypeManager());
        defaultValueEditor = new ExpressionEditor(new NumericLiteral(0, null), null, typeSelectionPane.selectedType(), tableManager, e -> {});
        contents.getChildren().add(new HBox(new Label(TransformationEditor.getString("newcolumn.name")), name));
        contents.getChildren().add(new Separator());
        contents.getChildren().add(typeSelectionPane.getNode());
        contents.getChildren().addAll(new Separator(), new HBox(new Label(TransformationEditor.getString("newcolumn.defaultvalue")), defaultValueEditor.getContainer()));
        int defaultValueContentsIndex = contents.getChildren().size() - 1;

        // TODO: tuple, array

        setResultConverter(this::makeResult);

        getDialogPane().getStylesheets().add(Utility.getStylesheet("general.css"));
        getDialogPane().setContent(contents);
        getDialogPane().getButtonTypes().setAll(ButtonType.CANCEL, ButtonType.OK);
        getDialogPane().lookupButton(ButtonType.OK).addEventFilter(ActionEvent.ACTION, e -> {
            if (getSelectedType() == null)
                e.consume();
        });
    }

    @RequiresNonNull({"typeSelectionPane"})
    private @Nullable DataType getSelectedType(@UnknownInitialization(Object.class) NewColumnDialog this)
    {
        return typeSelectionPane.selectedType().get();
    }

    @RequiresNonNull({"typeSelectionPane", "name"})
    private @Nullable NewColumnDetails makeResult(@UnderInitialization(Dialog.class) NewColumnDialog this, ButtonType bt)
    {
        if (bt == ButtonType.OK)
        {
            @Nullable DataType selectedType = getSelectedType();
            if (selectedType == null) // Shouldn't happen given our event filter, but only reasonable option is to back out and act like cancel:
                return null;
            return new NewColumnDetails(name.getText(), selectedType, "");
        }
        else
            return null;
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
