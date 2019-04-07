package test.gui.expressionEditor;

import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Region;
import javafx.scene.shape.Path;
import log.Log;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.testfx.util.WaitForAsyncUtils;
import records.data.CellPosition;
import records.data.ColumnId;
import records.data.EditableColumn;
import records.data.EditableRecordSet;
import records.data.MemoryNumericColumn;
import records.data.MemoryStringColumn;
import records.data.RecordSet;
import records.data.datatype.NumberInfo;
import records.data.datatype.TypeManager;
import records.data.unit.UnitManager;
import records.gui.MainWindow.MainWindowActions;
import records.gui.expressioneditor.TopLevelEditor;
import records.gui.expressioneditor.TopLevelEditor.TopLevelEditorFlowPane;
import records.gui.grid.RectangleBounds;
import records.gui.lexeditor.EditorLocationAndErrorRecorder.Span;
import test.TestUtil;
import test.gui.trait.ClickTableLocationTrait;
import test.gui.trait.ListUtilTrait;
import test.gui.trait.PopupTrait;
import test.gui.trait.ScrollToTrait;
import test.gui.util.FXApplicationTest;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
import utility.SimulationFunction;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

@RunWith(JUnitQuickcheck.class)
@OnThread(Tag.Simulation)
public class TestExpressionEditorError extends FXApplicationTest implements ScrollToTrait, ListUtilTrait, ClickTableLocationTrait, PopupTrait
{
    @OnThread(Tag.Any)
    private static class State
    {
        // null means can be anything
        public final @Nullable String headerText;
        public final boolean errorColourHeader;

        private State(@Nullable String headerText, boolean errorColourHeader)
        {
            this.headerText = headerText;
            this.errorColourHeader = errorColourHeader;
        }
        
        // Slight hack during testing to allow null to match anything:
        public boolean equals(@Nullable Object o)
        {
            if (o instanceof Pair)
            {
                @SuppressWarnings("unchecked")
                Pair<String, Boolean> p = (Pair<String, Boolean>)o;
                if (headerText != null && !headerText.equals(p.getFirst()))
                    return false;
                return errorColourHeader == p.getSecond();
            }
            return false;
        }
    }
    
    @Test
    public void test1()
    {
        // Check basic:
        testError("1");
    }

    @Test
    public void test1b()
    {
        // Check basic:
        testError("1+", 2, 2);
    }

    @Test
    public void test2()
    {
        testError("foo", 0, 3);
    }

    @Test
    public void test2A()
    {
        testError("foo+1", 0, 3);
    }

    @Test
    public void test2B()
    {
        testError("foo+", 0, 4);
    }

    @Test
    public void test2C()
    {
        // Error once we leave the slot:
        // (and error in the blank operand skipped)
        testError("1+/3", 0, 3);
    }

    @Test
    public void test2D()
    {
        // Error once we leave the slot:
        testError("foo*1", 0, 3);
    }

    
    /*
    @Test
    public void test3()
    {
        testError("@if true @then 3 @else 5", false,
            // if, condition
            h(), h(),
            // then, 3, endif
            h(), h(), h()
        );
    }

    @Test
    public void test3A()
    {
        testError("@if # @then # @endif", false,
                // if, condition
                h(), red(),
                // then, #
                h(), red(),
                // endif, blank (but unvisited)
                h(), e());
    }

    @Test
    public void test3B()
    {
        testError("@if 3 @then 4 @else 5", false,
                // if, condition (should be boolean)
                h(), red(""),
                // then, 4
                h(), h(),
                // else, 5
                h(), h());
    }

    @Test
    public void test3C()
    {
        testError("@if 3 @then #", false,
                // if, condition (type error)
                h(), red(""),
                // then, # (but focused)
                h(), h(),
                // else, blank (but unvisited)
                h(), e());
    }
    */

    private static State h()
    {
        return new State("", false);
    }


    private static State h(String s)
    {
        return new State(s, false);
    }

    private static State red(@Nullable String header)
    {
        return new State(header, true);
    }

    private static State red()
    {
        return red(null);
    }


    private static State eRed()
    {
        return new State("error", true);
    }

    private static State e()
    {
        return new State("error", false);
    }
    
    // Checks that errors don't show up while still in the span,
    // but do show up when you move out or when you click ok.
    @SuppressWarnings("units")
    private void testError(String expression, int... errorSpanEdges)
    {
        // Must be start and end for each span:
        assertEquals(0, errorSpanEdges.length % 2);
        ArrayList<Span> errorSpans = new ArrayList<>();
        for (int i = 0; i < errorSpanEdges.length; i += 2)
        {
            errorSpans.add(new Span(errorSpanEdges[i], errorSpanEdges[i + 1]));
        }
        
        try
        {
            UnitManager u = new UnitManager();
            TypeManager typeManager = new TypeManager(u);
            List<SimulationFunction<RecordSet, EditableColumn>> columns = new ArrayList<>();
            for (int i = 1; i <= 3; i++)
            {
                int iFinal = i;
                columns.add(rs -> new MemoryStringColumn(rs, new ColumnId("S" + iFinal), Collections.emptyList(), ""));
                columns.add(rs -> new MemoryNumericColumn(rs, new ColumnId("ACC" + iFinal), new NumberInfo(u.loadUse("m/s^2")), Collections.emptyList(), 0));
            }
            MainWindowActions mainWindowActions = TestUtil.openDataAsTable(windowToUse, typeManager, new EditableRecordSet(columns, () -> 0));

            Region gridNode = TestUtil.fx(() -> mainWindowActions._test_getVirtualGrid().getNode());
            CellPosition targetPos = new CellPosition(CellPosition.row(6), CellPosition.col(3));
            for (int i = 0; i < 2; i++)
                clickOnItemInBounds(from(gridNode), mainWindowActions._test_getVirtualGrid(), new RectangleBounds(targetPos, targetPos), MouseButton.PRIMARY);
            // Not sure why this doesn't work:
            //clickOnItemInBounds(lookup(".create-table-grid-button"), mainWindowActions._test_getVirtualGrid(), new RectangleBounds(targetPos, targetPos), MouseButton.PRIMARY);
            clickOn(".id-new-transform");
            clickOn(".id-transform-calculate");
            write("Table1");
            push(KeyCode.ENTER);
            TestUtil.sleep(200);
            write("DestCol");
            // Focus expression editor:
            push(KeyCode.TAB);
            write(expression);
            boolean hasSpanNotContainingEnd = errorSpans.stream().anyMatch(s -> !s.contains(expression.length()));
            assertErrorShowing(hasSpanNotContainingEnd, false);
            
            if (errorSpans.isEmpty())
            {
                // Clicking OK should be fine:
                moveAndDismissPopupsAtPos(point(".ok-button"));
                clickOn(".ok-button");
                sleep(300);
                assertFalse(lookup(".expression-editor").tryQuery().isPresent());
            }
            else
            {
                // Can either provoke error by moving caret into a span or by
                // clicking ok first time
                if (hasSpanNotContainingEnd && expression.hashCode() % 2 == 0)
                {
                    // Move into span:
                    boolean seenPopup = false;
                    for (int i = 0; i < expression.length(); i++)
                    {
                        push(KeyCode.LEFT);
                        if (isShowingErrorPopup())
                        {
                            seenPopup = true;
                            break;
                        }
                    }
                    assertTrue("Error popup showed somewhere", seenPopup);
                }
                else
                {
                    // Click ok and check dialog remains and error shows up
                    moveAndDismissPopupsAtPos(point(".ok-button"));
                    clickOn(".ok-button");
                    sleep(300);
                    assertTrue("Expression editor still showing", lookup(".expression-editor").tryQuery().isPresent());
                    assertErrorShowing(true, null);
                    assertTrue(lookup(".ok-double-prompt").tryQuery().isPresent());
                }
            }
            
        }
        catch (Exception e)
        {
            // Test fails regardless, so no harm turning checked exception
            // into unchecked for simpler signatures:
            throw new RuntimeException(e);
        }
    }

    private void assertErrorShowing(boolean underlineShowing, @Nullable Boolean popupShowing)
    {
        Scene dialogScene = TestUtil.fx(() -> getRealFocusedWindow().getScene());
        Path errorUnderline = lookup(".error-underline").match(n -> TestUtil.<@Nullable Scene>fx(() -> n.getScene()) == dialogScene).query();
        assertEquals("Underline showing", underlineShowing, TestUtil.fx(() -> errorUnderline.getElements().size()) > 0);
        if (popupShowing != null)
            assertEquals("Popup showing", popupShowing, isShowingErrorPopup());
    }

    private boolean isShowingErrorPopup()
    {
        return lookup(".expression-info-popup").tryQuery().isPresent();
    }
}
