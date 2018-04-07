package records.importers.gui;

import annotation.units.AbsColIndex;
import annotation.units.AbsRowIndex;
import annotation.units.GridAreaRowIndex;
import annotation.units.TableDataRowIndex;
import com.google.common.collect.ImmutableList;
import javafx.application.Platform;
import javafx.beans.binding.ObjectExpression;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.BoundingBox;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.VPos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.SplitPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import log.Log;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.CellPosition;
import records.data.ColumnId;
import records.data.RecordSet;
import records.data.Table.Display;
import records.data.TableId;
import records.data.TableManager;
import records.data.TableOperations;
import records.error.InternalException;
import records.error.UserException;
import records.gui.DataCellSupplier;
import records.gui.DataDisplay;
import records.gui.RowLabelSupplier;
import records.gui.grid.GridArea;
import records.gui.grid.RectangleBounds;
import records.gui.grid.RectangleOverlayItem;
import records.gui.grid.VirtualGrid;
import records.gui.grid.VirtualGridSupplier.ViewOrder;
import records.gui.grid.VirtualGridSupplier.VisibleBounds;
import records.gui.grid.VirtualGridSupplierFloating;
import records.gui.stable.ColumnDetails;
import records.gui.stable.ColumnOperation;
import records.gui.stable.ScrollGroup.ScrollLock;
import records.gui.stf.TableDisplayUtility;
import records.importers.GuessFormat.Import;
import records.importers.GuessFormat.ImportInfo;
import records.importers.GuessFormat.TrimChoice;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.FXPlatformFunction;
import utility.FXPlatformRunnable;
import utility.Pair;
import utility.Utility;
import utility.Workers;
import utility.Workers.Priority;
import utility.gui.FXUtility;

import java.util.Optional;

@OnThread(Tag.FXPlatform)
public class ImportChoicesDialog<SRC_FORMAT, FORMAT> extends Dialog<ImportInfo<FORMAT>>
{
    // Just for testing:
    private static @Nullable ImportChoicesDialog<?, ?> currentlyShowing;
    


    public static @Nullable ImportChoicesDialog<?, ?> _test_getCurrentlyShowing()
    {
        return currentlyShowing;
    }

    @OnThread(Tag.Any)
    private final RecordSetDataDisplay destData;
    private final SimpleObjectProperty<@Nullable RecordSet> destRecordSet;
    private @Nullable FORMAT destFormat;
    private final Import<SRC_FORMAT, FORMAT> importer;
    
    // These two are only stored as fields for testing purposes:
    @OnThread(Tag.Any)
    private final SrcDataDisplay srcDataDisplay;
    @OnThread(Tag.Any)
    private final VirtualGrid srcGrid;

    public ImportChoicesDialog(TableManager mgr, String suggestedName, Import<SRC_FORMAT, FORMAT> importer)
    {
        this.importer = importer;
        SimpleObjectProperty<@Nullable RecordSet> srcRecordSet = new SimpleObjectProperty<>(null);
        destRecordSet = new SimpleObjectProperty<>(null);
        VirtualGrid destGrid = new VirtualGrid(null, 0, 0);
            //new MessageWhenEmpty("import.noColumnsDest", "import.noRowsDest"));
        destData = new RecordSetDataDisplay(suggestedName, destGrid.getFloatingSupplier(), true, destRecordSet);
        destGrid.addGridAreas(ImmutableList.of(destData));
        DataCellSupplier destDataCellSupplier = new DataCellSupplier();
        destGrid.addNodeSupplier(destDataCellSupplier);
        destDataCellSupplier.addGrid(destData, destData.getDataGridCellInfo());
        //destGrid.setEditable(false);
        srcGrid = new VirtualGrid(null, 0, 0);
            //new MessageWhenEmpty("import.noColumnsSrc", "import.noRowsSrc"))
        srcGrid.getScrollGroup().add(destGrid.getScrollGroup(), ScrollLock.BOTH);
        destGrid.bindVisibleRowsTo(srcGrid);
        srcDataDisplay = new SrcDataDisplay(suggestedName, srcGrid.getFloatingSupplier(), srcRecordSet, destData);
        srcGrid.addGridAreas(ImmutableList.of(srcDataDisplay));
        DataCellSupplier srcDataCellSupplier = new DataCellSupplier();
        srcGrid.addNodeSupplier(srcDataCellSupplier);
        srcDataCellSupplier.addGrid(srcDataDisplay, srcDataDisplay.getDataGridCellInfo());

        RowLabelSupplier srcRowLabels = new RowLabelSupplier();
        srcGrid.addNodeSupplier(srcRowLabels);
        srcRowLabels.addTable(srcGrid, srcDataDisplay, true);

        RowLabelSupplier destRowLabels = new RowLabelSupplier();
        destGrid.addNodeSupplier(destRowLabels);
        destRowLabels.addTable(destGrid, destData, true);

        //@SuppressWarnings("unchecked")
        //SegmentedButtonValue<Boolean> linkCopyButtons = new SegmentedButtonValue<>(new Pair<@LocalizableKey String, Boolean>("table.copy", false), new Pair<@LocalizableKey String, Boolean>("table.link", true));
        //choices.addRow(GUI.labelledGridRow("table.linkCopy", "guess-format/linkCopy", linkCopyButtons));

        Node choices = this.importer.getGUI();
        
        //SimpleObjectProperty<@Nullable FORMAT> destFormatProperty = new SimpleObjectProperty<>(null);
        FXUtility.addChangeListenerPlatformAndCallNow(this.importer.currentSrcFormat(), srcFormat -> {
            if (srcFormat != null)
            {
                @NonNull SRC_FORMAT formatNonNull = srcFormat;
                Workers.onWorkerThread("Previewing data", Priority.LOAD_FROM_DISK, () -> {
                    try
                    {
                        Pair<TrimChoice, RecordSet> loadedSrc = this.importer.loadSource(formatNonNull);
                    
                        Platform.runLater(() -> {
                            int oldColumns = srcRecordSet.get() == null ? 0 : srcRecordSet.get().getColumns().size();
                            srcRecordSet.set(loadedSrc.getSecond());
                            // We use trim guess if size has changed from before:
                            if (oldColumns != loadedSrc.getSecond().getColumns().size())
                            {
                                srcDataDisplay.setTrim(loadedSrc.getFirst());
                            }
                            else
                            {
                                srcDataDisplay.setTrim(srcDataDisplay.getTrim());
                            }
                            // Because we are in a runLater, constructor will have finished by then:
                            Utility.later(this).updateDestPreview();
                            srcDataDisplay.setColumns(TableDisplayUtility.makeStableViewColumns(loadedSrc.getSecond(), new Pair<>(Display.ALL, c -> true), c -> null, (r, c) -> CellPosition.ORIGIN, null), null, null);
                        });
                    }
                    catch (InternalException | UserException e)
                    {
                        Log.log(e);
                        Platform.runLater(() -> {
                            srcDataDisplay.setColumns(ImmutableList.of(), null, null);
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
        // Helps with testing:
        getDialogPane().lookupButton(ButtonType.OK).getStyleClass().add("ok-button");
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
            if (bt == ButtonType.OK && destFormat != null)
            {
                return new ImportInfo<>(suggestedName/*, linkCopyButtons.valueProperty().get()*/, destFormat);
            }
            return null;
        });
        setResizable(true);
        getDialogPane().setPrefSize(800, 600);


        setOnShown(e -> {
            //initModality(Modality.NONE); // For scenic view
            //org.scenicview.ScenicView.show(getDialogPane().getScene());
            currentlyShowing = Utility.later(this);
        });
        setOnHidden(e -> {
            currentlyShowing = null;
        });
    }

    @OnThread(Tag.FXPlatform)
    public void updateDestPreview()
    {
        @Nullable SRC_FORMAT srcFormat = importer.currentSrcFormat().get();
        
        if (srcFormat == null)
            return;
        @NonNull SRC_FORMAT formatNonNull = srcFormat;
        
        TrimChoice trim = srcDataDisplay.getTrim();
        Workers.onWorkerThread("Previewing data", Priority.LOAD_FROM_DISK, () -> {
            try
            {
                Log.debug("Updating dest with trim " + trim);
                Pair<FORMAT, RecordSet> loadedDest = this.importer.loadDest(formatNonNull, trim);
                Log.debug("Dest RS size: " + loadedDest.getSecond().getColumns().size() + " x " + loadedDest.getSecond().getLength() + " from format " + formatNonNull);
                Platform.runLater(() -> {
                    destRecordSet.set(loadedDest.getSecond());
                    destFormat = loadedDest.getFirst();
                    destData.setColumns(TableDisplayUtility.makeStableViewColumns(loadedDest.getSecond(), new Pair<>(Display.ALL, c -> true), c -> null, (r, c) -> CellPosition.ORIGIN, null), null, null);
                });
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

    @OnThread(Tag.Any)
    public SrcDataDisplay _test_getSrcDataDisplay()
    {
        return srcDataDisplay;
    }

    @OnThread(Tag.Any)
    public VirtualGrid _test_getSrcGrid()
    {
        return srcGrid;
    }

    @OnThread(Tag.Any)
    public RecordSetDataDisplay _test_getDestDataDisplay()
    {
        return destData;
    }
    
    // public for testing
    public static class RecordSetDataDisplay extends DataDisplay
    {
        private final ObjectExpression<@Nullable RecordSet> recordSetProperty;

        @OnThread(Tag.FXPlatform)
        public RecordSetDataDisplay(String suggestedName, VirtualGridSupplierFloating destColumnHeaderSupplier, boolean showColumnTypes, ObjectExpression<@Nullable RecordSet> recordSetProperty)
        {
            super(new TableId(suggestedName), destColumnHeaderSupplier, true, showColumnTypes);
            setPosition(CellPosition.ORIGIN);
            this.recordSetProperty = recordSetProperty;
        }

        @Override
        public @OnThread(Tag.FXPlatform) void updateKnownRows(@GridAreaRowIndex int checkUpToRowInclGrid, FXPlatformRunnable updateSizeAndPositions)
        {
            RecordSet recordSet = recordSetProperty.get();
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
                            // Only on return from this method is bottom right set, so we need a run later:
                            FXUtility.runAfter(() -> numRowsChanged());
                        }
                    });
                }
                catch (UserException | InternalException e)
                {
                    Log.log(e);
                }
            });
        }

        @OnThread(Tag.FXPlatform)
        protected void numRowsChanged()
        {
        }

        @Override
        protected void doCopy(@Nullable RectangleBounds rectangleBounds)
        {
            // We don't currently support copy on this table
        }
        
        @OnThread(Tag.FXPlatform)
        public @Nullable RecordSet _test_getRecordSet()
        {
            return recordSetProperty.get();
        }
    }

    // class is public for testing purposes
    public class SrcDataDisplay extends RecordSetDataDisplay
    {
        public static final double HORIZ_INSET = 22;
        public static final double VERT_INSET = 0;
        private final RectangleOverlayItem selectionRectangle;
        private final GridArea destData;
        @OnThread(Tag.FXPlatform)
        private RectangleBounds curSelectionBounds;
        @OnThread(Tag.FXPlatform)
        private TrimChoice trim = new TrimChoice(0, 0, 0, 0);
        @OnThread(Tag.FXPlatform)
        private BoundingBox curBoundingBox;
        @OnThread(Tag.FXPlatform)
        private CellPosition mousePressed = CellPosition.ORIGIN;
        @OnThread(Tag.FXPlatform)
        private final Pane mousePane;

        @OnThread(Tag.FXPlatform)
        public SrcDataDisplay(String suggestedName, VirtualGridSupplierFloating srcColumnHeaderSupplier, ObjectExpression<@Nullable RecordSet> recordSetProperty, GridArea destData)
        {
            super(suggestedName, srcColumnHeaderSupplier, false, recordSetProperty);
            this.destData = destData;
            setPosition(CellPosition.ORIGIN.offsetByRowCols(1, 0));
            this.mousePane = new Pane();
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
            @Nullable TrimChoice[] pendingTrim = new TrimChoice[]{null};
            mousePane.setOnMouseReleased(e -> {
                mousePane.setCursor(FXUtility.mouse(this).calculateCursor(e.getX() - HORIZ_INSET, e.getY() - VERT_INSET));
                if (pendingTrim[0] != null)
                {
                    trim = pendingTrim[0];
                    destData.setPosition(curSelectionBounds.topLeftIncl.offsetByRowCols(-2, 0));
                    updateDestPreview();
                }
                e.consume();
            });
            mousePane.setOnMousePressed(e -> {
                pendingTrim[0] = null;
                withParent(p -> p.getVisibleBounds())
                    .flatMap(v -> v.getNearestTopLeftToScreenPos(new Point2D(e.getScreenX(), e.getScreenY()), HPos.LEFT, VPos.TOP))
                    .ifPresent(pos -> mousePressed = pos);
            });
            mousePane.setOnMouseDragged(e -> {
                Cursor c = mousePane.getCursor();
                Log.debug("Mouse dragged while cursor: " + c);
                withParent(p -> p.getVisibleBounds()).ifPresent(visibleBounds  -> {
                    Log.debug("We have bounds");
                    @Nullable CellPosition pos = visibleBounds.getNearestTopLeftToScreenPos(new Point2D(e.getScreenX(), e.getScreenY()), HPos.LEFT, VPos.TOP).orElse(null);
                    Log.debug("Nearest pos: " + pos);
                    boolean resizingTop = FXUtility.isResizingTop(c);
                    boolean resizingBottom = FXUtility.isResizingBottom(c);
                    boolean resizingLeft = FXUtility.isResizingLeft(c);
                    boolean resizingRight = FXUtility.isResizingRight(c);
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
                    withParent_(p -> p.positionOrAreaChanged());
                    pendingTrim[0] = new TrimChoice(
                            curSelectionBounds.topLeftIncl.rowIndex - getDataDisplayTopLeftIncl().rowIndex - getPosition().rowIndex,
                            getBottomRightIncl().rowIndex - curSelectionBounds.bottomRightIncl.rowIndex,
                            curSelectionBounds.topLeftIncl.columnIndex,
                            getBottomRightIncl().columnIndex - curSelectionBounds.bottomRightIncl.columnIndex
                    );
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

        public Node getMousePane()
        {
            return mousePane;
        }

        @OnThread(Tag.FXPlatform)
        public TrimChoice getTrim()
        {
            return trim;
        }
        
        @OnThread(Tag.FXPlatform)
        public void setTrim(TrimChoice trim)
        {
            this.trim = trim;
            curSelectionBounds = new RectangleBounds(getPosition().offsetByRowCols(1 + trim.trimFromTop, trim.trimFromLeft),
                getBottomRightIncl().offsetByRowCols(-trim.trimFromBottom, -trim.trimFromRight));
            destData.setPosition(curSelectionBounds.topLeftIncl.offsetByRowCols(-2, 0));
            withParent_(p -> p.positionOrAreaChanged());
        }

        @Override
        @OnThread(Tag.FXPlatform)
        protected void numRowsChanged()
        {
            // Update the trim:
            setTrim(trim);
            updateDestPreview();
        }

        @Override
        public @OnThread(Tag.FXPlatform) void setColumns(@UnknownInitialization(DataDisplay.class) SrcDataDisplay this, ImmutableList<ColumnDetails> columns, @Nullable TableOperations operations, @Nullable FXPlatformFunction<ColumnId, ImmutableList<ColumnOperation>> columnActions)
        {
            super.setColumns(columns, operations, columnActions);
            FXUtility.runAfter(() -> Utility.later(this).numRowsChanged());
        }

        @OnThread(Tag.FXPlatform)
        public RectangleBounds _test_getCurSelectionBounds()
        {
            return curSelectionBounds;
        }
    }
}
