package records.gui.table;

import com.google.common.collect.ImmutableList;
import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Region;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import log.Log;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.CellPosition;
import records.data.ColumnId;
import records.data.TableId;
import records.data.Transformation;
import records.data.datatype.DataType;
import records.gui.DataDisplay;
import records.gui.EditSortDialog;
import records.gui.HideColumnsDialog;
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
import records.transformations.Concatenate.IncompleteColumnHandling;
import records.transformations.Filter;
import records.transformations.HideColumns;
import records.transformations.Sort;
import records.transformations.SummaryStatistics;
import records.transformations.expression.Expression.MultipleTableLookup;
import styled.StyledCSS;
import styled.StyledString;
import styled.StyledString.Builder;
import threadchecker.OnThread;
import threadchecker.Tag;
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
    private TableDisplay tableDisplay;
    private final StyledString content;
    private final StyledString collapsedContent;
    private boolean collapsed = false;
    
    public TableHat(@UnknownInitialization(DataDisplay.class) TableDisplay tableDisplay, View parent, Transformation table)
    {
        super(ViewOrder.POPUP);
        if (table instanceof Filter)
        {
            Filter filter = (Filter)table;
            content = StyledString.concat(
                collapsedContent = StyledString.s("Filter"),
                StyledString.s(" "),
                tableDisplay.editSourceLink(parent, filter, filter.getSource(), newSource -> 
                    parent.getManager().edit(table.getId(), () -> new Filter(parent.getManager(), 
                        table.getDetailsForCopy(), newSource, filter.getFilterExpression()), null)),
                StyledString.s(", keeping rows where: "),
                tableDisplay.editExpressionLink(parent, filter.getFilterExpression(), parent.getManager().getSingleTableOrNull(filter.getSource()), new MultipleTableLookup(filter.getId(), parent.getManager(), filter.getSource()), DataType.BOOLEAN, newExp ->
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
                        new EditSortDialog(parent, screenPoint,
                            parent.getManager().getSingleTableOrNull(sort.getSource()),
                            sort,
                            sort.getSortBy()).showAndWait().ifPresent(newSort -> {
                                Workers.onWorkerThread("Editing sort", Priority.SAVE, () -> FXUtility.alertOnError_("Error editing sort", () -> 
                                    parent.getManager().edit(sort.getId(), () -> new Sort(parent.getManager(), sort.getDetailsForCopy(), sort.getSource(), newSort), null)
                                ));
                        });
                    }
                }
            });
            content = StyledString.concat(
                collapsedContent = StyledString.s("Sort"),
                StyledString.s(" "),
                tableDisplay.editSourceLink(parent, sort, sort.getSource(), newSource ->
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
                tableDisplay.editSourceLink(parent, aggregate, aggregate.getSource(), newSource ->
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
                    tableDisplay.editAggregateSplitBy(parent, aggregate);
                }
            };
            if (!splitBy.isEmpty())
            {
                builder.append(" splitting by ");
                builder.append(splitBy.stream().map(c -> c.toStyledString()).collect(StyledString.joining(", ")).withStyle(editSplitBy));
            }
            else
            {
                builder.append(StyledString.s("for whole table").withStyle(editSplitBy));
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
                                        parent.getManager().edit(table.getId(), () -> new Concatenate(parent.getManager(), table.getDetailsForCopy(), newList, IncompleteColumnHandling.DEFAULT), null);
                                })));
                            }
                        }
                    }
                )    
            );
        }
        else if (table instanceof Check)
        {
            Check check = (Check)table;
            content = StyledString.concat(
                collapsedContent = StyledString.s("Check"),
                StyledString.s(" "),
                tableDisplay.editSourceLink(parent, check, check.getSource(), newSource ->
                    parent.getManager().edit(table.getId(), () -> new Check(parent.getManager(),
                        table.getDetailsForCopy(), newSource, check.getCheckExpression()), null)),
                StyledString.s(" that "),
                tableDisplay.editExpressionLink(parent, check.getCheckExpression(), parent.getManager().getSingleTableOrNull(check.getSource()), new MultipleTableLookup(check.getId(), parent.getManager(), ((Check) table).getSource()), DataType.BOOLEAN, e -> 
                    parent.getManager().edit(check.getId(), () -> new Check(parent.getManager(), table.getDetailsForCopy(), check.getSource(), e), null)
                )
            );
        }
        else if (table instanceof Calculate)
        {
            Calculate calc = (Calculate)table;
            collapsedContent = StyledString.s("Calculate");
            StyledString.Builder builder = new Builder();
            builder.append("From ");
            builder.append(tableDisplay.editSourceLink(parent, calc, calc.getSource(), newSource -> 
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
                        FXUtility.alertOnErrorFX_("Error editing column", () -> tableDisplay.editColumn_Calc(parent, calc, c));
                    }
                }).withStyle(new StyledCSS("edit-calculate-column")));
                if (calc.getCalculatedColumns().keySet().size() > 3)
                    threeEditLinks = Stream.<StyledString>concat(threeEditLinks, Stream.<StyledString>of(StyledString.s("...")));
                builder.append(StyledString.intercalate(StyledString.s(", "), threeEditLinks.collect(Collectors.<StyledString>toList())));
            }
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
                    new HideColumnsDialog(parent.getWindow(), parent.getManager(), hide).showAndWait().ifPresent(makeTrans -> {
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
                    tableDisplay.editSourceLink(parent, hide, hide.getSource(), newSource ->
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

    @Override
    public Optional<BoundingBox> calculatePosition(VisibleBounds visibleBounds)
    {
        // The first time we are ever added, we will make a cell here and discard it,
        // but there's no good way round this:
        TableHatDisplay item = getNode() != null ? getNode() : makeCell(visibleBounds);
        
        double x = visibleBounds.getXCoord(tableDisplay.getPosition().columnIndex);
        double width = visibleBounds.getXCoordAfter(tableDisplay.getBottomRightIncl().columnIndex) - x;
        x += 30;
        width -= 40;
        width = Math.max(width, 250.0);
        // For some reason, BorderPane doesn't subtract insets from
        // prefWidth, so we must do it:
        Insets insets = item.getInsets();
        width -= insets.getLeft() + insets.getRight();

        double prefHeight = item.prefHeight(width);
        double y = Math.max(visibleBounds.getYCoord(tableDisplay.getPosition().rowIndex) - 10 - prefHeight, visibleBounds.getYCoord(CellPosition.row(0)) + 10);
        
        //Log.debug("Item " + collapsedContent.toPlain() + " positioned at " + x + ", " + y + " [" + width + " x " + prefHeight + "]");
        return Optional.of(new BoundingBox(
            x,
            y,
            width,
            prefHeight
        ));
    }

    @Override
    public TableHatDisplay makeCell(VisibleBounds visibleBounds)
    {
        return new TableHatDisplay();
    }

    private void toggleCollapse(TableHatDisplay display)
    {
        collapsed = !collapsed;
        FXUtility.setPseudoclass(display, "collapsed", collapsed);
        display.textFlow.getChildren().setAll((collapsed ? collapsedContent : content).toGUI().toArray(new Node[0]));
        display.collapse.setText(collapsed ? " \u25ba" : " \u25c4");
        display.collapseTip.setText(collapsed ? "Show detail" : "Hide detail");
        display.textFlow.getChildren().add(display.collapse);
        display.requestLayout();
        tableDisplay.relayoutGrid();
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
        // (Though maybe we should allow this somehow?s
    }
    
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
            // Re-layout once styles applied:
            /*
            FXUtility.addChangeListenerPlatformNN(borderPane.insetsProperty(), _insets -> {
                FXUtility.runAfter(() -> tableDisplay.relayoutGrid());
            });
            */
        }

        @Override
        protected void layoutChildren()
        {
            textFlow.resizeRelocate(INSET, INSET, getWidth() - INSET * 2, getHeight() - INSET * 2);
            //closeButton.relocate(getWidth() - closeButton.prefWidth(-1)  - 2, 2);
        }

        @Override
        protected double computePrefWidth(double height)
        {
            return textFlow.prefWidth(height == -1 ? -1 : height - INSET * 2) + INSET * 2;
        }

        @Override
        protected double computePrefHeight(double width)
        {
            return textFlow.prefHeight(width == -1 ? -1 : width - INSET * 2) + INSET * 2;
        }

        @Override
        public Orientation getContentBias()
        {
            return textFlow.getContentBias();
        }
    }
}
