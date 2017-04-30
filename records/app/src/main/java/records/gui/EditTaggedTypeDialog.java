package records.gui;

import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Separator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;
import records.data.datatype.TypeManager;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.gui.TranslationUtility;

import java.util.ArrayList;
import java.util.List;

/**
 * Allows the user to create a new tagged type or edit an existing one.
 *
 * The dialog returns the DataType as it has been created/edited.
 * The registration of type with the type manager is performed by this class,
 * so there is no need for the caller to worry about that.
 */
@OnThread(Tag.FXPlatform)
public class EditTaggedTypeDialog extends Dialog<Void>
{
    private final VBox tagsList;
    private final ErrorableTextField<String> typeName;
    private final List<TagInfo> tagInfo;

    public EditTaggedTypeDialog(TypeManager typeManager)
    {
        tagsList = new VBox();
        tagInfo = new ArrayList<>();
        typeName = new ErrorableTextField<String>(name -> {
            if (typeManager.lookupType(name) == null)
                return ErrorableTextField.ConversionResult.success(name);
            else
                return ErrorableTextField.ConversionResult.<@NonNull String>error(TranslationUtility.getString("taggedtype.exists", name));
        });


        getDialogPane().setContent(new VBox(typeName.getNode(), new Separator(), tagsList));
        getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        setResultConverter(bt -> {
            if (bt == ButtonType.OK)
            {
                // Need at least one tag to be valid:
                if (!tagInfo.isEmpty())
                {
                    // TODO check name is valid and unused
                }
            }
            return null;
        });
    }

    @OnThread(Tag.FXPlatform)
    private class TagInfo extends HBox
    {
        private final ErrorableTextField<String> tagName;
        private final Button addSubType;
        private @Nullable DataType subType;

        public TagInfo(TypeManager typeManager)
        {
            tagName = new ErrorableTextField<String>(name -> {
                // TODO check if tag name is valid
                return ErrorableTextField.ConversionResult.<@NonNull String>error("");
            });
            addSubType = new Button(TranslationUtility.getString("taggedtype.addsubtype"));
            addSubType.setOnAction(e -> {
                // Show dialog to pick type:
                @Nullable Scene scene = getScene();
                subType = new TypeDialog(scene == null ? null : scene.getWindow(), typeManager).showAndWait().orElse(null);
                // If they picked the type:
                if (subType != null)
                {
                    // Take out the button:
                    getChildren().remove(addSubType);

                    // TODO add a remove sub-type button (or just toggle the button text)
                }
            });

            // TODO allow dragging of this item to other positions.

        }

    }
}
