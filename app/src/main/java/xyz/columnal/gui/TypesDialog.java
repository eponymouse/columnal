/*
 * Columnal: Safer, smoother data table processing.
 * Copyright (c) Neil Brown, 2016-2020, 2022.
 *
 * This file is part of Columnal.
 *
 * Columnal is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 *
 * Columnal is distributed in the hope that it will be useful, but WITHOUT 
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or 
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for 
 * more details.
 *
 * You should have received a copy of the GNU General Public License along 
 * with Columnal. If not, see <https://www.gnu.org/licenses/>.
 */

package xyz.columnal.gui;

import annotation.identifier.qual.ExpressionIdentifier;
import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
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
import javafx.stage.Modality;
import xyz.columnal.log.Log;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;
import xyz.columnal.data.datatype.DataType.TagType;
import xyz.columnal.data.datatype.TaggedTypeDefinition;
import xyz.columnal.data.datatype.TaggedTypeDefinition.TypeVariableKind;
import xyz.columnal.data.datatype.TypeId;
import xyz.columnal.data.datatype.TypeManager;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.gui.lexeditor.TypeEditor;
import xyz.columnal.jellytype.JellyType;
import xyz.columnal.transformations.expression.type.InvalidIdentTypeExpression;
import xyz.columnal.transformations.expression.type.TypeExpression;
import xyz.columnal.transformations.expression.type.TypeExpression.JellyRecorder;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.function.fx.FXPlatformSupplier;
import xyz.columnal.utility.IdentifierUtility;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.Utility;
import xyz.columnal.utility.gui.DialogPaneWithSideButtons;
import xyz.columnal.utility.gui.ErrorableDialog;
import xyz.columnal.utility.gui.FXUtility;
import xyz.columnal.utility.gui.FancyList;
import xyz.columnal.utility.gui.GUI;
import xyz.columnal.utility.TranslationUtility;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@OnThread(Tag.FXPlatform)
public class TypesDialog extends Dialog<Void>
{
    private final TypeManager typeManager;
    private final View owner;

    public TypesDialog(View owner, TypeManager typeManager)
    {
        this.owner = owner;
        this.typeManager = typeManager;
        initModality(Modality.WINDOW_MODAL);
        initOwner(owner.dimWhileShowing(this));
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
                FXUtility.alertOnErrorFX_(TranslationUtility.getString("error.adding.new.type"), () -> typeManager.registerTaggedType(newTypeFinal.getTaggedTypeName().getRaw(), newTypeFinal.getTypeArguments(), newTypeFinal.getTags()));
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
            FXUtility.alertOnErrorFX_(TranslationUtility.getString("error.saving.type.change"), () -> {
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
        private final FancyList<Either<@Localized String, TagType<JellyType>>, TagValueEdit> innerValueTagList;
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

            typeName = new TextField();
            Node name = GUI.labelled("edit.type.name", typeName);

            plainTagList = new TextArea();
            plainTagList.getStyleClass().add("type-entry-plain-tags-textarea");
            plainTab = new Tab("Plain", GUI.borderTopCenter(
                GUI.label("edit.type.plain.tags", "plain-tags-label"),
                plainTagList
            ));
            plainTab.getStyleClass().add("type-entry-tab-plain");

            innerValueTagList = new InnerValueTagList(existing);
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
                    innerValueTagList.resetItems(getPlainTags(plainTagList).<Either<@Localized String, TagType<JellyType>>>map(plain -> {
                        return plain.map(p -> new TagType<JellyType>(p, null));
                    }).collect(Collectors.<Either<@Localized String, TagType<JellyType>>>toList()));
                    crossSetting = false;
                }
            });
            FXUtility.addChangeListenerPlatformNN(innerValueTypeArgs.textProperty(), args -> innerValueChanged());
            
            if (existing != null)
            {
                crossSetting = true;
                typeName.setText(existing.getTaggedTypeName().getRaw());
                plainTagList.setText(existing.getTags().stream().map(t -> t.getName()).collect(Collectors.joining("\n")));
                innerValueTypeArgs.setText(existing.getTypeArguments().stream().map(p -> p.getFirst() == TypeVariableKind.UNIT ? "{" + p.getSecond() + "}" : p.getSecond()).collect(Collectors.joining(", ")));
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
                Either<@Localized String, ImmutableList<String>> plainTagNames = Either.<@Localized String, String, Either<@Localized String, TagType<JellyType>>>mapM(innerValueTagList.getItems(), t -> t.<String>flatMap(v -> v.getInner() == null ? Either.<@NonNull @Localized String, @NonNull String>right(v.getName()) : Either.<@NonNull @Localized String, @NonNull String>left(Utility.universal("err"))));
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
                    return Either.left(Utility.concatLocal(TranslationUtility.getString("type.invalid.identifier"), Utility.userInput(typeName.getText().trim())));
                TypeId typeIdentifierFinal = new TypeId(typeIdentifier);

                if (tabPane.getSelectionModel().getSelectedItem() == plainTab)
                {
                    ImmutableList<Either<@Localized String, @ExpressionIdentifier String>> tags = getPlainTags(plainTagList).collect(ImmutableList.<Either<@Localized String, @ExpressionIdentifier String>>toImmutableList());
                    Either<@Localized String, ImmutableList<@ExpressionIdentifier String>> tagsFlipped = Either.<@Localized String, @ExpressionIdentifier String, Either<@Localized String, @ExpressionIdentifier String>>mapM(tags, (Either<@Localized String, @ExpressionIdentifier String> x) -> x);
                    Either<@Localized String, TaggedTypeDefinition> r = tagsFlipped.mapInt((ImmutableList<@ExpressionIdentifier String> ts) -> new TaggedTypeDefinition(typeIdentifierFinal, ImmutableList.of(), Utility.mapListI(ts, (@ExpressionIdentifier String t) -> new TagType<JellyType>(t, null))));
                    return r;
                }
                else if (tabPane.getSelectionModel().getSelectedItem() == innerValuesTab)
                {
                    String[] typeArgs = innerValueTypeArgs.getText().trim().split("\\s*,\\s*");
                    Either<@Localized String, ImmutableList<Pair<TypeVariableKind, @ExpressionIdentifier String>>> typeArgsOrErr = Either.right(ImmutableList.<Pair<TypeVariableKind, @ExpressionIdentifier String>>of());
                    if (!Arrays.equals(typeArgs, new String[]{""}))
                        typeArgsOrErr = Either.<@Localized String, Pair<TypeVariableKind, @ExpressionIdentifier String>, String>mapM(Arrays.<String>asList(typeArgs), t -> parseTagName(TranslationUtility.getString("type.invalid.argument"), t));
                    
                    return typeArgsOrErr.flatMapInt(args -> Either.<@Localized String, TagType<JellyType>, Either<@Localized String, TagType<JellyType>>>mapM(innerValueTagList.getItems(), (Either<@Localized String, TagType<JellyType>> e) -> e).mapInt(ts -> new TaggedTypeDefinition(typeIdentifierFinal, args, ts)));
                }
                
                // Shouldn't happen:
                return Either.left(Utility.universal("Select a tab in the tab pane"));
            }
            catch (InternalException e)
            {
                Log.log(e);
                return Either.left(e.getLocalizedMessage());
            }
        }

        private Stream<Either<@Localized String, @ExpressionIdentifier String>> getPlainTags(@UnknownInitialization(Object.class) EditTypeDialog this, TextArea plainTagList)
        {
            return Arrays.stream(plainTagList.getText().split(" *[,\n\r] *"))
                .map(s -> s.trim())
                .filter(s -> !s.isEmpty())
                .<Either<@Localized String, @ExpressionIdentifier String>>map(s -> {
                    @Nullable @ExpressionIdentifier String ident = IdentifierUtility.asExpressionIdentifier(s);
                    if (ident == null)
                    {
                        return Either.<@Localized String, @ExpressionIdentifier String>left(
                            Utility.concatLocal(ImmutableList.<@Localized String>of(TranslationUtility.getString("type.invalid.tag.name"),
                                Utility.universal("\""),
                                Utility.userInput(s),
                                Utility.universal("\""))));
                    }
                    else 
                    {
                        return Either.<@Localized String, @ExpressionIdentifier String>right(ident);
                    }
                });
        }

        // Variables surrounded by {} are unit variables.
        private Either<@Localized String, Pair<TypeVariableKind, @ExpressionIdentifier String>> parseTagName(@Localized String errorPrefix, String src)
        {
            TypeVariableKind typeVariableKind = TypeVariableKind.TYPE;
            
            if (src.startsWith("{") && src.endsWith("}"))
            {
                typeVariableKind = TypeVariableKind.UNIT;
                src = src.substring(1, src.length() - 1);
            }
            
            @Nullable @ExpressionIdentifier String identifier = IdentifierUtility.asExpressionIdentifier(src.trim());
            if (identifier == null)
                return Either.<@Localized String, Pair<TypeVariableKind, @ExpressionIdentifier String>>left(Utility.concatLocal(errorPrefix, Utility.userInput("\"" + src.trim() + "\"")));
            else
                return Either.<@Localized String, Pair<TypeVariableKind, @ExpressionIdentifier String>>right(new Pair<>(typeVariableKind, identifier));
        }

        @OnThread(Tag.FXPlatform)
        private class TagValueEdit extends BorderPane
        {
            private final TextField tagName;
            private final TypeEditor innerType;
            private final ObjectProperty<Either<@Localized String, TagType<JellyType>>> currentValue;

            public TagValueEdit(@Nullable TagType<JellyType> initialContent, boolean editImmediately)
            {
                getStyleClass().add("tag-value-edit");
                String initialName = initialContent == null ? "" : initialContent.getName();
                this.currentValue = new SimpleObjectProperty<Either<@Localized String, TagType<JellyType>>>(Either.left(Utility.universal("Loading")));
                this.tagName = new TextField(initialName);
                tagName.setPromptText(TranslationUtility.getString("edit.type.inner.tag.prompt"));
                FXUtility.addChangeListenerPlatformNN(tagName.textProperty(), name -> {
                    updateCurrentValue(null);
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
                this.innerType = new TypeEditor(typeManager, startingExpression, false, true, latest -> {
                    updateCurrentValue(latest);
                });
                //innerType.setPromptText(TranslationUtility.getString("edit.type.tag.inner.prompt"));
                
                setLeft(tagName);
                setCenter(innerType.getContainer());

                if (editImmediately)
                    FXUtility.onceNotNull(tagName.sceneProperty(), s -> tagName.requestFocus());
                
                FXUtility.addChangeListenerPlatformNN(currentValue, v -> {
                    innerValueChanged();
                });
                
                updateCurrentValue(null);
            }

            // null when only name has changed.
            @RequiresNonNull({"tagName", "currentValue"})
            public void updateCurrentValue(@UnknownInitialization(BorderPane.class) TagValueEdit this, @Nullable TypeExpression latest)
            {
                if (innerType == null)
                    return;
                
                if (latest == null)
                    latest = innerType.save(false);
                try
                {
                    @SuppressWarnings("recorded")
                    @Nullable JellyType jellyType = latest.isEmpty() ? null : latest.toJellyType(typeManager, new JellyRecorder()
                    {
                        @SuppressWarnings("recorded")
                        @Override
                        public @Recorded JellyType record(JellyType jellyType, @Recorded TypeExpression source)
                        {
                            return jellyType;
                        }
                    });
                    @ExpressionIdentifier String ident = IdentifierUtility.asExpressionIdentifier(tagName.getText());
                    if (ident != null)
                        currentValue.set(Either.right(new TagType<JellyType>(ident, jellyType)));
                    else
                        currentValue.set(Either.left(
                            Utility.concatLocal(ImmutableList.<@Localized String>of(
                                TranslationUtility.getString("type.invalid.tag.name"),
                                Utility.universal("\""),
                                Utility.userInput(tagName.getText()),
                                Utility.universal("\"")
                        ))));
                }
                catch (InternalException | UserException e)
                {
                    if (e instanceof InternalException)
                        Log.log(e);
                    currentValue.set(Either.left(e.getLocalizedMessage()));
                }
            }
        }

        private class InnerValueTagList extends FancyList<Either<@Localized String, TagType<JellyType>>, TagValueEdit>
        {
            @SuppressWarnings("identifier") // For the blank identifier used as starting argument
            @OnThread(Tag.FXPlatform)
            public InnerValueTagList(@Nullable TaggedTypeDefinition existing)
            {
                super(existing == null ? ImmutableList.<Either<@Localized String, TagType<JellyType>>>of() : Utility.<TagType<JellyType>, Either<@Localized String, TagType<JellyType>>>mapListI(existing.getTags(), x -> Either.<@Localized String, TagType<JellyType>>right(x)), true, true, true);
                listenForCellChange(c -> innerValueChanged());
            }

            @Override
            protected void cleanup(List<Cell> cellsToCleanup)
            {
                for (Cell cell : cellsToCleanup)
                {
                    cell.getContent().innerType.cleanup();
                }
            }

            @SuppressWarnings("prefer.map.and.orelse")
            @Override
            @OnThread(Tag.FXPlatform)
            protected Pair<TagValueEdit, FXPlatformSupplier<Either<@Localized String, TagType<JellyType>>>> makeCellContent(Optional<Either<@Localized String, TagType<JellyType>>> initialContent, boolean editImmediately)
            {
                TagValueEdit tagValueEdit = new TagValueEdit(!initialContent.isPresent() ? null : initialContent.get().<@Nullable TagType<JellyType>>either(s -> null, v -> v), editImmediately);
                return new Pair<>(tagValueEdit, tagValueEdit.currentValue::get);
            }

        }
    }
    
    
}
