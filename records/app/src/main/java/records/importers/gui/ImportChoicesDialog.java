package records.importers.gui;

import annotation.units.AbsRowIndex;
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
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import log.Log;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.initialization.qual.Initialized;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.KeyForBottom;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.units.qual.UnitsBottom;
import records.data.CellPosition;
import records.data.RecordSet;
import records.data.Table.Display;
import records.data.Table.MessageWhenEmpty;
import records.data.TableId;
import records.data.TableManager;
import records.data.datatype.TypeManager;
import records.error.InternalException;
import records.error.UserException;
import records.gui.DataDisplay;
import records.gui.ErrorableTextField;
import records.gui.ErrorableTextField.ConversionResult;
import records.gui.grid.GridArea;
import records.gui.grid.VirtualGrid;
import records.gui.grid.VirtualGridSupplierFloating;
import records.gui.stable.ColumnDetails;
import records.gui.stable.ScrollGroup.ScrollLock;
import records.gui.stf.TableDisplayUtility;
import records.gui.TableNameTextField;
import records.gui.stable.StableView;
import records.importers.ChoicePoint;
import records.importers.ChoicePoint.Choice;
import records.importers.ChoicePoint.Options;
import records.importers.Choices;
import records.importers.Format;
import records.importers.GuessFormat;
import records.importers.GuessFormat.ImportInfo;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.FXPlatformConsumer;
import utility.FXPlatformFunction;
import utility.FXPlatformRunnable;
import utility.Pair;
import utility.SimulationFunction;
import utility.Workers;
import utility.Workers.Priority;
import utility.gui.FXUtility;
import utility.gui.GUI;
import utility.gui.LabelledGrid;
import utility.gui.LabelledGrid.Row;
import utility.gui.TranslationUtility;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

@OnThread(Tag.FXPlatform)
public class ImportChoicesDialog<FORMAT extends Format> extends Dialog<Pair<ImportInfo, FORMAT>>
{
    public static class SourceInfo
    {
        private final ImmutableList<ColumnDetails> srcColumns;
        private final int numRows;

        public SourceInfo(ImmutableList<ColumnDetails> srcColumns, int numRows)
        {
            this.srcColumns = srcColumns;
            this.numRows = numRows;
        }
    }

    public ImportChoicesDialog(TableManager mgr, String suggestedName, ChoicePoint<?, FORMAT> choicePoints, SimulationFunction<FORMAT, ? extends @Nullable RecordSet> loadData, SimulationFunction<Choices, @Nullable SourceInfo> srcData)
    {
        SimpleObjectProperty<@Nullable RecordSet> destRecordSet = new SimpleObjectProperty<>(null);
        VirtualGrid destGrid = new VirtualGrid(null);
            //new MessageWhenEmpty("import.noColumnsDest", "import.noRowsDest"));
        VirtualGridSupplierFloating destColumnHeaderSupplier = new VirtualGridSupplierFloating();
        destGrid.addNodeSupplier(destColumnHeaderSupplier);
        DataDisplay destData = new DestDataDisplay(suggestedName, destColumnHeaderSupplier, destRecordSet);
        destGrid.addGridAreas(ImmutableList.of(destData));
        //destGrid.setEditable(false);
        VirtualGrid srcGrid = new VirtualGrid(null);
            //new MessageWhenEmpty("import.noColumnsSrc", "import.noRowsSrc"))
        destGrid.getScrollGroup().add(srcGrid, ScrollLock.VERTICAL);
        SimpleObjectProperty<@Nullable SourceInfo> srcInfo = new SimpleObjectProperty<>(null);
        VirtualGridSupplierFloating srcColumnHeaderSupplier = new VirtualGridSupplierFloating();
        srcGrid.addNodeSupplier(srcColumnHeaderSupplier);
        DataDisplay srcDataDisplay = new SrcDataDisplay(suggestedName, srcColumnHeaderSupplier, srcInfo);
        srcGrid.addGridAreas(ImmutableList.of(srcDataDisplay));


        LabelledGrid choices = new LabelledGrid();
        choices.getStyleClass().add("choice-grid");
        //@SuppressWarnings("unchecked")
        //SegmentedButtonValue<Boolean> linkCopyButtons = new SegmentedButtonValue<>(new Pair<@LocalizableKey String, Boolean>("table.copy", false), new Pair<@LocalizableKey String, Boolean>("table.link", true));
        //choices.addRow(GUI.labelledGridRow("table.linkCopy", "guess-format/linkCopy", linkCopyButtons));

        SimpleObjectProperty<@Nullable FORMAT> formatProperty = new SimpleObjectProperty<>(null);
        FXUtility.addChangeListenerPlatform(formatProperty, format -> {
            if (format != null)
            {
                @NonNull FORMAT formatNonNull = format;
                Workers.onWorkerThread("Previewing data", Priority.LOAD_FROM_DISK, () -> {
                    try
                    {
                        RecordSet recordSet = loadData.apply(formatNonNull);
                        if (recordSet != null)
                        {
                            @NonNull RecordSet recordSetNonNull = recordSet;
                            Platform.runLater(() -> {
                                destRecordSet.set(recordSetNonNull);
                                destData.setColumnsAndRows(TableDisplayUtility.makeStableViewColumns(recordSetNonNull, new Pair<>(Display.ALL, c -> true), c -> null, () -> CellPosition.ORIGIN, null), null, null);
                            });
                        }
                    }
                    catch (InternalException | UserException e)
                    {
                        Log.log(e);
                        Platform.runLater(() -> {
                            destData.setColumnsAndRows(ImmutableList.of(), null, null);
                            destData.setMessageWhenEmpty(new MessageWhenEmpty(e.getLocalizedMessage()));
                        });

                    }
                });
            }
            else
            {
                destData.setColumnsAndRows(ImmutableList.of(), null, null);
            }
        });
        SimpleObjectProperty<@Nullable Choices> choicesProperty = new SimpleObjectProperty<>(null);
        FXUtility.addChangeListenerPlatform(choicesProperty, curChoices -> {
            if (curChoices != null)
            {
                @NonNull final Choices curChoicesNonNull = curChoices;
                Workers.onWorkerThread("Showing import source", Priority.FETCH, () -> FXUtility.alertOnError_(() -> {
                    SourceInfo sourceInfo = srcData.apply(curChoicesNonNull);
                    if (sourceInfo != null)
                    {
                        @NonNull SourceInfo sourceInfoNonNull = sourceInfo;
                        Platform.runLater(() -> {
                            srcInfo.set(sourceInfoNonNull);
                            srcDataDisplay.setColumnsAndRows(sourceInfoNonNull.srcColumns, null, null);
                        });
                    }
                }));
            }
        });

        try
        {
            Choices bestGuess = GuessFormat.findBestGuess(choicePoints);

            makeGUI(mgr.getTypeManager(), choicePoints, bestGuess, Choices.FINISHED, choices, destData, formatProperty, choicesProperty);
        }
        catch (InternalException e)
        {
            Log.log(e);
            choices.addRow(new Row(new Label("Internal error: "), null, new TextFlow(new Text(e.getLocalizedMessage()))));
        }


        SplitPane splitPane = new SplitPane(srcGrid.getNode(), destGrid.getNode());
        Pane content = new BorderPane(splitPane, choices, null, null, null);
        content.getStyleClass().add("guess-format-content");
        getDialogPane().getStylesheets().addAll(FXUtility.getSceneStylesheets("guess-format"));
        getDialogPane().setContent(content);
        getDialogPane().getButtonTypes().setAll(ButtonType.CANCEL, ButtonType.OK);
        // Prevent enter/escape activating buttons:
        ((Button)getDialogPane().lookupButton(ButtonType.OK)).setDefaultButton(false);
        // I tried to use setCancelButton(false) but that isn't enough to prevent escape cancelling, so we consume
        // the keypress:
        getDialogPane().addEventHandler(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ESCAPE)
                e.consume();
        });
        //TODO disable ok button if name isn't valid
        setResultConverter(bt -> {
            @Nullable FORMAT format = formatProperty.get();
            if (bt == ButtonType.OK && format != null)
            {
                return new Pair<>(new ImportInfo(null/*, linkCopyButtons.valueProperty().get()*/), format);
            }
            return null;
        });
        setResizable(true);
        getDialogPane().setPrefSize(800, 600);


        setOnShown(e -> {
            //initModality(Modality.NONE); // For scenic view
            //org.scenicview.ScenicView.show(getDialogPane().getScene());
        });
    }

    @OnThread(Tag.FXPlatform)
    private static <C extends Choice, FORMAT extends Format> void makeGUI(TypeManager typeManager, final ChoicePoint<C, FORMAT> rawChoicePoint, Choices previousChoices, Choices currentChoices, LabelledGrid controlGrid, DataDisplay tableView, ObjectProperty<@Nullable FORMAT> destProperty, ObjectProperty<@Nullable Choices> choicesProperty) throws InternalException
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
                tableView.setColumnsAndRows(ImmutableList.of(), null, null);
                tableView.setMessageWhenEmpty(new MessageWhenEmpty(e.getLocalizedMessage()));
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
                final @NonNull Function<String, Either<@Localized String, C>> stringEntry = options.stringEntry;
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
            FXPlatformFunction<@Nullable PickOrOther<C>, @Nullable C> extract = (@Nullable PickOrOther<C> selectedItem) -> {
                if (selectedItem != null && selectedItem.value != null)
                    return selectedItem.value;
                else if (selectedItem != null && selectedItem.value == null && fieldValue != null && fieldValue.get() != null)
                    return fieldValue.get();
                else
                    return null;
            };
            if (fieldValue == null)
                choiceExpression = FXUtility.<@Nullable PickOrOther<C>, @Nullable C>mapBindingEager(selectedItemProperty, extract);
            else
            {
                @SuppressWarnings({"keyfor", "units"})
                @KeyForBottom @UnitsBottom ImmutableList<ObservableValue<?>> fieldList = ImmutableList.of(fieldValue);
                choiceExpression = FXUtility.<@Nullable PickOrOther<C>, @Nullable C>mapBindingEager(selectedItemProperty, extract, fieldList);
            }
        }
        int rowNumber = controlGrid.addRow(GUI.labelledGridRow(options.choiceType.getLabelKey(), options.choiceType.getHelpId(), choiceNode));
        FXPlatformConsumer<@Nullable C> pick = item -> {
            if (item == null)
            {
                tableView.setColumnsAndRows(ImmutableList.of(), null, null);
                tableView.setMessageWhenEmpty(new MessageWhenEmpty(TranslationUtility.getString("need.valid.option")));
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
                Log.log(e);
                tableView.setColumnsAndRows(ImmutableList.of(), null, null);
                tableView.setMessageWhenEmpty(new MessageWhenEmpty(e.getLocalizedMessage()));
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
    private static class PickOrOther<C extends Choice>
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

    private static class DestDataDisplay extends DataDisplay
    {
        private final SimpleObjectProperty<@Nullable RecordSet> destRecordSet;
        boolean currentKnownRowsIsFinal;
        int currentKnownRows;

        public DestDataDisplay(String suggestedName, VirtualGridSupplierFloating destColumnHeaderSupplier, SimpleObjectProperty<@Nullable RecordSet> destRecordSet)
        {
            super(null, new TableId(suggestedName), new MessageWhenEmpty(StyledString.s("...")), null, destColumnHeaderSupplier);
            this.destRecordSet = destRecordSet;
            currentKnownRows = 0;
        }

        @Override
        public @OnThread(Tag.FXPlatform) void updateKnownRows(int checkUpToOverallRowIncl, FXPlatformRunnable updateSizeAndPositions)
        {
            final int checkUpToRowIncl = checkUpToOverallRowIncl - getPosition().rowIndex;
            final RecordSet recordSet = destRecordSet.get();
            if (recordSet == null)
                return;
            final @NonNull RecordSet recordSetNonNull = recordSet;
            if (!currentKnownRowsIsFinal && currentKnownRows < checkUpToRowIncl)
            {
                Workers.onWorkerThread("Fetching row size", Priority.FETCH, () -> {
                    try
                    {
                        // Short-cut: check if the last index we are interested in has a row.  If so, can return early:
                        boolean lastRowValid = recordSetNonNull.indexValid(checkUpToRowIncl);
                        if (lastRowValid)
                        {
                            Platform.runLater(() -> {
                                currentKnownRows = checkUpToRowIncl;
                                currentKnownRowsIsFinal = false;
                                updateSizeAndPositions.run();
                            });
                        } else
                        {
                            // Just a matter of working out where it ends.  Since we know end is close,
                            // just force with getLength:
                            int length = recordSetNonNull.getLength();
                            Platform.runLater(() -> {
                                currentKnownRows = length;
                                currentKnownRowsIsFinal = true;
                                updateSizeAndPositions.run();
                            });
                        }
                    }
                    catch (InternalException | UserException e)
                    {
                        Log.log(e);
                        // We just don't call back the update function
                    }
                });
            }
        }

        @Override
        public int getCurrentKnownRows()
        {
            return currentKnownRows + HEADER_ROWS;
        }

        @Override
        public @OnThread(Tag.FXPlatform) @AbsRowIndex int getFirstDataDisplayRowIncl(@UnknownInitialization(GridArea.class) DestDataDisplay this)
        {
            return getPosition().rowIndex + CellPosition.row(HEADER_ROWS);
        }

        @Override
        public @OnThread(Tag.FXPlatform) @AbsRowIndex int getLastDataDisplayRowIncl(@UnknownInitialization(GridArea.class) DestDataDisplay this)
        {
            return getPosition().rowIndex + CellPosition.row(HEADER_ROWS + currentKnownRows - 1);
        }
    }

    private static class SrcDataDisplay extends DataDisplay
    {
        private final SimpleObjectProperty<@Nullable SourceInfo> srcInfo;

        public SrcDataDisplay(String suggestedName, VirtualGridSupplierFloating srcColumnHeaderSupplier, SimpleObjectProperty<@Nullable SourceInfo> srcInfo)
        {
            super(null, new TableId(suggestedName), new MessageWhenEmpty(StyledString.s("...")), null, srcColumnHeaderSupplier);
            this.srcInfo = srcInfo;
        }

        @Override
        public @OnThread(Tag.FXPlatform) void updateKnownRows(int checkUpToRowIncl, FXPlatformRunnable updateSizeAndPositions)
        {
        }

        @Override
        public int getCurrentKnownRows()
        {
            return internal_getCurrentKnownRows();
        }

        private int internal_getCurrentKnownRows(@UnknownInitialization(GridArea.class) SrcDataDisplay this)
        {
            return srcInfo == null || srcInfo.get() == null ? 0 : srcInfo.get().numRows;
        }

        @Override
        public @OnThread(Tag.FXPlatform) @AbsRowIndex int getFirstDataDisplayRowIncl(@UnknownInitialization(GridArea.class) SrcDataDisplay this)
        {
            return getPosition().rowIndex + CellPosition.row(HEADER_ROWS);
        }

        @Override
        public @OnThread(Tag.FXPlatform) @AbsRowIndex int getLastDataDisplayRowIncl(@UnknownInitialization(GridArea.class) SrcDataDisplay this)
        {
            return getPosition().rowIndex + CellPosition.row(HEADER_ROWS + internal_getCurrentKnownRows() - 1);
        }
    }
}
