package records.gui;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.binding.ObjectBinding;
import javafx.beans.binding.ObjectExpression;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableObjectValue;
import javafx.beans.value.ObservableValue;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.checkerframework.checker.i18n.qual.LocalizableKey;
import org.checkerframework.checker.initialization.qual.UnderInitialization;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataType.DateTimeInfo.DateTimeType;
import records.data.datatype.DataType.NumberInfo;
import records.data.datatype.TypeId;
import records.data.datatype.TypeManager;
import records.data.unit.Unit;
import records.transformations.TransformationEditor;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Utility;

import java.util.IdentityHashMap;
import java.util.Map.Entry;

/**
 * Created by neil on 24/03/2017.
 */
@OnThread(Tag.FXPlatform)
public class TypeSelectionPane
{
    private final ToggleGroup typeGroup;
    private final VBox contents;
    private final SimpleObjectProperty<@Nullable DataType> selectedType = new SimpleObjectProperty<>(DataType.NUMBER);
    // Stored as fields to prevent GC:
    private final BooleanBinding numberSelected, dateSelected, listSelected;
    private final IdentityHashMap<Toggle, ObservableValue<@Nullable DataType>> types = new IdentityHashMap<>();

    public TypeSelectionPane(TypeManager typeManager)
    {
        typeGroup = new ToggleGroup();
        contents = new VBox();

        ErrorableTextField<Unit> units = new ErrorableTextField<Unit>(unitSrc ->
            ErrorableTextField.validate(() -> typeManager.getUnitManager().loadUse(unitSrc))
        );
        numberSelected = addType("type.number", new NumberTypeBinding(units.valueProperty(), typeManager), new Label(TransformationEditor.getString("newcolumn.number.units")), units.getNode());
        units.disableProperty().bind(numberSelected.not());
        addType("type.text", new ReadOnlyObjectWrapper<>(DataType.TEXT));
        addType("type.boolean", new ReadOnlyObjectWrapper<>(DataType.BOOLEAN));
        ComboBox<DataType> dateTimeComboBox = new ComboBox<>();
        dateTimeComboBox.getItems().addAll(DataType.date(new DateTimeInfo(DateTimeType.YEARMONTHDAY)));
        dateTimeComboBox.getItems().addAll(DataType.date(new DateTimeInfo(DateTimeType.YEARMONTH)));
        dateTimeComboBox.getItems().addAll(DataType.date(new DateTimeInfo(DateTimeType.TIMEOFDAY)));
        dateTimeComboBox.getItems().addAll(DataType.date(new DateTimeInfo(DateTimeType.DATETIME)));
        dateTimeComboBox.getItems().addAll(DataType.date(new DateTimeInfo(DateTimeType.TIMEOFDAYZONED)));
        dateTimeComboBox.getItems().addAll(DataType.date(new DateTimeInfo(DateTimeType.DATETIMEZONED)));
        dateTimeComboBox.getSelectionModel().selectFirst();
        dateSelected = addType("type.datetime", dateTimeComboBox.valueProperty(), dateTimeComboBox);
        dateTimeComboBox.disableProperty().bind(dateSelected.not());

        ComboBox<DataType> taggedComboBox = new ComboBox<>();
        Button newTaggedTypeButton = new Button(TransformationEditor.getString("type.tagged.new"));
        // TODO wire this up to show a new tagged type dialog
        addType("type.tagged", taggedComboBox.valueProperty(), taggedComboBox, newTaggedTypeButton);
        for (Entry<TypeId, DataType> taggedType : typeManager.getKnownTaggedTypes().entrySet())
        {
            taggedComboBox.getItems().add(taggedType.getValue());
        }
        taggedComboBox.getSelectionModel().selectFirst();

        Button listSubTypeButton = new Button(TransformationEditor.getString("type.select"));
        SimpleObjectProperty<@Nullable DataType> listSubType = new SimpleObjectProperty<>(null);
        listSubTypeButton.setOnAction(e -> {
            Scene scene = contents.getScene();
            @Nullable DataType newValue = new TypeDialog(scene == null ? null : scene.getWindow(), typeManager).showAndWait().orElse(null);
            // Don't overwrite existing one if they cancelled:
            if (newValue != null)
                listSubType.setValue(newValue);
            @Nullable DataType dataType = listSubType.get();
            listSubTypeButton.setText(dataType == null ? TransformationEditor.getString("type.select") : dataType.toString());
        });
        listSelected = addType("type.list.of", listSubType, listSubTypeButton);
        listSubTypeButton.disableProperty().bind(listSelected.not());

        Utility.addChangeListenerPlatformNN(typeGroup.selectedToggleProperty(), toggle -> {
            updateSelectedType();
        });
    }

    @RequiresNonNull({"selectedType", "typeGroup", "types"})
    private void updateSelectedType(@UnderInitialization(Object.class) TypeSelectionPane this)
    {
        // This bit shouldn't be null, but we know what to do in that case: set selectedType to null:
        @Nullable ObservableValue<@Nullable DataType> dataTypeObservableValue = types.get(typeGroup.getSelectedToggle());
        selectedType.setValue(dataTypeObservableValue == null ? null : dataTypeObservableValue.getValue());
    }


    /**
     * Adds a type option to the typeGroup toggle group, and the contents VBox.
     *
     * @param typeKey The label-key for the type name.
     * @param calculateType An observable which constructs the full type (or null if there's an error)
     * @param furtherDetails Any nodes which should sit to the right of the radio button
     * @return An observable which is true when this type is selected (useful to enable/disable sub-items)
     */
    @RequiresNonNull({"contents", "typeGroup", "types"})
    private BooleanBinding addType(@UnderInitialization(Object.class) TypeSelectionPane this, @LocalizableKey String typeKey, ObservableValue<@Nullable DataType> calculateType, Node... furtherDetails)
    {
        RadioButton radioButton = new RadioButton(TransformationEditor.getString(typeKey));
        radioButton.setToggleGroup(typeGroup);
        HBox hbox = new HBox(radioButton);
        hbox.getChildren().addAll(furtherDetails);
        contents.getChildren().add(hbox);
        types.put(radioButton, calculateType);
        Utility.addChangeListenerPlatform(calculateType, t -> updateSelectedType());
        // Select first one by default:
        if (typeGroup.getSelectedToggle() == null)
            typeGroup.selectToggle(radioButton);
        return Bindings.equal(radioButton, typeGroup.selectedToggleProperty());
    }

    public Node getNode()
    {
        return contents;
    }

    public ObservableObjectValue<@Nullable DataType> selectedType()
    {
        return selectedType;
    }

    private static class NumberTypeBinding extends ObjectBinding<@Nullable DataType>
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
        protected @Nullable DataType computeValue()
        {
            Unit u = units.get();
            if (u == null)
                return null;
            return DataType.number(new NumberInfo(u, 0));
        }
    }
}
