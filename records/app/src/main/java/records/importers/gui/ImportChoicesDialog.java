package records.importers.gui;

import com.google.common.collect.ImmutableList;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.ObjectExpression;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Modality;
import org.checkerframework.checker.i18n.qual.LocalizableKey;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.initialization.qual.Initialized;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.RecordSet;
import records.data.Table.Display;
import records.data.TableId;
import records.data.TableManager;
import records.data.datatype.TypeManager;
import records.error.InternalException;
import records.error.UserException;
import records.gui.ErrorableTextField;
import records.gui.ErrorableTextField.ConversionResult;
import records.gui.stf.TableDisplayUtility;
import records.gui.TableNameTextField;
import records.gui.stable.StableView;
import records.gui.stable.StableView.ColumnHandler;
import records.gui.stable.VirtScrollStrTextGrid.ScrollLock;
import records.importers.ChoicePoint;
import records.importers.ChoicePoint.Choice;
import records.importers.ChoicePoint.Options;
import records.importers.Choices;
import records.importers.Format;
import records.importers.GuessFormat;
import records.importers.GuessFormat.ImportInfo;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.FXPlatformConsumer;
import utility.Pair;
import utility.SimulationFunction;
import utility.Utility;
import utility.Workers;
import utility.Workers.Priority;
import utility.gui.FXUtility;
import utility.gui.GUI;
import utility.gui.LabelledGrid;
import utility.gui.LabelledGrid.Row;
import utility.gui.SegmentedButtonValue;
import utility.gui.TranslationUtility;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

@OnThread(Tag.FXPlatform)
public class ImportChoicesDialog<FORMAT extends Format> extends Dialog<Pair<ImportInfo, FORMAT>>
{
    public static class SourceInfo
    {
        private final ImmutableList<Pair<String, ColumnHandler>> srcColumns;
        private final int numRows;

        public SourceInfo(ImmutableList<Pair<String, ColumnHandler>> srcColumns, int numRows)
        {
            this.srcColumns = srcColumns;
            this.numRows = numRows;
        }
    }

    public ImportChoicesDialog(TableManager mgr, String suggestedName, ChoicePoint<?, FORMAT> choicePoints, SimulationFunction<FORMAT, ? extends @Nullable RecordSet> loadData, SimulationFunction<Choices, @Nullable SourceInfo> srcData)
    {
        StableView tableView = new StableView();
        tableView.setEditable(false);
        StableView srcTableView = new StableView();
        srcTableView.getNode().addEventFilter(ScrollEvent.ANY, se -> {
            tableView.forwardedScrollEvent(se, ScrollLock.VERTICAL);
            srcTableView.forwardedScrollEvent(se, ScrollLock.HORIZONTAL);
            se.consume();
        });
        srcTableView.bindScroll(tableView, ScrollLock.VERTICAL);


        LabelledGrid choices = new LabelledGrid();
        choices.getStyleClass().add("choice-grid");
        TableNameTextField nameField = new TableNameTextField(mgr, new TableId(suggestedName));
        @SuppressWarnings("unchecked")
        SegmentedButtonValue<Boolean> linkCopyButtons = new SegmentedButtonValue<>(new Pair<@LocalizableKey String, Boolean>("table.copy", false), new Pair<@LocalizableKey String, Boolean>("table.link", true));
        choices.addRow(GUI.labelledGridRow("table.name", "guess-format/tableName", nameField.getNode()));
        choices.addRow(GUI.labelledGridRow("table.linkCopy", "guess-format/linkCopy", linkCopyButtons));

        SimpleObjectProperty<@Nullable FORMAT> formatProperty = new SimpleObjectProperty<>(null);
        FXUtility.addChangeListenerPlatform(formatProperty, format -> {
            if (format != null)
            {
                @NonNull FORMAT formatNonNull = format;
                Workers.onWorkerThread("Previewing " + nameField.valueProperty().get(), Priority.LOAD_FROM_DISK, () -> {
                    try
                    {
                        RecordSet recordSet = loadData.apply(formatNonNull);
                        if (recordSet != null)
                        {
                            @NonNull RecordSet recordSetNonNull = recordSet;
                            Platform.runLater(() -> {
                                tableView.setColumnsAndRows(TableDisplayUtility.makeStableViewColumns(recordSetNonNull, new Pair<>(Display.ALL, c -> true), null), null, recordSetNonNull::indexValid);
                            });
                        }
                    }
                    catch (InternalException | UserException e)
                    {
                        Utility.log(e);
                        Platform.runLater(() -> tableView.setPlaceholderText(e.getLocalizedMessage()));

                    }
                });
            }
            else
            {
                tableView.setColumnsAndRows(ImmutableList.of(), null, i -> false);
            }
        });
        SimpleObjectProperty<@Nullable Choices> choicesProperty = new SimpleObjectProperty<>(null);
        FXUtility.addChangeListenerPlatform(choicesProperty, curChoices -> {
            if (curChoices != null)
            {
                @NonNull final Choices curChoicesNonNull = curChoices;
                Workers.onWorkerThread("Showing import source", Priority.FETCH, () -> Utility.alertOnError_(() -> {
                    SourceInfo sourceInfo = srcData.apply(curChoicesNonNull);
                    if (sourceInfo != null)
                    {
                        @NonNull SourceInfo sourceInfoNonNull = sourceInfo;
                        Platform.runLater(() -> {
                            srcTableView.setColumnsAndRows(sourceInfoNonNull.srcColumns, null, i -> i < sourceInfoNonNull.numRows);
                        });
                    }
                }));
            }
        });

        try
        {
            Choices bestGuess = GuessFormat.findBestGuess(choicePoints);

            makeGUI(mgr.getTypeManager(), choicePoints, bestGuess, Choices.FINISHED, choices, tableView, formatProperty, choicesProperty);
        }
        catch (InternalException e)
        {
            Utility.log(e);
            choices.addRow(new Row(new Label("Internal error: "), null, new TextFlow(new Text(e.getLocalizedMessage()))));
        }


        SplitPane splitPane = new SplitPane(srcTableView.getNode(), tableView.getNode());
        Pane content = new BorderPane(splitPane, choices, null, null, null);
        content.getStyleClass().add("guess-format-content");
        getDialogPane().getStylesheets().addAll(FXUtility.getSceneStylesheets("guess-format"));
        getDialogPane().setContent(content);
        getDialogPane().getButtonTypes().setAll(ButtonType.CANCEL, ButtonType.OK);
        // Prevent enter/escape activating buttons:
        ((Button)getDialogPane().lookupButton(ButtonType.CANCEL)).setCancelButton(false);
        ((Button)getDialogPane().lookupButton(ButtonType.OK)).setDefaultButton(false);
        //TODO disable ok button if name isn't valid
        setResultConverter(bt -> {
            @Nullable TableId tableId = nameField.valueProperty().get();
            @Nullable FORMAT format = formatProperty.get();
            if (bt == ButtonType.OK && tableId != null && format != null)
            {
                return new Pair<>(new ImportInfo(tableId, linkCopyButtons.valueProperty().get()), format);
            }
            return null;
        });
        setResizable(true);
        getDialogPane().setPrefSize(800, 600);


        setOnShown(e -> {
            //initModality(Modality.NONE); // For scenic view
            //org.scenicview.ScenicView.show(getDialogPane().getScene());

            // Have to use runAfter because the OK button gets focused
            // so we have to wait, then steal the focus back:
            FXUtility.runAfter(() -> nameField.requestFocusWhenInScene());
        });
    }

    @OnThread(Tag.FXPlatform)
    private static <C extends Choice, FORMAT extends Format> void makeGUI(TypeManager typeManager, final ChoicePoint<C, FORMAT> rawChoicePoint, Choices previousChoices, Choices currentChoices, LabelledGrid controlGrid, StableView tableView, ObjectProperty<@Nullable FORMAT> destProperty, ObjectProperty<@Nullable Choices> choicesProperty) throws InternalException
    {
        final Options<C> options = rawChoicePoint.getOptions();
        if (options == null)
        {
            try
            {
                FORMAT t = rawChoicePoint.get();
                destProperty.set(t);
                choicesProperty.set(currentChoices);
            }
            catch (UserException e)
            {
                tableView.setPlaceholderText(e.getLocalizedMessage());
            }
            return;
        }
        @NonNull Options<C> optionsNonNull = options;
        // Default handling:
        Node choiceNode;
        ObjectExpression<@Nullable C> choiceExpression;

        if (options.isEmpty())
        {
            // This is only if quick picks is empty and manual entry is not possible:
            choiceNode = new Label("No possible options for " + TranslationUtility.getString(options.choiceType.getLabelKey()));
            choiceExpression = new ReadOnlyObjectWrapper<>(null);
        }
        else if (options.quickPicks.size() == 1 && options.stringEntry == null)
        {
            choiceNode = new Label(options.quickPicks.get(0).toString());
            choiceExpression = new ReadOnlyObjectWrapper<>(options.quickPicks.get(0));
        }
        else
        {
            List<PickOrOther<C>> quickAndOther = new ArrayList<>();
            for (C quickPick : options.quickPicks)
            {
                quickAndOther.add(new PickOrOther<>(quickPick));
            }
            if (options.stringEntry != null)
                quickAndOther.add(new PickOrOther<>());
            final @NonNull @Initialized ComboBox<PickOrOther<C>> combo = GUI.comboBoxStyled(FXCollections.observableArrayList(quickAndOther));
            @Nullable C choice = previousChoices.getChoice(options.choiceType);
            if (choice == null || !combo.getItems().contains(choice))
                combo.getSelectionModel().selectFirst();
            else
                combo.getSelectionModel().select(new PickOrOther<>(choice));

            final @Nullable @Initialized ObjectExpression<@Nullable C> fieldValue;
            if (options.stringEntry != null)
            {
                final @NonNull Function<String, Either<String, C>> stringEntry = options.stringEntry;
                ErrorableTextField<C> field = new ErrorableTextField<>(s -> {
                    return stringEntry.apply(s).either(e -> ConversionResult.error(e), v -> ConversionResult.success(v));
                });
                fieldValue = field.valueProperty();
                choiceNode = new HBox(combo, field.getNode());
                field.getNode().visibleProperty().bind(Bindings.equal(combo.getSelectionModel().selectedItemProperty(), new PickOrOther<>()));
                field.getNode().managedProperty().bind(field.getNode().visibleProperty());
                FXUtility.addChangeListenerPlatformNN(field.getNode().visibleProperty(), vis -> {
                    if (vis)
                        field.requestFocusWhenInScene();
                });
            }
            else
            {
                fieldValue = null;
                choiceNode = combo;
            }
            ReadOnlyObjectProperty<@Nullable PickOrOther<C>> selectedItemProperty = combo.getSelectionModel().selectedItemProperty();
            choiceExpression = FXUtility.<@Nullable PickOrOther<C>, @Nullable C>mapBindingEager(selectedItemProperty, (@Nullable PickOrOther<C> selectedItem) -> {
                    if (selectedItem != null && selectedItem.value != null)
                        return selectedItem.value;
                    else if (selectedItem != null && selectedItem.value == null && fieldValue != null && fieldValue.get() != null)
                        return fieldValue.get();
                    else
                        return null;
            }, fieldValue == null ? new ObservableValue<?>[0] : new ObservableValue<?>[] {fieldValue});
        }
        int rowNumber = controlGrid.addRow(GUI.labelledGridRow(options.choiceType.getLabelKey(), options.choiceType.getHelpId(), choiceNode));
        FXPlatformConsumer<@Nullable C> pick = item -> {
            if (item == null)
            {
                tableView.clear(null);
                tableView.setPlaceholderText("Enter a valid option");
                return;
            }
            try
            {
                ChoicePoint<?, FORMAT> next = rawChoicePoint.select(item);
                controlGrid.clearRowsAfter(rowNumber);
                makeGUI(typeManager, next, previousChoices, currentChoices.with(optionsNonNull.choiceType,item), controlGrid, tableView, destProperty, choicesProperty);
            }
            catch (InternalException e)
            {
                Utility.log(e);
                tableView.clear(null);
                tableView.setPlaceholderText(e.getLocalizedMessage());
            }
        };

        @Nullable C choice = choiceExpression.get();
        if (choice != null)
            pick.consume(choice);
        else if (!options.quickPicks.isEmpty())
            pick.consume(options.quickPicks.get(0));
        // Otherwise can't pick anything; no options available.
        FXUtility.addChangeListenerPlatform(choiceExpression, pick);
    }

    // Either a value of type C, or an "Other" item
    private static class PickOrOther<C>
    {
        private final @Nullable C value;

        public PickOrOther()
        {
            this.value = null;
        }

        public PickOrOther(C value)
        {
            this.value = value;
        }

        @Override
        public boolean equals(@Nullable Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            PickOrOther<?> that = (PickOrOther<?>) o;

            return value != null ? value.equals(that.value) : that.value == null;
        }

        @Override
        public int hashCode()
        {
            return value != null ? value.hashCode() : 0;
        }

        @Override
        public @Localized String toString()
        {
            return value == null ? TranslationUtility.getString("import.choice.specify") : value.toString();
        }
    }
}
