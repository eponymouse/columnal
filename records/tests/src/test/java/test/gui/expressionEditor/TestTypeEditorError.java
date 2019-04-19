package test.gui.expressionEditor;

import annotation.units.CanonicalLocation;
import com.google.common.collect.ImmutableList;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Region;
import javafx.scene.shape.Path;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.hamcrest.MatcherAssert;
import org.hamcrest.core.SubstringMatcher;
import org.junit.Test;
import org.junit.runner.RunWith;
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
import records.gui.grid.RectangleBounds;
import records.gui.lexeditor.EditorDisplay;
import records.gui.lexeditor.EditorLocationAndErrorRecorder.CanonicalSpan;
import records.gui.lexeditor.EditorLocationAndErrorRecorder.ErrorDetails;
import test.DummyManager;
import test.TestUtil;
import test.gui.trait.ClickTableLocationTrait;
import test.gui.trait.ListUtilTrait;
import test.gui.trait.PopupTrait;
import test.gui.trait.ScrollToTrait;
import test.gui.util.FXApplicationTest;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.SimulationFunction;
import utility.Utility;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

@RunWith(JUnitQuickcheck.class)
@OnThread(Tag.Simulation)
public class TestTypeEditorError extends FXApplicationTest implements ScrollToTrait, ListUtilTrait, ClickTableLocationTrait, PopupTrait
{
    @Test
    public void testFine1()
    {
        // Check basic:
        testError("Text");
    }
    @Test
    public void testFine2()
    {
        // Check basic:
        testError(TypeManager.MAYBE_NAME + "(Text)");
    }
    @Test
    public void testFine3()
    {
        // Check basic:
        testError("["+ TypeManager.MAYBE_NAME +  "(Number{m})]");
    }

    @Test
    public void testExtraOp()
    {
        // Check basic:
        testError("Text,", e(5, 5, "missing"));
    }

    @Test
    public void testInvalidChar()
    {
        // Check basic:
        testError("Text#", e(4,4, "missing", "operator"), e(4, 5, "not allowed"));
    }

    @Test
    public void testInvalidBracket1()
    {
        // Check basic:
        testError("Number()",e(6,6, "missing"), e(7, 7, "empty"));
    }
    
    @Test
    public void testInvalidEmptyBracket()
    {
        // Check basic:
        testError("()", e(1, 1, "empty"));
    }
    
    @Test
    public void testUnclosedRound()
    {
        // Check basic:
        testError("(", e(1, 1, "missing"));
    }

    @Test
    public void testUnclosedCurly()
    {
        // Check basic:
        testError("Number{", e(6, 7, "missing"));
    }

    @Test
    public void testEmptyUnit()
    {
        // Check basic:
        testError("Number{}", e(7, 7, "missing"));
    }

    @Test
    public void testOnlyUnit()
    {
        // Check basic:
        testError("{m}", e(0, 3, "unit"));
    }

    @Test
    public void testUnknownUnit()
    {
        // Check basic:
        testError("Number{zzz}", e(0, 11, "unknown"));
    }

    @Test
    public void testUnknownUnit2()
    {
        // Check basic:
        testError("(Number{zzz}, Text)", e(1, 12, "unknown"));
    }

    @Test
    public void testUnitInvalidOp()
    {
        // Check basic:
        testError("Number{m**m}", e(9, 9, "missing"));
    }

    @Test
    public void testUnitInvalidOp2()
    {
        // Check basic:
        testError("Number{m^m}", e(7, 10, "raise"));
    }

    @Test
    public void testUnknown0()
    {
        testError("NumbeX", e(0, 6, "unknown"));
    }
    
    @Test
    public void testUnknown1()
    {
        testError("NumberX", e(0, 7, "unknown"));
    }
    
    @Test
    public void testUnknown2()
    {
        testError("Number X", e(0, 8, "unknown"));
    }

    @Test
    public void testTuple()
    {
        testError("(Text, Number)");
    }
    
    @Test
    public void testBadTuple1()
    {
        testError("Text,Number", e(0, 11, "invalid"));
    }

    @Test
    public void testBadTuple2()
    {
        testError("[Text,Number]", e(1, 12, "invalid"));
    }

    // Checks that errors don't show up while still in the span,
    // but do show up when you move out or when you click ok.
    @SuppressWarnings("units")
    private void testError(String expression, Error... errors)
    {        
        try
        {
            MainWindowActions mainWindowActions = TestUtil.openDataAsTable(windowToUse, TestUtil.managerWithTestTypes().getFirst()).get();

            Region gridNode = TestUtil.fx(() -> mainWindowActions._test_getVirtualGrid().getNode());
            CellPosition targetPos = new CellPosition(CellPosition.row(1), CellPosition.col(1));
            for (int i = 0; i < 2; i++)
                clickOnItemInBounds(from(gridNode), mainWindowActions._test_getVirtualGrid(), new RectangleBounds(targetPos, targetPos), MouseButton.PRIMARY);
            clickOn(".id-new-data");
            write("Table1");
            push(KeyCode.TAB);
            write("Column1");
            // Focus type editor:
            push(KeyCode.TAB);
            for (char c : expression.toCharArray())
            {
                write(c);
                // Delete auto-matched brackets:
                if ("({[".contains("" + c))
                    push(KeyCode.DELETE);
            }
            sleep(200);
            
            if (errors.length == 0)
            {
                assertErrorShowing(false, false);
                // Clicking OK should be fine:
                moveAndDismissPopupsAtPos(point(".ok-button"));
                clickOn(".ok-button");
                sleep(300);
                assertFalse(lookup(".type-editor").tryQuery().isPresent());
            }
            else
            {
                EditorDisplay editorDisplay = lookup(".editor-display").<EditorDisplay>query();
                List<ErrorDetails> actualErrors = new ArrayList<>(TestUtil.fx(() -> editorDisplay._test_getErrors()));
                List<Error> expectedErrors = new ArrayList<>(Arrays.asList(errors));
                assertEquals(Utility.listToString(actualErrors), expectedErrors.size(), actualErrors.size());
                Collections.sort(actualErrors, Comparator.comparing(e -> e.location));
                Collections.sort(expectedErrors, Comparator.comparing(e -> e.location));
                for (int i = 0; i < expectedErrors.size(); i++)
                {
                    assertEquals("Error: " + actualErrors.get(i).error.toPlain(), expectedErrors.get(i).location, actualErrors.get(i).location);
                    MatcherAssert.assertThat(actualErrors.get(i).error.toPlain().toLowerCase(), new MultiSubstringMatcher(expectedErrors.get(i).expectedMessageParts));
                }

                boolean hasSpanNotContainingEnd = Arrays.stream(errors).anyMatch(s -> !s.location.touches(expression.length()));
                assertErrorShowing(hasSpanNotContainingEnd, false);


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
                    assertTrue("Type editor still showing", lookup(".type-editor").tryQuery().isPresent());
                    assertErrorShowing(true, null);
                    
                    // Should still show even if you click again:
                    clickOn(".ok-button");
                    sleep(300);
                    assertTrue("Type editor still showing", lookup(".type-editor").tryQuery().isPresent());
                }
                clickOn(".cancel-button");
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
        Collection<Path> errorUnderline = lookup(".type-editor .error-underline").<Path>queryAll();
        assertEquals("Underline showing", underlineShowing, errorUnderline.size() > 0);
        if (popupShowing != null)
            assertEquals("Popup showing", popupShowing, isShowingErrorPopup());
    }

    private boolean isShowingErrorPopup()
    {
        return lookup(".expression-info-popup").tryQuery().isPresent();
    }
    
    private static class Error
    {
        private final CanonicalSpan location;
        private final ImmutableList<String> expectedMessageParts;

        public Error(@CanonicalLocation int start, @CanonicalLocation int end, ImmutableList<String> expectedMessageParts)
        {
            this.location = new CanonicalSpan(start, end);
            this.expectedMessageParts = expectedMessageParts;
        }
    }
    
    @SuppressWarnings("units")
    private static final Error e(int start, int end, String... errorMessagePart)
    {
        return new Error(start, end, ImmutableList.copyOf(errorMessagePart));
    }
    
    // Also ignores case
    class MultiSubstringMatcher extends SubstringMatcher
    {
        private final ImmutableList<String> substrings;

        public MultiSubstringMatcher(ImmutableList<String> substrings)
        {
            super(substrings.stream().collect(Collectors.joining("\u2026")));
            this.substrings = substrings;
        }

        @Override
        protected boolean evalSubstringOf(String string)
        {
            int curIndex = 0;
            for (String sub : substrings)
            {
                curIndex = string.toLowerCase().indexOf(sub.toLowerCase(), curIndex);
                if (curIndex == -1)
                {
                    return false;
                }
            }
            return true;
        }

        @Override
        protected String relationship()
        {
            return "contains string(s)";
        }
    }
}
