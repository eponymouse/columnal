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

import annotation.identifier.qual.UnitIdentifier;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableColumn.CellDataFeatures;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import xyz.columnal.log.Log;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.sosy_lab.common.rationals.Rational;
import xyz.columnal.data.datatype.TypeManager;
import xyz.columnal.data.unit.SingleUnit;
import xyz.columnal.data.unit.Unit;
import xyz.columnal.data.unit.UnitDeclaration;
import xyz.columnal.data.unit.UnitManager;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.grammar.UnitLexer;
import xyz.columnal.grammar.UnitParser;
import xyz.columnal.grammar.UnitParser.ScaleContext;
import xyz.columnal.gui.lexeditor.UnitEditor;
import xyz.columnal.jellytype.JellyUnit;
import xyz.columnal.transformations.expression.UnitExpression;
import xyz.columnal.transformations.expression.UnitExpression.UnitLookupException;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.function.fx.FXPlatformRunnable;
import xyz.columnal.utility.IdentifierUtility;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.Utility;
import xyz.columnal.utility.gui.AlignedLabels;
import xyz.columnal.utility.gui.DialogPaneWithSideButtons;
import xyz.columnal.utility.gui.DimmableParent;
import xyz.columnal.utility.gui.ErrorableDialog;
import xyz.columnal.utility.gui.FXUtility;
import xyz.columnal.utility.gui.GUI;
import xyz.columnal.utility.gui.LabelledGrid;
import xyz.columnal.utility.gui.LabelledGrid.Row;
import xyz.columnal.utility.TranslationUtility;

import java.util.Comparator;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.stream.Collectors;

@OnThread(Tag.FXPlatform)
public class UnitsDialog extends Dialog<Optional<FXPlatformRunnable>>
{
    private final UnitManager unitManager;
    private final TypeManager typeManager;
    private final UnitList userDeclaredUnitList;
    private final View owner;

    public UnitsDialog(View owner, TypeManager typeManager)
    {
        this.owner = owner;
        this.typeManager = typeManager;
        this.unitManager = typeManager.getUnitManager();
        initModality(Modality.WINDOW_MODAL);
        initOwner(owner.dimWhileShowing(this));
        setTitle(TranslationUtility.getString("units.title"));
        setResizable(true);
        getDialogPane().getStylesheets().addAll(
            FXUtility.getStylesheet("general.css"),
            FXUtility.getStylesheet("dialogs.css")
        );
        setResultConverter(bt -> Optional.<FXPlatformRunnable>empty());

        userDeclaredUnitList = new UnitList(unitManager.getAllUserDeclared(), false, () -> FXUtility.mouse(this).editSingleSelectedItem(owner, typeManager));
        userDeclaredUnitList.getStyleClass().add("user-unit-list");
        Button addButton = GUI.button("units.userDeclared.add", () -> {
            setResult(Optional.of(() -> FXUtility.mouse(this).addUnit(null, owner, typeManager, userDeclaredUnitList)));
            close();
        });
        Button editButton = GUI.button("units.userDeclared.edit", () -> {
            setResult(Optional.of(() -> FXUtility.mouse(this).editSingleSelectedItem(owner, typeManager)));
            close();
        });
        Button removeButton = GUI.button("units.userDeclared.remove", () -> {
            for (Pair<String, Either<String, UnitDeclaration>> unit : userDeclaredUnitList.getSelectionModel().getSelectedItems())
            {
                unitManager.removeUserUnit(unit.getFirst());
            }
            userDeclaredUnitList.setUnits(unitManager.getAllUserDeclared());
        });
        FXUtility.addChangeListenerPlatformAndCallNow(userDeclaredUnitList.getSelectionModel().selectedItemProperty(), c -> {
            editButton.setDisable(c == null);
            removeButton.setDisable(c == null);
        });


        BorderPane buttons = GUI.borderLeftCenterRight(addButton, editButton, removeButton);
        BorderPane userDeclaredUnitPane = GUI.borderTopCenterBottom(GUI.label("units.userDeclared"), userDeclaredUnitList, buttons, "units-dialog-user-defined");
        
        
        UnitList builtInUnitList = new UnitList(unitManager.getAllBuiltIn(), true, null);
        builtInUnitList.setEditable(false);
        Label builtInLabel = GUI.label("units.builtIn");
        BorderPane builtInUnitPane = GUI.borderTopCenter(builtInLabel, builtInUnitList, "units-dialog-built-in");
        BorderPane.setMargin(builtInUnitList, new Insets(10, 0, 10, 0));
        BorderPane.setMargin(userDeclaredUnitList, new Insets(10, 0, 10, 0));
        BorderPane.setMargin(buttons, new Insets(0, 0, 10, 0));
        BorderPane.setMargin(builtInLabel, new Insets(10, 0, 0, 0));

        SplitPane content = GUI.splitPaneVert(
                userDeclaredUnitPane,
                builtInUnitPane,
                "units-dialog-content");
        
        content.setPrefWidth(550.0);
        content.setPrefHeight(700.0);
        
        getDialogPane().setContent(content);
        getDialogPane().getStyleClass().add("units-dialog");
        
        getDialogPane().getButtonTypes().setAll(ButtonType.CLOSE);
        getDialogPane().lookupButton(ButtonType.CLOSE).getStyleClass().add("close-button");
    }

    private void editSingleSelectedItem(View owner, TypeManager typeManager)
    {
        if (userDeclaredUnitList.getSelectionModel().getSelectedItems().size() == 1)
        {
            Pair<@UnitIdentifier String, Either<@UnitIdentifier String, UnitDeclaration>> prevValue = userDeclaredUnitList.getSelectionModel().getSelectedItems().get(0);
            Pair<@UnitIdentifier String, Either<@UnitIdentifier String, UnitDeclaration>> edited = new EditUnitDialog(typeManager, prevValue, owner).showAndWait().orElse(null);
            if (edited != null)
            {
                unitManager.removeUserUnit(prevValue.getFirst());
                unitManager.addUserUnit(edited);

                userDeclaredUnitList.setUnits(unitManager.getAllUserDeclared());
            }
        }
    }

    private static void addUnit(@Nullable @UnitIdentifier String initialName, DimmableParent parent, TypeManager typeManager, @Nullable UnitList userDeclaredUnitList)
    {
        Pair<@UnitIdentifier String, Either<@UnitIdentifier String, UnitDeclaration>> newUnit = new EditUnitDialog(typeManager, initialName == null ? null : new Pair<>(initialName, Either.<@UnitIdentifier String, UnitDeclaration>right(new UnitDeclaration(new SingleUnit(initialName, "", "", ""), null, ""))), parent).showAndWait().orElse(null);
        if (newUnit != null)
        {
            typeManager.getUnitManager().addUserUnit(newUnit);
            if (userDeclaredUnitList != null)
                userDeclaredUnitList.setUnits(typeManager.getUnitManager().getAllUserDeclared());
        }
    }

    public void showAndWaitNested()
    {
        showAndWait().flatMap(x -> x).ifPresent(nested -> {
            nested.run();
            showAndWaitNested();
        });
    }

    private final class UnitList extends TableView<Pair<@UnitIdentifier String, Either<@UnitIdentifier String, UnitDeclaration>>>
    {

        public UnitList(ImmutableMap<@UnitIdentifier String, Either<@UnitIdentifier String, UnitDeclaration>> units, boolean showCategory, @Nullable FXPlatformRunnable editSingleSelectedItem)
        {
            TableColumn<Pair<@UnitIdentifier String, Either<@UnitIdentifier String, UnitDeclaration>>, String> nameColumn = new TableColumn<>("Name");
            nameColumn.setCellValueFactory((CellDataFeatures<Pair<@UnitIdentifier String, Either<@UnitIdentifier String, UnitDeclaration>>, String> cdf) -> new ReadOnlyStringWrapper(cdf.getValue().getFirst()));
            TableColumn<Pair<@UnitIdentifier String, Either<@UnitIdentifier String, UnitDeclaration>>, String> definitionColumn = new TableColumn<>("Definition");
            definitionColumn.setCellValueFactory((CellDataFeatures<Pair<@UnitIdentifier String, Either<@UnitIdentifier String, UnitDeclaration>>, String> cdf) -> new ReadOnlyStringWrapper(cdf.getValue().getSecond().either(u -> u, d -> {
                @Nullable Pair<Rational, Unit> equivalentTo = d.getEquivalentTo();
                if (equivalentTo != null)
                    return equivalentTo.getFirst() + " * " + equivalentTo.getSecond();
                else
                    return "";
            })));
            TableColumn<Pair<@UnitIdentifier String, Either<@UnitIdentifier String, UnitDeclaration>>, String> descriptionColumn = new TableColumn<>("Description");
            descriptionColumn.setCellValueFactory((CellDataFeatures<Pair<@UnitIdentifier String, Either<@UnitIdentifier String, UnitDeclaration>>, String> cdf) -> new ReadOnlyStringWrapper(cdf.getValue().getSecond().either(u -> "", d -> d.getDefined().getDescription())));
            if (showCategory)
            {
                TableColumn<Pair<@UnitIdentifier String, Either<@UnitIdentifier String, UnitDeclaration>>, String> categoryColumn = new TableColumn<>("Category");
                categoryColumn.setCellValueFactory((CellDataFeatures<Pair<@UnitIdentifier String, Either<@UnitIdentifier String, UnitDeclaration>>, String> cdf) -> new ReadOnlyStringWrapper(getCategory(cdf.getValue())));
                getColumns().add(categoryColumn);
            }
            getColumns().add(nameColumn);
            getColumns().add(definitionColumn);
            getColumns().add(descriptionColumn);
            
            setOnMouseClicked(e -> {
                if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() >= 2 && editSingleSelectedItem != null)
                {
                    editSingleSelectedItem.run();
                }
            });
            
            // Safe at end of constructor:
            setUnits(units);
        }

        private String getCategory(@UnknownInitialization(Object.class) UnitList this, Pair<@UnitIdentifier String, Either<@UnitIdentifier String, UnitDeclaration>> pair)
        {
            return pair.getSecond().either(alias -> "", decl -> decl.getCategory());
        }

        public void setUnits(ImmutableMap<@UnitIdentifier String, Either<@UnitIdentifier String, UnitDeclaration>> units)
        {
            getItems().setAll(units.entrySet().stream()
                    .<Pair<@UnitIdentifier String, Either<@UnitIdentifier String, UnitDeclaration>>>map((Entry<@UnitIdentifier String, Either<@UnitIdentifier String, UnitDeclaration>> e) -> new Pair<@UnitIdentifier String, Either<@UnitIdentifier String, UnitDeclaration>>(e.getKey(), e.getValue()))
                    .sorted(Comparator.<Pair<@UnitIdentifier String, Either<@UnitIdentifier String, UnitDeclaration>>, String>comparing((Pair<@UnitIdentifier String, Either<@UnitIdentifier String, UnitDeclaration>> p) -> {
                        @Nullable ImmutableSet<String> canonicalBaseUnit = unitManager.getCanonicalBaseUnit(p.getFirst());
                        if (canonicalBaseUnit == null)
                            return getCategory(p);
                        // Make canonical units be without suffix so they get sorted ahead of their derivatives:
                        if (canonicalBaseUnit.contains(p.getFirst()) && canonicalBaseUnit.size() == 1)
                            return getCategory(p) + ":" + p.getFirst();
                        return getCategory(p) + ":" + canonicalBaseUnit.stream().sorted().collect(Collectors.joining(":")) + ";";
                    }))
                    .collect(Collectors.<Pair<@UnitIdentifier String, Either<@UnitIdentifier String, UnitDeclaration>>>toList()));
        }
    }

    // Gives back the name of the defined unit, and either a unit alias or a 
    // definition of the unit
    @OnThread(Tag.FXPlatform)
    private static class EditUnitDialog extends ErrorableDialog<Pair<@UnitIdentifier String, Either<@UnitIdentifier String, UnitDeclaration>>>
    {
        private final UnitManager unitManager;
        private final TextField unitNameField;
        private final ToggleGroup toggleGroup;
        private final TextField aliasTargetField;
        private final TextField descriptionField;
        private final UnitEditor definition;
        private final TextField scale;
        private final CheckBox equivalentTickBox;

        public EditUnitDialog(TypeManager typeManager, @Nullable Pair<@UnitIdentifier String, Either<@UnitIdentifier String, UnitDeclaration>> initialValue, DimmableParent parent)
        {
            super(new DialogPaneWithSideButtons());
            setTitle(TranslationUtility.getString("units.edit.title"));
            this.unitManager = typeManager.getUnitManager();
            initOwner(parent.dimWhileShowing(this));
            initModality(Modality.WINDOW_MODAL);
            
            AlignedLabels alignedLabels = new AlignedLabels(Pos.CENTER_LEFT);
            unitNameField = new TextField(initialValue == null ? "" : initialValue.getFirst());
            Row nameRow = LabelledGrid.labelledGridRow(alignedLabels, "unit.name", "edit-unit/name", unitNameField, "name-label");
            nameRow.setLabelHAlignment(HPos.LEFT);
            Label explanation = GUI.label("unit.edit.explanation");
            
            
            toggleGroup = new ToggleGroup();
            
            Row fullRadio = LabelledGrid.radioGridRow("unit.full", "edit-unit/full", toggleGroup);
            descriptionField = new TextField(initialValue == null ? "" : initialValue.getSecond().either(a -> "", d -> d.getDefined().getDescription()));
            Row fullDescription = LabelledGrid.labelledGridRow(alignedLabels, "unit.full.description", "edit-unit/description", descriptionField);
            @Nullable Pair<Rational, Unit> equiv = initialValue == null ? null : initialValue.getSecond().<@Nullable Pair<Rational, Unit>>either(a -> null, d -> d.getEquivalentTo());
            scale = new TextField(equiv == null ? "1" : equiv.getFirst().toString());
            definition = new UnitEditor(typeManager, equiv == null ? null : UnitExpression.load(equiv.getSecond()), u -> {});
            Pair<CheckBox, Row> fullDefinition = LabelledGrid.tickGridRow("unit.full.definition", "edit-unit/definition", new HBox(scale, new Label(" * "), definition.getContainer()));
            this.equivalentTickBox = fullDefinition.getFirst();
            equivalentTickBox.setSelected(equiv != null);
            LabelledGrid fullDetailGrid = new LabelledGrid(fullDescription, fullDefinition.getSecond());
            fullDetailGrid.getStyleClass().add("full-detail-grid");
            GridPane.setMargin(fullDetailGrid, new Insets(0, 0, 0, 40));
            LabelledGrid topRadio = new LabelledGrid(fullRadio, LabelledGrid.fullWidthRow(fullDetailGrid));
            GridPane.setMargin(topRadio, new Insets(0, 0, 0, 40));

            Row aliasRadio = LabelledGrid.radioGridRow("unit.alias", "edit-unit/alias", toggleGroup);
            aliasTargetField = new TextField(initialValue == null ? "" : initialValue.getSecond().either(s -> s, d -> ""));
            Row aliasTarget = LabelledGrid.labelledGridRow(alignedLabels, "unit.alias.target", "edit-unit/alias-target", aliasTargetField);
            
            if (initialValue != null)
                toggleGroup.selectToggle(toggleGroup.getToggles().get(initialValue.getSecond().isRight() ? 0 : 1));
            // Otherwise, if new unit, leave it unselected.
            
            LabelledGrid aliasDetailGrid = new LabelledGrid(aliasTarget);
            aliasDetailGrid.getStyleClass().add("alias-detail-grid");
            GridPane.setMargin(aliasDetailGrid, new Insets(0, 0, 0, 40));
            LabelledGrid bottomRadio = new LabelledGrid(aliasRadio, LabelledGrid.fullWidthRow(aliasDetailGrid));
            GridPane.setMargin(bottomRadio, new Insets(0, 0, 0, 40));

            FXUtility.addChangeListenerPlatformNNAndCallNow(toggleGroup.selectedToggleProperty(), toggle -> {
                FXUtility.setPseudoclass(fullDetailGrid, "selected", toggle == toggleGroup.getToggles().get(0));
                FXUtility.setPseudoclass(aliasDetailGrid, "selected", toggle == toggleGroup.getToggles().get(1));
            });
            
            getDialogPane().setContent(new LabelledGrid(nameRow, LabelledGrid.fullWidthRow(explanation), LabelledGrid.fullWidthRow(topRadio), LabelledGrid.fullWidthRow(bottomRadio), LabelledGrid.fullWidthRow(getErrorLabel())));
            getDialogPane().getStyleClass().add("edit-unit-dialog");

            FXUtility.addChangeListenerPlatformNN(toggleGroup.selectedToggleProperty(), t -> {
                updateEnabledState();
            });
            FXUtility.addChangeListenerPlatformNN(equivalentTickBox.selectedProperty(), s -> {
                updateEnabledState();
            });
            
            // Okay because it's the end of the constructor:
            updateEnabledState();
            
            setOnHiding(e -> {
                definition.cleanup();
            });
            setOnShown(e -> {
                FXUtility.runAfter(() -> unitNameField.requestFocus());
            });
        }

        private void updateEnabledState(@UnknownInitialization(EditUnitDialog.class) EditUnitDialog this)
        {
            boolean fullEnabled = toggleGroup.getSelectedToggle() == toggleGroup.getToggles().get(0);
            boolean aliasEnabled = !fullEnabled;
            boolean definedAsEnabled = equivalentTickBox.isSelected() && fullEnabled;
            
            descriptionField.setDisable(!fullEnabled);
            equivalentTickBox.setDisable(!fullEnabled);
            scale.setDisable(!definedAsEnabled);
            definition.setDisable(!definedAsEnabled);
            aliasTargetField.setDisable(!aliasEnabled);
        }

        @Override
        protected @OnThread(Tag.FXPlatform) Either<@Localized String, Pair<@UnitIdentifier String, Either<@UnitIdentifier String, UnitDeclaration>>> calculateResult()
        {
            @UnitIdentifier String name = IdentifierUtility.asUnitIdentifier(unitNameField.getText().trim());
            if (name == null)
                return Either.left(TranslationUtility.getString("invalid.name"));
            
            if (toggleGroup.getSelectedToggle() == null)
            {
                return Either.left(TranslationUtility.getString("invalid.unit.select.toggle"));
            }
            else if (toggleGroup.getSelectedToggle() == toggleGroup.getToggles().get(1))
            {
                @UnitIdentifier String aliasTarget = IdentifierUtility.asUnitIdentifier(aliasTargetField.getText().trim());
                if (aliasTarget == null)
                    return Either.left(TranslationUtility.getString("invalid.alias.target.name"));
                
                return Either.right(new Pair<@UnitIdentifier String, Either<@UnitIdentifier String, UnitDeclaration>>(name, Either.left(aliasTarget)));
            }
            else
            {
                SingleUnit singleUnit = new SingleUnit(name, descriptionField.getText().trim(), "", "");

                if (this.equivalentTickBox.isSelected() == false)
                {
                    return Either.right(new Pair<@UnitIdentifier String, Either<@UnitIdentifier String, UnitDeclaration>>(name, Either.right(new UnitDeclaration(singleUnit, null, ""))));
                }
                
                ScaleContext scaleContext;
                
                try
                {
                    scaleContext = Utility.parseAsOne(scale.getText().trim(), UnitLexer::new, UnitParser::new, p -> p.fullScale().scale());
                }
                catch (InternalException | UserException e)
                {
                    return Either.<@Localized String, Pair<@UnitIdentifier String, Either<@UnitIdentifier String, UnitDeclaration>>>left(Utility.concatLocal(TranslationUtility.getString("invalid.scale"),  e.getLocalizedMessage()));
                }

                JellyUnit jellyUnit;
                try
                {
                    jellyUnit = definition.save(false).asUnit(unitManager);
                }
                catch (UnitLookupException e)
                {
                    return Either.left(e.errorMessage == null ? TranslationUtility.getString("invalid.unit") : e.errorMessage.toPlain());
                }
                @Nullable Unit concreteUnit = null;
                try
                {
                    concreteUnit = jellyUnit.makeUnit(ImmutableMap.of());
                }
                catch (InternalException e)
                {
                    Log.log(e);
                    return Either.left(e.getLocalizedMessage());
                }
                if (concreteUnit == null)
                    return Either.left(TranslationUtility.getString("invalid.unit.contains.vars"));
                
                @Nullable Pair<Rational, Unit> equiv = new Pair<>(UnitManager.loadScale(scaleContext), concreteUnit);

                return Either.<@Localized String, Pair<@UnitIdentifier String, Either<@UnitIdentifier String, UnitDeclaration>>>right(new Pair<>(name, Either.<@UnitIdentifier String, UnitDeclaration>right(new UnitDeclaration(singleUnit, equiv, ""))));
            }
        }
    }
}
