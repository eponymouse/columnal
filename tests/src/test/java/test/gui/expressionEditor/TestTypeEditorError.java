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
import test.functions.TFunctionUtil;
import xyz.columnal.data.CellPosition;
import xyz.columnal.data.datatype.TypeManager;
import xyz.columnal.gui.MainWindow.MainWindowActions;
import xyz.columnal.gui.grid.RectangleBounds;
import xyz.columnal.gui.lexeditor.EditorDisplay;
import xyz.columnal.transformations.expression.CanonicalSpan;
import xyz.columnal.gui.lexeditor.EditorLocationAndErrorRecorder.ErrorDetails;
import test.TestUtil;
import test.gui.trait.ClickTableLocationTrait;
import test.gui.trait.EnterTypeTrait;
import test.gui.trait.ListUtilTrait;
import test.gui.trait.PopupTrait;
import test.gui.trait.ScrollToTrait;
import test.gui.util.FXApplicationTest;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.Utility;

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
public class TestTypeEditorError extends FXApplicationTest implements ScrollToTrait, ListUtilTrait, ClickTableLocationTrait, PopupTrait, EnterTypeTrait
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
        testError("Number{zzz}", e(7, 10, "unknown"));
    }

    @Test
    public void testUnknownUnit2()
    {
        // Check basic:
        testError("(a:Number{zzz}, b:Text)", e(10, 13, "unknown"));
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
    public void testRecord()
    {
        testError("(a: Text, b: Number)");
    }

    @Test
    public void testRecord2()
    {
        // It should be fine to use type names as labels
        testError("(Number: Text, Text: Number, Boolean: [Date], DateYM : Boolean)");
    }
    
    @Test
    public void testBadTuple1()
    {
        testError("Text,Number", e(0, 11, "bracket"));
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
            MainWindowActions mainWindowActions = TestUtil.openDataAsTable(windowToUse, TFunctionUtil.managerWithTestTypes().getFirst()).get();

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
            enterAndDeleteSmartBrackets(expression);
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
                List<ErrorDetails> actualErrors = new ArrayList<>(TestUtil.fx(() -> editorDisplay._test_getErrors().stream().filter(e -> e.error.getLength() > 0).collect(Collectors.toList())));
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
        // Important to check the .error part too, as it may be showing information or a prompt and that's fine:
        return lookup(".expression-info-popup.error").tryQuery().isPresent();
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
            super("contains string(s)", true, substrings.stream().collect(Collectors.joining("\u2026")));
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
    }
}
