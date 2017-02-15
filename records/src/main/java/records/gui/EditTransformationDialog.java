package records.gui;

import javafx.application.Platform;
import javafx.beans.binding.ObjectExpression;
import javafx.beans.binding.StringBinding;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.TextFieldListCell;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Window;
import javafx.util.Callback;
import javafx.util.StringConverter;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.Table;
import records.data.TableId;
import records.data.Transformation;
import records.transformations.TransformationInfo;
import records.transformations.TransformationEditor;
import records.transformations.TransformationManager;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformConsumer;
import utility.SimulationSupplier;
import utility.Utility;
import utility.Workers;

import java.util.List;
import java.util.Optional;

/**
 * Created by neil on 01/11/2016.
 */
@OnThread(Tag.FXPlatform)
public class EditTransformationDialog
{
    private final Dialog<SimulationSupplier<Transformation>> dialog;
    private final View parentView;

    private EditTransformationDialog(Window owner, View parentView, @Nullable TableId srcId, @Nullable TransformationEditor existing)
    {
        this.parentView = parentView;
        dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.initModality(Modality.WINDOW_MODAL);

        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.setResizable(true);
        dialog.getDialogPane().setPrefWidth(800.0);

        List<TransformationInfo> available = TransformationManager.getInstance().getTransformations();

        //#error TODO add a filter box, a listview with functions
        //and a custom section (per function) for params.

        BorderPane pane = new BorderPane();
        ObservableList<TransformationInfo> filteredList = FXCollections.observableArrayList(available);
        ListView<TransformationInfo> filteredListView = new ListView<>(filteredList);
        filteredListView.setEditable(false);
        filteredListView.setCellFactory(lv ->
        {
            return new TextFieldListCell<TransformationInfo>(new StringConverter<TransformationInfo>()
            {
                @Override
                public String toString(TransformationInfo object)
                {
                    return object.getName();
                }

                @Override
                public TransformationInfo fromString(String string)
                {
                    // Not editable so shouldn't happen
                    throw new UnsupportedOperationException();
                }
            });
        });
        filteredListView.getStyleClass().add("transformation-list");
        pane.setLeft(filteredListView);
        ReadOnlyObjectProperty<TransformationInfo> selectedTransformation = filteredListView.getSelectionModel().selectedItemProperty();
        SimpleObjectProperty<Optional<TransformationEditor>> editor = new SimpleObjectProperty<>();
        Utility.addChangeListenerPlatform(selectedTransformation, trans ->
        {
            if (trans != null)
                editor.set(Optional.of(trans.editNew(parentView, parentView.getManager(), srcId, srcId == null ? null : parentView.getManager().getSingleTableOrNull(srcId))));
            else
                editor.set(Optional.empty());
        });
        VBox infoPane = new VBox();
        infoPane.getStyleClass().add("transformation-info");
        Label title = new Label("");
        title.getStyleClass().add("transformation-title");
        title.textProperty().bind(new DisplayTitleStringBinding(editor));
        infoPane.getChildren().add(title);
        Label description = new Label("");
        description.getStyleClass().add("transformation-description");
        infoPane.getChildren().add(description);
        pane.setCenter(infoPane);

        Utility.addChangeListenerPlatform(editor, ed ->
        {
            if (infoPane.getChildren().size() > 2)
                infoPane.getChildren().remove(2);
            if (ed == null || !ed.isPresent())
            {
                // Leave out
            }
            else
            {
                infoPane.getChildren().add(ed.get().getParameterDisplay(this::showError));
            }
        });
        editor.set(existing == null ? Optional.empty() : Optional.of(existing));
        dialog.getDialogPane().setContent(pane);
        dialog.getDialogPane().getStylesheets().add(Utility.getStylesheet("general.css"));
        dialog.getDialogPane().getStylesheets().add(Utility.getStylesheet("transformation.css"));

        //dialog.setOnShown(e -> org.scenicview.ScenicView.show(dialog.getDialogPane().getScene()));

        dialog.setResultConverter(new Callback<ButtonType, SimulationSupplier<Transformation>>()
        {
            @Override
            @Nullable
            @SuppressWarnings("nullness")
            @OnThread(Tag.FXPlatform)
            public SimulationSupplier<Transformation> call(ButtonType bt)
            {
                if (bt.equals(ButtonType.OK))
                {
                    Optional<TransformationEditor> ed = editor.get();
                    if (ed.isPresent())
                    {
                        return ed.get().getTransformation(parentView.getManager());
                    }
                }
                return null;
            }
        });
    }

    // Make a new transformation with the given source table
    public EditTransformationDialog(Window owner, View parentView, TableId src)
    {
        this(owner, parentView, src, null);

    }

    public EditTransformationDialog(Window window, View parentView, TransformationEditor editor)
    {
        this(window, parentView, editor.getSourceId(), editor);
    }

    private void showError(@UnknownInitialization(Object.class) EditTransformationDialog this, Exception e)
    {
        //TODO have a pane when we can show it to the user.
        e.printStackTrace();
    }

    public void show(FXPlatformConsumer<Optional<Transformation>> withResult)
    {
        Optional<SimulationSupplier<Transformation>> supplier = dialog.showAndWait();
        if (supplier.isPresent())
        {
            Workers.onWorkerThread("Creating transformation", () -> {
                Optional<Transformation> trans = Utility.alertOnError(() -> supplier.get().get());
                Platform.runLater(() -> withResult.consume(trans));
            });
        }
        else
        {
            withResult.consume(Optional.empty());
        }
    }

    private static class DisplayTitleStringBinding extends StringBinding
    {
        private final ObjectExpression<Optional<TransformationEditor>> selectedTransformation;

        public DisplayTitleStringBinding(ObjectExpression<Optional<TransformationEditor>> selectedTransformation)
        {
            this.selectedTransformation = selectedTransformation;
            super.bind(selectedTransformation);
            // TODO bind against display title too
        }

        @Override
        protected String computeValue()
        {
            return (selectedTransformation.get() == null || !selectedTransformation.get().isPresent()) ? "" : selectedTransformation.get().get().displayTitle().get();
        }
    }
}
