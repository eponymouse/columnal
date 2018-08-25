package records.gui;

import annotation.identifier.qual.UnitIdentifier;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.TableColumn.CellDataFeatures;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import javafx.stage.Window;
import log.Log;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.sosy_lab.common.rationals.Rational;
import records.data.datatype.TypeManager;
import records.data.unit.SingleUnit;
import records.data.unit.Unit;
import records.data.unit.UnitDeclaration;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.grammar.UnitLexer;
import records.grammar.UnitParser;
import records.grammar.UnitParser.ScaleContext;
import records.gui.expressioneditor.UnitEditor;
import records.jellytype.JellyUnit;
import records.transformations.expression.UnitExpression;
import records.typeExp.units.UnitExp;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.IdentifierUtility;
import utility.Pair;
import utility.Utility;
import utility.gui.DialogPaneWithSideButtons;
import utility.gui.ErrorableDialog;
import utility.gui.FXUtility;
import utility.gui.GUI;
import utility.gui.LabelledGrid;
import utility.gui.LabelledGrid.Row;
import utility.gui.TranslationUtility;

import java.util.Comparator;
import java.util.List;
import java.util.Map.Entry;
import java.util.stream.Collectors;

@OnThread(Tag.FXPlatform)
public class UnitsDialog extends Dialog<Void>
{
    private final UnitManager unitManager;
    private final TypeManager typeManager;

    public UnitsDialog(Window owner, TypeManager typeManager)
    {
        this.typeManager = typeManager;
        this.unitManager = typeManager.getUnitManager();
        initModality(Modality.WINDOW_MODAL);
        initOwner(owner);
        setResizable(true);
        getDialogPane().getStylesheets().addAll(
            FXUtility.getStylesheet("general.css"),
            FXUtility.getStylesheet("dialogs.css")
        );

        UnitList userDeclaredUnitList = new UnitList(unitManager.getAllUserDeclared());
        userDeclaredUnitList.getStyleClass().add("user-unit-list");
        Button addButton = GUI.button("units.userDeclared.add", () -> {
            Pair<@UnitIdentifier String, Either<@UnitIdentifier String, UnitDeclaration>> newUnit = new EditUnitDialog(null).showAndWait().orElse(null);
            if (newUnit != null)
            {
                unitManager.addUserUnit(newUnit);
                userDeclaredUnitList.setUnits(unitManager.getAllUserDeclared());
            }
        });
        Button editButton = GUI.button("units.userDeclared.edit", () -> {
            if (userDeclaredUnitList.getSelectionModel().getSelectedItems().size() == 1)
            {
                Pair<@UnitIdentifier String, Either<@UnitIdentifier String, UnitDeclaration>> prevValue = userDeclaredUnitList.getSelectionModel().getSelectedItems().get(0);
                Pair<@UnitIdentifier String, Either<@UnitIdentifier String, UnitDeclaration>> edited = new EditUnitDialog(prevValue).showAndWait().orElse(null);
                if (edited != null)
                {
                    unitManager.removeUserUnit(prevValue.getFirst());
                    unitManager.addUserUnit(edited);

                    userDeclaredUnitList.setUnits(unitManager.getAllUserDeclared());
                }
            }
        });
        Button removeButton = GUI.button("units.userDeclared.remove", () -> {
            for (Pair<String, Either<String, UnitDeclaration>> unit : userDeclaredUnitList.getSelectionModel().getSelectedItems())
            {
                unitManager.removeUserUnit(unit.getFirst());
            }
            userDeclaredUnitList.setUnits(unitManager.getAllUserDeclared());
        });
        FXUtility.listen(userDeclaredUnitList.getSelectionModel().getSelectedItems(), c -> {
            editButton.setDisable(c.getList().size() != 1);
            removeButton.setDisable(c.getList().isEmpty());
        });


        BorderPane buttons = GUI.borderLeftCenterRight(addButton, editButton, removeButton);
        BorderPane userDeclaredUnitPane = GUI.borderTopCenterBottom(GUI.label("units.userDeclared"), userDeclaredUnitList, buttons, "units-dialog-user-defined");
        
        
        UnitList builtInUnitList = new UnitList(unitManager.getAllBuiltIn());
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
        
        content.setPrefWidth(400.0);
        content.setPrefHeight(700.0);
        
        getDialogPane().setContent(content);
        
        getDialogPane().getButtonTypes().setAll(ButtonType.CLOSE);
        getDialogPane().lookupButton(ButtonType.CLOSE).getStyleClass().add("close-button");
    }
    
    private class UnitList extends TableView<Pair<@UnitIdentifier String, Either<@UnitIdentifier String, UnitDeclaration>>>
    {

        public UnitList(ImmutableMap<@UnitIdentifier String, Either<@UnitIdentifier String, UnitDeclaration>> units)
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
            getColumns().add(nameColumn);
            getColumns().add(definitionColumn);
            getColumns().add(descriptionColumn);
            
            // Safe at end of constructor:
            Utility.later(this).setUnits(units);
        }

        public void setUnits(ImmutableMap<@UnitIdentifier String, Either<@UnitIdentifier String, UnitDeclaration>> units)
        {
            getItems().setAll(units.entrySet().stream()
                    .<Pair<@UnitIdentifier String, Either<@UnitIdentifier String, UnitDeclaration>>>map((Entry<@UnitIdentifier String, Either<@UnitIdentifier String, UnitDeclaration>> e) -> new Pair<@UnitIdentifier String, Either<@UnitIdentifier String, UnitDeclaration>>(e.getKey(), e.getValue()))
                    .sorted(Comparator.<Pair<@UnitIdentifier String, Either<@UnitIdentifier String, UnitDeclaration>>, String>comparing((Pair<@UnitIdentifier String, Either<@UnitIdentifier String, UnitDeclaration>> p) -> {
                        @Nullable ImmutableSet<String> canonicalBaseUnit = unitManager.getCanonicalBaseUnit(p.getFirst());
                        if (canonicalBaseUnit == null)
                            return "";
                        // Make canonical units be without suffix so they get sorted ahead of their derivatives:
                        if (canonicalBaseUnit.contains(p.getFirst()) && canonicalBaseUnit.size() == 1)
                            return p.getFirst();
                        return canonicalBaseUnit.stream().sorted().collect(Collectors.joining(":")) + ";";
                    }))
                    .collect(Collectors.toList()));
        }
    }

    // Gives back the name of the defined unit, and either a unit alias or a 
    // definition of the unit
    @OnThread(Tag.FXPlatform)
    private class EditUnitDialog extends ErrorableDialog<Pair<@UnitIdentifier String, Either<@UnitIdentifier String, UnitDeclaration>>>
    {

        private final TextField unitNameField;
        private final ToggleGroup toggleGroup;
        private final TextField aliasTargetField;
        private final TextField descriptionField;
        private final UnitEditor definition;
        private final TextField scale;
        private final CheckBox equivalentTickBox;

        public EditUnitDialog(@Nullable Pair<@UnitIdentifier String, Either<@UnitIdentifier String, UnitDeclaration>> initialValue)
        {
            super(new DialogPaneWithSideButtons());
            Scene parentScene = UnitsDialog.this.getDialogPane().getScene();
            if (parentScene != null && parentScene.getWindow() != null)
                initOwner(parentScene.getWindow());
            initModality(Modality.WINDOW_MODAL);
            getDialogPane().getStylesheets().addAll(
                    FXUtility.getStylesheet("general.css"),
                    FXUtility.getStylesheet("dialogs.css")
            );
            
            unitNameField = new TextField(initialValue == null ? "" : initialValue.getFirst());
            unitNameField.setPromptText(TranslationUtility.getString("unit.name.prompt"));
            Row nameRow = GUI.labelledGridRow("unit.name", "edit-unit/name", unitNameField);
            toggleGroup = new ToggleGroup();
            
            Row fullRadio = GUI.radioGridRow("unit.full", "edit-unit/full", toggleGroup);
            descriptionField = new TextField(initialValue == null ? "" : initialValue.getSecond().either(a -> "", d -> d.getDefined().getDescription()));
            descriptionField.setPromptText(TranslationUtility.getString("unit.full.description.prompt"));
            Row fullDescription = GUI.labelledGridRow("unit.full.description", "edit-unit/description", descriptionField);
            @Nullable Pair<Rational, Unit> equiv = initialValue == null ? null : initialValue.getSecond().<@Nullable Pair<Rational, Unit>>either(a -> null, d -> d.getEquivalentTo());
            scale = new TextField(equiv == null ? "" : equiv.getFirst().toString());
            scale.setPromptText(TranslationUtility.getString("unit.scale.prompt"));
            definition = new UnitEditor(typeManager, equiv == null ? null : UnitExpression.load(equiv.getSecond()), u -> {});
            definition.setPromptText(TranslationUtility.getString("unit.base.prompt"));
            Pair<CheckBox, Row> fullDefinition = GUI.tickGridRow("unit.full.definition", "edit-unit/definition", new HBox(scale, new Label(" * "), definition.getContainer()));
            this.equivalentTickBox = fullDefinition.getFirst();
            equivalentTickBox.setSelected(equiv != null);

            Row aliasRadio = GUI.radioGridRow("unit.alias", "edit-unit/alias", toggleGroup);
            aliasTargetField = new TextField(initialValue == null ? "" : initialValue.getSecond().either(s -> s, d -> ""));
            aliasTargetField.setPromptText(TranslationUtility.getString("unit.alias.target.prompt"));
            Row aliasTarget = GUI.labelledGridRow("unit.alias.target", "edit-unit/alias-target", aliasTargetField);
            
            toggleGroup.selectToggle(toggleGroup.getToggles().get(initialValue == null || initialValue.getSecond().isRight() ? 0 : 1));
            
            getDialogPane().setContent(new LabelledGrid(nameRow, fullRadio, fullDescription, fullDefinition.getSecond(), aliasRadio, aliasTarget, new Row(getErrorLabel())));

            FXUtility.addChangeListenerPlatformNN(toggleGroup.selectedToggleProperty(), t -> {
                updateEnabledState();
            });
            FXUtility.addChangeListenerPlatformNN(equivalentTickBox.selectedProperty(), s -> {
                updateEnabledState();
            });
            
            // Okay because it's the end of the constructor:
            updateEnabledState();
            
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
                return Either.left("Invalid name.  Names must be only letters, optionally with single underscores in the middle.");
            
            if (toggleGroup.getSelectedToggle() == toggleGroup.getToggles().get(1))
            {
                @UnitIdentifier String aliasTarget = IdentifierUtility.asUnitIdentifier(aliasTargetField.getText().trim());
                if (aliasTarget == null)
                    return Either.left("Invalid alias target name.  Names must be only letters, optionally with single underscores in the middle.");
                
                return Either.right(new Pair<@UnitIdentifier String, Either<@UnitIdentifier String, UnitDeclaration>>(name, Either.left(aliasTarget)));
            }
            else
            {
                SingleUnit singleUnit = new SingleUnit(name, descriptionField.getText().trim(), "", "");

                if (this.equivalentTickBox.isSelected() == false)
                {
                    return Either.right(new Pair<@UnitIdentifier String, Either<@UnitIdentifier String, UnitDeclaration>>(name, Either.right(new UnitDeclaration(singleUnit, null))));
                }
                
                ScaleContext scaleContext;
                
                try
                {
                    scaleContext = Utility.parseAsOne(scale.getText().trim(), UnitLexer::new, UnitParser::new, p -> p.fullScale().scale());
                }
                catch (InternalException | UserException e)
                {
                    return Either.left("Invalid scale: " + e.getLocalizedMessage());
                }

                Either<Pair<StyledString, List<UnitExpression>>, JellyUnit> unitExpOrError = definition.save().asUnit(unitManager);
                @NonNull @UnitIdentifier String nameFinal = name;
                return unitExpOrError.<Either<@Localized String, Pair<@UnitIdentifier String, Either<@UnitIdentifier String, UnitDeclaration>>>>either(err -> {
                    return Either.left(err.getFirst().toPlain());
                }, jellyUnit -> {
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
                        return Either.left("Invalid unit (cannot contain unit variables)");
                    
                    @Nullable Pair<Rational, Unit> equiv = new Pair<>(UnitManager.loadScale(scaleContext), concreteUnit);

                    return Either.<@Localized String, Pair<@UnitIdentifier String, Either<@UnitIdentifier String, UnitDeclaration>>>right(new Pair<>(nameFinal, Either.<@UnitIdentifier String, UnitDeclaration>right(new UnitDeclaration(singleUnit, equiv))));
                });
            }
        }
    }
}
