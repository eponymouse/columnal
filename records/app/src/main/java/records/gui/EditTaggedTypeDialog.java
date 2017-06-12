package records.gui;

import javafx.beans.value.ObservableObjectValue;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Separator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;
import records.data.datatype.TypeManager;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
import utility.gui.FXUtility;
import utility.gui.GUI;
import utility.gui.TranslationUtility;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
    private final TypeManager typeManager;

    public EditTaggedTypeDialog(TypeManager typeManager)
    {
        this.typeManager = typeManager;
        tagsList = new VBox();
        tagInfo = new ArrayList<>();
        typeName = new ErrorableTextField<String>(name -> {
            if (typeManager.lookupType(name) == null)
                return ErrorableTextField.ConversionResult.success(name);
            else
                return ErrorableTextField.ConversionResult.<@NonNull String>error(TranslationUtility.getString("taggedtype.exists", name));
        });

        Button addButton = GUI.button("taggedtype.addTag", () -> {
            tagInfo.add(new TagInfo());
            tagsList.getChildren().setAll(tagInfo);
            sizeToFit();
        });

        setResizable(true);
        getDialogPane().setContent(new VBox(new HBox(GUI.label("taggedtype.name"), typeName.getNode()), new Separator(), tagsList, addButton));
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

    private void sizeToFit(@UnknownInitialization(Object.class) EditTaggedTypeDialog this)
    {
        Scene scene = getDialogPane().getScene();
        if (scene != null)
        {
            Window window = scene.getWindow();
            if (window != null)
                window.sizeToScene();
        }
    }

    @OnThread(Tag.FXPlatform)
    private class TagInfo extends HBox
    {
        private final ErrorableTextField<String> tagName;
        private final Button addSubType;
        private ObservableObjectValue<@Nullable Optional<DataType>> subType;

        public TagInfo()
        {
            tagName = new ErrorableTextField<String>(name -> {
                // TODO check if tag name is valid
                return ErrorableTextField.ConversionResult.<@NonNull String>error("");
            });
            Pair<Button, ObservableObjectValue<@Nullable Optional<DataType>>> subTypeBits = TypeSelectionPane.makeTypeButton(typeManager, true);
            addSubType = subTypeBits.getFirst();
            subType = subTypeBits.getSecond();

            getChildren().setAll(
                GUI.label("taggedtype.tag.name"),
                tagName.getNode(),
                GUI.label("taggedtype.tag.subType"),
                addSubType
            );

            // TODO allow dragging of this item to other positions, and/or add up/down buttons

        }

    }
}
