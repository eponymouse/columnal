package records.gui.table;

import com.google.common.collect.ImmutableList;
import javafx.application.Platform;
import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.layout.BorderPane;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import log.Log;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.CellPosition;
import records.data.TableId;
import records.error.InternalException;
import records.error.UserException;
import records.gui.grid.VirtualGridSupplier;
import records.gui.grid.VirtualGridSupplier.ItemState;
import records.gui.grid.VirtualGridSupplier.ViewOrder;
import records.gui.grid.VirtualGridSupplier.VisibleBounds;
import records.gui.grid.VirtualGridSupplierFloating.FloatingItem;
import records.transformations.expression.explanation.Explanation;
import records.transformations.expression.explanation.ExplanationLocation;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.SimulationSupplier;
import utility.Workers;
import utility.Workers.Priority;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.Optional;
import java.util.Queue;

@OnThread(Tag.FXPlatform)
public class ExplanationDisplay extends FloatingItem<ExplanationDisplay.ExplanationPane>
{
    @OnThread(Tag.Any)
    private final Explanation explanation;
    private final CellPosition attachedTo;
    private final TableId srcTableId;

    protected ExplanationDisplay(TableId srcTableId, CellPosition attachedTo, Explanation explanation)
    {
        super(ViewOrder.POPUP);
        this.attachedTo = attachedTo;
        this.explanation = explanation;
        this.srcTableId = srcTableId;
    }

    @Override
    protected Optional<BoundingBox> calculatePosition(VisibleBounds visibleBounds)
    {
        double left = visibleBounds.getXCoord(attachedTo.columnIndex) - 80;
        double right = visibleBounds.getXCoordAfter(attachedTo.columnIndex) + 80;
        double top = visibleBounds.getYCoordAfter(attachedTo.rowIndex) + 10;
        double bottom = top + 300;
        return Optional.of(new BoundingBox(left, top, right - left, bottom - top));
    }

    @Override
    protected ExplanationPane makeCell(VisibleBounds visibleBounds)
    {
        return new ExplanationPane(() -> {
            ImmutableList.Builder<StyledString> lines = ImmutableList.builder();
            makeString(explanation, new HashSet<>(), lines);
            return lines.build().stream().collect(StyledString.joining("\n"));
        });
    }

    @OnThread(Tag.Simulation)
    private void makeString(Explanation explanation, HashSet<Explanation> alreadyExplained, ImmutableList.Builder<StyledString> lines) throws UserException, InternalException
    {
        // Go from lowest child upwards:
        for (Explanation child : explanation.getDirectSubExplanations())
        {
            makeString(child, alreadyExplained, lines);
        }

        StyledString description = explanation.describe(alreadyExplained, this::hyperlinkLocation);
        if (description != null)
            lines.add(description);
        
        alreadyExplained.add(explanation);
    }

    private StyledString hyperlinkLocation(ExplanationLocation explanationLocation)
    {
        String content = "";
        if (!explanationLocation.tableId.equals(srcTableId))
        {
            content += explanationLocation.tableId.getRaw() + ":";
        }
        content += explanationLocation.columnId.getRaw();
        // +1 to turn back into user index:
        if (explanationLocation.rowIndex.isPresent())
            content += " (row " + (explanationLocation.rowIndex.get() + 1) + ")";
        return StyledString.s(content);
    }

    @Override
    public VirtualGridSupplier.@Nullable ItemState getItemState(CellPosition cellPosition, Point2D screenPos)
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
        // No keyboard activation via cells
    }
    
    @OnThread(Tag.FXPlatform)
    static class ExplanationPane extends BorderPane
    {
        private final TextFlow textFlow;
        
        public ExplanationPane(SimulationSupplier<StyledString> loadContent)
        {
            textFlow = new TextFlow(new Text("Loading..."));
            Workers.onWorkerThread("Loading explanation", Priority.FETCH, () -> {
                StyledString content;
                try
                {
                    content = loadContent.get();
                }
                catch (InternalException | UserException e)
                {
                    Log.log(e);
                    content = StyledString.concat(StyledString.s("Error: "), e.getStyledMessage());
                }
                StyledString contentFinal = content;
                Platform.runLater(() -> textFlow.getChildren().setAll(contentFinal.toGUI()));
            });
            textFlow.getStyleClass().add("explanation-flow");
            setCenter(textFlow);
        }
    }
}
