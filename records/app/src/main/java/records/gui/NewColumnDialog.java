package records.gui;

import annotation.qual.Value;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.binding.ObjectBinding;
import javafx.beans.binding.ObjectExpression;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.value.ObservableBooleanValue;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.Callback;
import org.checkerframework.checker.i18n.qual.LocalizableKey;
import org.checkerframework.checker.initialization.qual.UnderInitialization;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;
import org.controlsfx.control.decoration.Decorator;
import org.controlsfx.control.decoration.StyleClassDecoration;
import org.controlsfx.validation.ValidationSupport;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataType.DateTimeInfo.DateTimeType;
import records.data.datatype.DataType.NumberInfo;
import records.data.datatype.TypeManager;
import records.data.unit.Unit;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.TransformationEditor;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformRunnable;
import utility.FXPlatformSupplier;
import utility.Pair;
import utility.UnitType;
import utility.Utility;
import utility.gui.FXUtility;

import java.util.IdentityHashMap;

/**
 * Created by neil on 20/03/2017.
 */
public class NewColumnDialog extends Dialog<NewColumnDialog.NewColumnDetails>
{
    private final TextField name;
    private final ToggleGroup typeGroup;
    private final VBox contents;
    // Stored as a field to prevent GC:
    private final BooleanBinding numberSelected, dateSelected;

    private final IdentityHashMap<Toggle, ObservableValue<@Nullable DataType>> types = new IdentityHashMap<>();
    private final ValidationSupport validationSupport = new ValidationSupport();
    private final Pair<Node, ObservableValue<?>> defaultValue;


    @OnThread(Tag.FXPlatform)
    public NewColumnDialog(TypeManager typeManager)
    {
        contents = new VBox();
        name = new TextField();
        contents.getChildren().add(new HBox(new Label(TransformationEditor.getString("newcolumn.name")), name));
        contents.getChildren().add(new Separator());
        typeGroup = new ToggleGroup();
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

        dateSelected = addType("type.datetime", dateTimeComboBox.valueProperty(), dateTimeComboBox);
        dateTimeComboBox.disableProperty().bind(dateSelected.not());
        Pair<Node, ObservableValue<?>> defVal;
        try
        {
            defVal = DataTypeGUI.getEditorFor(DataType.NUMBER);
        }
        catch (InternalException e)
        {
            Utility.log(e);
            defVal = new Pair<>(new Label("Internal Error"), new ReadOnlyObjectWrapper<@Nullable Object>(null));
        }
        defaultValue = defVal;
        contents.getChildren().addAll(new Separator(), new HBox(new Label(TransformationEditor.getString("newcolumn.defaultvalue")), defaultValue.getFirst()));
        //validationSupport.registerValidator(defaultValue, )
        // TODO: tagged, tuple, array

        setResultConverter(this::makeResult);

        getDialogPane().getStylesheets().add(Utility.getStylesheet("general.css"));
        getDialogPane().setContent(contents);
        getDialogPane().getButtonTypes().setAll(ButtonType.CANCEL, ButtonType.OK);
        getDialogPane().lookupButton(ButtonType.OK).addEventFilter(ActionEvent.ACTION, e -> {
            if (getSelectedType() == null)
                e.consume();
        });
    }

    @RequiresNonNull({"name", "types", "typeGroup"})
    private @Nullable NewColumnDetails makeResult(@UnderInitialization(Dialog.class) NewColumnDialog this, ButtonType bt)
    {
        if (bt == ButtonType.OK)
        {
            @Nullable DataType selectedType = getSelectedType();
            if (selectedType == null) // Shouldn't happen given our event filter, but only reasonable option is to back out and act like cancel:
                return null;
            return new NewColumnDetails(name.getText(), selectedType, "");
        }
        else
            return null;
    }

    @RequiresNonNull({"types", "typeGroup"})
    private @Nullable DataType getSelectedType(@UnderInitialization(Dialog.class) NewColumnDialog this)
    {
        @Nullable ObservableValue<@Nullable DataType> dataTypeObservableValue = types.get(typeGroup.getSelectedToggle());
        if (dataTypeObservableValue == null)
            return null; // No radio button selected, probably
        else
            return dataTypeObservableValue.getValue();
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
    private BooleanBinding addType(@UnderInitialization(Dialog.class) NewColumnDialog this, @LocalizableKey String typeKey, ObservableValue<@Nullable DataType> calculateType, Node... furtherDetails)
    {
        RadioButton radioButton = new RadioButton(TransformationEditor.getString(typeKey));
        radioButton.setToggleGroup(typeGroup);
        HBox hbox = new HBox(radioButton);
        hbox.getChildren().addAll(furtherDetails);
        contents.getChildren().add(hbox);
        types.put(radioButton, calculateType);
        return Bindings.equal(radioButton, typeGroup.selectedToggleProperty());
    }

    public static class NewColumnDetails
    {
        public final String name;
        public final DataType type;
        public final @Value Object defaultValue;

        public NewColumnDetails(String name, DataType type, @Value Object defaultValue)
        {
            this.name = name;
            this.type = type;
            this.defaultValue = defaultValue;
        }
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
