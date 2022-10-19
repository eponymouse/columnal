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

package xyz.columnal.gui.table;

import annotation.units.AbsColIndex;
import annotation.units.AbsRowIndex;
import com.google.common.collect.ImmutableList;
import de.uni_freiburg.informatik.ultimate.smtinterpol.util.IdentityHashSet;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.effect.GaussianBlur;
import javafx.stage.Window;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.CellPosition;
import xyz.columnal.data.Table.TableDisplayBase;
import xyz.columnal.gui.table.DataCellSupplier.CellStyle;
import xyz.columnal.gui.table.DataCellSupplier.VersionedSTF;
import xyz.columnal.gui.dtf.Document;
import xyz.columnal.gui.dtf.DocumentTextField;
import xyz.columnal.gui.dtf.ReadOnlyDocument;
import xyz.columnal.gui.grid.CellSelection;
import xyz.columnal.gui.grid.GridArea;
import xyz.columnal.gui.grid.RectangleBounds;
import xyz.columnal.gui.grid.VirtualGrid;
import xyz.columnal.gui.grid.VirtualGrid.ListenerOutcome;
import xyz.columnal.gui.grid.VirtualGrid.SelectionListener;
import xyz.columnal.gui.grid.VirtualGridSupplierIndividual;
import xyz.columnal.gui.grid.VirtualGridSupplierIndividual.GridCellInfo;
import xyz.columnal.gui.guidance.GuidanceWindow;
import xyz.columnal.gui.guidance.GuidanceWindow.Condition;
import xyz.columnal.gui.guidance.GuidanceWindow.Guidance;
import xyz.columnal.gui.guidance.GuidanceWindow.LookupCondition;
import xyz.columnal.gui.guidance.GuidanceWindow.LookupKeyCondition;
import xyz.columnal.gui.stable.ColumnDetails;
import xyz.columnal.styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.function.fx.FXPlatformConsumer;
import xyz.columnal.utility.function.fx.FXPlatformRunnable;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.gui.FXUtility;
import xyz.columnal.utility.TranslationUtility;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.Optional;
import java.util.OptionalDouble;

public class DataCellSupplier extends VirtualGridSupplierIndividual<VersionedSTF, CellStyle, GridCellInfo<VersionedSTF, CellStyle>>
{
    private final IdentityHashSet<Document> shownGuidanceFor = new IdentityHashSet<>();
    private final VirtualGrid virtualGrid;

    public DataCellSupplier(VirtualGrid virtualGrid)
    {
        super(Arrays.asList(CellStyle.values()));
        this.virtualGrid = virtualGrid;
    }
    
    @Override
    protected VersionedSTF makeNewItem(VirtualGrid virtualGrid)
    {
        VersionedSTF stf = new VersionedSTF(virtualGrid::positionOrAreaChanged);
        stf.getStyleClass().add("table-data-cell");
        return stf;
    }

    @Override
    public OptionalDouble getPrefColumnWidth(@AbsColIndex int colIndex)
    {
        return getItemsInColumn(colIndex).stream().mapToDouble(n -> n.calcWidthToFitContent()).max();
    }

    @Override
    protected @Nullable Pair<ItemState, @Nullable StyledString> getItemState(VersionedSTF stf, Point2D screenPos)
    {
        if (stf.isFocused())
            return new Pair<>(ItemState.EDITING, stf.getHoverText());
        else
            return new Pair<>(ItemState.NOT_CLICKABLE, stf.getHoverText());
    }

    @Override
    protected @Nullable Pair<ItemState, @Nullable StyledString> getItemState(CellPosition cellPosition, Point2D screenPos)
    {
        // Override here to check for expanded cell
        Optional<VersionedSTF> expanded = findItems(node -> node.isExpanded()).findFirst();
        if (expanded.isPresent() && expanded.get().localToScreen(expanded.get().getBoundsInLocal()).contains(screenPos))
        {
            return new Pair<>(ItemState.EDITING, null);
        }
        return super.getItemState(cellPosition, screenPos);
    }

    @Override
    protected void keyboardActivate(CellPosition cellPosition)
    {
        startEditing(null, cellPosition, null);
    }

    @Override
    protected void startEditing(@Nullable Point2D screenPosition, CellPosition cellPosition, @Nullable String startTyping)
    {
        @Nullable VersionedSTF stf = getItemAt(cellPosition);
        if (stf != null)
        {
            if (screenPosition != null)
            {
                Point2D localPos = stf.screenToLocal(screenPosition);
                stf.moveTo(localPos);
            }
            else
            {
                stf.selectAll();
            }
            stf.requestFocus();
            // Important to do this after focus because things like manual edit
            // revert to content at point of focus, so don't edit
            // while unfocused:
            if (startTyping != null)
            {
                stf.replaceAll(startTyping, false);
            }
        }
    }
    
    public static enum CellStyle
    {
        TABLE_DRAG_SOURCE
        {
            @Override
            public void applyStyle(Node item, boolean on)
            {
                // Don't override unrelated effects:
                if (!on && item.getEffect() instanceof GaussianBlur)
                    item.setEffect(null);
                else if (on && item.getEffect() == null)
                    item.setEffect(new GaussianBlur());
            }
        },
        HOVERING_EXPAND_DOWN
        {
            @Override
            public void applyStyle(Node item, boolean on)
            {
                FXUtility.setPseudoclass(item, "hovering-expand-down", on);
            }
        },
        HOVERING_EXPAND_RIGHT
        {
            @Override
            public void applyStyle(Node item, boolean on)
            {
                FXUtility.setPseudoclass(item, "hovering-expand-right", on);
            }
        };

        @OnThread(Tag.FX)
        public abstract void applyStyle(Node item, boolean on);
    }

    @Override
    protected @OnThread(Tag.FX) void adjustStyle(VersionedSTF item, CellStyle style, boolean on)
    {
        style.applyStyle(item, on);
    }

    @Override
    protected void hideItem(VersionedSTF spareCell)
    {
        super.hideItem(spareCell);
        // Clear EditorKit to avoid keeping it around while spare:
        spareCell.blank(new ReadOnlyDocument(TranslationUtility.getString("data.loading")));
    }

    @OnThread(Tag.FXPlatform)
    public @Nullable VersionedSTF _test_getCellAt(CellPosition position)
    {
        return getItemAt(position);
    }

    @Override
    protected void sizeAndLocateCell(double x, double y, @AbsColIndex int columnIndex, @AbsRowIndex int rowIndex, VersionedSTF cell, VisibleBounds visibleBounds)
    {
        if (cell.isExpanded())
        {
            double width = 350; //visibleBounds.getXCoordAfter(columnIndex + CellPosition.col(1)) - x;
            double rowHeight = 110; // visibleBounds.getYCoordAfter(rowIndex + CellPosition.row(1)) - y;
            FXUtility.resizeRelocate(cell, x, y, width, rowHeight);
        }
        else
            super.sizeAndLocateCell(x, y, columnIndex, rowIndex, cell, visibleBounds);
    }

    @Override
    protected ViewOrder viewOrderFor(VersionedSTF node)
    {
        if (node.isExpanded())
            return ViewOrder.POPUP;
        else
            return super.viewOrderFor(node);
    }
    

    // A simple subclass of STF that holds a version param.  A version is a weak reference
    // to a list of column details
    @OnThread(Tag.FXPlatform)
    public class VersionedSTF extends DocumentTextField
    {
        @OnThread(Tag.FXPlatform)
        private @Nullable WeakReference<ImmutableList<ColumnDetails>> currentVersion = null;
        
        public VersionedSTF(FXPlatformRunnable redoLayout)
        {
            super(redoLayout);
            setFocusTraversable(false);
        }

        @OnThread(Tag.FXPlatform)
        public boolean isUsingColumns(ImmutableList<ColumnDetails> columns)
        {
            // Very important here we use reference equality not .equals()
            // It's not about the content of the columns, it's about using the reference to the
            // immutable list as a simple way of doing a version check
            return currentVersion != null && currentVersion.get() == columns;
        }

        @OnThread(Tag.FXPlatform)
        public void blank(Document editorKit)
        {
            super.setDocument(editorKit);
            currentVersion = null;
        }

        @OnThread(Tag.FXPlatform)
        public void setContent(Document editorKit, ImmutableList<ColumnDetails> columns)
        {
            super.setDocument(editorKit);
            currentVersion = new WeakReference<>(columns);
        }

        @Override
        public @OnThread(Tag.FXPlatform) void documentChanged(Document document)
        {
            super.documentChanged(document);
            if (document.getText().startsWith("=") && isFocused())
            {
                if (!shownGuidanceFor.contains(document))
                {
                    shownGuidanceFor.add(document);
                    Scene scene = getScene();
                    if (scene != null)
                    {
                        Window window = scene.getWindow();
                        if (window != null)
                        {
                            GridArea gridArea = getGridFor(VersionedSTF.this);
                            new GuidanceWindow(makeTransformGuidance(window, virtualGrid, gridArea instanceof DataDisplay && gridArea instanceof TableDisplayBase ? (DataDisplay & TableDisplayBase) gridArea : null, document.getText().substring(1)), window).show();
                        }
                    }
                }
            }
            else if (!shownGuidanceFor.isEmpty()) // Optimisation; remove strangely expensive on empty collection
            {
                shownGuidanceFor.remove(document);
            }
        }
    }

    @OnThread(Tag.FXPlatform)
    private <T extends DataDisplay & TableDisplayBase> Guidance makeTransformGuidance(Window mainWindow, VirtualGrid virtualGrid, @Nullable T srcTable, String content)
    {
        // TODO make them choose upfront calculate vs aggregate?
        
        // Introduction:
        return new Guidance(StyledString.s("Data tables cannot contain formulas.  To perform a calculation, create a Calculate transformation."), "guidance.show.me", () ->
        // Empty cell
        new Guidance(StyledString.s("To create a transformation, click on an empty cell."), new EmptyCellSelectedCondition(), (String)null, () ->
        // New... button        
        new Guidance(StyledString.s("Click the New... button."), new LookupKeyCondition("new.transform"), ".id-create-table", () ->
        // Transform        
        new Guidance(StyledString.s("Click the Transform button."), new LookupCondition(".pick-transformation-tile-pane"), ".id-new-transform", () ->
        // Calculate
        new Guidance(StyledString.s("Click the Calculate button."), new LookupCondition(".pick-table-dialog"), ".id-transform-calculate", () ->
        // Click source table
        new Guidance(StyledString.s("Click on the table you want to use in the calculation"), new LookupCondition(".expression-editor"), () -> srcTable == null ? null : new Pair<>(mainWindow, virtualGrid.getRectangleBoundsScreen(new RectangleBounds(srcTable.getMostRecentPosition(), srcTable.getBottomRightIncl()))), () ->
        // Enter the formula
        new Guidance(StyledString.s("Enter the column name and expression you want to calculate, then click Ok."), new LookupCondition(".expression-editor", false), (String)null, () -> null)))))));
    }
    
    private class EmptyCellSelectedCondition implements Condition
    {
        @Override
        public @OnThread(Tag.FXPlatform) void onSatisfied(FXPlatformRunnable runOnceSatsified)
        {
            virtualGrid.addSelectionListener(new SelectionListener()
            {
                @Override
                public @OnThread(Tag.FXPlatform) Pair<ListenerOutcome, @Nullable FXPlatformConsumer<VisibleBounds>> selectionChanged(@Nullable CellSelection oldSelection, @Nullable CellSelection newSelection)
                {
                    if (newSelection != null && newSelection.isExactly(newSelection.getActivateTarget()))
                    {
                        FXUtility.runAfter(runOnceSatsified);
                        return new Pair<>(ListenerOutcome.REMOVE, null);
                    }

                    return new Pair<>(ListenerOutcome.KEEP, null);
                }
            });
        }
    }
}
