package records.gui.table;

import com.google.common.collect.ImmutableList;
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
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.CellPosition;
import records.data.ColumnId;
import records.data.Table;
import records.data.TableId;
import records.data.Transformation;
import records.data.datatype.DataType;
import records.gui.EditExpressionDialog;
import records.gui.EditSortDialog;
import records.gui.EntireTableSelection;
import records.gui.HideColumnsDialog;
import records.gui.PickTableDialog;
import records.gui.TableListDialog;
import records.gui.View;
import records.gui.grid.VirtualGridSupplier.ItemState;
import records.gui.grid.VirtualGridSupplier.ViewOrder;
import records.gui.grid.VirtualGridSupplier.VisibleBounds;
import records.gui.grid.VirtualGridSupplierFloating.FloatingItem;
import records.gui.table.TableHat.TableHatDisplay;
import records.transformations.Calculate;
import records.transformations.Check;
import records.transformations.Concatenate;
import records.transformations.Filter;
import records.transformations.HideColumns;
import records.transformations.Sort;
import records.transformations.SummaryStatistics;
import records.transformations.expression.Expression;
import records.transformations.expression.Expression.ColumnLookup;
import records.transformations.expression.Expression.MultipleTableLookup;
import styled.StyledCSS;
import styled.StyledString;
import styled.StyledString.Builder;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.SimulationConsumer;
import utility.Utility;
import utility.Workers;
import utility.Workers.Priority;
import utility.gui.FXUtility;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The popup-looking display for a transformation that gives
 * details about the table.
 */
@OnThread(Tag.FXPlatform)
class TableHat extends FloatingItem<TableHatDisplay>
{
    private HeadedDisplay tableDisplay;
    private final StyledString content;
    private final StyledString collapsedContent;
    private boolean collapsed = false;
    
    public TableHat(@UnknownInitialization(HeadedDisplay.class) HeadedDisplay tableDisplay, View parent, Transformation table)
    {
        super(ViewOrder.POPUP);
        if (table instanceof Filter)
        {
            Filter filter = (Filter)table;
            content = StyledString.concat(
                collapsedContent = StyledString.s("Filter"),
                StyledString.s(" "),
                editSourceLink(parent, filter, filter.getSource(), newSource -> 
                    parent.getManager().edit(table.getId(), () -> new Filter(parent.getManager(), 
                        table.getDetailsForCopy(), newSource, filter.getFilterExpression()), null)),
                StyledString.s(", keeping rows where: "),
                editExpressionLink(parent, filter.getFilterExpression(), parent.getManager().getSingleTableOrNull(filter.getSource()), new MultipleTableLookup(filter.getId(), parent.getManager(), filter.getSource()), DataType.BOOLEAN, newExp ->
                    parent.getManager().edit(table.getId(), () -> new Filter(parent.getManager(),
                        table.getDetailsForCopy(), filter.getSource(), newExp), null))
            );
        }
        else if (table instanceof Sort)
        {
            Sort sort = (Sort)table;
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
                        edit_Sort(screenPoint, parent, sort);
                    }
                }
            });
            content = StyledString.concat(
                collapsedContent = StyledString.s("Sort"),
                StyledString.s(" "),
                editSourceLink(parent, sort, sort.getSource(), newSource ->
                        parent.getManager().edit(table.getId(), () -> new Sort(parent.getManager(),
                                table.getDetailsForCopy(), newSource, sort.getSortBy()), null)),
                StyledString.s(" by "),
                sourceText
            );
        }
        else if (table instanceof SummaryStatistics)
        {
            SummaryStatistics aggregate = (SummaryStatistics)table;
            StyledString.Builder builder = new StyledString.Builder();
            builder.append(collapsedContent = StyledString.s("Aggregate"));
            builder.append(" ");
            builder.append(
                editSourceLink(parent, aggregate, aggregate.getSource(), newSource ->
                        parent.getManager().edit(table.getId(), () -> new SummaryStatistics(parent.getManager(),
                                table.getDetailsForCopy(), newSource, aggregate.getColumnExpressions(), aggregate.getSplitBy()), null))
            );
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
            content = builder.build();
        }
        else if (table instanceof Concatenate)
        {
            Concatenate concatenate = (Concatenate)table;
            @OnThread(Tag.Any) Stream<TableId> sources = concatenate.getPrimarySources();
            StyledString sourceText = sources.map(t -> t.toStyledString()).collect(StyledString.joining(", "));
            if (sourceText.toPlain().isEmpty())
            {
                sourceText = StyledString.s("no tables");
            }
            content = StyledString.concat(
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
                                new TableListDialog(parent, concatenate, concatenate.getPrimarySources().collect(ImmutableList.<TableId>toImmutableList()), screenPoint).showAndWait().ifPresent(newList -> 
                                    Workers.onWorkerThread("Editing concatenate", Priority.SAVE, () -> FXUtility.alertOnError_("Error editing concatenate", () -> {
                                        parent.getManager().edit(table.getId(), () -> new Concatenate(parent.getManager(), table.getDetailsForCopy(), newList, concatenate.getIncompleteColumnHandling(), concatenate.isIncludeMarkerColumn()), null);
                                })));
                            }
                        }
                    }
                ),
                StyledString.s(" "),
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
                            Workers.onWorkerThread("Editing concatenate", Priority.SAVE, () -> FXUtility.alertOnError_("Error editing concatenate", () -> {
                                parent.getManager().edit(table.getId(), () -> new Concatenate(parent.getManager(), table.getDetailsForCopy(), concatenate.getPrimarySources().collect(ImmutableList.<TableId>toImmutableList()), concatenate.getIncompleteColumnHandling(), !concatenate.isIncludeMarkerColumn()), null);
                            }));
                        }
                    }
                })
            );
        }
        else if (table instanceof Check)
        {
            Check check = (Check)table;
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
            content = StyledString.concat(
                collapsedContent = StyledString.s("Check"),
                StyledString.s(" "),
                editSourceLink(parent, check, check.getSource(), newSource ->
                    parent.getManager().edit(table.getId(), () -> new Check(parent.getManager(),
                        table.getDetailsForCopy(), newSource, check.getCheckType(), check.getCheckExpression()), null)),
                StyledString.s(" that "),
                StyledString.s(type),
                editExpressionLink(parent, check.getCheckExpression(), parent.getManager().getSingleTableOrNull(check.getSource()), check.getColumnLookup(), DataType.BOOLEAN, e -> 
                    parent.getManager().edit(check.getId(), () -> new Check(parent.getManager(), table.getDetailsForCopy(), check.getSource(), check.getCheckType(), e), null)
                )
            );
        }
        else if (table instanceof Calculate)
        {
            Calculate calc = (Calculate)table;
            collapsedContent = StyledString.s("Calculate");
            StyledString.Builder builder = new Builder();
            builder.append("From ");
            builder.append(editSourceLink(parent, calc, calc.getSource(), newSource -> 
                parent.getManager().edit(calc.getId(), () -> new Calculate(parent.getManager(),
                    table.getDetailsForCopy(), newSource, calc.getCalculatedColumns()), null)));
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
                        FXUtility.alertOnErrorFX_("Error editing column", () -> TransformationEdits.editColumn_Calc(parent, calc, c));
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
            content = builder.build();
        }
        else if (table instanceof HideColumns)
        {
            HideColumns hide = (HideColumns) table;
            
            Clickable edit = new Clickable()
            {
                @OnThread(Tag.FXPlatform)
                @Override
                protected void onClick(MouseButton mouseButton, Point2D screenPoint)
                {
                    new HideColumnsDialog(parent, parent.getManager(), hide).showAndWait().ifPresent(makeTrans -> {
                        Workers.onWorkerThread("Changing hidden columns", Priority.SAVE, () ->
                                FXUtility.alertOnError_("Error hiding column", () -> {
                                    parent.getManager().edit(hide.getId(), makeTrans, null);
                                })
                        );
                    });
                }
            };
            
            collapsedContent = StyledString.s("Drop columns");
            content = StyledString.concat(
                    StyledString.s("From "),
                    editSourceLink(parent, hide, hide.getSource(), newSource ->
                            parent.getManager().edit(table.getId(), () -> new HideColumns(parent.getManager(),
                                    table.getDetailsForCopy(), newSource, hide.getHiddenColumns()), null)),
                    StyledString.s(", drop columns: "),
                    hide.getHiddenColumns().isEmpty() ? StyledString.s("<none>") : hide.getHiddenColumns().stream().map(c -> c.toStyledString()).collect(StyledString.joining(", ")),
                    StyledString.s(" "),
                    StyledString.s("(edit)").withStyle(edit)
            );
        }
        else
        {
            content = StyledString.s("");
            collapsedContent = StyledString.s("");
        }

        this.tableDisplay = Utility.later(tableDisplay);
    }

    protected static void edit_Sort(@Nullable Point2D screenPoint, View parent, Sort sort)
    {
        new EditSortDialog(parent, screenPoint,
            parent.getManager().getSingleTableOrNull(sort.getSource()),
            sort,
            sort.getSortBy()).showAndWait().ifPresent(newSort -> {
                Workers.onWorkerThread("Editing sort", Priority.SAVE, () -> FXUtility.alertOnError_("Error editing sort", () -> 
                    parent.getManager().edit(sort.getId(), () -> new Sort(parent.getManager(), sort.getDetailsForCopy(), sort.getSource(), newSort), null)
                ));
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
        
        double veryTopY = visibleBounds.getYCoord(CellPosition.row(0));
        
        double tableX = visibleBounds.getXCoord(tableDisplay.getPosition().columnIndex);
        double tableY = visibleBounds.getYCoord(tableDisplay.getPosition().rowIndex);
        double columnTopY = visibleBounds.getYCoordAfter(tableDisplay.getPosition().rowIndex);
        double wholeTableWidth = visibleBounds.getXCoordAfter(tableDisplay.getBottomRightIncl().columnIndex) - tableX;

        double idealWidth = item.prefWidth(-1);
        double idealWidthAbove = Math.min(wholeTableWidth - INDENT, idealWidth);
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
        display.textFlow.getChildren().setAll((collapsed ? collapsedContent : content).toGUI().toArray(new Node[0]));
        display.collapse.setText(collapsed ? " \u25ba" : " \u25c4");
        display.collapseTip.setText(collapsed ? "Show detail" : "Hide detail");
        display.textFlow.getChildren().add(display.collapse);
        display.requestLayout();
        FXUtility.runAfterDelay(Duration.millis(100), () -> tableDisplay.relayoutGrid());
    }

    @Override
    public @Nullable ItemState getItemState(CellPosition cellPosition, Point2D screenPos)
    {
        Node node = getNode();
        if (node != null)
        {
            Bounds screenBounds = node.localToScreen(node.getBoundsInLocal());
            return screenBounds.contains(screenPos) ? ItemState.DIRECTLY_CLICKABLE : null;
        }
        return null;
    }

    @Override
    public void keyboardActivate(CellPosition cellPosition)
    {
        // Hat can't be triggered via keyboard
        // (Though maybe we should allow this somehow?)
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
                        Workers.onWorkerThread("Editing table source", Priority.SAVE, () -> FXUtility.alertOnError_("Error editing table", () -> changeSrcTableId.consume(t.getId())));
                    });
                }
                else if (mouseButton == MouseButton.MIDDLE && srcTable != null && srcTable.getDisplay() instanceof TableDisplay)
                {
                    TableDisplay target = (TableDisplay) srcTable.getDisplay();
                    if (target != null)
                        parent.getGrid().select(new EntireTableSelection(target, target.getPosition().columnIndex));
                }
            }
        });
    }

    private static StyledString editExpressionLink(View parent, Expression curExpression, @Nullable Table srcTable, ColumnLookup columnLookup, @Nullable DataType expectedType, SimulationConsumer<Expression> changeExpression)
    {
        return curExpression.toStyledString().withStyle(new Clickable() {
            @Override
            @OnThread(Tag.FXPlatform)
            protected void onClick(MouseButton mouseButton, Point2D screenPoint)
            {
                if (mouseButton == MouseButton.PRIMARY)
                {
                    new EditExpressionDialog(parent, srcTable, curExpression, columnLookup, expectedType).showAndWait().ifPresent(newExp -> {
                        Workers.onWorkerThread("Editing table source", Priority.SAVE, () -> FXUtility.alertOnError_("Error editing column", () -> changeExpression.consume(newExp)));
                    });
                }
            }
        }).withStyle(new StyledCSS("edit-expression-link"));
    }


    @OnThread(Tag.FXPlatform)
    class TableHatDisplay extends Region
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
                toggleCollapse(FXUtility.mouse(this));
                e.consume();
            });
            setOnMouseClicked(e -> {
                if (e.getButton() == MouseButton.MIDDLE)
                {
                    toggleCollapse(FXUtility.mouse(this));
                    e.consume();
                }
            });
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
