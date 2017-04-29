package records.gui;

import annotation.qual.Value;
import javafx.event.ActionEvent;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import org.checkerframework.checker.initialization.qual.UnderInitialization;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;
import records.data.TableManager;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeUtility;
import records.gui.expressioneditor.ExpressionEditor;
import records.transformations.expression.NumericLiteral;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.gui.FXUtility;
import utility.gui.TranslationUtility;

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
        setTitle(TranslationUtility.getString("newcolumn.title"));
        contents = new VBox();
        contents.getStyleClass().add("new-column-content");
        name = new TextField();
        typeSelectionPane = new TypeSelectionPane(tableManager.getTypeManager());
        defaultValueEditor = new ExpressionEditor(new NumericLiteral(0, null), null, typeSelectionPane.selectedType(), tableManager, e -> {});
        Label nameLabel = new Label(TranslationUtility.getString("newcolumn.name"));
        contents.getChildren().add(new Row(nameLabel, name));
        contents.getChildren().add(new Separator());
        contents.getChildren().add(typeSelectionPane.getNode());
        contents.getChildren().addAll(new Separator(), new HBox(new Label(TranslationUtility.getString("newcolumn.defaultvalue")), defaultValueEditor.getContainer()));

        setResultConverter(this::makeResult);

        setResizable(true);
        getDialogPane().getStylesheets().addAll(
                FXUtility.getStylesheet("general.css"),
                FXUtility.getStylesheet("dialogs.css"),
                FXUtility.getStylesheet("new-column-dialog.css")
        );
        getDialogPane().setContent(contents);
        getDialogPane().getButtonTypes().setAll(ButtonType.CANCEL, ButtonType.OK);
        getDialogPane().lookupButton(ButtonType.OK).addEventFilter(ActionEvent.ACTION, e -> {
            if (getSelectedType() == null)
                e.consume();
        });

        initModality(Modality.NONE);
        setOnShown(e -> org.scenicview.ScenicView.show(getDialogPane().getScene()));
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
            return new NewColumnDetails(name.getText(), selectedType, DataTypeUtility.value(""));
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
