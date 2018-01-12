package records.gui;

import com.google.common.collect.ImmutableList;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.binding.ObjectBinding;
import javafx.beans.binding.ObjectExpression;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableObjectValue;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import log.Log;
import org.checkerframework.checker.i18n.qual.LocalizableKey;
import org.checkerframework.checker.initialization.qual.UnderInitialization;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataType.DateTimeInfo.DateTimeType;
import records.data.datatype.NumberInfo;
import records.data.datatype.TaggedTypeDefinition;
import records.data.datatype.TypeId;
import records.data.datatype.TypeManager;
import records.data.unit.Unit;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformRunnable;
import utility.Pair;
import utility.gui.FXUtility;
import utility.gui.GUI;
import utility.gui.TranslationUtility;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;

/**
 * A pane which shows a set of radio button and sub-items which allow you to
 * select a DataType from the full possible range of types.
 */
@OnThread(Tag.FXPlatform)
public class TypeSelectionPane
{
    private final ToggleGroup typeGroup;
    private final VBox contents;
    private final SimpleObjectProperty<@Nullable Optional<DataType>> selectedType = new SimpleObjectProperty<>(Optional.of(DataType.NUMBER));
    // Stored as fields to prevent GC.  We store not because we bind disable to it:
    private final BooleanBinding numberNotSelected, dateNotSelected, taggedNotSelected, tupleNotSelected, listNotSelected;
    private final IdentityHashMap<Toggle, ObservableValue<@Nullable Optional<DataType>>> types = new IdentityHashMap<>();

    public TypeSelectionPane(TypeManager typeManager)
    {
        this(typeManager, false);
    }

    public TypeSelectionPane(TypeManager typeManager, boolean emptyAllowed)
    {
        typeGroup = new ToggleGroup();
        contents = new VBox();
        contents.getStylesheets().add(FXUtility.getStylesheet("type-selection-pane.css"));
        contents.getStyleClass().add("type-selection-pane");

        if (emptyAllowed)
        {
            addType("type.none", new ReadOnlyObjectWrapper<>(Optional.empty()));
        }
        ErrorableTextField<Unit> units = new ErrorableTextField<Unit>(unitSrc ->
            ErrorableTextField.validate(() -> typeManager.getUnitManager().loadUse(unitSrc))
        );
        numberNotSelected = addType("type.number", new NumberTypeBinding(units.valueProperty(), typeManager), new Label(TranslationUtility.getString("newcolumn.number.units")), units.getNode());
        units.getNode().getStyleClass().add("type-number-units");
        units.disableProperty().bind(numberNotSelected);
        addType("type.text", new ReadOnlyObjectWrapper<>(Optional.of(DataType.TEXT)));
        addType("type.boolean", new ReadOnlyObjectWrapper<>(Optional.of(DataType.BOOLEAN)));
        ComboBox<DataType> dateTimeComboBox = new ComboBox<>();
        dateTimeComboBox.getItems().addAll(DataType.date(new DateTimeInfo(DateTimeType.YEARMONTHDAY)));
        dateTimeComboBox.getItems().addAll(DataType.date(new DateTimeInfo(DateTimeType.YEARMONTH)));
        dateTimeComboBox.getItems().addAll(DataType.date(new DateTimeInfo(DateTimeType.TIMEOFDAY)));
        dateTimeComboBox.getItems().addAll(DataType.date(new DateTimeInfo(DateTimeType.DATETIME)));
        dateTimeComboBox.getItems().addAll(DataType.date(new DateTimeInfo(DateTimeType.TIMEOFDAYZONED)));
        dateTimeComboBox.getItems().addAll(DataType.date(new DateTimeInfo(DateTimeType.DATETIMEZONED)));
        dateTimeComboBox.getSelectionModel().selectFirst();
        dateTimeComboBox.getStyleClass().add("type-datetime-combo");
        dateNotSelected = addType("type.datetime", FXUtility.<@Nullable DataType, @Nullable Optional<DataType>>mapBindingEager(dateTimeComboBox.valueProperty(), x -> x == null ? null : Optional.of(x)), dateTimeComboBox);
        dateTimeComboBox.disableProperty().bind(dateNotSelected);

        ComboBox<TaggedTypeDefinition> taggedComboBox = new ComboBox<>();
        taggedComboBox.getStyleClass().add("type-tagged-combo");
        FXPlatformRunnable updateTaggedCombo = () -> {
            for (Entry<TypeId, TaggedTypeDefinition> taggedType : typeManager.getKnownTaggedTypes().entrySet())
            {
                taggedComboBox.getItems().add(taggedType.getValue());
            }
        };
        Button newTaggedTypeButton = GUI.button("type.tagged.new", () -> {
            @Nullable TaggedTypeDefinition newType = new EditTaggedTypeDialog(typeManager).showAndWait().orElse(null);
            updateTaggedCombo.run();
            if (newType != null)
            {
                taggedComboBox.getSelectionModel().select(newType);
            }
        });
        taggedNotSelected = addType("type.tagged", FXUtility.<@Nullable TaggedTypeDefinition, @Nullable Optional<DataType>>mapBindingEager(taggedComboBox.valueProperty(), x -> {
            if (x == null)
            {
                return null;
            }
            else
            {
                try
                {
                    return Optional.of(x.instantiate(ImmutableList.of() /* TODO */));
                }
                catch (InternalException | UserException e)
                {
                    Log.log(e);
                    return null;
                }
            }
        }), taggedComboBox, newTaggedTypeButton);

        updateTaggedCombo.run();
        taggedComboBox.getSelectionModel().selectFirst();
        taggedComboBox.disableProperty().bind(taggedNotSelected);
        newTaggedTypeButton.disableProperty().bind(taggedNotSelected);

        ObservableList<ObservableObjectValue<@Nullable Optional<DataType>>> tupleTypes = FXCollections.observableArrayList();
        ObservableList<Label> commas = FXCollections.observableArrayList();
        FlowPane tupleTypesPane = new FlowPane();
        ObjectProperty<@Nullable Optional<DataType>> tupleType = new SimpleObjectProperty<>(null);
        FXPlatformRunnable recalcTupleType = () -> {
            List<@NonNull DataType> types = new ArrayList<>();
            for (ObservableObjectValue<@Nullable Optional<DataType>> obsType : tupleTypes)
            {
                @Nullable Optional<DataType> opt = obsType.get();
                @Nullable DataType type = opt == null ? null : opt.orElse(null);
                if (type == null)
                {
                    tupleType.setValue(null);
                    return;
                }
                types.add(type);
            }
            tupleType.setValue(Optional.of(DataType.tuple(types)));
        };
        FXUtility.listen(tupleTypes, c -> recalcTupleType.run());
        FXUtility.listen(commas, c -> {
            for (int i = 0; i < commas.size(); i++)
            {
                commas.get(i).setText(i == commas.size() - 1 ? "" : ",");
            }
        });
        tupleNotSelected = addType("type.tuple", tupleType,  tupleTypesPane);


        FXPlatformRunnable addTupleType = () -> {
            Pair<Button, ObservableObjectValue<@Nullable Optional<DataType>>> typeButton = makeTypeButton(typeManager, false);
            typeButton.getFirst().disableProperty().bind(tupleNotSelected);
            // For testing purposes, to identify the different buttons:
            typeButton.getFirst().getStyleClass().add("type-tuple-element-" + tupleTypes.size());
            FXUtility.addChangeListenerPlatform(typeButton.getSecond(), x -> {
                recalcTupleType.run();
            });
            tupleTypes.add(typeButton.getSecond());
            Label comma = new Label(",");
            commas.add(comma);
            tupleTypesPane.getChildren().add(tupleTypesPane.getChildren().size() - 1, new HBox(typeButton.getFirst(), comma));
        };
        FXPlatformRunnable removeTupleType = () -> {
            // Can't make it size < 2:
            if (tupleTypes.size() > 2)
            {
                commas.remove(commas.size() - 1);
                tupleTypes.remove(tupleTypes.size() - 1);
                // Final item is bracket; remove the one before that:
                tupleTypesPane.getChildren().remove(tupleTypesPane.getChildren().size() - 2);
            }
        };

        Button extendTupleButton = GUI.button("type.tuple.more", addTupleType);
        Button shrinkTupleButton = GUI.button("type.tuple.less", removeTupleType);
        extendTupleButton.disableProperty().bind(tupleNotSelected);
        shrinkTupleButton.disableProperty().bind(tupleNotSelected);
        tupleTypesPane.getChildren().setAll(new Label("("), new HBox(new Label(")"), shrinkTupleButton, extendTupleButton));

        // Make it a pair by default:
        addTupleType.run();
        addTupleType.run();


        Pair<Button, ObservableObjectValue<@Nullable Optional<DataType>>> listSubType = makeTypeButton(typeManager, false);
        listSubType.getFirst().getStyleClass().add("type-list-of-set");
        listNotSelected = addType("type.list.of", FXUtility.<@Nullable Optional<DataType>, @Nullable Optional<DataType>>mapBindingEager(listSubType.getSecond(), inner -> inner == null || !inner.isPresent() ? null : Optional.of(DataType.array(inner.get()))), listSubType.getFirst());
        listSubType.getFirst().disableProperty().bind(listNotSelected);

        FXUtility.addChangeListenerPlatformNN(typeGroup.selectedToggleProperty(), toggle -> {
            updateSelectedType();
        });
    }

    public static Pair<Button, ObservableObjectValue<@Nullable Optional<DataType>>> makeTypeButton(TypeManager typeManager, boolean emptyAllowed)
    {
        Button listSubTypeButton = new Button(TranslationUtility.getString("type.select"));
        listSubTypeButton.getStyleClass().add("type-select-button");
        SimpleObjectProperty<@Nullable Optional<DataType>> listSubType = new SimpleObjectProperty<>(emptyAllowed ? Optional.empty() : null);
        listSubTypeButton.setOnAction(e -> {
            Scene scene = listSubTypeButton.getScene();
            @Nullable Optional<DataType> newValue = new TypeDialog(scene == null ? null : scene.getWindow(), typeManager, emptyAllowed).showAndWait().orElse(null);
            // Don't overwrite existing one if they cancelled:
            if (newValue != null)
                listSubType.setValue(newValue);
            @Nullable Optional<DataType> dataType = listSubType.get();
            listSubTypeButton.setText(
                    dataType == null ? TranslationUtility.getString("type.select") :
                    (dataType.isPresent() ? dataType.get().toString() : TranslationUtility.getString("type.none")));
            if (scene != null)
            {
                Window window = scene.getWindow();
                if (window != null)
                    window.sizeToScene();
            }
        });
        return new Pair<>(listSubTypeButton, listSubType);
    }

    @RequiresNonNull({"selectedType", "typeGroup", "types"})
    private void updateSelectedType(@UnderInitialization(Object.class) TypeSelectionPane this)
    {
        // This bit shouldn't be null, but we know what to do in that case: set selectedType to null:
        @Nullable ObservableValue<@Nullable Optional<DataType>> dataTypeObservableValue = types.get(typeGroup.getSelectedToggle());
        selectedType.setValue(dataTypeObservableValue == null ? null : dataTypeObservableValue.getValue());
    }


    /**
     * Adds a type option to the typeGroup toggle group, and the contents VBox.
     *
     * @param typeKey The label-key for the type name.
     * @param calculateType An observable which constructs the full type (or null if there's an error)
     * @param furtherDetails Any nodes which should sit to the right of the radio button
     * @return An observable which is *false* when this type is selected (useful to disable sub-items) and *true* when it is *not* selected
     */
    @RequiresNonNull({"contents", "typeGroup", "types"})
    private BooleanBinding addType(@UnderInitialization(Object.class) TypeSelectionPane this, @LocalizableKey String typeKey, ObservableValue<@Nullable Optional<DataType>> calculateType, Node... furtherDetails)
    {
        RadioButton radioButton = new RadioButton(TranslationUtility.getString(typeKey));
        radioButton.getStyleClass().add("id-" + typeKey.replace(".", "-"));
        radioButton.setToggleGroup(typeGroup);
        HBox hbox = new Row(radioButton);
        hbox.getChildren().addAll(furtherDetails);
        contents.getChildren().add(hbox);
        types.put(radioButton, calculateType);
        FXUtility.addChangeListenerPlatform(calculateType, t -> updateSelectedType());
        // Select first one by default:
        if (typeGroup.getSelectedToggle() == null)
            typeGroup.selectToggle(radioButton);
        return Bindings.notEqual(radioButton, typeGroup.selectedToggleProperty());
    }

    public Node getNode()
    {
        return contents;
    }

    // null means not valid
    // Optional.empty() means that "None" was selected (if permitted: see constructor)
    public ObservableObjectValue<@Nullable Optional<DataType>> selectedType()
    {
        return selectedType;
    }

    private static class NumberTypeBinding extends ObjectBinding<@Nullable Optional<DataType>>
    {
        private final @NonNull ObjectExpression<@Nullable Unit> units;
        private final TypeManager typeManager;

        public NumberTypeBinding(@NonNull ObjectExpression<@Nullable Unit> units, TypeManager typeManager)
        {
            this.units = units;
            this.typeManager = typeManager;
            super.bind(units);
        }

        @Override
        protected @Nullable Optional<DataType> computeValue()
        {
            Unit u = units.get();
            if (u == null)
                return null;
            return Optional.of(DataType.number(new NumberInfo(u, null)));
        }
    }
}
