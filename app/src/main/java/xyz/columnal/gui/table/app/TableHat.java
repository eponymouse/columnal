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

package xyz.columnal.gui.table.app;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import javafx.application.Platform;
import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.geometry.Orientation;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Region;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.util.Duration;
import xyz.columnal.gui.EditCheckExpressionDialog;
import xyz.columnal.gui.EditExpressionDialog;
import xyz.columnal.gui.EditJoinDialog;
import xyz.columnal.gui.EditRTransformationDialog;
import xyz.columnal.gui.EditSortDialog;
import xyz.columnal.gui.table.EntireTableSelection;
import xyz.columnal.gui.dialog.HideColumnsDialog;
import xyz.columnal.gui.ManualEditEntriesDialog;
import xyz.columnal.gui.PickManualEditIdentifierDialog;
import xyz.columnal.gui.PickTableDialog;
import xyz.columnal.gui.TableListDialog;
import xyz.columnal.gui.View;
import xyz.columnal.gui.table.EntireTableSelection;
import xyz.columnal.gui.table.HeadedDisplay;
import xyz.columnal.log.Log;
import org.checkerframework.checker.i18n.qual.LocalizableKey;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.CellPosition;
import xyz.columnal.id.ColumnId;
import xyz.columnal.id.DataItemPosition;
import xyz.columnal.data.RecordSet;
import xyz.columnal.data.RenameOnEdit;
import xyz.columnal.data.SingleSourceTransformation;
import xyz.columnal.data.Table;
import xyz.columnal.id.TableId;
import xyz.columnal.data.Transformation;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.DataTypeValue;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.gui.ManualEditEntriesDialog.Entry;
import xyz.columnal.gui.grid.RectangleBounds;
import xyz.columnal.gui.grid.VirtualGridSupplier.ItemState;
import xyz.columnal.gui.grid.VirtualGridSupplier.ViewOrder;
import xyz.columnal.gui.grid.VirtualGridSupplier.VisibleBounds;
import xyz.columnal.gui.grid.VirtualGridSupplierFloating.FloatingItem;
import xyz.columnal.gui.highlights.TableHighlights.HighlightType;
import xyz.columnal.gui.highlights.TableHighlights.PickResult;
import xyz.columnal.gui.table.app.TableHat.TableHatDisplay;
import xyz.columnal.transformations.Aggregate;
import xyz.columnal.transformations.Calculate;
import xyz.columnal.transformations.Check;
import xyz.columnal.transformations.Concatenate;
import xyz.columnal.transformations.Filter;
import xyz.columnal.transformations.HideColumns;
import xyz.columnal.transformations.Join;
import xyz.columnal.transformations.ManualEdit;
import xyz.columnal.transformations.ManualEdit.ColumnReplacementValues;
import xyz.columnal.transformations.MultipleTableLookup;
import xyz.columnal.transformations.RTransformation;
import xyz.columnal.transformations.Sort;
import xyz.columnal.transformations.TransformationVisitor;
import xyz.columnal.transformations.VisitableTransformation;
import xyz.columnal.transformations.expression.Expression;
import xyz.columnal.transformations.expression.Expression.ColumnLookup;
import xyz.columnal.transformations.expression.TypeState;
import xyz.columnal.styled.StyledCSS;
import xyz.columnal.styled.StyledString;
import xyz.columnal.styled.StyledString.Builder;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.function.ExFunction;
import xyz.columnal.utility.function.fx.FXPlatformFunction;
import xyz.columnal.utility.function.fx.FXPlatformRunnable;
import xyz.columnal.utility.function.fx.FXPlatformSupplierInt;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.function.simulation.SimulationConsumer;
import xyz.columnal.utility.function.simulation.SimulationFunctionInt;
import xyz.columnal.utility.TranslationUtility;
import xyz.columnal.utility.Utility;
import xyz.columnal.utility.Workers;
import xyz.columnal.utility.Workers.Priority;
import xyz.columnal.utility.gui.Clickable;
import xyz.columnal.utility.gui.FXUtility;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The popup-looking display for a transformation that gives
 * details about the table.
 */
@OnThread(Tag.FXPlatform)
class TableHat extends FloatingItem<TableHatDisplay>
{
    private FXPlatformRunnable updateGUI = () -> {};
    private final HeadedDisplay tableDisplay;
    private StyledString content;
    private StyledString collapsedContent = StyledString.s("Transformation");
    private boolean collapsed = false;

    public TableHat(@UnknownInitialization(HeadedDisplay.class) HeadedDisplay tableDisplay, View parent, VisitableTransformation table)
    {
        super(ViewOrder.TABLE_HAT);
        this.content = table.visit(new TransformationVisitor<StyledString>()
        {
            @Override
            @OnThread(Tag.FXPlatform)
            public StyledString filter(Filter filter)
            {
                return StyledString.concat(
                        collapsedContent = StyledString.s("Filter"),
                        StyledString.s(" "),
                        editSourceLink(parent, filter),
                        StyledString.s(", keeping rows where: "),
                        editExpressionLink(parent, filter.getFilterExpression(), parent.getManager().getSingleTableOrNull(filter.getSrcTableId()), new MultipleTableLookup(filter.getId(), parent.getManager(), filter.getSrcTableId(), null), () -> Filter.makeTypeState(parent.getManager().getTypeManager()), DataType.BOOLEAN, "filter.header", newExp ->
                                parent.getManager().edit(table, id -> new Filter(parent.getManager(),
                                        table.getDetailsForCopy(id), filter.getSrcTableId(), newExp), RenameOnEdit.ifOldAuto(Filter.suggestedName(newExp))))
                );
            }

            @Override
            @OnThread(Tag.FXPlatform)
            public StyledString runR(RTransformation rTransformation)
            {
                String rExpression = rTransformation.getRExpression();
                return StyledString.concat(
                    collapsedContent = StyledString.s("Run R"),
                    StyledString.s(" expression "),
                    StyledString.s(rExpression.trim().isEmpty() ? "<blank>" : rExpression).limit(60).withStyle(new Clickable() {

                        @Override
                        protected void onClick(MouseButton mouseButton, Point2D screenPoint)
                        {
                        editR(parent, rTransformation, false, RenameOnEdit::ifOldAuto);
                        }
                    })
                );
            }

            @Override
            @OnThread(Tag.FXPlatform)
            public StyledString sort(Sort sort)
            {
                StyledString sourceText = sort.getSortBy().isEmpty() ?
                        StyledString.s("original order") :
                        sort.getSortBy().stream().map(c -> StyledString.concat(c.getFirst().toStyledString(), c.getSecond().toStyledString())).collect(StyledString.joining(", "));
                sourceText = sourceText.withStyle(new Clickable()
                {
                    @Override
                    @OnThread(Tag.FXPlatform)
                    protected void onClick(MouseButton mouseButton, Point2D screenPoint)
                    {
                        if (mouseButton == MouseButton.PRIMARY)
                        {
                            editSort(screenPoint, parent, sort, RenameOnEdit::ifOldAuto);
                        }
                    }
                }).withStyle(new StyledCSS("edit-sort-by"));
                return StyledString.concat(
                        collapsedContent = StyledString.s("Sort"),
                        StyledString.s(" "),
                        editSourceLink(parent, sort),
                        StyledString.s(" by "),
                        sourceText
                );
            }
            
            @Override
            @OnThread(Tag.FXPlatform)
            public StyledString aggregate(Aggregate aggregate)
            {
                StyledString.Builder builder = new StyledString.Builder();
                builder.append(collapsedContent = StyledString.s("Aggregate"));
                builder.append(" ");
                builder.append(editSourceLink(parent, aggregate));
                // TODO should we add the calculations here if there are only one or two?

                List<ColumnId> splitBy = aggregate.getSplitBy();
                Clickable editSplitBy = new Clickable()
                {
                    @Override
                    protected @OnThread(Tag.FXPlatform) void onClick(MouseButton mouseButton, Point2D screenPoint)
                    {
                        TransformationEdits.editAggregateSplitBy(parent, aggregate);
                    }
                };
                if (!splitBy.isEmpty())
                {
                    builder.append(" splitting by ");
                    builder.append(splitBy.stream().map(c -> c.toStyledString()).collect(StyledString.joining(", ")).withStyle(editSplitBy));
                }
                else
                {
                    builder.append(StyledString.s(" across whole table").withStyle(editSplitBy));
                    builder.append(".");
                }
                return builder.build();
            }

            @Override
            @OnThread(Tag.FXPlatform)
            public StyledString concatenate(Concatenate concatenate)
            {
                Stream<TableId> sources = concatenate.getPrimarySources();
                StyledString sourceText = sources.map(t -> t.toStyledString()).collect(StyledString.joining(", "));
                if (sourceText.toPlain().isEmpty())
                {
                    sourceText = StyledString.s("no tables");
                }
                return StyledString.concat(
                        collapsedContent = StyledString.s("Concatenate"),
                        StyledString.s(" "),
                        sourceText.withStyle(
                                new Clickable("click.to.change") {
                                    @Override
                                    @OnThread(Tag.FXPlatform)
                                    protected void onClick(MouseButton mouseButton, Point2D screenPoint)
                                    {
                                        if (mouseButton == MouseButton.PRIMARY)
                                        {
                                            editConcatenate(screenPoint, parent, concatenate, RenameOnEdit::ifOldAuto);
                                        }
                                    }

                                    @Override
                                    protected void setHovering(boolean hovering, Point2D screenPos)
                                    {
                                        if (hovering)
                                        {
                                            ImmutableList<TableDisplay> tableDisplays = Utility.filterOutNulls(Utility.filterOutNulls(concatenate.getPrimarySources().<@Nullable Table>map(id -> parent.getManager().getSingleTableOrNull(id))).<@Nullable TableDisplay>map(t -> (TableDisplay)t.getDisplay())).collect(ImmutableList.<TableDisplay>toImmutableList());
                                            ImmutableList<RectangleBounds> srcTableBounds = Utility.mapListI(tableDisplays, t -> new RectangleBounds(t.getMostRecentPosition(), t.getBottomRightIncl()));
                                            parent.getHighlights().highlightAtScreenPos(new Point2D(0, 0), p -> {
                                                return new PickResult<String>(srcTableBounds, HighlightType.SOURCE, "", ImmutableList.<FXPlatformFunction<Point2D, Point2D>>copyOf(Utility.<FXPlatformFunction<Point2D, Point2D>>replicate(srcTableBounds.size(), s -> screenPos)));
                                            }, c -> {
                                            });
                                        }
                                        else
                                            parent.getHighlights().stopHighlightingGridArea();
                                    }
                                }
                        ),
                        StyledString.s(" - "),
                        StyledString.s(
                                concatenate.isIncludeMarkerColumn()
                                        ? "with source column"
                                        : "without source column"
                        ).withStyle(new Clickable("click.to.toggle") {
                            @Override
                            protected @OnThread(Tag.FXPlatform) void onClick(MouseButton mouseButton, Point2D screenPoint)
                            {
                                if (mouseButton == MouseButton.PRIMARY)
                                {
                                    Workers.onWorkerThread("Editing concatenate", Priority.SAVE, () -> FXUtility.alertOnError_(TranslationUtility.getString("error.editing.concatenate"), () -> {
                                        parent.getManager().edit(table, id -> new Concatenate(parent.getManager(), table.getDetailsForCopy(id), concatenate.getPrimarySources().collect(ImmutableList.<TableId>toImmutableList()), concatenate.getIncompleteColumnHandling(), !concatenate.isIncludeMarkerColumn()), RenameOnEdit.UNNEEDED /* no rename when toggling source column */);
                                    }));
                                }
                            }
                        })
                );
            }

            @Override
            @OnThread(Tag.FXPlatform)
            public StyledString check(Check check)
            {
                String type = "";
                switch (check.getCheckType())
                {
                    case ALL_ROWS:
                        type = "for all rows ";
                        break;
                    case ANY_ROW:
                        type = "at least one row ";
                        break;
                    case NO_ROWS:
                        type = "no rows ";
                        break;
                }
                return StyledString.concat(
                        collapsedContent = StyledString.s("Check"),
                        StyledString.s(" "),
                        editSourceLink(parent, check),
                        StyledString.s(" that "),
                        StyledString.s(type),
                        editCheckLink(parent, check, parent.getManager().getSingleTableOrNull(check.getSrcTableId()), RenameOnEdit::ifOldAuto)
                );
            }
            
            @Override
            @OnThread(Tag.FXPlatform)
            public StyledString calculate(Calculate calc)
            {
                collapsedContent = StyledString.s("Calculate");
                StyledString.Builder builder = new Builder();
                builder.append("From ");
                builder.append(editSourceLink(parent, calc));
                builder.append(" calculate ");
                if (calc.getCalculatedColumns().isEmpty())
                {
                    builder.append("<none>");
                }
                else
                {
                    // Mention max three columns
                    Stream<StyledString> threeEditLinks = calc.getCalculatedColumns().keySet().stream().limit(3).map(c -> c.toStyledString().withStyle(new Clickable()
                    {
                        @Override
                        protected @OnThread(Tag.FXPlatform) void onClick(MouseButton mouseButton, Point2D screenPoint)
                        {
                            FXUtility.alertOnErrorFX_(TranslationUtility.getString("error.editing.column"), () -> TransformationEdits.editColumn_Calc(parent, calc, c));
                        }
                    }).withStyle(new StyledCSS("edit-calculate-column")));
                    if (calc.getCalculatedColumns().keySet().size() > 3)
                        threeEditLinks = Stream.<StyledString>concat(threeEditLinks, Stream.<StyledString>of(StyledString.s("...")));
                    builder.append(StyledString.intercalate(StyledString.s(", "), threeEditLinks.collect(Collectors.<StyledString>toList())));
                }
            /*
            builder.append(" ");
            builder.append(StyledString.s("(add new)").withStyle(new Clickable() {
                @Override
                protected @OnThread(Tag.FXPlatform) void onClick(MouseButton mouseButton, Point2D screenPoint)
                {
                    if (mouseButton == MouseButton.PRIMARY)
                    {
                        tableDisplay.addColumnBefore_Calc(parent, calc, null, null);
                    }
                }
            }));
            */
                return builder.build();
            }
            
            @Override
            @OnThread(Tag.FXPlatform)
            public StyledString hideColumns(HideColumns hide)
            {
                Clickable edit = new Clickable(null, "edit-hide-columns")
                {
                    @OnThread(Tag.FXPlatform)
                    @Override
                    protected void onClick(MouseButton mouseButton, Point2D screenPoint)
                    {
                        editHideColumns(parent, hide, RenameOnEdit::ifOldAuto);
                    }
                };

                collapsedContent = StyledString.s("Exclude columns");
                return StyledString.concat(
                        StyledString.s("From "),
                        editSourceLink(parent, hide),
                        StyledString.s(", exclude columns: "),
                        hide.getHiddenColumns().isEmpty() ? StyledString.s("<none>") : hide.getHiddenColumns().stream().map(c -> c.toStyledString()).collect(StyledString.joining(", ")),
                        StyledString.s(" "),
                        StyledString.s("(edit)").withStyle(edit)
                );
            }

            @Override
            @OnThread(Tag.FXPlatform)
            public StyledString manualEdit(ManualEdit manualEdit)
            {
                collapsedContent = StyledString.s("Edit");

                Clickable editBy = new Clickable()
                {
                    @Override
                    protected @OnThread(Tag.FXPlatform) void onClick(MouseButton mouseButton, Point2D screenPoint)
                    {

                        ImmutableList<ColumnId> columnIds = ImmutableList.of();
                        try
                        {
                            columnIds = manualEdit.getData().getColumnIds();
                        }
                        catch (InternalException | UserException e)
                        {
                            if (e instanceof InternalException)
                                Log.log(e);
                        }
                        new PickManualEditIdentifierDialog(parent, Optional.of(manualEdit.getReplacementIdentifier()), columnIds).showAndWait().ifPresent(newEditBy -> {
                            Workers.onWorkerThread("Changing edit transformation", Priority.SAVE, () -> FXUtility.alertOnError_(TranslationUtility.getString("error.changing.edit.transformation"), () -> {
                                SimulationFunctionInt<TableId, ManualEdit> maker = manualEdit.swapReplacementIdentifierTo(newEditBy.orElse(null));
                                parent.getManager().edit(manualEdit, maker, RenameOnEdit.ifOldAuto(ManualEdit.suggestedName(newEditBy.orElse(null), manualEdit.getReplacements())));
                            }));
                        });
                    }
                };

                Clickable editList = new Clickable() {

                    @Override
                    protected @OnThread(Tag.FXPlatform) void onClick(MouseButton mouseButton, Point2D screenPoint)
                    {
                        Workers.onWorkerThread("Fetching manual edit entries", Priority.FETCH, () -> {
                            ImmutableList<Entry> entries = ManualEditEntriesDialog.getEntries(manualEdit);
                            Platform.runLater(() -> {
                                ExFunction<ColumnId, DataType> lookupColumnType = c -> manualEdit.getData().getColumn(c).getType().getType();
                                new ManualEditEntriesDialog(parent, manualEdit.getReplacementIdentifier().orElse(null), lookupColumnType, entries).showAndWait().ifPresent(p -> {
                                    // We store this before editing as it will generate a new table display:
                                    TableDisplay tableDisplayCast = (TableDisplay) FXUtility.mouse(tableDisplay);
                                    ImmutableList<ColumnId> displayColumns = Utility.mapListI(tableDisplayCast.getDisplayColumns(), d -> d.getColumnId());

                                    // Need to change the values
                                    Workers.onWorkerThread("Removing manual edit entries", Priority.SAVE, () -> FXUtility.alertOnError_(TranslationUtility.getString("error.deleting.manual.edits"), () -> {
                                        ImmutableMap<ColumnId, ColumnReplacementValues> newValues = p.getSecond().get();
                                        if (!newValues.equals(manualEdit.getReplacements()))
                                        {
                                            parent.getManager().edit(manualEdit, manualEdit.swapReplacementsTo(newValues), RenameOnEdit.ifOldAuto(ManualEdit.suggestedName(manualEdit.getReplacementIdentifier().orElse(null), newValues)));
                                        
                                            FXUtility.runFX(() -> {
                                                // This is a hack; we need to wait until the new table display is shown for the manual edit before
                                                // querying it, especially because we need its known rows count to be accurate.  So we wait, but if the
                                                // system is loaded, it's possible this may not jump to the location:
                                                FXUtility.runAfterDelay(Duration.millis(500), () -> p.getFirst().ifPresent(jumpTo -> {
                                                    Utility.findFirstIndex(displayColumns, c -> c.equals(jumpTo.getSecond())).ifPresent(colIndex -> {
                                                        Workers.onWorkerThread("Finding manual edit location", Priority.FETCH, () -> {
                                                            int rowIndex = -1;
                                                            try
                                                            {
                                                                Table srcTable = manualEdit.getSrcTable();
                                                                if (srcTable == null)
                                                                    return; // Give up
                                                                RecordSet srcData = srcTable.getData();
                                                                int length = srcData.getLength();
                                                                @Nullable ColumnId keyColumnId = manualEdit.getReplacementIdentifier().orElse(null);
                                                                if (keyColumnId == null)
                                                                {
                                                                    rowIndex = ((Number) jumpTo.getFirst().getValue()).intValue();
                                                                }
                                                                else
                                                                {
                                                                    DataTypeValue keyColumn = srcData.getColumn(keyColumnId).getType();
                                                                    for (int row = 0; row < length; row++)
                                                                    {
                                                                        try
                                                                        {
                                                                            if (Utility.compareValues(keyColumn.getCollapsed(row), jumpTo.getFirst().getValue()) == 0)
                                                                                rowIndex = row;
                                                                        }
                                                                        catch (UserException e)
                                                                        {
                                                                            // Ignore fetch issues, just check following value.
                                                                        }
                                                                    }
                                                                }
                                                                int rowIndexFinal = rowIndex;
                                                                if (rowIndexFinal != -1)
                                                                {
                                                                    Platform.runLater(() -> {
                                                                        Table latestManualEdit = parent.getManager().getSingleTableOrNull(manualEdit.getId());
                                                                        if (latestManualEdit != null && latestManualEdit.getDisplay() instanceof TableDisplay)
                                                                        {
                                                                            TableDisplay latestTableDisplay = (TableDisplay) latestManualEdit.getDisplay();
                                                                            CellPosition cellPosition = latestTableDisplay.getDataPosition(DataItemPosition.row(rowIndexFinal), DataItemPosition.col(colIndex));
                                                                            parent.getGrid().findAndSelect(Either.left(cellPosition));
                                                                        }
                                                                    });
                                                                }
                                                            }
                                                            catch (InternalException | UserException e)
                                                            {
                                                                if (e instanceof InternalException)
                                                                    Log.log(e);
                                                            }
                                                        });
                                                    });
                                                }));
                                            });
                                        }
                                    }));
                                });
                            });
                        });
                    }
                };

                // Satisfy static analysis (will be overwritten by the runnable):
                content = StyledString.s("");

                FXPlatformRunnable setContent = () -> {
                    Optional<ColumnId> byColumn = manualEdit.getReplacementIdentifier();
                    content = StyledString.concat(
                            StyledString.s("Edit "),
                            editSourceLink(parent, manualEdit),
                            StyledString.s(" to change "),
                            StyledString.s(Integer.toString(manualEdit.getReplacementCount()) + " entries").withStyle(editList).withStyle(new StyledCSS("manual-edit-entries")),
                            StyledString.s(" identified by "),
                            byColumn.map(c -> c.toStyledString()).orElse(StyledString.s("row number")).withStyle(editBy)
                    );
                    updateGUI.run();
                };
                setContent.run();
                Workers.onWorkerThread("Adding edit listener", Priority.FETCH, () -> manualEdit.addModificationListener(() -> FXUtility.runFX(setContent)));

                
                return content;
            }

            @Override
            @OnThread(Tag.FXPlatform)
            public StyledString join(Join join)
            {
                collapsedContent = StyledString.s("Join");
                Clickable clickToEdit = new Clickable() {

                    @Override
                    protected @OnThread(Tag.FXPlatform) void onClick(MouseButton mouseButton, Point2D screenPoint)
                    {
                        editJoin(parent, join, RenameOnEdit::ifOldAuto);
                    }
                };
                return StyledString.concat(
                        StyledString.s("Join "),
                        join.getPrimarySource().toStyledString().withStyle(clickToEdit),
                        StyledString.s(" with "),
                        join.getSecondarySource().toStyledString().withStyle(clickToEdit),
                        join.getColumnsToMatch().isEmpty() ? StyledString.s("") :
                                StyledString.concat(StyledString.s(" on "),
                                        join.getColumnsToMatch().stream().map(p -> p.getFirst().equals(p.getSecond()) ? p.getFirst().toStyledString() : StyledString.concat(p.getFirst().toStyledString(), StyledString.s(" = "), p.getSecond().toStyledString())).collect(StyledString.joining(" and ")).withStyle(clickToEdit)
                                )
                );
            }    
        });
        
        this.tableDisplay = Utility.later(tableDisplay);
    }

    public static void editHideColumns(View parent, HideColumns hide, Function<TableId, RenameOnEdit> renameOnEdit)
    {
        new HideColumnsDialog(parent, parent.getManager(), hide).showAndWait().ifPresent(newHidden -> {
            Workers.onWorkerThread("Changing hidden columns", Priority.SAVE, () ->
                    FXUtility.alertOnError_(TranslationUtility.getString("error.hiding.column"), () -> {
                        parent.getManager().<HideColumns>edit(hide, id -> new HideColumns(parent.getManager(), hide.getDetailsForCopy(id), hide.getSrcTableId(), newHidden), renameOnEdit.apply(HideColumns.suggestedName(newHidden)));
                    })
            );
        });
    }

    public static void editConcatenate(Point2D screenPoint, View parent, Concatenate concatenate, Function<TableId, RenameOnEdit> renameOnEdit)
    {
        new TableListDialog(parent, concatenate, concatenate.getPrimarySources().collect(ImmutableList.<TableId>toImmutableList()), screenPoint).showAndWait().ifPresent(newList -> 
            Workers.onWorkerThread("Editing concatenate", Priority.SAVE, () -> FXUtility.alertOnError_(TranslationUtility.getString("error.editing.concatenate"), () -> {
                parent.getManager().edit(concatenate, id -> new Concatenate(parent.getManager(), concatenate.getDetailsForCopy(id), newList, concatenate.getIncompleteColumnHandling(), concatenate.isIncludeMarkerColumn()), renameOnEdit.apply(Concatenate.suggestedName(newList)));
        })));
    }

    protected static void editSort(@Nullable Point2D screenPoint, View parent, Sort sort, Function<TableId, RenameOnEdit> renameOnEdit)
    {
        new EditSortDialog(parent, screenPoint,
            parent.getManager().getSingleTableOrNull(sort.getSrcTableId()),
            sort,
            sort.getSortBy().isEmpty() ? null : sort.getSortBy()).showAndWait().ifPresent(newSort -> {
                Workers.onWorkerThread("Editing sort", Priority.SAVE, () -> FXUtility.alertOnError_(TranslationUtility.getString("error.editing.sort"), () -> 
                    parent.getManager().edit(sort, id -> new Sort(parent.getManager(), sort.getDetailsForCopy(id), sort.getSrcTableId(), newSort), renameOnEdit.apply(Sort.suggestedName(newSort)))
                ));
        });
    }
    
    protected static void editManualEdit(View parent, ManualEdit manualEdit, boolean deleteIfCancel, Function<TableId, RenameOnEdit> renameOnEdit)
    {
        ImmutableList<ColumnId> columnIds = ImmutableList.of();
        try
        {
            Table srcTable = manualEdit.getSrcTable();
            if (srcTable != null)
                columnIds = srcTable.getData().getColumnIds();
        }
        catch (InternalException | UserException e)
        {
            if (e instanceof InternalException)
                Log.log(e);
        }
        Optional<Optional<ColumnId>> columnId = new PickManualEditIdentifierDialog(parent, deleteIfCancel ? Optional.empty() : Optional.of(manualEdit.getReplacementIdentifier()), columnIds).showAndWait();
        columnId.ifPresent(maybeCol -> Workers.onWorkerThread("Editing manual edit", Priority.SAVE, () -> FXUtility.alertOnError_(TranslationUtility.getString("error.editing.manual.edit"), () -> {
            ColumnId newReplacementKey = maybeCol.orElse(null);
            SimulationFunctionInt<TableId, ManualEdit> makeSwapped = manualEdit.swapReplacementIdentifierTo(newReplacementKey);
            parent.getManager().edit(manualEdit, makeSwapped, renameOnEdit.apply(ManualEdit.suggestedName(newReplacementKey, manualEdit.getReplacements())));
        })));
        if (!columnId.isPresent() && deleteIfCancel)
        {
            Workers.onWorkerThread("Cancelling manual edit", Priority.SAVE, () -> {
                parent.getManager().remove(manualEdit.getId());
            });
        }
            
    }

    private static Function<String, @Nullable ColumnId> hasColumn(@Nullable Table srcTable)
    {
        if (srcTable != null)
        {
            try
            {
                RecordSet rs = srcTable.getData();
                return s -> rs.getColumnIds().stream().filter(c -> c.getRaw().equals(s)).findFirst().orElse(null);
            }
            catch (InternalException | UserException e)
            {
                if (e instanceof InternalException)
                    Log.log(e);
            }
        }
        return c -> null;
    }

    public static void editJoin(View parent, Join join, Function<TableId, RenameOnEdit> renameOnEdit)
    {
        new EditJoinDialog(parent, join).showAndWait().ifPresent(details ->
            Workers.onWorkerThread("Editing join", Priority.SAVE, () -> FXUtility.alertOnError_(TranslationUtility.getString("error.editing.join"), () -> {
                parent.getManager().edit(join, id -> new Join(parent.getManager(), join.getDetailsForCopy(id), details.primaryTableId, details.secondaryTableId, details.isLeftJoin, details.joinOn), renameOnEdit.apply(Join.suggestedName(details.joinOn)));
        })));
    }

    public static void editR(View parent, RTransformation rTransformation, boolean selectWholeExpression, Function<TableId, RenameOnEdit> renameOnEdit)
    {
        new EditRTransformationDialog(parent, rTransformation, selectWholeExpression).showAndWait().ifPresent(details -> {
            Workers.onWorkerThread("Editing R transformation", Priority.SAVE, () -> FXUtility.alertOnError_(TranslationUtility.getString("error.editing.r.transformation"), () -> {
                parent.getManager().unban(details.rExpression);
                parent.getManager().edit(rTransformation, id -> new RTransformation(parent.getManager(), rTransformation.getDetailsForCopy(id), details.includedTables, details.packages, details.rExpression), renameOnEdit.apply(RTransformation.suggestedName(details.rExpression)));
            }));
        });
    }

    @Override
    public Optional<BoundingBox> calculatePosition(VisibleBounds visibleBounds)
    {
        // The first time we are ever added, we will make a cell here and discard it,
        // but there's no good way round this:
        TableHatDisplay item;
        if (getNode() != null)
        {
            item = getNode();
        }
        else
        {
            item = makeCell(visibleBounds);
            FXUtility.runAfterDelay(Duration.millis(200), () -> tableDisplay.relayoutGrid());
        }

        // We have N possibilities:
        //  - One is that the table header can be fully above the table
        //    In this case, the preferred width will be at most the table width
        //    minus a little bit, and the required height to show all text.
        //  If the table header can't be above the table, we will have to overlap
        //  - One possibility is we overlap the header, starting to the right of
        //    the table name.  However, if this overlaps the column names,
        //    we prefer the last option:
        //  - We overlap almost all of the table name if it will help us reveal
        //    the column names.
        
        final double INDENT = 20;
        final double MIN_WIDTH = 200;
        
        double veryTopY = visibleBounds.getYCoord(CellPosition.row(0));
        
        double tableX = visibleBounds.getXCoord(tableDisplay.getPosition().columnIndex);
        double tableY = visibleBounds.getYCoord(tableDisplay.getPosition().rowIndex);
        double columnTopY = visibleBounds.getYCoordAfter(tableDisplay.getPosition().rowIndex);
        double wholeTableWidth = visibleBounds.getXCoordAfter(tableDisplay.getBottomRightIncl().columnIndex) - tableX;

        double idealWidth = item.prefWidth(-1);
        double idealWidthAbove = Math.min(Math.max(MIN_WIDTH, wholeTableWidth - INDENT), idealWidth);
        double heightAbove = item.prefHeight(idealWidthAbove);
        
        if (tableY - heightAbove >= veryTopY - 2)
        {
            // Just about fit without overlapping table header; do it!
            return Optional.of(new BoundingBox(
                tableX + INDENT,
                Math.max(veryTopY, tableY - heightAbove - 5),
                idealWidthAbove,
                heightAbove    
            ));
        }
        
        // Now try the overlapping options.  First, try overlapping the table name:
        double tableNameWidth = tableDisplay.getTableNameWidth();
        double widthWithoutOverlappingTableName = Math.min(wholeTableWidth - INDENT - tableNameWidth, idealWidth);
        double heightWithoutOverlappingTableName = item.prefHeight(widthWithoutOverlappingTableName);
        
        if (columnTopY - heightWithoutOverlappingTableName >= veryTopY)
        {
            // Just about fit without overlapping column names; do it!
            return Optional.of(new BoundingBox(
                    tableX + INDENT + tableNameWidth,
                    veryTopY + 2,
                    widthWithoutOverlappingTableName,
                    heightWithoutOverlappingTableName
            ));
        }
        
        // Running out of options, overlap table name and hope we don't overlap
        // the column names too badly:
        return Optional.of(new BoundingBox(
            tableX + INDENT,
            veryTopY + 2,
            idealWidthAbove,
            heightAbove
        ));
    }

    @Override
    public TableHatDisplay makeCell(VisibleBounds visibleBounds)
    {
        return new TableHatDisplay();
    }

    private void toggleCollapse(TableHatDisplay display)
    {
        //org.scenicview.ScenicView.show(display.getScene());
        collapsed = !collapsed;
        FXUtility.setPseudoclass(display, "collapsed", collapsed);
        updateGUI(display);
    }

    private void updateGUI(TableHatDisplay display)
    {
        display.textFlow.getChildren().setAll((collapsed ? collapsedContent : content).toGUI().toArray(new Node[0]));
        display.collapse.setText(collapsed ? " \u25ba" : " \u25c4");
        display.collapseTip.setText(collapsed ? "Show detail" : "Hide detail");
        display.textFlow.getChildren().add(display.collapse);
        display.requestLayout();
        FXUtility.runAfterDelay(Duration.millis(100), () -> tableDisplay.relayoutGrid());
    }

    void setCollapsed(boolean collapsed)
    {
        if (this.collapsed != collapsed)
        {
            TableHatDisplay tableHatDisplay = getNode();
            if (tableHatDisplay != null)
                toggleCollapse(tableHatDisplay);
        }
    }

    @Override
    public @Nullable Pair<ItemState, @Nullable StyledString> getItemState(CellPosition cellPosition, Point2D screenPos)
    {
        Node node = getNode();
        if (node != null)
        {
            Bounds screenBounds = node.localToScreen(node.getBoundsInLocal());
            return screenBounds.contains(screenPos) ? new Pair<>(ItemState.DIRECTLY_CLICKABLE, null) : null;
        }
        return null;
    }

    @Override
    public void keyboardActivate(CellPosition cellPosition)
    {
        // Hat can't be triggered via keyboard
        // (Though maybe we should allow this somehow?)
    }

    private static <T extends Transformation & SingleSourceTransformation> StyledString editSourceLink(View parent, T table)
    {
        return editSourceLink(parent, table, table.getSrcTableId(), newSrcTableId -> parent.getManager().edit(table, _id -> table.withNewSource(newSrcTableId), RenameOnEdit.UNNEEDED /* ideally would rename, but will live without */));
    }

    private static StyledString editSourceLink(View parent, Table destTable, TableId srcTableId, SimulationConsumer<TableId> changeSrcTableId)
    {
        // If this becomes broken/unbroken, we should get re-run:
        @Nullable Table srcTable = parent.getManager().getSingleTableOrNull(srcTableId);
        String[] styleClasses = srcTable == null ?
                new String[] { "broken-link" } : new String[0];
        return srcTableId.toStyledString().withStyle(new Clickable("source.link.tooltip", styleClasses) {
            @Override
            @OnThread(Tag.FXPlatform)
            protected void onClick(MouseButton mouseButton, Point2D screenPoint)
            {
                if (mouseButton == MouseButton.PRIMARY)
                {
                    new PickTableDialog(parent, destTable, screenPoint).showAndWait().ifPresent(t -> {
                        Workers.onWorkerThread(TranslationUtility.getString("error.editing.table.source"), Priority.SAVE, () -> FXUtility.alertOnError_(TranslationUtility.getString("error.editing.table"), () -> changeSrcTableId.consume(t.getId())));
                    });
                }
                else if (mouseButton == MouseButton.MIDDLE && srcTable != null && srcTable.getDisplay() instanceof TableDisplay)
                {
                    TableDisplay target = (TableDisplay) srcTable.getDisplay();
                    if (target != null)
                        parent.getGrid().select(new EntireTableSelection(target, target.getPosition().columnIndex));
                }
            }

            @Override
            protected void setHovering(boolean hovering, Point2D screenPos)
            {
                if (hovering)
                {
                    Table table = parent.getManager().getSingleTableOrNull(srcTableId);
                    if (table != null && table.getDisplay() != null)
                    {
                        RectangleBounds srcTableBounds = new RectangleBounds(table.getDisplay().getMostRecentPosition(), table.getDisplay().getBottomRightIncl());
                        parent.getHighlights().highlightAtScreenPos(new Point2D(0, 0), p -> {
                            return new PickResult<>(ImmutableList.of(srcTableBounds), HighlightType.SOURCE, "", ImmutableList.of(s -> screenPos));
                        }, c -> {
                        });
                    }
                }
                else
                    parent.getHighlights().stopHighlightingGridArea();
            }
        });
    }

    private static StyledString editExpressionLink(View parent, Expression curExpression, @Nullable Table srcTable, ColumnLookup columnLookup, FXPlatformSupplierInt<TypeState> makeTypeState, @Nullable DataType expectedType, @Nullable @LocalizableKey String headerKey, SimulationConsumer<Expression> changeExpression)
    {
        return curExpression.toSimpleStyledString().limit(60).withStyle(new Clickable() {
            @Override
            @OnThread(Tag.FXPlatform)
            protected void onClick(MouseButton mouseButton, Point2D screenPoint)
            {
                if (mouseButton == MouseButton.PRIMARY)
                {
                    new EditExpressionDialog(parent, srcTable, curExpression, false, columnLookup, makeTypeState, expectedType, headerKey).showAndWait().ifPresent(newExp -> {
                        Workers.onWorkerThread("Editing table source", Priority.SAVE, () -> FXUtility.alertOnError_(TranslationUtility.getString("error.editing.column"), () -> changeExpression.consume(newExp)));
                    });
                }
            }
        }).withStyle(new StyledCSS("edit-expression-link"));
    }

    private static StyledString editCheckLink(View parent, Check check, @Nullable Table srcTable, Function<TableId, RenameOnEdit> renameOnEdit)
    {
        return check.getCheckExpression().toSimpleStyledString().limit(60).withStyle(new Clickable() {
            @Override
            @OnThread(Tag.FXPlatform)
            protected void onClick(MouseButton mouseButton, Point2D screenPoint)
            {
                if (mouseButton == MouseButton.PRIMARY)
                {
                    new EditCheckExpressionDialog(parent, srcTable, check.getCheckType(), check.getCheckExpression(), false, ct -> Check.getColumnLookup(parent.getManager(), check.getSrcTableId(), check.getId(), ct)).showAndWait().ifPresent(p -> {
                        Workers.onWorkerThread("Editing table source", Priority.SAVE, () -> FXUtility.alertOnError_(TranslationUtility.getString("error.editing.column"), () -> {
                            parent.getManager().edit(check, id -> new Check(parent.getManager(), check.getDetailsForCopy(id), check.getSrcTableId(), p.getFirst(), p.getSecond()), renameOnEdit.apply(Check.suggestedName(p.getSecond())));
                        }));
                    });
                }
            }
        }).withStyle(new StyledCSS("edit-expression-link"));
    }

    @OnThread(Tag.FXPlatform)
    final class TableHatDisplay extends Region
    {
        private static final double INSET = 4.0;
        private final TextFlow textFlow;
        private final Text collapse;
        private final Tooltip collapseTip;
        //private final Polygon closeButton;

        public TableHatDisplay()
        {
            getStyleClass().add("table-hat");
            textFlow = new TextFlow(content.toGUI().toArray(new Node[0]));
            collapse = new Text(" \u25c4");
            collapse.getStyleClass().add("table-hat-collapse");
            collapseTip = new Tooltip("Hide detail");
            Tooltip.install(collapse, collapseTip);
            textFlow.getChildren().add(collapse);
            textFlow.getStyleClass().add("table-hat-text-flow");
            getChildren().setAll(textFlow);

            collapse.setOnMouseClicked(e -> {
                toggleCollapse(this);
                e.consume();
            });
            setOnMouseClicked(e -> {
                if (e.getButton() == MouseButton.MIDDLE)
                {
                    toggleCollapse(this);
                    e.consume();
                }
            });
            updateGUI = () -> updateGUI(this);
        }

        @Override
        @OnThread(value = Tag.FXPlatform, ignoreParent = true)
        protected void layoutChildren()
        {
            FXUtility.resizeRelocate(textFlow, INSET, INSET, getWidth() - INSET * 2, getHeight() - INSET * 2);
            //closeButton.relocate(getWidth() - closeButton.prefWidth(-1)  - 2, 2);
        }

        @Override
        @OnThread(value = Tag.FXPlatform, ignoreParent = true)
        protected double computePrefWidth(double height)
        {
            return textFlow.prefWidth(height == -1 ? -1 : (height - INSET * 2)) + INSET * 2;
        }

        @Override
        @OnThread(value = Tag.FXPlatform, ignoreParent = true)
        protected double computePrefHeight(double width)
        {
            return textFlow.prefHeight(width == -1 ? -1 : (width - INSET * 2)) + INSET * 2;
        }

        @Override
        @OnThread(value = Tag.FXPlatform, ignoreParent = true)
        public Orientation getContentBias()
        {
            return textFlow.getContentBias();
        }
    }
}
