package records.importers.gui;

import com.google.common.collect.ImmutableList;
import javafx.application.Platform;
import javafx.beans.binding.ObjectExpression;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleObjectProperty;
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
import javafx.scene.layout.Pane;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import org.checkerframework.checker.i18n.qual.LocalizableKey;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.RecordSet;
import records.data.Table.Display;
import records.data.TableId;
import records.data.TableManager;
import records.data.datatype.TypeManager;
import records.error.InternalException;
import records.error.UserException;
import records.gui.TableDisplayUtility;
import records.gui.TableNameTextField;
import records.gui.stable.StableView;
import records.gui.stable.StableView.ColumnHandler;
import records.importers.ChoicePoint;
import records.importers.ChoicePoint.Choice;
import records.importers.ChoicePoint.ChoiceType;
import records.importers.Choices;
import records.importers.Format;
import records.importers.GuessFormat;
import records.importers.GuessFormat.ImportInfo;
import threadchecker.OnThread;
import threadchecker.Tag;
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

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

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
        srcTableView.getNode().addEventFilter(ScrollEvent.SCROLL, se -> {
            tableView.forwardedScrollEvent(se);
            se.consume();
        });
        /* TODO restore matching scroll
        FXUtility.addChangeListenerPlatformNN(tableView.topShowingCell(), topShowing -> {
            System.err.println("Top: " + topShowing);
            sourceFileView.showParagraphAtTop(topShowing.getFirst());
            sourceFileView.layout();
            // We want to adjust offset after layout pass:
            double y = 0 - topShowing.getSecond() - tableView.topHeightProperty().get();
            System.err.println("Scrolling by " + y);
            sourceFileView.scrollBy(new Point2D(0, y));
            sourceFileView.layout();
        });*/


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
                tableView.setColumnsAndRows(Collections.emptyList(), null, i -> false);
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

        //dialog.initModality(Modality.NONE); // For scenic view
        setOnShown(e -> {
            //org.scenicview.ScenicView.show(dialog.getDialogPane().getScene());

            // Have to use runAfter because the OK button gets focused
            // so we have to wait, then steal the focus back:
            FXUtility.runAfter(() -> nameField.requestFocusWhenInScene());
        });
    }

    @OnThread(Tag.FXPlatform)
    private static <C extends Choice, FORMAT extends Format> void makeGUI(TypeManager typeManager, final ChoicePoint<C, FORMAT> rawChoicePoint, Choices previousChoices, Choices currentChoices, LabelledGrid controlGrid, StableView tableView, ObjectProperty<@Nullable FORMAT> destProperty, ObjectProperty<@Nullable Choices> choicesProperty) throws InternalException
    {
        final Pair<ChoiceType<C>, ImmutableList<C>> options = rawChoicePoint.getOptions();
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
        @NonNull Pair<ChoiceType<C>, ImmutableList<C>> optionsNonNull = options;
        // Default handling:
        Node choiceNode;
        ObjectExpression<@Nullable C> choiceExpression;

        if (options.getSecond().isEmpty())
        {
            choiceNode = new Label("No possible options for " + TranslationUtility.getString(options.getFirst().getLabelKey()));
            choiceExpression = new ReadOnlyObjectWrapper<>(null);
        }
        else if (options.getSecond().size() == 1)
        {
            choiceNode = new Label(options.getSecond().get(0).toString());
            choiceExpression = new ReadOnlyObjectWrapper<>(options.getSecond().get(0));
        }
        else
        {
            ComboBox<C> combo = GUI.comboBoxStyled(FXCollections.observableArrayList(options.getSecond()));
            @Nullable C choice = previousChoices.getChoice(options.getFirst());
            if (choice == null || !combo.getItems().contains(choice))
                combo.getSelectionModel().selectFirst();
            else
                combo.getSelectionModel().select(choice);
            choiceNode = combo;
            choiceExpression = combo.getSelectionModel().selectedItemProperty();
        }
        int rowNumber = controlGrid.addRow(GUI.labelledGridRow(options.getFirst().getLabelKey(), options.getFirst().getHelpId(), choiceNode));
        FXPlatformConsumer<C> pick = item -> {
            try
            {
                ChoicePoint<?, FORMAT> next = rawChoicePoint.select(item);
                controlGrid.clearRowsAfter(rowNumber);
                makeGUI(typeManager, next, previousChoices, currentChoices.with(optionsNonNull.getFirst(),item), controlGrid, tableView, destProperty, choicesProperty);
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
        else if (!options.getSecond().isEmpty())
            pick.consume(options.getSecond().get(0));
        // Otherwise can't pick anything; no options available.
        FXUtility.addChangeListenerPlatformNN(choiceExpression, pick);
    }
}
