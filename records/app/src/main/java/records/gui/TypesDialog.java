package records.gui;

import annotation.identifier.qual.ExpressionIdentifier;
import com.google.common.collect.ImmutableList;
import javafx.beans.binding.ObjectExpression;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TabPane.TabClosingPolicy;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Window;
import log.Log;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;
import records.data.datatype.DataType.TagType;
import records.data.datatype.TaggedTypeDefinition;
import records.data.datatype.TaggedTypeDefinition.TypeVariableKind;
import records.data.datatype.TypeId;
import records.data.datatype.TypeManager;
import records.error.InternalException;
import records.error.UserException;
import records.gui.expressioneditor.TypeEditor;
import records.jellytype.JellyType;
import records.transformations.expression.type.InvalidIdentTypeExpression;
import records.transformations.expression.type.TypeExpression;
import records.transformations.expression.type.IdentTypeExpression;
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
import utility.gui.TranslationUtility;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@OnThread(Tag.FXPlatform)
public class TypesDialog extends Dialog<Void>
{
    private final TypeManager typeManager;

    public TypesDialog(Window owner, TypeManager typeManager)
    {
        this.typeManager = typeManager;
        initModality(Modality.WINDOW_MODAL);
        initOwner(owner);
        setTitle(TranslationUtility.getString("types.title"));
        setResizable(true);
        getDialogPane().getStylesheets().addAll(
                FXUtility.getStylesheet("general.css"),
                FXUtility.getStylesheet("dialogs.css")
        );

        ListView<TaggedTypeDefinition> types = new ListView<>();
        types.setEditable(false);
        types.setCellFactory(lv -> {
            ListCell<TaggedTypeDefinition> cell = new ListCell<TaggedTypeDefinition>()
            {
                @Override
                protected void updateItem(@Nullable TaggedTypeDefinition item, boolean empty)
                {
                    super.updateItem(item, empty);
                    if (!empty && item != null)
                        setText(item.getTaggedTypeName().getRaw() + item.getTypeArguments().stream().map(t -> "(" + t.getSecond() + ")").collect(Collectors.joining("")));
                }
            };
            return cell;
        });
        updateTypesList(typeManager, types);
        FXUtility.listViewDoubleClick(types, t -> editType(typeManager, types, t));
        types.getStyleClass().add("types-list");

        Button addButton = GUI.button("types.add", () -> {
            TaggedTypeDefinition newType = new EditTypeDialog(null).showAndWait().orElse(null);
            if (newType != null)
            {
                TaggedTypeDefinition newTypeFinal = newType;
                FXUtility.alertOnErrorFX_("Error adding new type", () -> typeManager.registerTaggedType(newTypeFinal.getTaggedTypeName().getRaw(), newTypeFinal.getTypeArguments(), newTypeFinal.getTags()));
                updateTypesList(typeManager, types);
            }
        });
        
        Button editButton = GUI.button("types.edit", () -> {
            @Nullable TaggedTypeDefinition typeDefinition = types.getSelectionModel().getSelectedItem();
            if (typeDefinition != null)
            {
                editType(typeManager, types, typeDefinition);
            }
        });
        Button removeButton = GUI.button("types.remove", () -> {
            @Nullable TaggedTypeDefinition typeDefinition = types.getSelectionModel().getSelectedItem();
            if (typeDefinition != null)
            {
                typeManager.unregisterTaggedType(typeDefinition.getTaggedTypeName());
                updateTypesList(typeManager, types);
            }
        });

        BorderPane.setMargin(types, new Insets(10, 0, 10, 0));
        BorderPane buttons = GUI.borderLeftCenterRight(addButton, editButton, removeButton, "types-dialog-buttons");
        BorderPane content = GUI.borderTopCenterBottom(GUI.label("types.userDeclared"), types, buttons, "types-dialog-content");

        getDialogPane().setContent(content);

        getDialogPane().getButtonTypes().setAll(ButtonType.CLOSE);
        getDialogPane().lookupButton(ButtonType.CLOSE).getStyleClass().add("close-button");
    }

    public void editType(@UnknownInitialization(Object.class) TypesDialog this, TypeManager typeManager, ListView<TaggedTypeDefinition> types, TaggedTypeDefinition existing)
    {
        @Nullable TaggedTypeDefinition changedType = new EditTypeDialog(existing).showAndWait().orElse(null);
        if (changedType != null)
        {
            TypeId oldTypeName = existing.getTaggedTypeName();
            @NonNull TaggedTypeDefinition newTypeFinal = changedType;
            FXUtility.alertOnErrorFX_("Error saving type change", () -> {
                typeManager.unregisterTaggedType(oldTypeName);
                typeManager.registerTaggedType(newTypeFinal.getTaggedTypeName().getRaw(), newTypeFinal.getTypeArguments(), newTypeFinal.getTags());
            });
            updateTypesList(typeManager, types);
        }
    }

    private static void updateTypesList(TypeManager typeManager, ListView<TaggedTypeDefinition> types)
    {
        types.getItems().setAll(typeManager.getUserTaggedTypes().values());
    }

    @OnThread(Tag.FXPlatform)
    private class EditTypeDialog extends ErrorableDialog<TaggedTypeDefinition>
    {
        private final TextArea plainTagList;
        private final Tab plainTab;
        private final Tab innerValuesTab;
        private final TabPane tabPane;
        private final TextField typeName;
        // Error or tag type
        private final FancyList<Either<String, TagType<JellyType>>, TagValueEdit> innerValueTagList;
        private final TextField innerValueTypeArgs;
        // Avoids re-entrancy when copying one tab to another:
        private boolean crossSetting = false;

        public EditTypeDialog(@Nullable TaggedTypeDefinition existing)
        {
            super(new DialogPaneWithSideButtons());
            Scene parentScene = TypesDialog.this.getDialogPane().getScene();
            if (parentScene != null && parentScene.getWindow() != null)
                initOwner(parentScene.getWindow());
            initModality(Modality.WINDOW_MODAL);
            setTitle(TranslationUtility.getString("types.edit.title"));
            getDialogPane().getStylesheets().addAll(
                    FXUtility.getStylesheet("general.css"),
                    FXUtility.getStylesheet("dialogs.css")
            );

            typeName = new TextField();
            Node name = GUI.labelled("edit.type.name", typeName);

            plainTagList = new TextArea();
            plainTagList.getStyleClass().add("type-entry-plain-tags-textarea");
            plainTab = new Tab("Plain", GUI.borderTopCenter(
                GUI.label("edit.type.plain.tags", "plain-tags-label"),
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
                
                {
                    listenForCellChange(c -> innerValueChanged());
                }
            };
            innerValueTagList.getNode().setMinHeight(200);
            innerValueTagList.getNode().setPrefHeight(350);
            
            innerValueTypeArgs = new TextField();
            innerValueTypeArgs.getStyleClass().add("type-entry-inner-type-args");
            innerValuesTab = new Tab("Inner Values", GUI.vbox("inner-values-tab-content",
                GUI.labelled("edit.type.args", innerValueTypeArgs, "inner-values-type-args-pane"),
                GUI.borderLeftRight(
                    GUI.label("edit.type.inner.tag"),
                    GUI.label("edit.type.inner.type")
                , "inner-values-list-header-pane"),
                innerValueTagList.getNode()
            ));
            innerValuesTab.getStyleClass().add("type-entry-tab-standard");
            FXUtility.addChangeListenerPlatformNN(innerValuesTab.selectedProperty(), sel -> {
                innerValueTagList.getNode().requestLayout();
            });

            tabPane = new TabPane(plainTab, innerValuesTab);
            tabPane.setTabClosingPolicy(TabClosingPolicy.UNAVAILABLE);
            
            getDialogPane().setContent(GUI.vbox("edit-type-dialog-content", name, tabPane, getErrorLabel()));

            setOnShown(e -> {
                FXUtility.runAfter(() -> typeName.requestFocus());
            });

            FXUtility.addChangeListenerPlatformNN(plainTagList.textProperty(), t -> {
                if (tabPane.getSelectionModel().getSelectedItem() == plainTab && !crossSetting)
                {
                    crossSetting = true;
                    innerValueTypeArgs.setText("");
                    innerValueTagList.resetItems(getPlainTags(plainTagList).map(plain -> {
                        return Either.<String, TagType<JellyType>>right(new TagType<JellyType>(plain, null));
                    }).collect(ImmutableList.toImmutableList()));
                    crossSetting = false;
                }
            });
            FXUtility.addChangeListenerPlatformNN(innerValueTypeArgs.textProperty(), args -> innerValueChanged());
            
            if (existing != null)
            {
                crossSetting = true;
                typeName.setText(existing.getTaggedTypeName().getRaw());
                plainTagList.setText(existing.getTags().stream().map(t -> t.getName()).collect(Collectors.joining("\n")));
                innerValueTypeArgs.setText(existing.getTypeArguments().stream().map(p -> p.getSecond()).collect(Collectors.joining(", ")));
                // tag list done above
                
                if (existing.getTypeArguments().isEmpty() && 
                    existing.getTags().stream().allMatch(t -> t.getInner() == null))
                {
                    tabPane.getSelectionModel().select(plainTab);
                }
                else
                {
                    tabPane.getSelectionModel().select(innerValuesTab);
                }
                crossSetting = false;
            }
        }

        public void innerValueChanged(@UnknownInitialization(Object.class) EditTypeDialog this)
        {
            if (tabPane == null || innerValueTypeArgs == null || innerValueTagList == null || plainTab == null || plainTagList == null)
                return; // Not initialised yet, just ignores
            
            if (tabPane.getSelectionModel().getSelectedItem() == innerValuesTab && !crossSetting)
            {
                boolean hasTypeArgs = !innerValueTypeArgs.getText().trim().isEmpty();
                Either<String, ImmutableList<String>> plainTagNames = Either.mapM(innerValueTagList.getItems(), t -> t.flatMap(v -> v.getInner() == null ? Either.<@NonNull String, @NonNull String>right(v.getName()) : Either.<@NonNull String, @NonNull String>left("err")));
                boolean enablePlain = !hasTypeArgs && plainTagNames.isRight();
                crossSetting = true;
                plainTab.setDisable(!enablePlain);
                if (enablePlain)
                {
                    // Should definitely be right:
                    plainTagNames.ifRight(names -> plainTagList.setText(names.stream().collect(Collectors.joining("\n"))));
                }
                crossSetting = false;
            }
        }

        @Override
        protected @OnThread(Tag.FXPlatform) Either<@Localized String, TaggedTypeDefinition> calculateResult()
        {
            try
            {
                @ExpressionIdentifier String typeIdentifier = IdentifierUtility.asExpressionIdentifier(typeName.getText().trim());
                
                if (typeIdentifier == null)
                    return Either.left("Not valid type identifier: " + typeName.getText().trim());
                TypeId typeIdentifierFinal = new TypeId(typeIdentifier);

                if (tabPane.getSelectionModel().getSelectedItem() == plainTab)
                {
                    String[] tags = getPlainTags(plainTagList).toArray(String[]::new);
                    Either<@Localized String, ImmutableList<String>> tagNames = Either.right(ImmutableList.of());
                    if (!Arrays.equals(tags, new String[]{""}))
                        tagNames = Either.mapM(Arrays.asList(tags), t -> parseTagName("Invalid tag name: ", t));
                    Either<@Localized String, TaggedTypeDefinition> r = tagNames.mapInt(ts -> new TaggedTypeDefinition(typeIdentifierFinal, ImmutableList.of(), Utility.mapListI(ts, t -> new TagType<>(t, null))));
                    return r;
                }
                else if (tabPane.getSelectionModel().getSelectedItem() == innerValuesTab)
                {
                    String[] typeArgs = innerValueTypeArgs.getText().trim().split("\\s*,\\s*");
                    Either<@Localized String, ImmutableList<String>> typeArgsOrErr = Either.right(ImmutableList.of());
                    if (!Arrays.equals(typeArgs, new String[]{""}))
                        typeArgsOrErr = Either.mapM(Arrays.asList(typeArgs), t -> parseTagName("Invalid type argument: ", t));
                    
                    return typeArgsOrErr.flatMapInt(args -> Either.mapM(innerValueTagList.getItems(), e -> e).mapInt(ts -> new TaggedTypeDefinition(typeIdentifierFinal, Utility.mapListI(args, a -> new Pair<>(TypeVariableKind.TYPE, a)), ts)));
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

        private Stream<String> getPlainTags(@UnknownInitialization(Object.class) EditTypeDialog this, TextArea plainTagList)
        {
            return Arrays.stream(plainTagList.getText()
                .split(" *(,|\n|\r) *"))
                .map(s -> s.trim())
                .filter(s -> !s.isEmpty());
        }

        private Either<@Localized String, @ExpressionIdentifier String> parseTagName(String errorPrefix, String src)
        {
            @Nullable @ExpressionIdentifier String identifier = IdentifierUtility.asExpressionIdentifier(src.trim());
            if (identifier == null)
                return Either.left(errorPrefix + "\"" + src.trim() + "\"");
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
                getStyleClass().add("tag-value-edit");
                this.currentValue = new SimpleObjectProperty<>(Either.right(new TagType<>(
                    initialContent == null ? "" : initialContent.getName(),
                    initialContent == null ? null : initialContent.getInner() 
                )));
                this.tagName = new TextField(initialContent == null ? "" : initialContent.getName());
                tagName.setPromptText(TranslationUtility.getString("edit.type.inner.tag.prompt"));
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
                    startingExpression = new InvalidIdentTypeExpression("");
                this.innerType = new TypeEditor(typeManager, startingExpression, latest -> {
                    try
                    {
                        @Nullable JellyType jellyType = latest.isEmpty() ? null : latest.toJellyType(typeManager);
                        currentValue.set(Either.right(new TagType<JellyType>(tagName.getText(),
                                jellyType)));
                    }
                    catch (InternalException | UserException e)
                    {
                        if (e instanceof InternalException)
                            Log.log(e);
                        currentValue.set(Either.left(e.getLocalizedMessage()));
                    }
                });
                innerType.setPromptText(TranslationUtility.getString("edit.type.tag.inner.prompt"));
                
                setLeft(tagName);
                setCenter(innerType.getContainer());

                if (editImmediately)
                    FXUtility.onceNotNull(tagName.sceneProperty(), s -> tagName.requestFocus());
                
                FXUtility.addChangeListenerPlatformNN(currentValue, v -> {
                    innerValueChanged();
                });
            }
        }
    }
    
    
}
