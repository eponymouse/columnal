/*
 * Columnal: Safer, smoother data table processing.
 * Copyright (c) Neil Brown, 2016-2020, 2022.
 *
 * This file is part of Columnal.
 *
 * Columnal is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 *
 * Columnal is distributed in the hope that it will be useful, but WITHOUT 
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or 
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for 
 * more details.
 *
 * You should have received a copy of the GNU General Public License along 
 * with Columnal. If not, see <https://www.gnu.org/licenses/>.
 */

package xyz.columnal.importers.gui;

import annotation.units.AbsColIndex;
import annotation.units.AbsRowIndex;
import annotation.units.GridAreaRowIndex;
import annotation.units.TableDataColIndex;
import annotation.units.TableDataRowIndex;
import com.google.common.collect.ImmutableList;
import javafx.application.Platform;
import javafx.beans.binding.ObjectExpression;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.BoundingBox;
import javafx.geometry.Dimension2D;
import javafx.geometry.HPos;
import javafx.geometry.Point2D;
import javafx.geometry.VPos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Shape;
import javafx.stage.Window;
import xyz.columnal.log.Log;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.CellPosition;
import xyz.columnal.id.ColumnId;
import xyz.columnal.data.RecordSet;
import xyz.columnal.data.Table;
import xyz.columnal.data.Table.Display;
import xyz.columnal.id.TableId;
import xyz.columnal.data.TableOperations;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.gui.DataCellSupplier;
import xyz.columnal.gui.DataDisplay;
import xyz.columnal.gui.RowLabelSupplier;
import xyz.columnal.gui.grid.GridArea;
import xyz.columnal.gui.grid.RectangleBounds;
import xyz.columnal.gui.grid.RectangleOverlayItem;
import xyz.columnal.gui.grid.VirtualGrid;
import xyz.columnal.gui.grid.VirtualGridSupplier.ViewOrder;
import xyz.columnal.gui.grid.VirtualGridSupplier.VisibleBounds;
import xyz.columnal.gui.grid.VirtualGridSupplierFloating;
import xyz.columnal.gui.stable.ColumnDetails;
import xyz.columnal.gui.stable.ScrollGroup.ScrollLock;
import xyz.columnal.gui.dtf.TableDisplayUtility;
import xyz.columnal.gui.dtf.TableDisplayUtility.GetDataPosition;
import xyz.columnal.importers.GuessFormat.Import;
import xyz.columnal.importers.GuessFormat.Import.SrcDetails;
import xyz.columnal.importers.GuessFormat.ImportInfo;
import xyz.columnal.importers.GuessFormat.TrimChoice;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.function.fx.FXPlatformConsumer;
import xyz.columnal.utility.function.fx.FXPlatformFunction;
import xyz.columnal.utility.function.fx.FXPlatformRunnable;
import xyz.columnal.utility.IdentifierUtility;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.Utility;
import xyz.columnal.utility.Workers;
import xyz.columnal.utility.Workers.Priority;
import xyz.columnal.utility.gui.FXUtility;
import xyz.columnal.utility.gui.GUI;

import java.util.ArrayDeque;
import java.util.Deque;
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
    private final Label curSelectionDescription;
    // Does not include the current trim.  New items are added as last:
    private final Deque<TrimChoice> mostRecentTrimSelections = new ArrayDeque<>(32);
    private @Nullable TrimChoice curGuessTrim;
    
    // These two are only stored as fields for testing purposes:
    @OnThread(Tag.Any)
    private final SrcDataDisplay srcDataDisplay;
    @OnThread(Tag.Any)
    private final VirtualGrid srcGrid;

    // Window should only be null in test code
    public ImportChoicesDialog(@Nullable Window parentWindow, String suggestedName, Import<SRC_FORMAT, FORMAT> importer)
    {
        if (parentWindow != null)
            initOwner(parentWindow);
        this.importer = importer;
        SimpleObjectProperty<@Nullable RecordSet> srcRecordSet = new SimpleObjectProperty<>(null);
        destRecordSet = new SimpleObjectProperty<>(null);
        VirtualGrid destGrid = new VirtualGrid(null, 0, 0);
            //new MessageWhenEmpty("import.noColumnsDest", "import.noRowsDest"));
        destData = new RecordSetDataDisplay(suggestedName, destGrid.getFloatingSupplier(), true, destRecordSet);
        destGrid.addGridAreas(ImmutableList.of(destData));
        DataCellSupplier destDataCellSupplier = new DataCellSupplier(destGrid);
        destGrid.addNodeSupplier(destDataCellSupplier);
        destDataCellSupplier.addGrid(destData, destData.getDataGridCellInfo());
        //destGrid.setEditable(false);
        srcGrid = new VirtualGrid(null, 0, 0);
            //new MessageWhenEmpty("import.noColumnsSrc", "import.noRowsSrc"))
        srcGrid.getScrollGroup().add(destGrid.getScrollGroup(), ScrollLock.BOTH);
        destGrid.bindVisibleRowsTo(srcGrid);
        srcDataDisplay = new SrcDataDisplay(suggestedName, srcGrid.getFloatingSupplier(), srcRecordSet, destData);
        srcGrid.addGridAreas(ImmutableList.of(srcDataDisplay));
        DataCellSupplier srcDataCellSupplier = new DataCellSupplier(srcGrid);
        srcGrid.addNodeSupplier(srcDataCellSupplier);
        srcDataCellSupplier.addGrid(srcDataDisplay, srcDataDisplay.getDataGridCellInfo());

        RowLabelSupplier srcRowLabels = new RowLabelSupplier(srcGrid);
        srcGrid.addNodeSupplier(srcRowLabels);
        srcRowLabels.addTable(srcGrid, srcDataDisplay, true);

        RowLabelSupplier destRowLabels = new RowLabelSupplier(destGrid);
        destGrid.addNodeSupplier(destRowLabels);
        destRowLabels.addTable(destGrid, destData, true);

        //@SuppressWarnings("unchecked")
        //SegmentedButtonValue<Boolean> linkCopyButtons = new SegmentedButtonValue<>(new Pair<@LocalizableKey String, Boolean>("table.copy", false), new Pair<@LocalizableKey String, Boolean>("table.link", true));
        //choices.addRow(GUI.labelledGridRow("table.linkCopy", "guess-format/linkCopy", linkCopyButtons));

        Button resetSelectionButton = GUI.button("reset.import.selection", FXUtility.mouse(this)::resetSelectionChange);
        resetSelectionButton.setDisable(true);
        
        Node choices = this.importer.getGUI();
        
        //SimpleObjectProperty<@Nullable FORMAT> destFormatProperty = new SimpleObjectProperty<>(null);
        FXUtility.addChangeListenerPlatformAndCallNow(this.importer.currentSrcFormat(), srcFormat -> {
            if (srcFormat != null)
            {
                @NonNull SRC_FORMAT formatNonNull = srcFormat;
                Workers.onWorkerThread("Previewing data", Priority.LOAD_FROM_DISK, () -> {
                    try
                    {
                        SrcDetails loadedSrc = this.importer.loadSource(formatNonNull);
                    
                        Platform.runLater(() -> {
                            RecordSet curSrcRecordSet = srcRecordSet.get();
                            int oldColumns = curSrcRecordSet == null ? 0 : curSrcRecordSet.getColumns().size();
                            curGuessTrim = loadedSrc.trimChoice;
                            resetSelectionButton.setDisable(false);
                            srcRecordSet.set(loadedSrc.recordSet);
                            // We use trim guess if size has changed from before:
                            if (oldColumns != loadedSrc.recordSet.getColumns().size())
                            {
                                srcDataDisplay.setTrim(loadedSrc.trimChoice, true);
                            }
                            else
                            {
                                srcDataDisplay.setTrim(srcDataDisplay.getTrim(), true);
                            }
                            // Because we are in a runLater, constructor will have finished by then:
                            Utility.later(this).updateDestPreview();
                            ImmutableList<ColumnDetails> columnDetailsOrigLabels = TableDisplayUtility.makeStableViewColumns(loadedSrc.recordSet, new Pair<>(Display.ALL, c -> true), c -> null, makeGetDataPosition(), null, null);
                            ImmutableList.Builder<ColumnDetails> columnsWithDisplayNames = ImmutableList.builderWithExpectedSize(columnDetailsOrigLabels.size());
                            ImmutableList<@Localized String> columnNameOverrides = loadedSrc.columnNameOverrides;
                            if (columnNameOverrides != null && columnNameOverrides.size() == columnDetailsOrigLabels.size())
                            {
                                for (int i = 0; i < columnDetailsOrigLabels.size(); i++)
                                {

                                    columnsWithDisplayNames.add(columnDetailsOrigLabels.get(i).withDisplayHeaderLabel(columnNameOverrides.get(i)));
                                }
                            }
                            else
                                columnsWithDisplayNames.addAll(columnDetailsOrigLabels);
                            srcDataDisplay.setColumns(columnsWithDisplayNames.build(), null, null);
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
        
        srcGrid.addMousePane(srcDataDisplay.getMousePane());
        SplitPane splitPane = new SplitPane(new StackPane(srcGrid.getNode()), new StackPane(destGrid.getNode()));
        Shape arrowShape = new Path(new MoveTo(0, 8), new LineTo(40, 8), new LineTo(32, 0), new MoveTo(32, 16), new LineTo(40, 8));
        arrowShape.getStyleClass().add("import-arrow");
        BorderPane splitPanePlusHeader = GUI.borderTopCenter(GUI.borderLeftCenterRight(GUI.label("import.src.grid.label"), arrowShape, GUI.label("import.dest.grid.label"), "import-split-labels"), splitPane, "import-split-and-labels");
        this.curSelectionDescription = new Label();
        Button undoChangeSelectionButton = GUI.button("undo.import.selection.change", FXUtility.mouse(this)::undoSelectionChange);
        HBox undoResetButtons = GUI.hbox("import-undo-reset-panel", undoChangeSelectionButton, resetSelectionButton, curSelectionDescription);
        Pane content = new BorderPane(splitPanePlusHeader, choices, null, undoResetButtons, null);
        content.getStyleClass().add("guess-format-content");
        getDialogPane().getStylesheets().addAll(FXUtility.getSceneStylesheets("guess-format"));
        getDialogPane().setContent(content);
        getDialogPane().getButtonTypes().setAll(ButtonType.CANCEL, ButtonType.OK);
        // Helps with testing:
        getDialogPane().lookupButton(ButtonType.OK).getStyleClass().add("ok-button");
        // Prevent enter/escape activating buttons:
        ((Button)getDialogPane().lookupButton(ButtonType.OK)).setDefaultButton(false);
        FXUtility.preventCloseOnEscape(getDialogPane());

        //TODO disable ok button if name isn't valid
        setResultConverter(bt -> {
            if (bt == ButtonType.OK && destFormat != null)
            {
                @NonNull FORMAT f = destFormat;
                return new ImportInfo<FORMAT>(IdentifierUtility.fixExpressionIdentifier(suggestedName, "Table")/*, linkCopyButtons.valueProperty().get()*/, f);
            }
            return null;
        });
        setResizable(true);
        @Nullable Dimension2D size = parentWindow == null ? null: FXUtility.sizeOfBiggestScreen(parentWindow);
        getDialogPane().setPrefSize(size == null ? 800.0 : size.getWidth() - 100.0, size == null ? 600.0 : size.getHeight() - 100.0);


        setOnShown(e -> {
            //initModality(Modality.NONE); // For scenic view
            //org.scenicview.ScenicView.show(getDialogPane().getScene());
            currentlyShowing = Utility.later(this);
        });
        setOnHidden(e -> {
            currentlyShowing = null;
        });
    }

    private void undoSelectionChange()
    {
        // Poll removes last item, but gives null if empty:
        TrimChoice recent = mostRecentTrimSelections.pollLast();
        if (recent != null)
            srcDataDisplay.setTrim(recent, false);
    }

    private void resetSelectionChange()
    {
        if (curGuessTrim != null)
            srcDataDisplay.setTrim(curGuessTrim, true);
    }

    private GetDataPosition makeGetDataPosition(@UnknownInitialization(Object.class) ImportChoicesDialog<SRC_FORMAT, FORMAT> this)
    {
        return new GetDataPosition()
        {
            @Override
            public @OnThread(Tag.FXPlatform) CellPosition getDataPosition(@TableDataRowIndex int rowIndex, @TableDataColIndex int columnIndex)
            {
                return CellPosition.ORIGIN.offsetByRowCols(1, 1);
            }

            @SuppressWarnings("units")
            @Override
            public @OnThread(Tag.FXPlatform) @TableDataRowIndex int getFirstVisibleRowIncl()
            {
                // TODO return proper value
                return -1;
            }

            @SuppressWarnings("units")
            @Override
            public @OnThread(Tag.FXPlatform) @TableDataRowIndex int getLastVisibleRowIncl()
            {
                // TODO return proper value
                return -1;
            }
        };
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
                    destData.setColumns(TableDisplayUtility.makeStableViewColumns(loadedDest.getSecond(), new Pair<>(Display.ALL, c -> true), c -> null, makeGetDataPosition(), null, null), null, null);
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
            super(new TableId(IdentifierUtility.fixExpressionIdentifier(suggestedName, "Table")), destColumnHeaderSupplier, true, showColumnTypes);
            setPosition(CellPosition.ORIGIN.offsetByRowCols(0, 1));
            this.recordSetProperty = recordSetProperty;
        }

        @Override
        protected boolean isShowingRowLabels()
        {
            return true;
        }

        @Override
        public String getSortKey()
        {
            return "";
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
        protected @TableDataRowIndex int getCurrentKnownRows()
        {
            return currentKnownRows;
        }

        @Override
        protected CellPosition getDataPosition(@TableDataRowIndex int rowIndex, @TableDataColIndex int columnIndex)
        {
            return getPosition().offsetByRowCols(2 + rowIndex, columnIndex);
        }

        @Override
        protected @Nullable FXPlatformConsumer<TableId> renameTableOperation(Table table)
        {
            return null;
        }

        @Override
        public void doCopy(@Nullable RectangleBounds rectangleBounds)
        {
            // We don't currently support copy on this table
        }

        @Override
        public void doDelete()
        {
            // Not supported
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
            setPosition(CellPosition.ORIGIN.offsetByRowCols(1, 1));
            this.mousePane = new Pane();
            CellPosition bottomRight = getBottomRightIncl();
            this.curSelectionBounds = new RectangleBounds(getPosition().offsetByRowCols(Math.min(1, bottomRight.rowIndex - getPosition().rowIndex), 0), bottomRight);
            // Will be set right at first layout:
            this.curBoundingBox = new BoundingBox(0, 0, 0, 0);
            this.selectionRectangle = new RectangleOverlayItem(ViewOrder.CELL_SELECTION) {
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
                        
                    return Optional.of(Either.<BoundingBox, RectangleBounds>right(curSelectionBounds));
                }

                @Override
                protected void styleNewRectangle(Rectangle r, VisibleBounds visibleBounds)
                {
                    r.getStyleClass().add("prospective-import-rectangle");
                }
            };
            srcColumnHeaderSupplier.addItem(this.selectionRectangle);
            mousePane.setOnMouseMoved(e -> {
                mousePane.setCursor(FXUtility.mouse(this).calculateCursor(e.getX(), e.getY()));
                withParent_(g -> g.handlePossibleNudgeEvent(e));
                e.consume();
            });
            @Nullable TrimChoice[] pendingTrim = new TrimChoice[]{null};
            mousePane.setOnMouseReleased(e -> {
                withParent_(g -> g.setNudgeScroll(false));
                mousePane.setCursor(FXUtility.mouse(this).calculateCursor(e.getX(), e.getY()));
                if (pendingTrim[0] != null)
                {
                    FXUtility.mouse(this).setTrim(pendingTrim[0], true);
                    updateDestPreview();
                }
                else
                {
                    // Will set rectangle back:
                    FXUtility.mouse(this).setTrim(trim, true);
                }
                e.consume();
            });
            mousePane.setOnMousePressed(e -> {
                pendingTrim[0] = null;
                withParent_(g -> g.setNudgeScroll(e.isPrimaryButtonDown()));
                withParent(p -> p.getVisibleBounds())
                    .flatMap(v -> v.getNearestTopLeftToScreenPos(new Point2D(e.getScreenX(), e.getScreenY()), HPos.LEFT, VPos.TOP))
                    .map(pos -> {
                        return new CellPosition(Utility.maxRow(CellPosition.row(2), pos.rowIndex), pos.columnIndex);
                    })
                    .ifPresent(pos -> mousePressed = pos);
            });
            mousePane.setOnMouseDragged(e -> {
                Cursor c = mousePane.getCursor();
                withParent_(g -> g.handlePossibleNudgeEvent(e));
                
                //Log.debug("Mouse dragged while cursor: " + c);
                withParent(p -> p.getVisibleBounds()).ifPresent(visibleBounds  -> {
                    //Log.debug("We have bounds");
                    @Nullable CellPosition pos = visibleBounds.getNearestTopLeftToScreenPos(new Point2D(e.getScreenX(), e.getScreenY()), HPos.LEFT, VPos.TOP).orElse(null);
                    //Log.debug("Nearest pos: " + pos);
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
                        newTop = Utility.maxRow(CellPosition.row(2), newTop);
                        newBottom = Utility.maxRow(newTop, newBottom);
                        newRight = Utility.maxCol(newLeft, newRight);
                    }
                    else
                    {
                        if (pos == null)
                            return;
                        // Drag from the original position where they pressed:
                        newTop = Utility.maxRow(CellPosition.row(2), Utility.minRow(pos.rowIndex, mousePressed.rowIndex));
                        newBottom = Utility.maxRow(CellPosition.row(2), Utility.maxRow(pos.rowIndex, mousePressed.rowIndex));
                        newLeft = Utility.minCol(pos.columnIndex, mousePressed.columnIndex);
                        newRight = Utility.maxCol(pos.columnIndex, mousePressed.columnIndex);
                    }
                    curSelectionBounds = new RectangleBounds(new CellPosition(newTop, newLeft), new CellPosition(newBottom, newRight));
                    withParent_(p -> p.positionOrAreaChanged());
                    pendingTrim[0] = new TrimChoice(
                            curSelectionBounds.topLeftIncl.rowIndex - getDataDisplayTopLeftIncl().rowIndex - getPosition().rowIndex,
                            getBottomRightIncl().rowIndex - curSelectionBounds.bottomRightIncl.rowIndex,
                            curSelectionBounds.topLeftIncl.columnIndex - getDataDisplayTopLeftIncl().columnIndex - getPosition().columnIndex,
                            getBottomRightIncl().columnIndex - curSelectionBounds.bottomRightIncl.columnIndex
                    );
                    FXUtility.mouse(this).updateDescription(curSelectionBounds);
                });
            });
            mousePane.setOnScroll(e -> {
                withParent_(g -> g.getScrollGroup().requestScroll(e));
                // In case of small slide scroll, need to recalculate cursor:
                withParent(g -> g.getVisibleBounds()).ifPresent(b -> selectionRectangle.calculatePosition(b));
                mousePane.setCursor(FXUtility.mouse(this).calculateCursor(e.getX(), e.getY()));
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
        public void cleanupFloatingItems(VirtualGridSupplierFloating floatingItems)
        {
            super.cleanupFloatingItems(floatingItems);
            withParent_(p -> p.getFloatingSupplier().removeItem(selectionRectangle));
        }

        public Pane getMousePane()
        {
            return mousePane;
        }

        @OnThread(Tag.FXPlatform)
        public TrimChoice getTrim()
        {
            return trim;
        }
        
        @OnThread(Tag.FXPlatform)
        public void setTrim(TrimChoice trim, boolean addToUndoChain)
        {
            // Add old trim:
            if (addToUndoChain && !this.trim.equals(mostRecentTrimSelections.peekLast()))
            {
                while (mostRecentTrimSelections.size() + 1 >= 32)
                    mostRecentTrimSelections.removeFirst();
                mostRecentTrimSelections.addLast(this.trim);
            }
            this.trim = trim;            
            curSelectionBounds = RectangleBounds.fixBottomRight(getPosition().offsetByRowCols(1 + trim.trimFromTop, trim.trimFromLeft),
                getBottomRightIncl().offsetByRowCols(-trim.trimFromBottom, -trim.trimFromRight));
            destData.setPosition(curSelectionBounds.topLeftIncl.offsetByRowCols(-2, 0));
            withParent_(p -> p.positionOrAreaChanged());
            updateDescription(curSelectionBounds);
        }

        @OnThread(Tag.FXPlatform)
        private void updateDescription(RectangleBounds selectionBounds)
        {
            // Update description:
            StringBuilder sel = new StringBuilder();
            // Rows take account of +1 for one-based indexes, but -2 for header rows.
            sel.append("Rows \u2195 ").append(-1 + selectionBounds.topLeftIncl.rowIndex).append("-").append(-1 + selectionBounds.bottomRightIncl.rowIndex)
                .append(" of ").append(srcDataDisplay.currentKnownRows).append(".  ");
            sel.append("Columns \u2194 ").append( selectionBounds.topLeftIncl.columnIndex).append("-").append(selectionBounds.bottomRightIncl.columnIndex)
                .append(" of ").append(srcDataDisplay.displayColumns.size()).append(".");

            curSelectionDescription.setText(sel.toString());
        }

        @Override
        @OnThread(Tag.FXPlatform)
        protected void numRowsChanged()
        {
            // Update the trim:
            setTrim(trim, true);
            updateDestPreview();
        }

        @Override
        public @OnThread(Tag.FXPlatform) void setColumns(@UnknownInitialization(DataDisplay.class) SrcDataDisplay this, ImmutableList<ColumnDetails> columns, @Nullable TableOperations operations, @Nullable FXPlatformFunction<ColumnId, ColumnHeaderOps> columnActions)
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
