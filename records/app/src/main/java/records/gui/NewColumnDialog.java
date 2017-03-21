package records.gui;

import annotation.qual.Value;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.binding.ObjectBinding;
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
    private final BooleanBinding numberSelected;
    private final IdentityHashMap<Toggle, ObservableValue<@Nullable DataType>> types = new IdentityHashMap<>();
    private final ValidationSupport validationSupport = new ValidationSupport();
    private final TextField defaultValue;

    public NewColumnDialog(TypeManager typeManager)
    {
        contents = new VBox();
        name = new TextField();
        contents.getChildren().add(new HBox(new Label(TransformationEditor.getString("newcolumn.name")), name));
        contents.getChildren().add(new Separator());
        typeGroup = new ToggleGroup();
        TextField units = new TextField("");
        numberSelected = addType("type.number", new NumberTypeBinding(units, typeManager), new Label(TransformationEditor.getString("newcolumn.number.units")), units);
        units.disableProperty().bind(numberSelected.not());
        validationSupport.registerValidator(units, (Control c, String unitSrc) -> {
            return FXUtility.validate(units, () -> typeManager.getUnitManager().loadUse(unitSrc));
        });
        addType("type.text", new ReadOnlyObjectWrapper<>(DataType.TEXT));
        addType("type.boolean", new ReadOnlyObjectWrapper<>(DataType.BOOLEAN));
        ComboBox<DataType> dateTimeComboBox = new ComboBox<>();
        addType("type.datetime", dateTimeComboBox.valueProperty(), dateTimeComboBox);
        defaultValue = new TextField();
        contents.getChildren().addAll(new Separator(), new HBox(new Label(TransformationEditor.getString("newcolumn.defaultvalue")), defaultValue));
        //validationSupport.registerValidator(defaultValue, )
        // TODO: tagged, tuple, array

        setResultConverter(this::makeResult);

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
        private @NonNull final TextField units;
        private final TypeManager typeManager;

        public NumberTypeBinding(@NonNull TextField units, TypeManager typeManager)
        {
            this.units = units;
            this.typeManager = typeManager;
            super.bind(units.textProperty());
        }

        @Override
        protected @Nullable DataType computeValue()
        {
            try
            {
                return DataType.number(new NumberInfo(typeManager.getUnitManager().loadUse(units.getText()), 0));
            }
            catch (InternalException | UserException e)
            {
                // TODO: display the error
                return null;
            }
        }
    }
}
