package records.gui;

import annotation.identifier.qual.UnitIdentifier;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.TableColumn.CellDataFeatures;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
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
import records.gui.lexeditor.UnitEditor;
import records.jellytype.JellyUnit;
import records.transformations.expression.FixHelper;
import records.transformations.expression.UnitExpression;
import records.transformations.expression.UnitExpression.UnitLookupException;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.FXPlatformRunnable;
import utility.IdentifierUtility;
import utility.Pair;
import utility.Utility;
import utility.gui.DialogPaneWithSideButtons;
import utility.gui.ErrorableDialog;
import utility.gui.FXUtility;
import utility.gui.GUI;
import utility.gui.LabelledGrid;
import utility.gui.LabelledGrid.Row;
import utility.TranslationUtility;

import java.util.Comparator;
import java.util.Map.Entry;
import java.util.stream.Collectors;

@OnThread(Tag.FXPlatform)
public class UnitsDialog extends Dialog<Void>
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

        userDeclaredUnitList = new UnitList(unitManager.getAllUserDeclared(), false, () -> FXUtility.mouse(this).editSingleSelectedItem(owner, typeManager));
        userDeclaredUnitList.getStyleClass().add("user-unit-list");
        Button addButton = GUI.button("units.userDeclared.add", () -> {
            FXUtility.mouse(this).addUnit(null, getDialogPane().getScene(), typeManager, owner.getFixHelper(), userDeclaredUnitList);
        });
        Button editButton = GUI.button("units.userDeclared.edit", () -> {
            FXUtility.mouse(this).editSingleSelectedItem(owner, typeManager);
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
            @SuppressWarnings("nullness")
            @NonNull Scene scene = UnitsDialog.this.getDialogPane().getScene();
            Pair<@UnitIdentifier String, Either<@UnitIdentifier String, UnitDeclaration>> edited = new EditUnitDialog(typeManager, prevValue, owner.getFixHelper(), scene).showAndWait().orElse(null);
            if (edited != null)
            {
                unitManager.removeUserUnit(prevValue.getFirst());
                unitManager.addUserUnit(edited);

                userDeclaredUnitList.setUnits(unitManager.getAllUserDeclared());
            }
        }
    }

    private static void addUnit(@Nullable @UnitIdentifier String initialName, @Nullable Scene parentScene, TypeManager typeManager, FixHelper fixHelper, @Nullable UnitList userDeclaredUnitList)
    {
        Pair<@UnitIdentifier String, Either<@UnitIdentifier String, UnitDeclaration>> newUnit = new EditUnitDialog(typeManager, initialName == null ? null : new Pair<>(initialName, Either.<@UnitIdentifier String, UnitDeclaration>right(new UnitDeclaration(new SingleUnit(initialName, "", "", ""), null, ""))), fixHelper, parentScene).showAndWait().orElse(null);
        if (newUnit != null)
        {
            typeManager.getUnitManager().addUserUnit(newUnit);
            if (userDeclaredUnitList != null)
                userDeclaredUnitList.setUnits(typeManager.getUnitManager().getAllUserDeclared());
        }
    }

    public static void addUnit(@UnitIdentifier String initialName, @Nullable Scene parentScene, TypeManager typeManager, FixHelper fixHelper)
    {
        addUnit(initialName, parentScene, typeManager, fixHelper, null);
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

        public EditUnitDialog(TypeManager typeManager, @Nullable Pair<@UnitIdentifier String, Either<@UnitIdentifier String, UnitDeclaration>> initialValue, FixHelper fixHelper, @Nullable Scene parentScene)
        {
            super(new DialogPaneWithSideButtons());
            setTitle(TranslationUtility.getString("units.edit.title"));
            this.unitManager = typeManager.getUnitManager();
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
            definition = new UnitEditor(typeManager, equiv == null ? null : UnitExpression.load(equiv.getSecond()), fixHelper, u -> {});
            //definition.setPromptText(TranslationUtility.getString("unit.base.prompt"));
            Pair<CheckBox, Row> fullDefinition = GUI.tickGridRow("unit.full.definition", "edit-unit/definition", new HBox(scale, new Label(" * "), definition.getContainer()));
            this.equivalentTickBox = fullDefinition.getFirst();
            equivalentTickBox.setSelected(equiv != null);

            Row aliasRadio = GUI.radioGridRow("unit.alias", "edit-unit/alias", toggleGroup);
            aliasTargetField = new TextField(initialValue == null ? "" : initialValue.getSecond().either(s -> s, d -> ""));
            aliasTargetField.setPromptText(TranslationUtility.getString("unit.alias.target.prompt"));
            Row aliasTarget = GUI.labelledGridRow("unit.alias.target", "edit-unit/alias-target", aliasTargetField);
            
            toggleGroup.selectToggle(toggleGroup.getToggles().get(initialValue == null || initialValue.getSecond().isRight() ? 0 : 1));
            
            getDialogPane().setContent(new LabelledGrid(nameRow, fullRadio, fullDescription, fullDefinition.getSecond(), aliasRadio, aliasTarget, new Row(getErrorLabel())));
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
            
            if (toggleGroup.getSelectedToggle() == toggleGroup.getToggles().get(1))
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
                    jellyUnit = definition.save().asUnit(unitManager);
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
