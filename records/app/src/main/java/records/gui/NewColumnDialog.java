package records.gui;

import annotation.qual.Value;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.binding.ObjectBinding;
import javafx.beans.binding.ObjectExpression;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.checkerframework.checker.i18n.qual.LocalizableKey;
import org.checkerframework.checker.initialization.qual.UnderInitialization;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
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
import records.error.InternalException;
import records.transformations.TransformationEditor;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
import utility.Utility;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map.Entry;

/**
 * Created by neil on 20/03/2017.
 */
@OnThread(Tag.FXPlatform)
public class NewColumnDialog extends Dialog<NewColumnDialog.NewColumnDetails>
{
    private final TextField name;
    private final VBox contents;
    private final TypeSelectionPane typeSelectionPane;
    /**
     * The editors for the default value slot.  If the user changes type,
     * we either re-use the existing editor (if it is in this map) or make a new one.
     * An editor is a pair of GUI item and observable value (for the actual default value)
     */
    private final HashMap<DataType, Pair<Node, ObservableValue<? extends @Value @Nullable Object>>> defaultValueEditors = new HashMap<>();


    @OnThread(Tag.FXPlatform)
    public NewColumnDialog(TypeManager typeManager)
    {
        contents = new VBox();
        name = new TextField();
        typeSelectionPane = new TypeSelectionPane(typeManager);
        contents.getChildren().add(new HBox(new Label(TransformationEditor.getString("newcolumn.name")), name));
        contents.getChildren().add(new Separator());
        contents.getChildren().add(typeSelectionPane.getNode());




        contents.getChildren().addAll(new Separator(), new HBox(new Label(TransformationEditor.getString("newcolumn.defaultvalue")), getCurrentDefaultValueEditor().getFirst()));
        int defaultValueContentsIndex = contents.getChildren().size() - 1;

        // TODO: tuple, array

        typeSelectionPane.selectedType().addListener(c -> {
            Pair<Node, ObservableValue<? extends @Value @Nullable Object>> editor = getCurrentDefaultValueEditor();
            contents.getChildren().set(defaultValueContentsIndex, editor.getFirst());
        });

        setResultConverter(this::makeResult);

        getDialogPane().getStylesheets().add(Utility.getStylesheet("general.css"));
        getDialogPane().setContent(contents);
        getDialogPane().getButtonTypes().setAll(ButtonType.CANCEL, ButtonType.OK);
        getDialogPane().lookupButton(ButtonType.OK).addEventFilter(ActionEvent.ACTION, e -> {
            if (getSelectedType() == null)
                e.consume();
        });
    }

    @RequiresNonNull({"typeSelectionPane"})
    private @Nullable DataType getSelectedType(@UnknownInitialization(Object.class) NewColumnDialog this)
    {
        return typeSelectionPane.selectedType().get();
    }

    @RequiresNonNull({"typeSelectionPane", "defaultValueEditors"})
    private Pair<Node, ObservableValue<? extends @Value @Nullable Object>> getCurrentDefaultValueEditor(@UnderInitialization(Dialog.class) NewColumnDialog this)
    {
        @Nullable DataType selectedType = getSelectedType();
        if (selectedType == null)
            return new Pair<>(new Label("Error"), new ReadOnlyObjectWrapper<@Value @Nullable Object>(null));
        return defaultValueEditors.computeIfAbsent(selectedType, NewColumnDialog::makeEditor);
    }

    private static Pair<Node, ObservableValue<? extends @Value @Nullable Object>> makeEditor(DataType dataType)
    {
        return DataTypeGUI.getEditorFor(dataType);
    }

    @RequiresNonNull({"typeSelectionPane", "name"})
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


}
