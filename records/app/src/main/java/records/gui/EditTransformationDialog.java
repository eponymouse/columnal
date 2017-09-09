package records.gui;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.ObjectExpression;
import javafx.beans.binding.StringBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.cell.TextFieldListCell;
import javafx.scene.effect.Blend;
import javafx.scene.effect.BlendMode;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.effect.ColorInput;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Modality;
import javafx.stage.Window;
import javafx.util.Callback;
import javafx.util.StringConverter;
import org.checkerframework.checker.i18n.qual.LocalizableKey;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;
import org.controlsfx.control.HyperlinkLabel;
import records.data.TableId;
import records.data.Transformation;
import records.transformations.TransformationInfo;
import records.transformations.TransformationEditor;
import records.transformations.TransformationManager;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformConsumer;
import utility.FXPlatformFunction;
import utility.Pair;
import utility.SimulationSupplier;
import utility.Utility;
import utility.Workers;
import utility.gui.FXUtility;
import utility.gui.GUI;
import utility.gui.TranslationUtility;

import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Created by neil on 01/11/2016.
 */
@OnThread(Tag.FXPlatform)
public class EditTransformationDialog
{
    private final Dialog<SimulationSupplier<Transformation>> dialog;
    // Once user has started editing, we keep hold of it if they switch back, until the dialog is dismissed:
    // The key is the canonical name of the transformation.
    private final Map<String, TransformationEditor> editors = new HashMap<>();
    private final BooleanProperty showingMoreDescription = new SimpleBooleanProperty(false);
    private final View parentView;

    private EditTransformationDialog(Window owner, View parentView, @Nullable TableId srcId, @Nullable Pair<TableId, TransformationEditor> existing)
    {
        this.parentView = parentView;
        dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.initModality(Modality.WINDOW_MODAL);

        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.getDialogPane().lookupButton(ButtonType.OK).getStyleClass().add("ok-button");
        dialog.setResizable(true);
        dialog.getDialogPane().setPrefWidth(850.0);
        dialog.getDialogPane().setPrefHeight(700.0);

        List<TransformationInfo> available = TransformationManager.getInstance().getTransformations();

        if (existing != null)
        {
            editors.put(existing.getSecond().getInfo().getCanonicalName(), existing.getSecond());
        }
        BorderPane pane = new BorderPane();
        ListView<TransformationInfo> transformationTypeList = new ListView<>(FXCollections.observableArrayList(available));
        transformationTypeList.setEditable(false);
        transformationTypeList.setCellFactory(lv ->
        {
            return new ListCell<TransformationInfo>()
            {
                private @Nullable ImageView imageView;

                @Override
                protected void updateItem(@Nullable TransformationInfo item, boolean empty)
                {
                    // By default, image view vanishes:
                    imageView = null;
                    setGraphic(null);

                    if (item != null && !empty)
                    {
                        setText(item.getDisplayName());
                        ClassLoader systemClassLoader = ClassLoader.getSystemClassLoader();
                        if (systemClassLoader != null)
                        {
                            URL imageURL = systemClassLoader.getResource(item.getImageFileName());
                            if (imageURL != null)
                            {
                                imageView = new ImageView(imageURL.toExternalForm());
                                imageView.setFitWidth(100.0);
                                imageView.setPreserveRatio(true);
                                imageView.setSmooth(true);
                                setGraphic(imageView);
                            }
                        }
                    }
                    super.updateItem(item, empty);
                }

                @Override
                public void updateSelected(boolean selected)
                {
                    if (imageView != null)
                    {
                        imageView.setEffect(selected ? new ColorAdjust(0, 0, 1.0, 0.0) : null);
                    }
                    super.updateSelected(selected);
                }
            };
        });
        transformationTypeList.getStyleClass().add("transformation-list");
        pane.setLeft(GUI.labelledAbove("transformEditor.transform.type", transformationTypeList));
        ReadOnlyObjectProperty<TransformationInfo> selectedTransformation = transformationTypeList.getSelectionModel().selectedItemProperty();
        SimpleObjectProperty<Optional<TransformationEditor>> editor = new SimpleObjectProperty<>();
        FXUtility.addChangeListenerPlatform(selectedTransformation, trans ->
        {
            if (trans != null)
            {
                @NonNull TransformationInfo transFinal = trans;
                editor.set(Optional.ofNullable(
                    editors.computeIfAbsent(transFinal.getCanonicalName(), n ->
                        transFinal.editNew(parentView, parentView.getManager(), srcId, srcId == null ? null : parentView.getManager().getSingleTableOrNull(srcId))
                    )
                ));
            }
            else
                editor.set(Optional.empty());
        });
        VBox infoPane = new VBox();
        infoPane.getStyleClass().add("transformation-info");
        TableNameTextField tableNameTextField = new TableNameTextField(parentView.getManager(), existing == null ? null : existing.getFirst());
        tableNameTextField.getStyleClass().add("transformation-table-id");
        infoPane.getChildren().add(GUI.labelled("transformEditor.table.name", tableNameTextField.getNode()));


        Text title = new Text("");
        title.getStyleClass().add("transformation-title");
        title.textProperty().bind(new SelectedTransformationStringBinding(editor, TransformationEditor::getDisplayTitle));
        HBox titleWrapper = new HBox(/*new Label("\u219D"), */title);
        titleWrapper.getStyleClass().add("transformation-title-wrapper");
        infoPane.getChildren().add(titleWrapper);
        Text description = new Text("");
        description.getStyleClass().add("transformation-description");
        @SuppressWarnings("initialization")
        SelectedTransformationStringBinding descriptionBinding = new SelectedTransformationStringBinding(editor, this::getDescriptionFor)
        {{
            super.bind(showingMoreDescription);
        }};
        description.textProperty().bind(descriptionBinding);
        Hyperlink moreLessLink = new Hyperlink();
        moreLessLink.textProperty().bind(Bindings.when(showingMoreDescription).then(TranslationUtility.getString("transformEditor.less")).otherwise(TranslationUtility.getString("transformEditor.more")));
        moreLessLink.setOnAction(e -> showingMoreDescription.set(!showingMoreDescription.get()));
        TextFlow textFlow = new TextFlow(description, moreLessLink);
        textFlow.getStyleClass().add("transformation-description-wrapper");
        //textFlow.maxWidthProperty().bind(infoPane.widthProperty());
        infoPane.getChildren().add(textFlow);
        pane.setCenter(infoPane);

        FXUtility.addChangeListenerPlatform(editor, ed ->
        {
            if (infoPane.getChildren().size() > 2)
                infoPane.getChildren().remove(2);
            if (ed == null || !ed.isPresent())
            {
                // Leave out
            }
            else
            {
                Pane parameterDisplay = ed.get().getParameterDisplay(this::showError);
                VBox.setVgrow(parameterDisplay, Priority.ALWAYS);
                infoPane.getChildren().add(parameterDisplay);
            }
        });
        editor.set(existing == null ? Optional.empty() : Optional.of(existing.getSecond()));
        dialog.getDialogPane().setContent(pane);
        dialog.getDialogPane().getStylesheets().add(FXUtility.getStylesheet("general.css"));
        dialog.getDialogPane().getStylesheets().add(FXUtility.getStylesheet("transformation.css"));

        //dialog.setOnShown(e -> org.scenicview.ScenicView.show(dialog.getDialogPane().getScene()));

        dialog.setResultConverter(new Callback<ButtonType, @Nullable SimulationSupplier<Transformation>>()
        {
            @Override
            @OnThread(Tag.FXPlatform)
            public @Nullable SimulationSupplier<Transformation> call(ButtonType bt)
            {
                if (bt.equals(ButtonType.OK))
                {
                    Optional<TransformationEditor> ed = editor.get();
                    if (ed.isPresent())
                    {
                        @Nullable TableId destId = tableNameTextField.valueProperty().get();
                        if (destId != null)
                        {
                            return ed.get().getTransformation(parentView.getManager(), destId);
                        }
                    }
                }
                return null;
            }
        });

        // Wait until everything is set up to execute this:
        if (existing == null)
            transformationTypeList.getSelectionModel().selectFirst();
        else
            transformationTypeList.getSelectionModel().select(existing.getSecond().getInfo());
    }

    @RequiresNonNull("showingMoreDescription")
    private @Localized String getDescriptionFor(@UnknownInitialization(Object.class) EditTransformationDialog this, TransformationEditor editor)
    {
        Pair<@LocalizableKey String, @LocalizableKey String> descriptionKeys = editor.getDescriptionKeys();
        return Utility.concatLocal(TranslationUtility.getString(descriptionKeys.getFirst()), showingMoreDescription.get() ? TranslationUtility.getString(descriptionKeys.getSecond()) : "");
    }

    // Make a new transformation with the given source table
    public EditTransformationDialog(Window owner, View parentView, TableId src)
    {
        this(owner, parentView, src, (Pair<TableId, TransformationEditor>)null);

    }

    public EditTransformationDialog(Window window, View parentView, TableId existingTableId, TransformationEditor editor)
    {
        this(window, parentView, editor.getSourceId(), new Pair<>(existingTableId, editor));
    }

    private void showError(@UnknownInitialization(Object.class) EditTransformationDialog this, Exception e)
    {
        //TODO have a pane when we can show it to the user.
        e.printStackTrace();
    }

    public Optional<SimulationSupplier<Transformation>> show()
    {
        return dialog.showAndWait();
    }

    private static class SelectedTransformationStringBinding extends StringBinding
    {
        private final ObjectExpression<Optional<TransformationEditor>> selectedTransformation;
        private final FXPlatformFunction<TransformationEditor, @Localized String> getString;

        public SelectedTransformationStringBinding(ObjectExpression<Optional<TransformationEditor>> selectedTransformation, FXPlatformFunction<TransformationEditor, @Localized String> getString)
        {
            this.selectedTransformation = selectedTransformation;
            this.getString = getString;
            super.bind(selectedTransformation);
        }

        @Override
        @OnThread(value = Tag.FXPlatform, ignoreParent = true)
        protected String computeValue()
        {
            return (selectedTransformation.get() == null || !selectedTransformation.get().isPresent()) ? "" : getString.apply(selectedTransformation.get().get());
        }
    }
}
