package records.gui;

import javafx.application.Platform;
import javafx.beans.binding.Binding;
import javafx.beans.binding.StringBinding;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.TextFieldListCell;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Window;
import javafx.util.Callback;
import javafx.util.StringConverter;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.helpers.Util;
import records.data.RecordSet;
import records.data.Transformation;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.TransformationInfo;
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
public class NewTransformationDialog
{
    private final Dialog<SimulationSupplier<Transformation>> dialog;
    private final View parentView;

    public NewTransformationDialog(Window owner, View parentView, Table src)
    {
        this.parentView = parentView;
        dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.initModality(Modality.WINDOW_MODAL);

        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.setResizable(true);

        List<TransformationInfo> available = TransformationManager.getInstance().getTransformations();
        //#error TODO add a filter box, a listview with functions
        //and a custom section (per function) for params.

        BorderPane pane = new BorderPane();
        ObservableList<TransformationInfo> filteredList = FXCollections.observableArrayList(available);
        ListView<TransformationInfo> filteredListView = new ListView<>(filteredList);
        filteredListView.setEditable(false);
        filteredListView.setCellFactory(lv -> {
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
        pane.setCenter(new VBox(new TextField(), filteredListView));
        ReadOnlyObjectProperty<TransformationInfo> selectedTransformation = filteredListView.getSelectionModel().selectedItemProperty();
        BorderPane infoPane = new BorderPane();
        infoPane.setMinWidth(600.0);
        Label title = new Label("");
        title.textProperty().bind(new DisplayTitleStringBinding(selectedTransformation));
        infoPane.setTop(title);
        pane.setRight(infoPane);

        Utility.addChangeListenerPlatform(selectedTransformation, trans -> {
            if (trans == null)
                infoPane.setCenter(null);
            else
                infoPane.setCenter(trans.getParameterDisplay(src));
        });
        dialog.getDialogPane().setContent(pane);



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
                    TransformationInfo trans = selectedTransformation.get();
                    if (trans != null)
                    {
                        return trans.getTransformation();
                    }
                }
                return null;
            }
        });
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
        private final ReadOnlyObjectProperty<TransformationInfo> selectedTransformation;

        public DisplayTitleStringBinding(ReadOnlyObjectProperty<TransformationInfo> selectedTransformation)
        {
            this.selectedTransformation = selectedTransformation;
            super.bind(selectedTransformation);
        }

        @Override
        protected String computeValue()
        {
            return selectedTransformation.get() == null ? "" : selectedTransformation.get().getDisplayTitle();
        }
    }
}
