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

package xyz.columnal.gui;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import javafx.application.Platform;
import javafx.beans.binding.ObjectExpression;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableObjectValue;
import javafx.geometry.BoundingBox;
import javafx.geometry.Point2D;
import javafx.scene.Scene;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.effect.Effect;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.input.Clipboard;
import javafx.scene.input.DataFormat;
import javafx.scene.input.MouseButton;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import javafx.util.Duration;
import xyz.columnal.data.CellPosition;
import xyz.columnal.data.DataSource;
import xyz.columnal.data.EditableColumn;
import xyz.columnal.data.EditableRecordSet;
import xyz.columnal.data.GridComment;
import xyz.columnal.data.ImmediateDataSource;
import xyz.columnal.data.RecordSet;
import xyz.columnal.data.Settings;
import xyz.columnal.data.Table;
import xyz.columnal.data.TableManager;
import xyz.columnal.data.Transformation;
import xyz.columnal.id.ColumnId;
import xyz.columnal.id.SaveTag;
import xyz.columnal.id.TableId;
import xyz.columnal.log.Log;
import org.apache.commons.io.FileUtils;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.initialization.qual.Initialized;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.PolyNull;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;
import org.checkerframework.dataflow.qual.Pure;
import xyz.columnal.data.Table.InitialLoadDetails;
import xyz.columnal.data.Table.FullSaver;
import xyz.columnal.data.Table.TableDisplayBase;
import xyz.columnal.data.TableManager.TableManagerListener;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.gui.EditImmediateColumnDialog.InitialFocus;
import xyz.columnal.gui.NewTableDialog.DataOrTransform;
import xyz.columnal.gui.EditImmediateColumnDialog.ColumnDetails;
import xyz.columnal.gui.grid.CellSelection;
import xyz.columnal.gui.grid.GridArea;
import xyz.columnal.gui.grid.RectangleBounds;
import xyz.columnal.gui.grid.VirtualGrid;
import xyz.columnal.gui.grid.VirtualGrid.VirtualGridManager;
import xyz.columnal.gui.grid.VirtualGridLineSupplier;
import xyz.columnal.gui.grid.VirtualGridSupplier.ItemState;
import xyz.columnal.gui.grid.VirtualGridSupplier.ViewOrder;
import xyz.columnal.gui.grid.VirtualGridSupplier.VisibleBounds;
import xyz.columnal.gui.grid.VirtualGridSupplierFloating;
import xyz.columnal.gui.grid.VirtualGridSupplierFloating.FloatingItem;
import xyz.columnal.gui.highlights.TableHighlights;
import xyz.columnal.gui.highlights.TableHighlights.HighlightType;
import xyz.columnal.gui.highlights.TableHighlights.PickResult;
import xyz.columnal.gui.highlights.TableHighlights.Picker;
import xyz.columnal.gui.lexeditor.ExpressionEditor;
import xyz.columnal.gui.settings.EditSettingsDialog;
import xyz.columnal.gui.table.CheckDisplay;
import xyz.columnal.gui.table.ExplanationDisplay;
import xyz.columnal.gui.table.TableDisplay;
import xyz.columnal.importers.ClipboardUtils;
import xyz.columnal.importers.ClipboardUtils.LoadedColumnInfo;
import xyz.columnal.importers.TextImporter;
import xyz.columnal.importers.manager.ImporterManager;
import xyz.columnal.plugins.PluginManager;
import xyz.columnal.transformations.Check;
import xyz.columnal.transformations.Check.CheckType;
import xyz.columnal.transformations.RTransformation;
import xyz.columnal.transformations.TransformationManager;
import xyz.columnal.transformations.expression.BooleanLiteral;
import xyz.columnal.transformations.expression.explanation.Explanation;
import xyz.columnal.styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.function.fx.FXPlatformConsumer;
import xyz.columnal.utility.function.fx.FXPlatformFunction;
import xyz.columnal.utility.function.fx.FXPlatformRunnable;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.function.simulation.SimulationConsumerNoError;
import xyz.columnal.utility.function.simulation.SimulationFunction;
import xyz.columnal.utility.function.simulation.SimulationRunnable;
import xyz.columnal.utility.function.simulation.SimulationSupplier;
import xyz.columnal.utility.TranslationUtility;
import xyz.columnal.utility.Utility;
import xyz.columnal.utility.Workers;
import xyz.columnal.utility.Workers.Priority;
import xyz.columnal.utility.gui.DimmableParent;
import xyz.columnal.utility.gui.FXUtility;

import javax.swing.filechooser.FileSystemView;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by neil on 18/10/2016.
 */
@OnThread(Tag.FXPlatform)
public class View extends StackPane implements DimmableParent, ExpressionEditor.ColumnPicker
{
    private static final double DEFAULT_SPACE = 150.0;

    private final UndoManager undoManager = new UndoManager();
    //private final ObservableMap<Transformation, Overlays> overlays;
    private final TableManager tableManager;
    // The pane which actually holds the TableDisplay items:
    private final VirtualGrid mainPane;
    private final TableHighlights tableHighlights;
    private final Pane overlayPane;
    private final Pane snapGuidePane;
    // The STF supplier for the main pane:
    private final DataCellSupplier dataCellSupplier;
    // The supplier for buttons to add rows and columns:
    private final ExpandTableArrowSupplier expandTableArrowSupplier;
    // The supplier for row labels:
    private final RowLabelSupplier rowLabelSupplier;
    private final HintMessage hintMessage;
    private @Nullable Pair<Table, ExplanationDisplay> explanationDisplay;

    // This is only put into our children while we are doing special mouse capture, but it is always non-null.
    private @Nullable Pane pickPaneMouse;
    @OnThread(Tag.FXPlatform)
    private final ObjectProperty<File> diskFile;
    // Null means modified since last save
    private final ObjectProperty<@Nullable Instant> lastSaveTime = new SimpleObjectProperty<>(Instant.now());
    // Cancels a delayed save operation:
    private @Nullable FXPlatformRunnable cancelDelayedSave;

    // We start in readOnly mode, and enable writing later if everything goes well:
    private boolean readOnly = true;
    private int saveCount = 0;

    void save(boolean keepPrevForUndo)
    {
        // Log.logStackTrace("Save requested, R/O: " + readOnly);
        
        if (readOnly)
            return;
        
        saveCount += 1;
        
        doSaveTo(keepPrevForUndo, diskFile.get());
    }

    void doSaveTo(boolean keepPrevForUndo, File dest)
    {
        ImmutableList<String> displayDetailLines = mainPane.getCustomisedColumnWidths().entrySet().stream().sorted(Comparator.comparing(e -> e.getKey())).map(e -> String.format("COLUMNWIDTH %d %d", e.getKey(), Math.round(e.getValue()))).collect(ImmutableList.<String>toImmutableList());
        Workers.onWorkerThread("Saving file", Priority.SAVE, () ->
        {
            FullSaver fetcher = new FullSaver(displayDetailLines);
            tableManager.save(dest, fetcher);
            String completeFile = fetcher.getCompleteFile();
            Utility.saveLock.lock();
            Instant now = Instant.now();
            try
            {
                // This will do backup for undo, but also for
                // files being replaced, in extreme cases:
                if (dest.exists() && keepPrevForUndo)
                {
                    undoManager.backupForUndo(dest, now);
                }
                FileUtils.writeStringToFile(dest, completeFile, "UTF-8");
            }
            catch (IOException ex)
            {
                FXUtility.logAndShowError("save.error", ex);
            }
            finally
            {
                Utility.saveLock.unlock();
            }
            boolean hasBannedR = tableManager.getAllTables().stream().anyMatch(t -> t instanceof RTransformation && tableManager.isBannedRExpression(((RTransformation)t).getRExpression()));
            if (!hasBannedR)
                recordFileHash(dest, Hashing.sha256().hashString(completeFile, StandardCharsets.UTF_8));
            Platform.runLater(() -> lastSaveTime.setValue(now));
        });
    }

    @OnThread(Tag.Any)
    private synchronized @NonNull List<Table> getAllTables()
    {
        return tableManager.getAllTables();
    }

    @OnThread(Tag.Any)
    public TableManager getManager()
    {
        return tableManager;
    }
    
    @OnThread(Tag.Simulation)
    private void removeTable(Table t, int remainingCount)
    {
        FXUtility.runFX(() ->
        {
            save(true);
            //overlays.remove(t); // Listener removes them from display
            TableDisplayBase displayBase = t.getDisplay();
            CellPosition topLeft = displayBase == null ? CellPosition.ORIGIN : displayBase.getMostRecentPosition();
            boolean wasSelected = false;
            if (displayBase != null && displayBase instanceof TableDisplay)
            {
                TableDisplay display = (TableDisplay) displayBase;
                wasSelected = mainPane.selectionIncludes(display);
                // Call this first so that the nodes are actually removed when we call removeGridArea:
                display.cleanupFloatingItems(getGrid().getFloatingSupplier());
                dataCellSupplier.removeGrid(display);
                mainPane.removeSelectionListener(display);
                expandTableArrowSupplier.removeGrid(display);
                rowLabelSupplier.removeGrid(display, mainPane.getContainerChildren());
                // This goes last because it will redo layout:
                mainPane.removeGridArea(display);
            }
            else if (displayBase != null && displayBase instanceof CheckDisplay)
            {
                CheckDisplay checkDisplay = (CheckDisplay) displayBase;
                wasSelected = mainPane.selectionIncludes(checkDisplay);
                checkDisplay.cleanupFloatingItems(getGrid().getFloatingSupplier());
                mainPane.removeSelectionListener(checkDisplay);
                // This goes last because it will redo layout:
                mainPane.removeGridArea(checkDisplay);
            }
            if (wasSelected)
            {
                mainPane.findAndSelect(Either.left(topLeft));
            }
            hintMessage.updateState();
            FXUtility.setPseudoclass(View.this, "empty", tableManager.getAllTables().isEmpty());
        });
    }

    public void setDiskFileAndSave(File newDest)
    {
        diskFile.set(newDest);
        save(true);
        Utility.usedFile(newDest);
    }

    public ObservableObjectValue<@Nullable Instant> lastSaveTime()
    {
        return lastSaveTime;
    }

    public void ensureSaved()
    {
        //TODO
    }

    public void modified()
    {
        lastSaveTime.set(null);
        // TODO use a timer rather than saving instantly every time?
        save(true);
    }

    @OnThread(Tag.Any)
    @Pure
    public VirtualGrid getGrid()
    {
        return mainPane;
    }

    @OnThread(Tag.FXPlatform)
    public void disablePickingMode()
    {
        if (pickPaneMouse != null)
        {
            tableHighlights.stopHighlightingGridArea();
            getChildren().remove(pickPaneMouse);
            pickPaneMouse = null;
        }
    }
    
    @OnThread(Tag.FXPlatform)
    public void enableTablePickingMode(Point2D screenPos, ObjectExpression<@PolyNull Scene> sceneProperty, ImmutableSet<Table> excludeTables, FXPlatformConsumer<Table> onPick)
    {
        if (pickPaneMouse != null)
            disablePickingMode();
        
        final @NonNull Pane pickPaneMouseFinal = pickPaneMouse = new Pane();
        
        pickPaneMouseFinal.setPickOnBounds(true);
        @Nullable Table[] picked = new @Nullable Table[1];
        Picker<Table> validPick = p -> {
            if (p == null)
                return null;
            GridArea g = p.getFirst();
            if (g instanceof TableDisplay && !excludeTables.contains(((TableDisplay) g).getTable()))
            {
                return new PickResult<>(ImmutableList.of(new RectangleBounds(g.getPosition(), g.getBottomRightIncl())), HighlightType.SELECT, ((TableDisplay) g).getTable(), getWindowTargetPoint(sceneProperty));
            }
            else
                return null;
        };
        pickPaneMouseFinal.setOnMouseMoved(e -> {
            picked[0] = tableHighlights.highlightAtScreenPos(
                new Point2D(e.getScreenX(), e.getScreenY()),
                validPick,
                pickPaneMouseFinal::setCursor);
            e.consume();
        });
        pickPaneMouseFinal.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY && picked[0] != null)
                onPick.consume(picked[0]);
        });
        pickPaneMouseFinal.addEventFilter(ScrollEvent.ANY, scrollEvent -> {
            getGrid().getScrollGroup().requestScroll(scrollEvent);
            scrollEvent.consume();
        });
        getChildren().add(pickPaneMouseFinal);
        // Highlight immediately:
        tableHighlights.highlightAtScreenPos(screenPos, validPick, pickPaneMouseFinal::setCursor);
    }

    private static ImmutableList<FXPlatformFunction<Point2D, Point2D>> getWindowTargetPoint(ObjectExpression<@PolyNull Scene> sceneProperty)
    {
        Scene scene = sceneProperty.get();
        if (scene != null)
        {
            Window window = scene.getWindow();
            if (window != null)
            {
                BoundingBox bounds = FXUtility.getWindowBounds(window);
                return ImmutableList.of(from -> new Point2D(Utility.clampIncl(bounds.getMinX(), from.getX(), bounds.getMaxX()), Utility.clampIncl(bounds.getMinY(), from.getY(), bounds.getMaxY())));
            }
        }
        return ImmutableList.of();
    }

    @OnThread(Tag.FXPlatform)
    public void enableColumnPickingMode(@Nullable Point2D screenPos, ObjectExpression<@PolyNull Scene> sceneProperty, Predicate<Pair<Table, ColumnId>> includeColumn, FXPlatformConsumer<Pair<Table, ColumnId>> onPick)
    {
        if (pickPaneMouse != null)
            disablePickingMode();

        final @NonNull Pane pickPaneMouseFinal = pickPaneMouse = new Pane();

        pickPaneMouseFinal.setPickOnBounds(true);
        class Ref
        {
            @Nullable Pair<Table, ColumnId> pick = null;
        }
        Ref picked = new Ref();
        Picker<Pair<Table, ColumnId>> validPick = p -> {
            if (p == null)
                return null;
            GridArea g = p.getFirst();
            CellPosition cell = p.getSecond();
            if (!(g instanceof TableDisplay))
                return null;
            TableDisplay tableDisplay = (TableDisplay) g;
            @Nullable Pair<ColumnId, RectangleBounds> c = tableDisplay.getColumnAt(cell);
            if (c != null && includeColumn.test(new Pair<>(tableDisplay.getTable(), c.getFirst())))
            {
                return new PickResult<>(ImmutableList.of(c.getSecond()), HighlightType.SELECT, new Pair<>(tableDisplay.getTable(), c.getFirst()), getWindowTargetPoint(sceneProperty));
            }
            return null;
        };
        pickPaneMouseFinal.setOnMouseMoved(e -> {
            picked.pick = tableHighlights.highlightAtScreenPos(
                new Point2D(e.getScreenX(), e.getScreenY()),
                validPick,
                pickPaneMouseFinal::setCursor);
            e.consume();
        });
        pickPaneMouseFinal.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY && picked.pick != null)
                onPick.consume(picked.pick);
        });
        pickPaneMouseFinal.addEventFilter(ScrollEvent.ANY, scrollEvent -> {
            getGrid().getScrollGroup().requestScroll(scrollEvent);
            scrollEvent.consume();
        });
        getChildren().add(pickPaneMouseFinal);
        // Highlight immediately:
        if (screenPos != null)
            tableHighlights.highlightAtScreenPos(screenPos, validPick, pickPaneMouseFinal::setCursor);
    }

    public void editSettings()
    {
        Settings prevSettings = TableManager.getSettings();
        Settings newSettings = new EditSettingsDialog(getWindow(), prevSettings).showAndWait().orElse(prevSettings);
        if (!newSettings.equals(prevSettings))
        {
            TableManager.setSettings(newSettings);
            // TODO should really re-run for all other open windows, too.
            
            // We need to re-run R transformations:
            Workers.onWorkerThread("Re-run R transformations", Priority.LOAD_FROM_DISK, () -> {
                for (Table t : getManager().getAllTablesAvailableTo(null, true))
                {
                    // This may involve running a few twice, but I'll live:

                    // We use ID because re-running may have regenerated table, but ID should be the same:
                    t = getManager().getSingleTableOrNull(t.getId());
                    if (t == null)
                        continue; // Shouldn't happen, but just ignore

                    SimulationRunnable reRun = t.getReevaluateOperation();
                    if (t instanceof RTransformation && reRun != null)
                    {
                        try
                        {
                            reRun.run();
                        }
                        catch (InternalException e)
                        {
                            Log.log(e);
                        }
                    }
                }
            });
        }
    }

    public static enum Pick {
        TABLE, COLUMN, NONE;
    }
    
    @OnThread(Tag.FXPlatform)
    public void enableTableOrColumnPickingMode(@Nullable Point2D screenPos, ObjectExpression<@PolyNull Scene> sceneProperty, FXPlatformFunction<Pair<Table, @Nullable ColumnId>, Pick> check, FXPlatformConsumer<Pair<Table, @Nullable ColumnId>> onPick)
    {
        disablePickingMode();

        final @NonNull Pane pickPaneMouseFinal = pickPaneMouse = new Pane();

        pickPaneMouseFinal.setPickOnBounds(true);
        class Ref
        {
            @Nullable Pair<Table, @Nullable ColumnId> pick = null;
        }
        Ref picked = new Ref();
        Picker<Pair<Table, @Nullable ColumnId>> validPick = p -> {
            if (p == null)
                return null;
            GridArea g = p.getFirst();
            CellPosition cell = p.getSecond();
            if (!(g instanceof TableDisplay))
                return null;
            TableDisplay tableDisplay = (TableDisplay) g;
            @Nullable Pair<ColumnId, RectangleBounds> c = tableDisplay.getColumnAt(cell);
            Pick pick = check.apply(new Pair<Table, @Nullable ColumnId>(tableDisplay.getTable(), c == null ? null : c.getFirst()));
            if (pick == Pick.COLUMN && c != null)
                return new PickResult<>(ImmutableList.of(c.getSecond()), HighlightType.SELECT, new Pair<>(tableDisplay.getTable(), c.getFirst()), getWindowTargetPoint(sceneProperty));
            else if (pick == Pick.TABLE)
                return new PickResult<>(ImmutableList.of(new RectangleBounds(g.getPosition(), g.getBottomRightIncl())), HighlightType.SELECT, new Pair<>(tableDisplay.getTable(), null), getWindowTargetPoint(sceneProperty));
            else
                return null;
        };
        pickPaneMouseFinal.setOnMouseMoved(e -> {
            picked.pick = tableHighlights.highlightAtScreenPos(
                new Point2D(e.getScreenX(), e.getScreenY()),
                validPick,
                pickPaneMouseFinal::setCursor);
            e.consume();
        });
        pickPaneMouseFinal.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY && picked.pick != null)
                onPick.consume(picked.pick);
        });
        pickPaneMouseFinal.addEventFilter(ScrollEvent.ANY, scrollEvent -> {
            getGrid().getScrollGroup().requestScroll(scrollEvent);
            scrollEvent.consume();
        });
        getChildren().add(pickPaneMouseFinal);
        // Highlight immediately:
        if (screenPos != null)
            tableHighlights.highlightAtScreenPos(screenPos, validPick, pickPaneMouseFinal::setCursor);
    }
    
    // If any sources are invalid, they are skipped
    private ImmutableList<Table> getSources(Table table)
    {
        if (table instanceof Transformation)
        {
            Collection<TableId> sourceIds = ((Transformation) table).getSources();
            return sourceIds.stream().flatMap(id -> Utility.streamNullable(tableManager.getSingleTableOrNull(id))).collect(ImmutableList.<Table>toImmutableList());
        }
        else
            return ImmutableList.of();
    }

    // Doesn't really need to be generic in both, but better type safety checking this way:
    private static <A,B> Pair<@Nullable A, @Nullable B> findFirstLeftAndFirstRight(Stream<Either<A, B>> stream)
    {
        // Atomic is a bit overkill, but creating arrays of generic types is a pain:
        AtomicReference<@Nullable A> left = new AtomicReference<>(null);
        AtomicReference<@Nullable B> right = new AtomicReference<>(null);
        for (Either<A, B> v : Utility.iterableStream(stream))
        {
            // Overwrite null only; leave other values intact:
            v.either_(x -> left.compareAndSet(null, x), x -> right.compareAndSet(null, x));

            // No point continuing if we've already found both:
            if (left.get() != null && right.get() != null)
                break;
        }

        return new Pair<>(left.get(), right.get());
    }

    public File getSaveFile()
    {
        return diskFile.get();
    }

    @OnThread(Tag.FXPlatform)
    public void undo()
    {
        File file = diskFile.get();
        readOnly = true;
        Workers.onWorkerThread("Undo", Priority.SAVE, () -> {
            @Nullable String current = null;
            try
            {
                current = FileUtils.readFileToString(file, StandardCharsets.UTF_8);
            }
            catch (IOException e)
            {
                Log.log(e);
            }
            @Nullable String prev = undoManager.undo(file);
            while (current != null && Objects.equals(current, prev))
                prev = undoManager.undo(file);

            if (prev == null)
                return; // Nothing to undo
            
            final @NonNull String previousVersion = prev;
            
            try
            {
                getManager().loadAll(previousVersion, this::loadColumnWidths);
            }
            catch (UserException | InternalException e)
            {
                Log.log("Problem undoing", e);
                try
                {
                    // Write out the content!
                    File dir = ((Supplier<File>)this::getHomeDirectory).get();
                    File dest = new File(dir.exists() ? dir : null, "emergency" + System.currentTimeMillis() + Main.EXTENSION_INCL_DOT);
                    FileUtils.writeStringToFile(dest, previousVersion, StandardCharsets.UTF_8);
                    Platform.runLater(() -> {
                        FXUtility.showError(TranslationUtility.getString("problem.undoing.content.saved.to", dest.getAbsolutePath()), e);
                    });
                }
                catch (IOException ioEx)
                {
                    // Last gasp -- copy content to clipboard and tell user
                    Platform.runLater(() -> {
                        Clipboard.getSystemClipboard().setContent(ImmutableMap.of(DataFormat.PLAIN_TEXT, previousVersion));
                        FXUtility.showError(TranslationUtility.getString("problem.undoing.content.copied.to.clipboard"), e);
                    });
                }
            }
            Platform.runLater(() -> {
                readOnly = false;
                save(false);
            });
        });
    }

    @SuppressWarnings("units") // Because of AbsColIndex
    @OnThread(Tag.Simulation)
    void loadColumnWidths(ImmutableList<Pair<Integer, Double>> columnWidths)
    {
        Platform.runLater(() -> {
            for (Pair<Integer, Double> columnWidth : columnWidths)
            {
                getGrid().setColumnWidth(columnWidth.getFirst(), columnWidth.getSecond(), false);
            }
        });
    }

    @OnThread(Tag.Swing)
    public File getHomeDirectory()
    {
        return FileSystemView.getFileSystemView().getDefaultDirectory();
    }

    public void gotoRowDialog()
    {
        getGrid().gotoRow(getWindow());
    }

    @OnThread(Tag.FXPlatform)
    public int test_getSaveCount()
    {
        return saveCount;
    }

    public boolean isReadOnly()
    {
        return readOnly;
    }

    /* TODO
    private boolean overlapsAnyExcept(List<TableDisplay> except, double x, double y)
    {
        List<Bounds> exceptHeaders = Utility.mapList(except, e -> new BoundingBox(x, y, e.getHeaderBoundsInParent().getWidth(), e.getHeaderBoundsInParent().getHeight()));
        return tableManager.getAllTables().stream()
                .flatMap(t -> Utility.streamNullable(t.getDisplay()))
                .filter(d -> !except.contains(d))
                .map(t -> t.getHeaderBoundsInParent())
                .anyMatch(r -> exceptHeaders.stream().anyMatch(e -> r.intersects(e)));
    }
    */

    /* TODO
    @OnThread(Tag.FXPlatform)
    private class Overlays
    {
        private final QuadCurve arrowFrom;
        private final HBox name;
        private final QuadCurve arrowTo;
        private final TableDisplay dest;
        private @MonotonicNonNull TableDisplay source;

        @OnThread(Tag.FXPlatform)
        public Overlays(List<TableDisplay> sources, String text, TableDisplay dest, FXPlatformRunnable edit)
        {
            this.dest = dest;
            Button button = new Button(text);
            button.setOnAction(e -> edit.run());
            name = new HBox(button);
            arrowFrom = new QuadCurve();
            arrowTo = new QuadCurve();
            Utility.addStyleClass(arrowFrom, "transformation-arrow");
            Utility.addStyleClass(arrowTo, "transformation-arrow");

            ChangeListener<Object> recalculate = new RecalcListener();
            
            if (!sources.isEmpty())
            {
                this.source = sources.get(0);  
                source.layoutXProperty().addListener(recalculate);
                source.layoutYProperty().addListener(recalculate);
                source.widthProperty().addListener(recalculate);
                source.heightProperty().addListener(recalculate);
            }
            
            dest.layoutXProperty().addListener(recalculate);
            dest.layoutYProperty().addListener(recalculate);
            dest.widthProperty().addListener(recalculate);
            dest.heightProperty().addListener(recalculate);

            recalculate.changed(new ReadOnlyBooleanWrapper(false), false, false);
        }

        @OnThread(Tag.FXPlatform)
        private void recalculatePosition()
        {
            double namePrefWidth = name.prefWidth(Double.MAX_VALUE);
            double namePrefHeight = name.prefHeight(Double.MAX_VALUE);
            
            if (source != null)
            {
                Pair<Point2D, Point2D> closestSrcDest = source.closestPointTo(dest.getBoundsInParent());
                double midX = 0.5 * (closestSrcDest.getFirst().getX() + closestSrcDest.getSecond().getX());
                double midY = 0.5 * (closestSrcDest.getFirst().getY() + closestSrcDest.getSecond().getY());
                
                Bounds predictedBounds = new BoundingBox(midX - namePrefWidth * 0.5, midY - namePrefHeight  * 0.5, namePrefWidth, namePrefHeight);
                
                if (!streamAllTables().<@Nullable TableDisplayBase>map(t -> t.getDisplay()).anyMatch(d -> d != null && d.getBoundsInParent().intersects(predictedBounds)))
                {
                    
                    name.layoutXProperty().unbind();
                    name.layoutXProperty().bind(name.widthProperty().multiply(-0.5).add(midX));
                    name.layoutYProperty().unbind();
                    name.layoutYProperty().bind(name.heightProperty().multiply(-0.5).add(midY));

                    Point2D from = source.closestPointTo(midX, midY - 100);
                    Point2D to = dest.closestPointTo(midX, midY + 100);

                    // Should use nearest point, not top-left:
                    arrowFrom.setLayoutX(from.getX());
                    arrowFrom.setLayoutY(from.getY());
                    arrowFrom.setControlX(midX - arrowFrom.getLayoutX());
                    arrowFrom.setControlY(midY - 100 - arrowFrom.getLayoutY());
                    arrowFrom.setEndX(midX - arrowFrom.getLayoutX());
                    arrowFrom.setEndY(midY - 50 - arrowFrom.getLayoutY());
                    arrowFrom.setVisible(true);
                    arrowTo.setLayoutX(midX);
                    arrowTo.setLayoutY(midY + 50);
                    arrowTo.setControlX(midX - arrowTo.getLayoutX());
                    arrowTo.setControlY(midY + 100 - arrowTo.getLayoutY());
                    arrowTo.setEndX(to.getX() - arrowTo.getLayoutX());
                    arrowTo.setEndY(to.getY() - arrowTo.getLayoutY());
                    arrowTo.setVisible(true);

                    return;
                }
            }
            
            // if we reach here, snap to top of table:
            double midX = dest.getBoundsInParent().getMinX() + namePrefWidth / 2.0;
            double midY = dest.getBoundsInParent().getMinY() - namePrefHeight * 0.7;
            
            
            arrowTo.setVisible(false);
            if (source != null)
            {
                Point2D from = source.closestPointTo(midX, midY - 100);
                arrowFrom.setLayoutX(from.getX());
                arrowFrom.setLayoutY(from.getY());
                arrowFrom.setControlX(midX - arrowFrom.getLayoutX());
                arrowFrom.setControlY(midY - 100 - arrowFrom.getLayoutY());
                arrowFrom.setEndX(midX - arrowFrom.getLayoutX());
                arrowFrom.setEndY(midY - 50 - arrowFrom.getLayoutY());
            }
            else
            {
                arrowFrom.setVisible(false);
            }
                
            name.layoutXProperty().unbind();
            name.layoutXProperty().bind(name.widthProperty().multiply(-0.5).add(midX));
            name.layoutYProperty().unbind();
            name.layoutYProperty().bind(name.heightProperty().multiply(-0.5).add(midY));
        }

        @OnThread(Tag.FXPlatform)
        private class RecalcListener implements ChangeListener<Object>
        {
            @Override
            @OnThread(value = Tag.FXPlatform, ignoreParent = true)
            public void changed(ObservableValue<?> a, Object b, Object c)
            {
                Overlays.this.recalculatePosition();
            }
        }
    }
    */

    // The type of the listener really throws off the checkers so suppress them all:
    @SuppressWarnings({"keyfor", "interning", "userindex", "valuetype", "helpfile"})
    public View(File location) throws InternalException, UserException
    {
        diskFile = new SimpleObjectProperty<>(location);
        hintMessage = new HintMessage();
        FXUtility.setPseudoclass(View.this, "empty", true);
        tableManager = new TableManager(TransformationManager.getInstance(), new PluginManager());
        tableManager.addListener(new TableManagerListener()
        {
            // No-one will add tables after the constructor, so this is okay:
            @SuppressWarnings("assignment")
            private final View thisView = View.this;

            @Override
            public void removeTable(Table t, int tablesRemaining)
            {
                thisView.removeTable(t, tablesRemaining);
            }

            @Override
            public void addSource(DataSource dataSource)
            {
                FXUtility.runFX(() -> {
                    thisView.hintMessage.updateState();
                    FXUtility.setPseudoclass(View.this, "empty", false);
                    VirtualGridSupplierFloating floatingSupplier = FXUtility.mouse(View.this).getGrid().getFloatingSupplier();
                    thisView.addDisplay(new TableDisplay(thisView, floatingSupplier, dataSource));
                    thisView.save(true);
                });
            }

            @Override
            public void addTransformation(Transformation transformation)
            {
                FXUtility.runFX(() ->
                {
                    thisView.hintMessage.updateState();
                    FXUtility.setPseudoclass(View.this, "empty", false);
                    VirtualGridSupplierFloating floatingSupplier = FXUtility.mouse(View.this).getGrid().getFloatingSupplier();
                    if (transformation instanceof Check)
                    {
                        CheckDisplay checkDisplay = new CheckDisplay(thisView, floatingSupplier, (Check)transformation);
                        thisView.mainPane.addGridAreas(ImmutableList.of(checkDisplay));
                        thisView.mainPane.addSelectionListener(checkDisplay);

                    }
                    else
                    {
                        TableDisplay tableDisplay = new TableDisplay(thisView, floatingSupplier, transformation);
                        thisView.addDisplay(tableDisplay);
                    }

                    List<TableDisplay> sourceDisplays = new ArrayList<>();
                    for (TableId t : transformation.getSources())
                    {
                        TableDisplay td = thisView.getTableDisplayOrNull(t);
                        if (td != null)
                            sourceDisplays.add(td);
                    }
                    /*overlays.put(transformation, new Overlays(sourceDisplays, transformation.getTransformationLabel(), tableDisplay, () ->
                    {
                        View.this.editTransform((TransformationEditable)transformation);
                    }));*/

                    thisView.save(true);
                });
            }

            @Override
            public void addComment(GridComment gridComment)
            {
                FXUtility.runFX(() -> {
                    GridCommentDisplay display = new GridCommentDisplay(thisView.tableManager, gridComment);
                    thisView.getGrid().addGridAreas(ImmutableList.of(display));
                    thisView.getGrid().getFloatingSupplier().addItem(display.getFloatingItem());
                    thisView.save(true);
                    thisView.getGrid().redoLayoutAfterScroll();
                    display.requestFocus();
                });
            }

            @Override
            public void removeComment(GridComment gridComment)
            {
                FXUtility.runFX(() -> {
                    GridCommentDisplay gridCommentDisplay = (GridCommentDisplay)gridComment.getDisplay();
                    if (gridCommentDisplay != null)
                    {
                        thisView.getGrid().getFloatingSupplier().removeItem(gridCommentDisplay.getFloatingItem());
                        thisView.getGrid().removeGridArea(gridCommentDisplay);
                    }
                    thisView.save(true);
                    thisView.getGrid().redoLayoutAfterScroll();
                });
            }
        });
        
        VirtualGridManager vgManager = new VirtualGridManager()
        {
            private @Nullable FXPlatformRunnable cancelSave;

            @Override
            public @OnThread(Tag.FXPlatform) void notifyColumnSizeChanged()
            {
                // Save after five seconds of no resizing:
                if (cancelSave != null)
                {
                    cancelSave.run();
                    cancelSave = null;
                }
                cancelSave = FXUtility.runAfterDelay(Duration.seconds(5), () -> {
                    cancelSave = null;
                    Utility.later(View.this).modified();
                });
            }

            @Override
            @OnThread(Tag.FXPlatform)
            public void createTable(CellPosition cellPos, Point2D mousePos, VirtualGrid grid)
            {
                // Data table if there are none, or if we ask and they say data
// Ask what they want
                View.this.createTable(FXUtility.mouse(View.this), tableManager, cellPos, mousePos, grid);
            }

            @Override
            @OnThread(Tag.FXPlatform)
            public void pasteIntoEmpty(CellPosition target)
            {
                @Initialized View thisView = Utility.later(View.this);
                FXPlatformConsumer<DataSource> selectAfter = data -> {
                    if (data.getDisplay() instanceof TableDisplay)
                    {
                        TableDisplay display = (TableDisplay) data.getDisplay();
                        if (display != null)
                            thisView.getGrid().select(new EntireTableSelection(display, target.columnIndex));
                    }
                };
                Optional<ImmutableList<LoadedColumnInfo>> loaded = ClipboardUtils.loadValuesFromClipboard(thisView.getManager().getTypeManager());
                if (loaded.isPresent())
                {
                    ImmutableList<LoadedColumnInfo> content = loaded.get();
                    if (!content.isEmpty())
                    {
                        Workers.onWorkerThread("Pasting new table", Priority.SAVE, () -> {
                            FXUtility.alertOnError_(TranslationUtility.getString("error.pasting.table.data"), () -> {
                                ImmediateDataSource data = new ImmediateDataSource(tableManager, new InitialLoadDetails(null, null, target, null), new EditableRecordSet(Utility.<LoadedColumnInfo, SimulationFunction<RecordSet, EditableColumn>>mapList_Index(content, (i, c) -> c.load(i)), () -> content.stream().mapToInt(c -> c.dataValues.size()).max().orElse(0)));
                                tableManager.record(data);
                                Platform.runLater(() -> selectAfter.consume(data));
                            });
                        });
                    }
                }
                else
                {
                    // Try treating them as text values:
                    String clip = Clipboard.getSystemClipboard().getString();
                    if (clip != null && !clip.trim().isEmpty())
                    {
                        Window window = thisView.getWindow();
                        Workers.onWorkerThread("Pasting new table", Priority.SAVE, () -> {
                            try
                            {
                                File tmp = File.createTempFile("clipboard", "txt");
                                FileUtils.write(tmp, clip, StandardCharsets.UTF_8);
                                TextImporter.importTextFile(window, thisView.tableManager, tmp, target, t -> {
                                    tableManager.record(t);
                                    Platform.runLater(() -> selectAfter.consume(t));
                                });
                            }
                            catch (IOException e)
                            {
                                Log.log(e);
                            }
                        });
                    }
                }
            }
        };
        
        mainPane = new VirtualGrid(vgManager,
            10, 20, "main-view-grid");
        tableHighlights = new TableHighlights(mainPane);
        expandTableArrowSupplier = new ExpandTableArrowSupplier();
        mainPane.addNodeSupplier(new VirtualGridLineSupplier());
        this.dataCellSupplier = new DataCellSupplier(mainPane);
        mainPane.addNodeSupplier(dataCellSupplier);
        mainPane.addNodeSupplier(expandTableArrowSupplier);
        this.rowLabelSupplier = new RowLabelSupplier(mainPane);
        mainPane.addNodeSupplier(rowLabelSupplier);
        mainPane.getFloatingSupplier().addItem(hintMessage);
        mainPane.addNewButtonVisibleListener(vis -> {
            hintMessage.newButtonVisible = vis;
            hintMessage.updateState();
        });
        overlayPane = new Pane();
        overlayPane.setPickOnBounds(false);
        snapGuidePane = new Pane();
        snapGuidePane.setMouseTransparent(true);
        pickPaneMouse = new Pane();
        getChildren().addAll(mainPane.getNode(), overlayPane, snapGuidePane);
        getStyleClass().add("view");
    }
    
    public TableHighlights getHighlights()
    {
        return tableHighlights;
    }

    private void addDisplay(TableDisplay tableDisplay)
    {
        dataCellSupplier.addGrid(tableDisplay, tableDisplay.getDataGridCellInfo());
        mainPane.addGridAreas(ImmutableList.of(tableDisplay));
        mainPane.addSelectionListener(tableDisplay);
        rowLabelSupplier.addTable(mainPane, tableDisplay, false);
        @Nullable FXPlatformConsumer<@Nullable ColumnId> addColumn = tableDisplay.addColumnOperation();
        boolean addRows = tableDisplay.getTable().getOperations().appendRows != null;
        if (addColumn != null || addRows)
        {
            @Nullable FXPlatformRunnable addColumnAtEnd = null;
            if (addColumn != null)
            {
                @NonNull FXPlatformConsumer<@Nullable ColumnId> addColumnFinal = addColumn;
                addColumnAtEnd = () -> addColumnFinal.consume(null);
            }
            expandTableArrowSupplier.addTable(tableDisplay, addColumnAtEnd, addRows);
        }
    }

    private @Nullable TableDisplay getTableDisplayOrNull(TableId tableId)
    {
        @Nullable Table table = tableManager.getSingleTableOrNull(tableId);
        if (table != null && table.getDisplay() instanceof TableDisplay)
            return (TableDisplay)table.getDisplay();
        else
            return null;        
    }

    // Can't be a View without an actual window
    private Window getWindow()
    {
        @SuppressWarnings("nullness")
        @NonNull Scene scene = getScene();
        @SuppressWarnings("nullness")
        @NonNull Window window = scene.getWindow();
        return window;
    }

    @Override
    @OnThread(Tag.FX)
    public void requestFocus()
    {
        // Don't allow focus
    }

    public void addTitleListenerAndCallNow(FXPlatformConsumer<String> onTitleChange)
    {
        FXUtility.addChangeListenerPlatformNNAndCallNow(diskFile, f -> onTitleChange.consume(f.getName() + " [" + f.getParent() + "]"));
    }
    
    public DataCellSupplier getDataCellSupplier()
    {
        return dataCellSupplier;
    }

    @Override
    @OnThread(Tag.FXPlatform)
    public Window dimWhileShowing(@UnknownInitialization(Dialog.class) Dialog<?> dialog)
    {
        Effect dim = new ColorAdjust(0.0, 0.0, -0.2, 0.0);
        FXUtility.addChangeListenerPlatformNN(dialog.showingProperty(), show -> {
            if (show)
            {
                getGrid().setEffectOnNonOverlays(dim);
            }
            else
            {
                getGrid().setEffectOnNonOverlays(null);
            }
        });
        return getWindow();
    }

    @Override
    public <T> @OnThread(Tag.FXPlatform) T dimAndWait(FXPlatformFunction<Window, T> showAndWait)
    {
        Effect dim = new ColorAdjust(0.0, 0.0, -0.2, 0.0);
        getGrid().setEffectOnNonOverlays(dim);
        T t = showAndWait.apply(getWindow());
        getGrid().setEffectOnNonOverlays(null);
        return t;
    }

    private static void createTable(View thisView, TableManager tableManager, CellPosition cellPosition, Point2D mouseScreenPos, VirtualGrid virtualGrid)
    {
        // Ask what they want
        GaussianBlur blur = new GaussianBlur(4.0);
        blur.setInput(new ColorAdjust(0.0, 0.0, -0.2, 0.0));
        virtualGrid.setEffectOnNonOverlays(blur);
        Window window = thisView.getWindow();
        Optional<Pair<Point2D, DataOrTransform>> choice = new NewTableDialog(thisView, !tableManager.getAllTables().isEmpty()).showAndWaitCentredOn(mouseScreenPos);

        if (choice.isPresent())
        {
            InitialLoadDetails initialLoadDetails = new InitialLoadDetails(null, null, cellPosition, null);
            SimulationConsumerNoError<DataSource> record = d -> tableManager.record(d);
            switch (choice.get().getSecond())
            {
                case DATA:
                    Optional<ColumnDetails> optInitialDetails = new EditImmediateColumnDialog(thisView, thisView.getManager(), null, null, true, InitialFocus.FOCUS_TABLE_NAME).showAndWait();
                    optInitialDetails.ifPresent(initialDetails -> {
                        Workers.onWorkerThread("Creating table", Priority.SAVE, () -> {
                            FXUtility.alertOnError_(TranslationUtility.getString("error.creating.first.column"), () -> {
                                ImmediateDataSource data = new ImmediateDataSource(tableManager, initialDetails.tableId != null ? initialLoadDetails.withTableId(initialDetails.tableId) : initialLoadDetails, EditableRecordSet.newRecordSetSingleColumn(initialDetails.columnId, initialDetails.dataType, initialDetails.defaultValue));
                                tableManager.record(data);
                            });
                        });
                    });
                    break;
                case IMPORT_FILE:
                    ImporterManager.getInstance().chooseAndImportFile(window, tableManager, cellPosition, record);
                    break;
                case IMPORT_URL:
                    ImporterManager.getInstance().chooseAndImportURL(window, tableManager, cellPosition, record);
                    break;
                case COMMENT:
                    Workers.onWorkerThread("Adding new comment", Priority.SAVE, () -> thisView.tableManager.addComment(new GridComment(SaveTag.generateRandom(), "", cellPosition, 2, 2)));
                    break;
                case TRANSFORM:
                    new PickTransformationDialog(thisView).showAndWaitCentredOn(mouseScreenPos).ifPresent(createTrans -> {
                        @Nullable SimulationSupplier<Transformation> makeTrans = createTrans.getSecond().make(thisView.getManager(), cellPosition, () ->
                            new PickTableDialog(thisView, null, createTrans.getFirst()).showAndWait());
                        if (makeTrans != null)
                        {
                            @NonNull SimulationSupplier<Transformation> makeTransFinal = makeTrans;
                            Workers.onWorkerThread("Creating transformation", Priority.SAVE, () -> {
                                FXUtility.alertOnError_(TranslationUtility.getString("error.creating.transformation"), () -> {
                                    Transformation transformation = makeTransFinal.get();
                                    tableManager.record(transformation);
                                    Platform.runLater(() -> {
                                        if (transformation.getDisplay() instanceof TableDisplay)
                                        {
                                            TableDisplay display = (TableDisplay) transformation.getDisplay();
                                            if (display != null)
                                                display.editAfterCreation();
                                        }
                                    });
                                });
                            });
                        }
                    });
                    break;
                case CHECK:
                    new PickTableDialog(thisView, null, mouseScreenPos).showAndWait().ifPresent(srcTable -> {
                        new EditCheckExpressionDialog(thisView, srcTable, CheckType.ALL_ROWS, new BooleanLiteral(true), true, ct -> Check.getColumnLookup(tableManager, srcTable.getId(), null, ct)).showAndWait().ifPresent(details -> {
                            Workers.onWorkerThread("Creating check", Priority.SAVE, () -> FXUtility.alertOnError_(TranslationUtility.getString("error.creating.check"), () -> {
                                Check check = new Check(thisView.getManager(), new InitialLoadDetails(null, null, cellPosition, null), srcTable.getId(), details.getFirst(), details.getSecond());
                                tableManager.record(check);
                            }));
                        });
                    });
                    break;
            }
        }
        virtualGrid.setEffectOnNonOverlays(null);
    }
    
    public void createTransform(Table srcTable, Point2D mouseScreenPos)
    {
        new PickTransformationDialog(this).showAndWaitCentredOn(mouseScreenPos).ifPresent(createTrans -> {
            @Nullable SimulationSupplier<Transformation> makeTrans = createTrans.getSecond().make(getManager(), tableManager.getNextInsertPosition(srcTable.getId()), () -> Optional.of(srcTable));
            if (makeTrans != null)
            {
                @NonNull SimulationSupplier<Transformation> makeTransFinal = makeTrans;
                Workers.onWorkerThread("Creating transformation", Priority.SAVE, () -> {
                    FXUtility.alertOnError_(TranslationUtility.getString("error.creating.transformation"), () -> {
                        Transformation transformation = makeTransFinal.get();
                        tableManager.record(transformation);
                        Platform.runLater(() -> {
                            if (transformation.getDisplay() instanceof TableDisplay)
                            {
                                TableDisplay display = (TableDisplay) transformation.getDisplay();
                                if (display != null)
                                    display.editAfterCreation();
                            }
                        });
                    });
                });
            }
        });
    }

    public void enableWriting()
    {
        readOnly = false;
        File dest = diskFile.get();
        if (!dest.exists() || dest.length() == 0L)
        {
            save(true);
        }
        else
        {
            Workers.onWorkerThread("Backing up file for undo", Priority.SAVE, () ->
            {
                Instant now = Instant.now();
                // This will do backup for undo, but also for
                // files being replaced, in extreme cases:
                if (dest.exists())
                {
                    undoManager.backupForUndo(dest, now);
                }
            });
        }
    }

    private void removeExplanationDisplay()
    {
        if (explanationDisplay != null)
        {
            getGrid().getFloatingSupplier().removeItem(explanationDisplay.getSecond());
            this.explanationDisplay = null;
        }
        getGrid().positionOrAreaChanged();
    }

    public void removeExplanationDisplayFor(Table table)
    {
        if (explanationDisplay != null && explanationDisplay.getFirst() == table)
        {
            removeExplanationDisplay();
        }
    }
    
    public void showExplanationDisplay(Table table, TableId srcTableId, CellPosition attachedTo, Explanation explanation)
    {
        removeExplanationDisplay();
        explanationDisplay = new Pair<>(table, new ExplanationDisplay(srcTableId, attachedTo, explanation, l -> {
            Table t = getManager().getSingleTableOrNull(l.tableId);
            if (t != null && l.columnId != null && l.rowIndex != null && t.getDisplay() instanceof DataDisplay)
            {
                CellSelection selection = ((DataDisplay)t.getDisplay()).getSelectionForSingleCell(l.columnId, l.rowIndex);
                if (selection != null)
                    getGrid().select(selection);
            }
        }, item -> {
            removeExplanationDisplay();
        }, () -> getGrid().positionOrAreaChanged()));
        getGrid().getFloatingSupplier().addItem(explanationDisplay.getSecond());
        getGrid().positionOrAreaChanged();
    }

    private static final String HASH_FILE_NAME = "hashes.txt";

    // Checks if we have seen that file with that content last time we saved the file.
    @OnThread(Tag.Simulation)
    public static boolean checkHashMatch(File file, HashCode contentHash)
    {
        try
        {
            // Each line in the file is shasha <space> sha256hash <space> sha256hash.  The first hash is the hash of the path to the file, the second is the hash of the content.
            ImmutableMap<HashCode, HashCode> filesToContent = loadFileHashes();
            // Missing is the same as untrusted:
            return contentHash.equals(filesToContent.get(hashFilePath(file)));
        }
        catch (IOException e)
        {
            // Two choices; assume bad or assume fine.  Too irritating for users if permanent file issue and we assume bad, so I think have to assume fine:
            Log.log(e);
            return true;
        }
    }

    @OnThread(Tag.Any)
    private static HashCode hashFilePath(File file)
    {
        return Hashing.sha256().hashString(file.getAbsolutePath(), StandardCharsets.UTF_8);
    }

    @OnThread(Tag.Simulation)
    private static ImmutableMap<HashCode, HashCode> loadFileHashes() throws IOException
    {
        File hashesFile = new File(Utility.getStorageDirectory(), HASH_FILE_NAME);
        // It's not an error to simply not exist yet:
        if (!hashesFile.exists())
            return ImmutableMap.of();
        return FileUtils.readLines(hashesFile, StandardCharsets.UTF_8)
            .stream()
            .flatMap(l -> {
                String[] parts = l.trim().split(" ");
                if (parts.length == 3 && parts[0].equals("shasha"))
                {
                    try
                    {
                        return Stream.of(new Pair<>(HashCode.fromString(parts[1]), HashCode.fromString(parts[2])));
                    }
                    catch (Throwable t)
                    {
                        Log.log(t);
                    }
                }
                return Stream.of();
            }).distinct().collect(ImmutableMap.<Pair<HashCode, HashCode>, HashCode, HashCode>toImmutableMap(p -> p.getFirst(), p -> p.getSecond()));
    }

    @OnThread(Tag.Simulation)
    private static void recordFileHash(File file, HashCode contentHash)
    {
        try
        {
            ImmutableMap<HashCode, HashCode> updated = Utility.appendToMap(loadFileHashes(), hashFilePath(file), contentHash, null);
            FileUtils.writeLines(new File(Utility.getStorageDirectory(), HASH_FILE_NAME), "UTF-8",
                updated.entrySet().stream().map(e -> "shasha " + e.getKey().toString() + " " + e.getValue().toString()).collect(ImmutableList.<String>toImmutableList())
            );
        }
        catch (IOException e)
        {
            // Not much we can really do:
            Log.log(e);
        }
    }

    @OnThread(Tag.FXPlatform)
    public class FindEverywhereDialog extends Dialog<Void>
    {

        private final ListView<Result> results;

        private class Result
        {
            // TODO separate show text from find text
            private final String text;
            private final FXPlatformRunnable onPick;

            public Result(String text, FXPlatformRunnable onPick)
            {
                this.text = text;
                this.onPick = onPick;
            }

            public boolean matches(String findString)
            {
                return text.toLowerCase().contains(findString.toLowerCase());
            }

            @Override
            public String toString()
            {
                return text;
            }
        }

        public FindEverywhereDialog()
        {
            TextField findField = new TextField();
            results = new ListView<>();

            List<Result> allPossibleResults = findAllPossibleResults();
            results.getItems().setAll(allPossibleResults);

            FXUtility.addChangeListenerPlatformNN(findField.textProperty(), text -> {
                results.getItems().setAll(allPossibleResults.stream().filter(r -> r.matches(text)).collect(Collectors.<Result>toList()));
                results.getSelectionModel().selectFirst();
            });
            findField.setOnAction(e -> selectResult());

            BorderPane content = new BorderPane();
            content.setTop(findField);
            content.setCenter(results);
            getDialogPane().setContent(content);
            getDialogPane().getButtonTypes().setAll(ButtonType.CLOSE);

            setOnShown(e -> findField.requestFocus());
        }

        @RequiresNonNull("results")
        @SuppressWarnings("method.invocation")
        private void selectResult(@UnknownInitialization(Object.class) FindEverywhereDialog this)
        {
            @Nullable Result result = results.getSelectionModel().getSelectedItem();
            if (result != null)
            {
                result.onPick.run();
            }
            close();
        }

        private List<Result> findAllPossibleResults(@UnknownInitialization(Object.class) FindEverywhereDialog this)
        {
            List<Result> r = new ArrayList<>();
            // Tables:
            r.addAll(Utility.mapList(getAllTables(), t -> new Result(t.getId().getRaw(), () -> {
                //TableDisplay tableDisplay = t.getDisplay();
            })));

            return r;
        }
    }
    
    @OnThread(Tag.FXPlatform)
    private final class HintMessage extends FloatingItem<VBox>
    {
        /*
        public static enum ContentState
        {
            // Empty window with no 
            EMPTY_NO_SEL,
            EMPTY_SEL,
            NON_EMPTY;
        }
        
        private ContentState state;
        */
        private final VBox container;
        private final Label label;
        private final Label label2; // smaller, beneath, not always present
        private boolean newButtonVisible;
        
        public HintMessage()
        {
            super(ViewOrder.STANDARD_CELLS);
            label = new Label(TranslationUtility.getString("main.emptyHint"));
            label.getStyleClass().add("main-hint");
            label.setWrapText(true);
            label.setMaxWidth(400.0);
            label.setMouseTransparent(true);
            label2 = new Label();
            label2.getStyleClass().add("main-sub-hint");
            label2.setWrapText(true);
            label2.setMaxWidth(400.0);
            label2.setMouseTransparent(true);
            container = new VBox(label);
            container.getStyleClass().add("main-hint-container");
        }
        
        public void updateState()
        {
            ImmutableList<Table> allTables = tableManager.getAllTables();
            if (allTables.isEmpty())
            {
                if (newButtonVisible)
                {
                    show(TranslationUtility.getString("main.selHint"));
                }
                else
                {
                    show(TranslationUtility.getString("main.emptyHint"));
                }
            }
            else if (allTables.stream().allMatch(t -> t instanceof DataSource))
            {
                show(TranslationUtility.getString("main.transHint"), TranslationUtility.getString("main.transHint2"));
            }
            else
            {
                container.getChildren().clear();
                container.setVisible(false);
            }
        }

        private void show(@Localized String main)
        {
            label.setText(main);
            container.getChildren().setAll(label);
            container.setVisible(true);
        }

        private void show(@Localized String main, @Localized String sub)
        {
            label.setText(main);
            label2.setText(sub);
            container.getChildren().setAll(label, label2);
            container.setVisible(true);
        }

        @Override
        protected Optional<BoundingBox> calculatePosition(VisibleBounds visibleBounds)
        {            
            // If visible, we position ourselves slightly to the right of any existing tables, at their level
            
            CellPosition rightMostTableEdge = getManager().getAllTables().stream().flatMap(t -> Utility.<CellPosition>streamNullable(Utility.<TableDisplayBase, CellPosition>onNullable(t.getDisplay(), d -> new CellPosition(d.getMostRecentPosition().rowIndex, d.getBottomRightIncl().columnIndex)))).max(Comparator.<CellPosition, Integer>comparing(c -> c.columnIndex).thenComparing(c -> -c.rowIndex)).orElse(CellPosition.ORIGIN.offsetByRowCols(2, 1));
            
            CellPosition target = rightMostTableEdge.offsetByRowCols(2, 2);
            double x = visibleBounds.getXCoord(target.columnIndex) + 20;
            double y = visibleBounds.getYCoord(target.rowIndex) + 10;

            double width = Math.min(400, container.prefWidth(-1));
            double height = container.prefHeight(width);
            return Optional.of(new BoundingBox(x, y, width, height));
        }

        @Override
        protected VBox makeCell(VisibleBounds visibleBounds)
        {
            return container;
        }

        @Override
        public @Nullable Pair<ItemState, @Nullable StyledString> getItemState(CellPosition cellPosition, Point2D screenPos)
        {
            return null;
        }

        @Override
        public void keyboardActivate(CellPosition cellPosition)
        {
        }
    }
}

