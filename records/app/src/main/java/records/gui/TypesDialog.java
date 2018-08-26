package records.gui;

import annotation.identifier.qual.ExpressionIdentifier;
import com.google.common.collect.ImmutableList;
import javafx.beans.binding.ObjectExpression;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.ListView;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Window;
import log.Log;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType.TagType;
import records.data.datatype.TaggedTypeDefinition;
import records.data.datatype.TypeId;
import records.data.datatype.TypeManager;
import records.error.InternalException;
import records.error.UserException;
import records.gui.expressioneditor.TypeEditor;
import records.jellytype.JellyType;
import records.transformations.expression.type.TypeExpression;
import records.transformations.expression.type.UnfinishedTypeExpression;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.IdentifierUtility;
import utility.Pair;
import utility.Utility;
import utility.gui.DialogPaneWithSideButtons;
import utility.gui.ErrorableDialog;
import utility.gui.FXUtility;
import utility.gui.FancyList;
import utility.gui.GUI;

import java.util.Arrays;
import java.util.List;

@OnThread(Tag.FXPlatform)
public class TypesDialog extends Dialog<Void>
{
    private final TypeManager typeManager;

    public TypesDialog(Window owner, TypeManager typeManager)
    {
        this.typeManager = typeManager;
        initModality(Modality.WINDOW_MODAL);
        initOwner(owner);
        setResizable(true);
        getDialogPane().getStylesheets().addAll(
                FXUtility.getStylesheet("general.css"),
                FXUtility.getStylesheet("dialogs.css")
        );

        ListView<TaggedTypeDefinition> types = new ListView<>();

        Button addButton = GUI.button("types.add", () -> {
            TaggedTypeDefinition newType = new EditTypeDialog(null).showAndWait().orElse(null);
            if (newType != null)
            {
                TaggedTypeDefinition newTypeFinal = newType;
                FXUtility.alertOnErrorFX_(() -> typeManager.registerTaggedType(newTypeFinal.getTaggedTypeName().getRaw(), newTypeFinal.getTypeArguments(), newTypeFinal.getTags()));
            }
        });
        
        Button editButton = new Button();
        Button removeButton = new Button();

        BorderPane buttons = GUI.borderLeftCenterRight(addButton, editButton, removeButton);
        BorderPane content = GUI.borderTopCenterBottom(GUI.label("units.userDeclared"), types, buttons, "units-dialog-user-defined");

        getDialogPane().setContent(content);

        getDialogPane().getButtonTypes().setAll(ButtonType.CLOSE);
        getDialogPane().lookupButton(ButtonType.CLOSE).getStyleClass().add("close-button");
    }

    @OnThread(Tag.FXPlatform)
    private class EditTypeDialog extends ErrorableDialog<TaggedTypeDefinition>
    {

        private final TextArea plainTagList;
        private final Tab plainTab;
        private final Tab innerValuesTab;
        private final TabPane tabPane;
        private final TextField typeName;
        private final FancyList<Either<String, TagType<JellyType>>, TagValueEdit> innerValueTagList;

        public EditTypeDialog(@Nullable TaggedTypeDefinition existing)
        {
            super(new DialogPaneWithSideButtons());
            Scene parentScene = TypesDialog.this.getDialogPane().getScene();
            if (parentScene != null && parentScene.getWindow() != null)
                initOwner(parentScene.getWindow());
            initModality(Modality.WINDOW_MODAL);
            getDialogPane().getStylesheets().addAll(
                    FXUtility.getStylesheet("general.css"),
                    FXUtility.getStylesheet("dialogs.css")
            );

            typeName = new TextField();
            Node name = GUI.labelled("edit.type.name", typeName);

            plainTagList = new TextArea();
            plainTagList.getStyleClass().add("type-entry-plain-tags-textarea");
            plainTab = new Tab("Plain", GUI.borderTopCenter(
                GUI.label("edit.type.plain.tags"),
                plainTagList
            ));
            plainTab.getStyleClass().add("type-entry-tab-plain");

            innerValueTagList = new FancyList<Either<String, TagType<JellyType>>, TagValueEdit>(existing == null ? ImmutableList.of() : Utility.mapListI(existing.getTags(), Either::right), true, true, true)
            {
                @Override
                protected @OnThread(Tag.FXPlatform) Pair<TagValueEdit, ObjectExpression<Either<String, TagType<JellyType>>>> makeCellContent(@Nullable Either<String, TagType<JellyType>> initialContent, boolean editImmediately)
                {
                    TagValueEdit tagValueEdit = new TagValueEdit(initialContent == null ? null : initialContent.<@Nullable TagType<JellyType>>either(s -> null, v -> v), editImmediately);
                    return new Pair<>(tagValueEdit, tagValueEdit.currentValue);
                }
            };
            
            innerValuesTab = new Tab("Inner Values", new VBox(
                innerValueTagList.getNode()
            ));
            innerValuesTab.getStyleClass().add("type-entry-tab-standard");

            tabPane = new TabPane(plainTab, innerValuesTab);
            
            getDialogPane().setContent(new VBox(name, tabPane, getErrorLabel()));

            setOnShown(e -> {
                FXUtility.runAfter(() -> typeName.requestFocus());
            });
        }

        @Override
        protected @OnThread(Tag.FXPlatform) Either<@Localized String, TaggedTypeDefinition> calculateResult()
        {
            try
            {
                String typeIdentifier = IdentifierUtility.asExpressionIdentifier(typeName.getText().trim());
                
                if (typeIdentifier == null)
                    return Either.left("Not valid type identifier: " + typeName.getText().trim());
                TypeId typeIdentifierFinal = new TypeId(typeIdentifier);

                if (tabPane.getSelectionModel().getSelectedItem() == plainTab)
                {
                    String[] tags = plainTagList.getText().split("\\w*\\|\\w*");
                    Either<@Localized String, ImmutableList<String>> tagNames = Either.mapM(Arrays.asList(tags), this::parseTagName);
                    return tagNames.mapInt(ts -> new TaggedTypeDefinition(typeIdentifierFinal, ImmutableList.of(), Utility.mapListI(ts, t -> new TagType<>(t, null))));
                }
                else if (tabPane.getSelectionModel().getSelectedItem() == innerValuesTab)
                {
                    return Either.mapM(innerValueTagList.getItems(), e -> e).mapInt(ts -> new TaggedTypeDefinition(typeIdentifierFinal, ImmutableList.of(), ts));
                }
                
                // Shouldn't happen:
                return Either.left("Select a tab in the tab pane");
            }
            catch (InternalException e)
            {
                Log.log(e);
                return Either.left(e.getLocalizedMessage());
            }
        }
        
        private Either<@Localized String, @ExpressionIdentifier String> parseTagName(String src)
        {
            @Nullable @ExpressionIdentifier String identifier = IdentifierUtility.asExpressionIdentifier(src.trim());
            if (identifier == null)
                return Either.left("Invalid tag name: " + src.trim());
            else
                return Either.right(identifier);
        }

        @OnThread(Tag.FXPlatform)
        private class TagValueEdit extends BorderPane
        {
            private final TextField tagName;
            private final TypeEditor innerType;
            private final ObjectProperty<Either<String, TagType<JellyType>>> currentValue;

            public TagValueEdit(@Nullable TagType<JellyType> initialContent, boolean editImmediately)
            {
                this.currentValue = new SimpleObjectProperty<>(Either.right(new TagType<>(
                    initialContent == null ? "" : initialContent.getName(),
                    initialContent == null ? null : initialContent.getInner() 
                )));
                this.tagName = new TextField();
                FXUtility.addChangeListenerPlatformNN(tagName.textProperty(), name -> {
                    currentValue.set(currentValue.getValue().map(tt -> new TagType<>(name, tt.getInner())));
                });
                TypeExpression startingExpression = null;
                try
                {
                    startingExpression = initialContent == null || initialContent.getInner() == null ? null : TypeExpression.fromJellyType(initialContent.getInner(), typeManager);
                }
                catch (InternalException | UserException e)
                {
                    Log.log(e);
                }
                if (startingExpression == null)
                    startingExpression = new UnfinishedTypeExpression("");
                this.innerType = new TypeEditor(typeManager, startingExpression, latest -> {
                    try
                    {
                        @Nullable JellyType jellyType = latest.isEmpty() ? null : latest.toJellyType(typeManager);
                        currentValue.set(Either.right(new TagType<JellyType>(tagName.getText(),
                                jellyType)));
                    }
                    catch (InternalException | UserException e)
                    {
                        Log.log(e);
                        currentValue.set(Either.left(e.getLocalizedMessage()));
                    }
                });
                
                setLeft(tagName);
                setCenter(innerType.getContainer());

                if (editImmediately)
                    FXUtility.onceNotNull(tagName.sceneProperty(), s -> tagName.requestFocus());
            }
        }
    }
    
    
}
