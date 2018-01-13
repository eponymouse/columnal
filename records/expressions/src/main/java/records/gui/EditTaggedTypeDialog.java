package records.gui;

import com.google.common.collect.ImmutableList;
import javafx.beans.value.ObservableObjectValue;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Separator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;
import records.data.datatype.DataType.TagType;
import records.data.datatype.TaggedTypeDefinition;
import records.data.datatype.TypeId;
import records.data.datatype.TypeManager;
import records.error.InternalException;
import records.gui.ErrorableTextField.ConversionResult;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.Pair;
import utility.gui.ErrorableDialog;
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
public class EditTaggedTypeDialog extends ErrorableDialog<TaggedTypeDefinition>
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
            name = name.trim();
            if (name.isEmpty())
                return ErrorableTextField.ConversionResult.<@NonNull String>error(TranslationUtility.getString("taggedtype.error.name.missing"));
            else if (typeManager.getKnownTaggedTypes().get(new TypeId(name)) == null)
                return ErrorableTextField.ConversionResult.success(name);
            else
                return ErrorableTextField.ConversionResult.<@NonNull String>error(TranslationUtility.getString("taggedtype.exists", name));
        });
        typeName.getNode().getStyleClass().add("taggedtype-type-name");

        Button addButton = GUI.button("taggedtype.addTag", () -> {
            tagInfo.add(new TagInfo(tagInfo.size()));
            tagsList.getChildren().setAll(tagInfo);
            sizeToFit();
        });
        // Add one to begin with:
        addButton.fire();

        setResizable(true);
        getDialogPane().getStylesheets().addAll(
            FXUtility.getStylesheet("general.css"),
            FXUtility.getStylesheet("dialogs.css"),
            FXUtility.getStylesheet("type-selection-pane.css")
        );
        getDialogPane().setContent(new VBox(new HBox(GUI.label("taggedtype.name"), typeName.getNode()), new Separator(), tagsList, addButton, getErrorLabel()));
    }

    @Override
    protected Either<@Localized String, TaggedTypeDefinition> calculateResult()
    {
        @Nullable String name = typeName.valueProperty().get();
        if (name == null)
        {
            return Either.left(TranslationUtility.getString("taggedtype.error.name.invalid"));
        }
        if (tagInfo.isEmpty())
        {
            return Either.left(TranslationUtility.getString("taggedtype.error.empty"));
        }

        ImmutableList.Builder<TagType<DataType>> tagTypes = ImmutableList.builder();
        for (TagInfo info : tagInfo)
        {
            Either<@Localized String, TagType<DataType>> r = info.calculateResult();
            @Nullable @Localized String err = r.<@Nullable @Localized String>either(e -> e, t -> {
                tagTypes.add(t);
                return null;
            });
            if (err != null)
            {
                return Either.left(err);
            }
        }
        // TODO check for duplicate tag names

        try
        {
            return Either.right(typeManager.registerTaggedType(name, ImmutableList.of(), tagTypes.build()));
        }
        catch (InternalException e)
        {
            return Either.left(e.getLocalizedMessage());
        }
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
        private final Button setSubType;
        private ObservableObjectValue<@Nullable Optional<DataType>> subType;

        public TagInfo(int index)
        {
            tagName = new ErrorableTextField<String>(name -> {
                // TODO check if tag name is valid
                if (name.trim().isEmpty())
                    return ErrorableTextField.ConversionResult.<@NonNull String>error(TranslationUtility.getString("taggedtype.error.tagName.empty"));
                else
                    return ConversionResult.success(name.trim());
            });
            tagName.getNode().getStyleClass().add("taggedtype-tag-name-" + index);
            Pair<Button, ObservableObjectValue<@Nullable Optional<DataType>>> subTypeBits = TypeSelectionPane.makeTypeButton(typeManager, true);
            setSubType = subTypeBits.getFirst();
            setSubType.getStyleClass().add("taggedtype-tag-set-subType-" + index);
            subType = subTypeBits.getSecond();

            getChildren().setAll(
                GUI.label("taggedtype.tag.name"),
                tagName.getNode(),
                GUI.label("taggedtype.tag.subType"),
                setSubType
            );

            // TODO allow dragging of this item to other positions, and/or add up/down buttons

        }

        public Either<@Localized String, TagType<DataType>> calculateResult()
        {
            @Nullable String name = tagName.valueProperty().getValue();
            if (name == null)
            {
                return Either.left(TranslationUtility.getString("taggedtype.error.tagName.invalid"));
            }
            @Nullable Optional<DataType> innerType = subType.get();
            // Not actually sure this can ever occur, but no harm in handling it:
            if (innerType == null)
            {
                return Either.left(TranslationUtility.getString("taggedtype.error.tagType.invalid"));
            }
            return Either.right(new DataType.TagType<>(name, innerType.orElse(null)));
        }
    }
}
