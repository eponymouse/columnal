package records.importers.gui;

import annotation.units.AbsColIndex;
import annotation.units.AbsRowIndex;
import annotation.units.GridAreaRowIndex;
import annotation.units.TableDataRowIndex;
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
import javafx.geometry.BoundingBox;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.VPos;
import javafx.scene.Cursor;
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
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import log.Log;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.initialization.qual.Initialized;
import org.checkerframework.checker.nullness.qual.KeyForBottom;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.units.qual.UnitsBottom;
import records.data.CellPosition;
import records.data.RecordSet;
import records.data.Table.Display;
import records.data.TableId;
import records.data.TableManager;
import records.data.datatype.TypeManager;
import records.error.InternalException;
import records.error.UserException;
import records.gui.DataCellSupplier;
import records.gui.DataDisplay;
import records.gui.ErrorableTextField;
import records.gui.ErrorableTextField.ConversionResult;
import records.gui.RowLabelSupplier;
import records.gui.grid.GridArea;
import records.gui.grid.RectangleBounds;
import records.gui.grid.RectangleOverlayItem;
import records.gui.grid.VirtualGrid;
import records.gui.grid.VirtualGridSupplier.ViewOrder;
import records.gui.grid.VirtualGridSupplier.VisibleBounds;
import records.gui.grid.VirtualGridSupplierFloating;
import records.gui.stable.ColumnDetails;
import records.gui.stable.ScrollGroup.ScrollLock;
import records.gui.stf.TableDisplayUtility;
import records.importers.ChoicePoint;
import records.importers.ChoicePoint.Choice;
import records.importers.ChoicePoint.Options;
import records.importers.Choices;
import records.importers.Format;
import records.importers.GuessFormat;
import records.importers.GuessFormat.ImportInfo;
import records.importers.GuessFormat.TrimChoice;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.FXPlatformConsumer;
import utility.FXPlatformFunction;
import utility.FXPlatformRunnable;
import utility.Pair;
import utility.SimulationFunction;
import utility.Utility;
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
        private final @TableDataRowIndex int numRows;

        public SourceInfo(ImmutableList<ColumnDetails> srcColumns, @TableDataRowIndex int numRows)
        {
            this.srcColumns = srcColumns;
            this.numRows = numRows;
        }
    }

    public ImportChoicesDialog(TableManager mgr, String suggestedName, ChoicePoint<?, FORMAT> choicePoints, SimulationFunction<FORMAT, ? extends @Nullable RecordSet> loadData, SimulationFunction<Choices, @Nullable SourceInfo> srcData)
    {
        SimpleObjectProperty<@Nullable RecordSet> destRecordSet = new SimpleObjectProperty<>(null);
        VirtualGrid destGrid = new VirtualGrid(null, 0, 0);
            //new MessageWhenEmpty("import.noColumnsDest", "import.noRowsDest"));
        DataDisplay destData = new DestDataDisplay(suggestedName, destGrid.getFloatingSupplier(), destRecordSet);
        destGrid.addGridAreas(ImmutableList.of(destData));
        DataCellSupplier destDataCellSupplier = new DataCellSupplier();
        destGrid.addNodeSupplier(destDataCellSupplier);
        destDataCellSupplier.addGrid(destData, destData.getDataGridCellInfo());
        //destGrid.setEditable(false);
        VirtualGrid srcGrid = new VirtualGrid(null, 0, 0);
            //new MessageWhenEmpty("import.noColumnsSrc", "import.noRowsSrc"))
        srcGrid.getScrollGroup().add(destGrid.getScrollGroup(), ScrollLock.BOTH);
        destGrid.bindVisibleRowsTo(srcGrid);
        SimpleObjectProperty<@Nullable SourceInfo> srcInfo = new SimpleObjectProperty<>(null);
        SrcDataDisplay srcDataDisplay = new SrcDataDisplay(suggestedName, srcGrid.getFloatingSupplier(), srcInfo, destData);
        srcGrid.addGridAreas(ImmutableList.of(srcDataDisplay));
        DataCellSupplier srcDataCellSupplier = new DataCellSupplier();
        srcGrid.addNodeSupplier(srcDataCellSupplier);
        srcDataCellSupplier.addGrid(srcDataDisplay, srcDataDisplay.getDataGridCellInfo());

        RowLabelSupplier srcRowLabels = new RowLabelSupplier();
        srcGrid.addNodeSupplier(srcRowLabels);
        srcRowLabels.addTable(srcGrid, srcDataDisplay, true);


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
                                destData.setColumns(TableDisplayUtility.makeStableViewColumns(recordSetNonNull, new Pair<>(Display.ALL, c -> true), c -> null, (r, c) -> CellPosition.ORIGIN, null), null, null);
                            });
                        }
                    }
                    catch (InternalException | UserException e)
                    {
                        Log.log(e);
                        Platform.runLater(() -> {
                            destData.setColumns(ImmutableList.of(), null, null);
                            //destData.setMessageWhenEmpty(new MessageWhenEmpty(e.getLocalizedMessage()));
                        });

                    }
                });
            }
            else
            {
                destData.setColumns(ImmutableList.of(), null, null);
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
                            srcDataDisplay.setColumns(sourceInfoNonNull.srcColumns, null, null);
                        });
                    }
                }));
            }
        });

        try
        {
            Choices bestGuess = GuessFormat.findBestGuess(choicePoints);

            makeGUI(mgr.getTypeManager(), choicePoints, bestGuess, Choices.FINISHED, choices, destData, srcDataDisplay.trimExpression(), formatProperty, choicesProperty);
        }
        catch (InternalException e)
        {
            Log.log(e);
            choices.addRow(new Row(new Label("Internal error: "), null, new TextFlow(new Text(e.getLocalizedMessage()))));
        }

        // Crucial that these use the same margins, to get the scrolling to line up:
        Insets insets = new Insets(SrcDataDisplay.VERT_INSET, SrcDataDisplay.HORIZ_INSET, SrcDataDisplay.VERT_INSET, SrcDataDisplay.HORIZ_INSET);
        StackPane.setMargin(srcGrid.getNode(), insets);
        StackPane.setMargin(destGrid.getNode(), insets);
        
        SplitPane splitPane = new SplitPane(new StackPane(srcGrid.getNode(), srcDataDisplay.getMousePane()), new StackPane(destGrid.getNode()));
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
                return new Pair<>(new ImportInfo(suggestedName/*, linkCopyButtons.valueProperty().get()*/), format);
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
    private static <C extends Choice, FORMAT extends Format> void makeGUI(TypeManager typeManager, final ChoicePoint<C, FORMAT> rawChoicePoint, Choices previousChoices, Choices currentChoices, LabelledGrid controlGrid, DataDisplay tableView, ObjectExpression<TrimChoice> trimObservable, ObjectProperty<@Nullable FORMAT> destProperty, ObjectProperty<@Nullable Choices> choicesProperty) throws InternalException
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
                tableView.setColumns(ImmutableList.of(), null, null);
                //tableView.setMessageWhenEmpty(new MessageWhenEmpty(e.getLocalizedMessage()));
            }
            return;
        }
        @NonNull Options<C> optionsNonNull = options;
        // Default handling:
        final @Nullable Node choiceNode;
        final ObjectExpression<@Nullable C> choiceExpression;
        
        // Trim is a special case:
        if (options.choiceType.getChoiceClass().equals(TrimChoice.class))
        {
            choiceNode = null;
            @SuppressWarnings("unchecked") // Safe because we know C is TrimChoice
            ObjectExpression<@Nullable C> casted = (ObjectExpression<@Nullable C>) trimObservable;
            choiceExpression = casted;
        }
        else if (options.isEmpty())
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
        int rowNumber = choiceNode == null ? controlGrid.getLastRow() : controlGrid.addRow(GUI.labelledGridRow(options.choiceType.getLabelKey(), options.choiceType.getHelpId(), choiceNode));
        FXPlatformConsumer<@Nullable C> pick = item -> {
            if (item == null)
            {
                tableView.setColumns(ImmutableList.of(), null, null);
                //tableView.setMessageWhenEmpty(new MessageWhenEmpty(TranslationUtility.getString("need.valid.option")));
                return;
            }
            try
            {
                ChoicePoint<?, FORMAT> next = rawChoicePoint.select(item);
                controlGrid.clearRowsAfter(rowNumber);
                makeGUI(typeManager, next, previousChoices, currentChoices.with(optionsNonNull.choiceType,item), controlGrid, tableView, trimObservable, destProperty, choicesProperty);
            }
            catch (InternalException e)
            {
                Log.log(e);
                tableView.setColumns(ImmutableList.of(), null, null);
                //tableView.setMessageWhenEmpty(new MessageWhenEmpty(e.getLocalizedMessage()));
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

        @OnThread(Tag.FXPlatform)
        public DestDataDisplay(String suggestedName, VirtualGridSupplierFloating destColumnHeaderSupplier, SimpleObjectProperty<@Nullable RecordSet> destRecordSet)
        {
            super(new TableId(suggestedName), destColumnHeaderSupplier, true, true);
            setPosition(CellPosition.ORIGIN);
            this.destRecordSet = destRecordSet;
        }

        @Override
        public @OnThread(Tag.FXPlatform) void updateKnownRows(@GridAreaRowIndex int checkUpToRowInclGrid, FXPlatformRunnable updateSizeAndPositions)
        {
            RecordSet recordSet = destRecordSet.get();
            if (recordSet == null)
                return;
            @NonNull RecordSet recordSetFinal = recordSet;
            Workers.onWorkerThread("Fetching row size", Priority.FETCH, () -> {
                try
                {
                    @TableDataRowIndex int len = recordSetFinal.getLength();
                    Platform.runLater(() -> {
                        if (currentKnownRows != len)
                        {
                            currentKnownRows = len;
                            updateSizeAndPositions.run();
                        }
                    });
                }
                catch (UserException | InternalException e)
                {
                    Log.log(e);
                }
            });
        }

        @Override
        protected void doCopy(@Nullable RectangleBounds rectangleBounds)
        {
            // We don't currently support copy on this table
        }
    }

    private static class SrcDataDisplay extends DataDisplay
    {
        public static final double HORIZ_INSET = 22;
        public static final double VERT_INSET = 0;
        private final SimpleObjectProperty<@Nullable SourceInfo> srcInfo;
        private final RectangleOverlayItem selectionRectangle;
        private RectangleBounds curSelectionBounds;
        private final SimpleObjectProperty<TrimChoice> trim = new SimpleObjectProperty<>(new TrimChoice(0, 0, 0, 0));
        private BoundingBox curBoundingBox;
        private CellPosition mousePressed = CellPosition.ORIGIN;
        private final Pane mousePane;

        @OnThread(Tag.FXPlatform)
        public SrcDataDisplay(String suggestedName, VirtualGridSupplierFloating srcColumnHeaderSupplier, SimpleObjectProperty<@Nullable SourceInfo> srcInfo, GridArea destData)
        {
            super(new TableId(suggestedName), srcColumnHeaderSupplier, true, false);
            setPosition(CellPosition.ORIGIN.offsetByRowCols(1, 0));
            this.mousePane = new Pane();
            this.srcInfo = srcInfo;
            FXUtility.addChangeListenerPlatform(srcInfo, s -> {
                if (s != null)
                {
                    // Reset selection:
                    curSelectionBounds = new RectangleBounds(getPosition().offsetByRowCols(1, 0), getPosition().offsetByRowCols(s.numRows, s.srcColumns.size() - 1));
                    trim.set(new TrimChoice(0, 0, 0, 0));
                }
            });
            this.curSelectionBounds = new RectangleBounds(getPosition().offsetByRowCols(1, 0), getBottomRightIncl());
            // Will be set right at first layout:
            this.curBoundingBox = new BoundingBox(0, 0, 0, 0);
            this.selectionRectangle = new RectangleOverlayItem(ViewOrder.TABLE_BORDER) {
                
                
                @Override
                protected Optional<Either<BoundingBox, RectangleBounds>> calculateBounds(VisibleBounds visibleBounds)
                {
                    double x = visibleBounds.getXCoord(FXUtility.mouse(SrcDataDisplay.this).curSelectionBounds.topLeftIncl.columnIndex);
                    double y = visibleBounds.getYCoord(FXUtility.mouse(SrcDataDisplay.this).curSelectionBounds.topLeftIncl.rowIndex);
                    curBoundingBox = new BoundingBox(
                            x,
                            y,
                            visibleBounds.getXCoordAfter(FXUtility.mouse(SrcDataDisplay.this).curSelectionBounds.bottomRightIncl.columnIndex) - x,
                            visibleBounds.getYCoordAfter(FXUtility.mouse(SrcDataDisplay.this).curSelectionBounds.bottomRightIncl.rowIndex) - y
                    );
                        
                    return Optional.of(Either.right(curSelectionBounds));
                }

                @Override
                protected void styleNewRectangle(Rectangle r, VisibleBounds visibleBounds)
                {
                    r.getStyleClass().add("prospective-import-rectangle");
                }
            };
            srcColumnHeaderSupplier.addItem(this.selectionRectangle);
            mousePane.setOnMouseMoved(e -> {
                mousePane.setCursor(FXUtility.mouse(this).calculateCursor(e.getX() - HORIZ_INSET, e.getY() - VERT_INSET));
                e.consume();
            });
            mousePane.setOnMouseReleased(e -> {
                mousePane.setCursor(FXUtility.mouse(this).calculateCursor(e.getX() - HORIZ_INSET, e.getY() - VERT_INSET));
                e.consume();
            });
            mousePane.setOnMousePressed(e -> {
                withParent(p -> p.getVisibleBounds())
                    .flatMap(v -> v.getNearestTopLeftToScreenPos(new Point2D(e.getScreenX(), e.getScreenY()), HPos.LEFT, VPos.TOP))
                    .ifPresent(pos -> mousePressed = pos);
            });
            mousePane.setOnMouseDragged(e -> {
                Cursor c = mousePane.getCursor();
                withParent(p -> p.getVisibleBounds()).ifPresent(visibleBounds  -> {
                    @Nullable CellPosition pos = visibleBounds.getNearestTopLeftToScreenPos(new Point2D(e.getScreenX(), e.getScreenY()), HPos.LEFT, VPos.TOP).orElse(null);
                    boolean resizingTop = c == Cursor.NW_RESIZE || c == Cursor.N_RESIZE || c == Cursor.NE_RESIZE;
                    boolean resizingBottom = c == Cursor.SW_RESIZE || c == Cursor.S_RESIZE || c == Cursor.SE_RESIZE;
                    boolean resizingLeft = c == Cursor.NW_RESIZE || c == Cursor.W_RESIZE || c == Cursor.SW_RESIZE;
                    boolean resizingRight = c == Cursor.NE_RESIZE || c == Cursor.E_RESIZE || c == Cursor.SE_RESIZE;
                    @AbsRowIndex int newTop;
                    @AbsRowIndex int newBottom;
                    @AbsColIndex int newLeft;
                    @AbsColIndex int newRight;
                    if (resizingTop || resizingBottom || resizingLeft || resizingRight)
                    {
                        newTop = curSelectionBounds.topLeftIncl.rowIndex;
                        newBottom = curSelectionBounds.bottomRightIncl.rowIndex;
                        newLeft = curSelectionBounds.topLeftIncl.columnIndex;
                        newRight = curSelectionBounds.bottomRightIncl.columnIndex;
                        if (pos != null)
                        {
                            if (resizingTop)
                                newTop = pos.rowIndex;
                            else if (resizingBottom)
                                newBottom = pos.rowIndex - CellPosition.row(1);
                            if (resizingLeft)
                                newLeft = pos.columnIndex;
                            else if (resizingRight)
                                newRight = pos.columnIndex - CellPosition.col(1);
                        }
                        // Restrict to valid bounds:
                        newTop = Utility.maxRow(CellPosition.row(1), newTop);
                        newBottom = Utility.maxRow(newTop, newBottom);
                        newRight = Utility.maxCol(newLeft, newRight);
                    }
                    else
                    {
                        if (pos == null)
                            return;
                        // Drag from the original position where they pressed:
                        newTop = Utility.minRow(pos.rowIndex, mousePressed.rowIndex);
                        newBottom = Utility.maxRow(pos.rowIndex, mousePressed.rowIndex);
                        newLeft = Utility.minCol(pos.columnIndex, mousePressed.columnIndex);
                        newRight = Utility.maxCol(pos.columnIndex, mousePressed.columnIndex);
                    }
                    curSelectionBounds = new RectangleBounds(new CellPosition(newTop, newLeft), new CellPosition(newBottom, newRight));
                    destData.setPosition(curSelectionBounds.topLeftIncl.offsetByRowCols(-2, 0));
                    trim.set(new TrimChoice(
                            curSelectionBounds.topLeftIncl.rowIndex - getDataDisplayTopLeftIncl().rowIndex - getPosition().rowIndex,
                            getBottomRightIncl().rowIndex - curSelectionBounds.bottomRightIncl.rowIndex,
                            curSelectionBounds.topLeftIncl.columnIndex,
                            getBottomRightIncl().columnIndex - curSelectionBounds.bottomRightIncl.columnIndex
                    ));
                    withParent_(p -> p.positionOrAreaChanged());
                });
            });
            mousePane.setOnScroll(e -> {
                withParent_(g -> g.getScrollGroup().requestScroll(e));
                // In case of small slide scroll, need to recalculate cursor:
                withParent(g -> g.getVisibleBounds()).ifPresent(b -> selectionRectangle.calculatePosition(b));
                mousePane.setCursor(FXUtility.mouse(this).calculateCursor(e.getX() - HORIZ_INSET, e.getY() - VERT_INSET));
                e.consume();
            });
        }
        
        private Cursor calculateCursor(double x, double y)
        {
            final int EXTRA = 5;
            boolean inX = curBoundingBox.getMinX() - EXTRA <= x && x <= curBoundingBox.getMaxX() + EXTRA;
            boolean inY = curBoundingBox.getMinY() - EXTRA <= y && y <= curBoundingBox.getMaxY() + EXTRA;
            if (!inX || !inY)
                return Cursor.DEFAULT;
            boolean topEdge = Math.abs(curBoundingBox.getMinY() - y) <= EXTRA;
            boolean bottomEdge = Math.abs(curBoundingBox.getMaxY() - y) <= EXTRA;
            boolean leftEdge = Math.abs(curBoundingBox.getMinX() - x) <= EXTRA;
            boolean rightEdge = Math.abs(curBoundingBox.getMaxX() - x) <= EXTRA;
            if (topEdge && leftEdge)
            {
                return Cursor.NW_RESIZE;
            }
            else if (topEdge && rightEdge)
            {
                return Cursor.NE_RESIZE;
            }
            else if (bottomEdge && leftEdge)
            {
                return Cursor.SW_RESIZE;
            }
            else if (bottomEdge && rightEdge)
            {
                return Cursor.SE_RESIZE;
            }
            else if (topEdge)
            {
                return Cursor.N_RESIZE;
            }
            else if (bottomEdge)
            {
                return Cursor.S_RESIZE;
            }
            else if (leftEdge)
            {
                return Cursor.W_RESIZE;
            }
            else if (rightEdge)
            {
                return Cursor.E_RESIZE;
            }
            else
            {
                return Cursor.DEFAULT;
            }
        }

        @Override
        public void cleanupFloatingItems()
        {
            super.cleanupFloatingItems();
            withParent_(p -> p.getFloatingSupplier().removeItem(selectionRectangle));
        }

        @Override
        public @OnThread(Tag.FXPlatform) void updateKnownRows(@GridAreaRowIndex int checkUpToRowIncl, FXPlatformRunnable updateSizeAndPositions)
        {
            @Nullable SourceInfo sourceInfo = srcInfo.get();
            @SuppressWarnings("units")
            @TableDataRowIndex int zero = 0;
            currentKnownRows = sourceInfo == null ? zero : sourceInfo.numRows;
        }

        @Override
        protected void doCopy(@Nullable RectangleBounds rectangleBounds)
        {
            // We don't currently support copy on this table
        }

        public Node getMousePane()
        {
            return mousePane;
        }

        public ObjectExpression<TrimChoice> trimExpression()
        {
            return trim;
        }
    }
}
