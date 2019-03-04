package test.gui.trait;

import annotation.units.TableDataColIndex;
import annotation.units.TableDataRowIndex;
import javafx.geometry.Bounds;
import javafx.geometry.HorizontalDirection;
import javafx.geometry.Orientation;
import javafx.geometry.VerticalDirection;
import javafx.scene.Node;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import log.Log;
import org.apache.commons.lang3.SystemUtils;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.testfx.api.FxRobotInterface;
import org.testfx.service.query.NodeQuery;
import records.data.CellPosition;
import records.data.DataItemPosition;
import records.data.Table;
import records.data.Table.TableDisplayBase;
import records.data.TableId;
import records.data.TableManager;
import records.error.UserException;
import records.gui.table.TableDisplay;
import records.gui.grid.CellSelection;
import records.gui.grid.VirtualGrid;
import test.TestUtil;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Utility;

import java.util.Optional;
import java.util.Random;

import static org.junit.Assert.*;

public interface ScrollToTrait extends FxRobotInterface, FocusOwnerTrait
{
    // Scrolls until the entire node is on screen
    @OnThread(Tag.Any)
    default public void scrollTo(String nodeLocator)
    {
        scrollTo(lookup(nodeLocator));
    }

    // Scrolls until the entire node is on screen
    @OnThread(Tag.Any)
    default public void scrollTo(NodeQuery nodeLocator)
    {
        @Nullable Node targetQ = nodeLocator.query();
        if (targetQ == null)
        {
            System.err.println("No such node to scroll to: " + nodeLocator);
            return;
        }
        scrollTo(targetQ);
    }

    // Scrolls until the entire node is on screen
    @OnThread(Tag.Any)
    default public void scrollTo(Node target)
    {
        // Find enclosing scroll:
        ScrollPane enclosingScroll = TestUtil.fx(() -> {
            Node n = target.getParent();
            while (n != null && !(n instanceof ScrollPane))
            {
                n = n.getParent();
            }
            return (ScrollPane) n;
        });
        if (enclosingScroll == null)
        {
            fail("Could not find enclosing scroll pane");
        }

        Bounds targetBeforeScroll = TestUtil.fx(() -> target.localToScreen(target.getBoundsInLocal()));
        Bounds viewBounds = TestUtil.fx(() -> enclosingScroll.localToScreen(enclosingScroll.getBoundsInLocal()));
        // Move, ready for potentially scrolling:
        moveTo(viewBounds.getMinX() + 2, viewBounds.getMinY() + 2);

        ScrollBar horizScroll = getScrollBar(enclosingScroll, Orientation.HORIZONTAL);
        ScrollBar vertScroll = getScrollBar(enclosingScroll, Orientation.VERTICAL);
        
        // We limit how many times we will scroll, to avoid an infinite loop in case of test failure:
        for (int i = 0;i < 100 && TestUtil.fx(() -> target.localToScreen(target.getBoundsInLocal()).getMaxX()) > viewBounds.getMaxX(); i++)
        {
            System.out.println("Scrolling RIGHT");
            clickOrScroll(from(horizScroll).lookup(".increment-button"), () -> scroll(HorizontalDirection.RIGHT));
            //scroll(HorizontalDirection.RIGHT);
        }
        for (int i = 0; i < 100 && TestUtil.fx(() -> target.localToScreen(target.getBoundsInLocal()).getMinX()) < viewBounds.getMinX(); i++)
        {
            System.out.println("Scrolling LEFT");
            clickOrScroll(from(horizScroll).lookup(".decrement-button"), () -> scroll(HorizontalDirection.LEFT));
            //scroll(HorizontalDirection.LEFT);
        }
        for (int i = 0; i < 100 && TestUtil.fx(() -> target.localToScreen(target.getBoundsInLocal()).getMaxY()) > viewBounds.getMaxY(); i++)
        {
            System.out.println("Scrolling DOWN");
            //scroll(VerticalDirection.DOWN);
            clickOrScroll(from(vertScroll).lookup(".increment-button"), () -> scroll(SystemUtils.IS_OS_MAC_OSX ? VerticalDirection.UP : VerticalDirection.DOWN));
        }
        for (int i = 0; i < 100 && TestUtil.fx(() -> target.localToScreen(target.getBoundsInLocal()).getMinY()) < viewBounds.getMinY(); i++)
        {
            System.out.println("Scrolling UP");
            //scroll(VerticalDirection.UP);
            clickOrScroll(from(vertScroll).lookup(".decrement-button"), () -> scroll(SystemUtils.IS_OS_MAC_OSX ? VerticalDirection.DOWN : VerticalDirection.UP));
        }
        Bounds targetScreenBounds = TestUtil.fx(() -> target.localToScreen(target.getBoundsInLocal()));
        assertTrue("View bounds: " + viewBounds + " target: " + targetScreenBounds + " target before scroll: " + targetBeforeScroll, viewBounds.contains(targetScreenBounds));
    }
    
    @OnThread(Tag.Any)
    default void keyboardMoveTo(VirtualGrid virtualGrid, CellPosition target)
    {
        Log.debug("Moving to position " + target);
        
        int pageHeight = TestUtil.fx(() -> virtualGrid.calcPageHeight());
        
        push(KeyCode.CONTROL, KeyCode.HOME);
        // First go to correct row:
        for (int i = 0; i < target.rowIndex / pageHeight; i++)
        {
            push(KeyCode.PAGE_DOWN);
        }
        for (int i = 0; i < target.rowIndex % pageHeight; i++)
        {
            push(KeyCode.DOWN);
        }
        
        int maxAttempts = target.columnIndex + 1;
        int attempts = 0;
        
        while (TestUtil.fx(() -> virtualGrid._test_getSelection().map(s -> s.positionToEnsureInView().columnIndex).orElse(Integer.MAX_VALUE)) < target.columnIndex && attempts++ < maxAttempts)
        
        {
            push(KeyCode.RIGHT);
        }
        // Wait for smooth scroll to finish:
        TestUtil.sleep(300);

        Optional<CellSelection> selection = TestUtil.fx(() -> virtualGrid._test_getSelection());
        assertTrue("Selected is " + selection.toString() + " aiming for " + target + " focus owner is " + getFocusOwner(), TestUtil.fx(() -> selection.map(s -> s.isExactly(target) || s.getActivateTarget().equals(target)).orElse(false)));
    }

    @OnThread(Tag.Any)
    default CellPosition keyboardMoveTo(VirtualGrid virtualGrid, TableManager tableManager, TableId tableId, @TableDataRowIndex int row, @TableDataColIndex int col) throws UserException
    {
        Random r = new Random(row * 100 + col);
        
        // Chance of using menu is higher for rows further down.
        // Chance at top: 0.05, chance at bottom: ~0.95
        double menuChance = 0.95 - (0.9 / Math.log(row + 11));
        
        boolean usingMenu = r.nextDouble() < menuChance;
        Table table = tableManager.getSingleTableOrThrow(tableId);
        TableDisplay tableDisplay = (TableDisplay) TestUtil.<@Nullable TableDisplayBase>fx(() -> table.getDisplay());
        assertNotNull(tableDisplay);
        if (tableDisplay == null)
            throw new RuntimeException("Impossible");
        keyboardMoveTo(virtualGrid, TestUtil.fx(() -> tableDisplay._test_getDataPosition(usingMenu ? DataItemPosition.row(0) : row, col)));
        if (usingMenu)
        {
            clickOn("#id-menu-view").clickOn(".id-menu-view-goto-row");
            TestUtil.sleep(200);
            write(Integer.toString(row));
            push(KeyCode.ENTER);
        }
        // Wait for complete refresh:
        TestUtil.sleep(1000);
        return TestUtil.fx(() -> tableDisplay._test_getDataPosition(row, col));
    }

    default CellPosition keyboardMoveTo(VirtualGrid virtualGrid, TableManager tableManager, TableId tableId, @TableDataRowIndex int row) throws UserException
    {
        return keyboardMoveTo(virtualGrid, tableManager, tableId, row, DataItemPosition.col(0));
    }
    
    // Ideally, will be private in later Java:
    @OnThread(Tag.Any)
    default public void clickOrScroll(NodeQuery nodeQuery, Runnable scroll)
    {
        Node scrollButton = nodeQuery.tryQuery().orElse(null);
        if (scrollButton != null)
            clickOn(scrollButton, MouseButton.PRIMARY);
        else
            scroll.run();
    }
    
    default public ScrollBar getScrollBar(ScrollPane scrollPane, Orientation orientation)
    {
        return TestUtil.fx(() -> Utility.filterClass(scrollPane.getChildrenUnmodifiable().stream(), ScrollBar.class)
            .filter(sb -> sb.getOrientation().equals(orientation))
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Can't find " + orientation + " scroll bar.")));
    }
}
