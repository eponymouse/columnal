package records.gui.table;

import annotation.units.AbsColIndex;
import annotation.units.GridAreaRowIndex;
import annotation.units.TableDataColIndex;
import annotation.units.TableDataRowIndex;
import com.google.common.collect.ImmutableList;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.geometry.BoundingBox;
import javafx.geometry.Point2D;
import javafx.scene.Cursor;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.stage.Window;
import log.Log;
import org.checkerframework.checker.initialization.qual.Initialized;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.CellPosition;
import records.data.ColumnId;
import records.data.TableOperations.RenameTable;
import records.data.datatype.DataType;
import records.gui.EntireTableSelection;
import records.transformations.expression.explanation.Explanation;
import records.data.Table;
import records.data.Table.Display;
import records.data.Table.TableDisplayBase;
import records.data.TableId;
import records.error.InternalException;
import records.error.UserException;
import records.gui.View;
import records.gui.grid.CellSelection;
import records.gui.grid.GridArea;
import records.gui.grid.RectangleBounds;
import records.gui.grid.VirtualGrid.SelectionListener;
import records.gui.grid.VirtualGridSupplier.ItemState;
import records.gui.grid.VirtualGridSupplier.ViewOrder;
import records.gui.grid.VirtualGridSupplier.VisibleBounds;
import records.gui.grid.VirtualGridSupplierFloating;
import records.gui.grid.VirtualGridSupplierFloating.FloatingItem;
import records.transformations.Check;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.FXPlatformConsumer;
import utility.FXPlatformRunnable;
import utility.Pair;
import utility.Utility;
import utility.Workers;
import utility.Workers.Priority;
import utility.gui.FXUtility;
import utility.gui.GUI;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Display of a Check transformation.  Has two cells: a title
 * above a cell showing the result.
 */
@OnThread(Tag.FXPlatform)
public final class CheckDisplay extends HeadedDisplay implements TableDisplayBase, SelectionListener
{
    private final Check check;
    @OnThread(Tag.Any)
    private final AtomicReference<CellPosition> mostRecentBounds;
    private final View parent;
    private final TableHat tableHat;
    private final TableBorderOverlay tableBorderOverlay;
    private final FloatingItem<Label> resultFloatingItem;
    private final StringProperty resultContent = new SimpleStringProperty("");
    private final ObjectProperty<@Nullable Explanation> failExplanationProperty = new SimpleObjectProperty<>(null);

    public CheckDisplay(View parent, VirtualGridSupplierFloating floatingSupplier, Check check)
    {
        super(new TableHeaderItemParams(parent.getManager(), check.getId(), check, floatingSupplier), floatingSupplier);
        this.check = check;
        this.parent = parent;
        mostRecentBounds = new AtomicReference<>(getPosition());
        
        this.resultFloatingItem = new FloatingItem<Label>(ViewOrder.STANDARD_CELLS) {

            private @MonotonicNonNull Label label;

            @Override
            protected Optional<BoundingBox> calculatePosition(VisibleBounds visibleBounds)
            {
                CellPosition titlePos = getPosition();
                double left = visibleBounds.getXCoord(titlePos.columnIndex);
                double right = visibleBounds.getXCoordAfter(titlePos.columnIndex);
                double top = visibleBounds.getYCoord(titlePos.rowIndex + CellPosition.row(1));
                double bottom = visibleBounds.getYCoordAfter(titlePos.rowIndex + CellPosition.row(1));
                return Optional.of(new BoundingBox(left, top, right - left, bottom - top));
            }

            @Override
            protected Label makeCell(VisibleBounds visibleBounds)
            {
                Label labelFinal = label = new Label("");
                label.getStyleClass().add("check-result");
                label.textProperty().bind(resultContent);
                FXUtility.addChangeListenerPlatformNN(label.hoverProperty(), h -> {
                    if (h && failExplanationProperty.get() != null)
                    {
                        labelFinal.setUnderline(true);
                        labelFinal.setCursor(Cursor.HAND);
                    }
                    else
                    {
                        labelFinal.setUnderline(false);
                        labelFinal.setCursor(null);
                    }
                });
                label.setOnMouseClicked(e -> {
                    FXUtility.mouse(this).showExplanation();
                });
                return label;
            }

            @OnThread(Tag.FXPlatform)
            private void showExplanation()
            {
                @Nullable Explanation explanation = failExplanationProperty.get();
                if (explanation != null)
                {
                    parent.showExplanationDisplay(check, check.getSrcTableId(), getPosition().offsetByRowCols(1, 0), explanation);
                }
            }

            @Override
            public @Nullable Pair<ItemState, @Nullable StyledString> getItemState(CellPosition cellPosition, Point2D screenPos)
            {
                @Nullable StyledString prompt = null;
                if (label != null && label.prefWidth(-1) > label.getWidth() + 3)
                    prompt = StyledString.s(label.getText());
                return getPosition().offsetByRowCols(1, 0).equals(cellPosition) ? new Pair<>(ItemState.DIRECTLY_CLICKABLE, prompt) : null;
            }

            @Override
            public void keyboardActivate(CellPosition cellPosition)
            {
                FXUtility.runAfter(() -> showExplanation());
            }
        };
        floatingSupplier.addItem(resultFloatingItem);

        // Border overlay.  Note this makes use of calculations based on hat and row label border,
        // so it is important that we add this after them (since floating supplier iterates in order of addition):
        tableBorderOverlay = new TableBorderOverlay();
        floatingSupplier.addItem(tableBorderOverlay);

        @SuppressWarnings("initialization") // Don't understand why I need this
        @Initialized TableHat hat = new TableHat(this, parent, check);
        this.tableHat = hat;
        floatingSupplier.addItem(this.tableHat);

        Workers.onWorkerThread("Loading check result", Priority.FETCH, () -> {
            try
            {
                boolean pass = Utility.cast(check.getData().getColumns().get(0).getType().getCollapsed(0), Boolean.class);
                @Nullable Explanation failExplanation = pass ? null : check.getExplanation();
                Platform.runLater(() -> {
                    resultContent.set(pass ? "OK" : "Fail");
                    failExplanationProperty.set(failExplanation);
                    if (CheckDisplay.this.tableHeaderItem != null)
                        CheckDisplay.this.tableHeaderItem.setPseudoclass("check-failing", !pass);
                });
            }
            catch (UserException | InternalException e)
            {
                @Nullable Explanation errorExplanation = check.getExplanation();
                if (errorExplanation != null)
                {
                    Platform.runLater(() -> {
                        resultContent.set("Error");
                        failExplanationProperty.set(errorExplanation);
                        if (CheckDisplay.this.tableHeaderItem != null)
                            CheckDisplay.this.tableHeaderItem.setPseudoclass("check-failing", true);
                    });
                }
                else
                {
                    Log.log(e);
                    Platform.runLater(() -> {
                        resultContent.set("ERR:" + e.getLocalizedMessage());
                    });
                }
            }
        });

        // Must be done as last item:
        @Initialized CheckDisplay usInit = this;
        this.check.setDisplay(usInit);
    }

    /*
    private @Nullable CellSelection makeSelection(TableManager tableManager, ExplanationLocation explanationLocation)
    {
        Table table = tableManager.getSingleTableOrNull(explanationLocation.tableId);
        if (table == null)
            return null;
        @Nullable TableDisplayBase display = table.getDisplay();
        if (display instanceof TableDisplay)
        {
            TableDisplay tableDisplay = (TableDisplay) display;
            ImmutableList<ColumnDetails> displayColumns = tableDisplay.getDisplayColumns();
            for (int i = 0; i < displayColumns.size(); i++)
            {
                ColumnDetails displayColumn = displayColumns.get(i);
                if (displayColumn.getColumnId().equals(explanationLocation.columnId))
                {
                    @TableDataColIndex int relColumnIndex = DataItemPosition.col(i);
                    CellPosition targetPos = tableDisplay.getDataPosition(explanationLocation.rowIndex, relColumnIndex);
                    return tableDisplay.getSelectionForSingleCell(targetPos);
                }
            }
        }
        return null;
    }
    */

    @Override
    public void cleanupFloatingItems(VirtualGridSupplierFloating floating)
    {
        super.cleanupFloatingItems(floating);
        floating.removeItem(tableBorderOverlay);
        floating.removeItem(tableHat);
        floating.removeItem(resultFloatingItem);
        parent.removeExplanationDisplayFor(check);
    }

    @Override
    @SuppressWarnings("units")
    protected @TableDataRowIndex int getCurrentKnownRows()
    {
        return 1;
    }

    @Override
    protected CellPosition getDataPosition(@TableDataRowIndex int rowIndex, @TableDataColIndex int columnIndex)
    {
        return getPosition().offsetByRowCols(1 + rowIndex, columnIndex);
    }

    @Override
    protected @Nullable FXPlatformConsumer<TableId> renameTableOperation(Table table)
    {
        @Nullable RenameTable renameTable = table.getOperations().renameTable;
        if (renameTable == null)
            return null;
        @NonNull RenameTable renameTableFinal = renameTable;

        return newTableId -> {
            if (Objects.equals(newTableId, check.getId()))
                return; // Ignore if hasn't actually changed

            if (newTableId != null)
                Workers.onWorkerThread("Renaming table", Priority.SAVE, () -> renameTableFinal.renameTable(newTableId));
        };
    }

    @Override
    public void gotoRow(Window parent, @AbsColIndex int column)
    {
        // Only one row...
    }

    @Override
    public void doCopy(@Nullable RectangleBounds bounds)
    {
        // Nothing really to copy
    }

    @Override
    public void doDelete()
    {
        Workers.onWorkerThread("Deleting table", Priority.SAVE, () -> FXUtility.alertOnError_("Deleting check", () -> check.getManager().remove(check.getId())));
    }

    @Override
    protected boolean isShowingRowLabels()
    {
        return false;
    }

    @Override
    protected void setTableDragSource(boolean on, BorderPane tableNamePane)
    {
        // TODO
    }

    @Override
    public @OnThread(Tag.FXPlatform) void loadPosition(CellPosition position, Pair<Display, ImmutableList<ColumnId>> display)
    {
        this.setPosition(position);
    }

    @Override
    public @OnThread(Tag.Any) CellPosition getMostRecentPosition()
    {
        return mostRecentBounds.get();
    }

    @Override
    public @OnThread(Tag.FXPlatform) void promptForTransformationEdit(int index, Pair<ColumnId, DataType> column, Either<String, Object> value)
    {
        // Not applicable for CheckDisplay, so do nothing
    }

    @Override
    protected @OnThread(Tag.FXPlatform) void updateKnownRows(@GridAreaRowIndex int checkUpToRowIncl, FXPlatformRunnable updateSizeAndPositions)
    {
        // Nothing to do
    }

    @Override
    protected CellPosition recalculateBottomRightIncl()
    {
        return getPosition().offsetByRowCols(1, 0);
    }

    @Override
    public @Nullable CellSelection getSelectionForSingleCell(CellPosition cellPosition)
    {
        if (cellPosition.equals(getPosition()))
            return new EntireTableSelection(this, cellPosition.columnIndex);
        
        if (!cellPosition.equals(getPosition().offsetByRowCols(1, 0)))
            return null;
        
        return new CellSelection()
        {
            @Override
            public void doCopy()
            {
                // N/A
            }

            @Override
            public void doPaste()
            {
                // N/A
            }

            @Override
            public void doDelete()
            {
                // N/A
            }

            @Override
            public void notifySelected(boolean selected, boolean animateFlash)
            {
            }

            @Override
            public CellPosition getActivateTarget()
            {
                return cellPosition;
            }

            @Override
            public CellSelection atHome(boolean extendSelection)
            {
                return this;
            }

            @Override
            public CellSelection atEnd(boolean extendSelection)
            {
                return this;
            }

            @Override
            public Either<CellPosition, CellSelection> move(boolean extendSelection, int byRows, int byColumns)
            {
                return Either.left(cellPosition.offsetByRowCols(byRows, byColumns));
            }

            @Override
            public @Nullable CellSelection extendTo(CellPosition cellPosition)
            {
                return null;
            }

            @Override
            public CellPosition positionToEnsureInView()
            {
                return cellPosition;
            }

            @Override
            public RectangleBounds getSelectionDisplayRectangle()
            {
                return new RectangleBounds(cellPosition, cellPosition);
            }

            @Override
            public boolean isExactly(CellPosition pos)
            {
                return cellPosition.equals(pos);
            }

            @Override
            public boolean includes(@UnknownInitialization(GridArea.class) GridArea tableDisplay)
            {
                return tableDisplay == CheckDisplay.this;
            }

            @Override
            public void gotoRow(Window parent)
            {
                // N/A
            }
        };
    }

    @Override
    public String getSortKey()
    {
        return check.getId().getRaw();
    }

    @Override
    public void setPosition(@UnknownInitialization(GridArea.class) CheckDisplay this, CellPosition cellPosition)
    {
        super.setPosition(cellPosition);
        if (mostRecentBounds != null)
            mostRecentBounds.set(getPosition());
    }

    @Override
    protected ImmutableList<String> getExtraTitleStyleClasses()
    {
        return ImmutableList.of("check-table-title");
    }

    @Override
    protected @Nullable ContextMenu getTableHeaderContextMenu()
    {
        return new ContextMenu(
            GUI.menuItem("tableDisplay.menu.delete", () -> {
                Workers.onWorkerThread("Deleting " + check.getId(), Workers.Priority.SAVE, () ->
                    parent.getManager().remove(check.getId())
                );
            })
        );
    }
}
