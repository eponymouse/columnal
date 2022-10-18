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
import annotation.units.DisplayLocation;
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
import org.junit.runner.RunWith;
import test.gui.TAppUtil;
import test.gui.TFXUtil;
import xyz.columnal.data.CellPosition;
import xyz.columnal.id.ColumnId;
import xyz.columnal.data.EditableColumn;
import xyz.columnal.data.EditableRecordSet;
import xyz.columnal.data.MemoryNumericColumn;
import xyz.columnal.data.MemoryStringColumn;
import xyz.columnal.data.RecordSet;
import xyz.columnal.data.datatype.NumberInfo;
import xyz.columnal.data.datatype.TypeManager;
import xyz.columnal.data.unit.UnitManager;
import xyz.columnal.gui.MainWindow.MainWindowActions;
import xyz.columnal.gui.grid.RectangleBounds;
import xyz.columnal.gui.lexeditor.EditorDisplay;
import xyz.columnal.transformations.expression.CanonicalSpan;
import xyz.columnal.gui.lexeditor.EditorLocationAndErrorRecorder.DisplaySpan;
import xyz.columnal.gui.lexeditor.EditorLocationAndErrorRecorder.ErrorDetails;
import test.gui.trait.ClickTableLocationTrait;
import test.gui.trait.ListUtilTrait;
import test.gui.trait.PopupTrait;
import test.gui.trait.ScrollToTrait;
import test.gui.util.FXApplicationTest;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.function.simulation.SimulationFunction;
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
class BaseTestExpressionEditorError extends FXApplicationTest implements ScrollToTrait, ListUtilTrait, ClickTableLocationTrait, PopupTrait
{
    // Checks that errors don't show up while still in the span,
    // but do show up when you move out or when you click ok.
    // If expression has \u0000 as first character, bracket auto-matching is relied upon.
    // If no such character is there, auto-inserted brackets are deleted
    // as they are entered, then typed manually later on.
    @SuppressWarnings({"units", "identifier"})
    void testError(String expression, Error... errors)
    {        
        try
        {
            UnitManager u = new UnitManager();
            TypeManager typeManager = new TypeManager(u);
            List<SimulationFunction<RecordSet, EditableColumn>> columns = new ArrayList<>();
            for (int i = 1; i <= 3; i++)
            {
                int iFinal = i;
                columns.add(rs -> new MemoryStringColumn(rs, new ColumnId("S" + iFinal), ImmutableList.of(Either.right("Hi " + iFinal)), ""));
                columns.add(rs -> new MemoryNumericColumn(rs, new ColumnId("ACC" + iFinal), new NumberInfo(u.loadUse("m/s^2")), ImmutableList.of(Either.right(iFinal)), 0));
            }
            MainWindowActions mainWindowActions = TAppUtil.openDataAsTable(windowToUse, typeManager, new EditableRecordSet(columns, () -> 1));

            Region gridNode = TFXUtil.fx(() -> mainWindowActions._test_getVirtualGrid().getNode());
            CellPosition targetPos = new CellPosition(CellPosition.row(6), CellPosition.col(3));
            for (int i = 0; i < 2; i++)
                clickOnItemInBounds(fromNode(gridNode), mainWindowActions._test_getVirtualGrid(), new RectangleBounds(targetPos, targetPos), MouseButton.PRIMARY);
            // Not sure why this doesn't work:
            //clickOnItemInBounds(lookup(".create-table-grid-button"), mainWindowActions._test_getVirtualGrid(), new RectangleBounds(targetPos, targetPos), MouseButton.PRIMARY);
            clickOn(".id-new-transform");
            clickOn(".id-transform-calculate");
            write("Table1");
            push(KeyCode.ENTER);
            TFXUtil.sleep(200);
            write("DestCol");
            // Focus expression editor:
            push(KeyCode.TAB);
            for (char c : expression.toCharArray())
            {
                if (c != 0)
                    write(c);
                // Delete auto-matched brackets:
                if (!expression.startsWith("\u0000") && "({[\"".contains("" + c))
                    push(KeyCode.DELETE);
            }
            sleep(2000);
            
            if (errors.length == 0)
            {
                assertErrorShowing(false, false);
                // Clicking OK should be fine:
                moveAndDismissPopupsAtPos(point(".ok-button"));
                clickOn(".ok-button");
                sleep(300);
                assertNotShowing("Expression editor", ".expression-editor");
            }
            else
            {
                EditorDisplay editorDisplay = waitForOne(".editor-display");
                List<ErrorDetails> actualErrors = new ArrayList<>(TFXUtil.fx(() -> editorDisplay._test_getErrors().stream().filter(e -> e.error.getLength() > 0).collect(Collectors.toList())));
                List<Error> expectedErrors = new ArrayList<>(Arrays.asList(errors));
                assertEquals(Utility.listToString(actualErrors), expectedErrors.size(), actualErrors.size());
                Collections.sort(actualErrors, Comparator.comparing(e -> e.location));
                Collections.sort(expectedErrors, Comparator.comparing(e -> e.location));
                for (int i = 0; i < expectedErrors.size(); i++)
                {
                    assertEquals("Error: " + actualErrors.get(i).error.toPlain(), expectedErrors.get(i).location, actualErrors.get(i).location);
                    MatcherAssert.assertThat(actualErrors.get(i).error.toPlain().toLowerCase(), new MultiSubstringMatcher(expectedErrors.get(i).expectedMessageParts));
                }
                
                // Not necessarily caret pos of the end, if they
                // entered auto-matched brackets.
                @CanonicalLocation int endingCaretPos = TFXUtil.fx(() -> editorDisplay.getCaretPosition());

                boolean hasSpanNotContainingEndingPos = Arrays.stream(errors).anyMatch(s -> !s.location.touches(endingCaretPos));
                assertErrorShowing(hasSpanNotContainingEndingPos, false);


                // Can either provoke error by moving caret into a span or by
                // clicking ok first time
                if (hasSpanNotContainingEndingPos && expression.hashCode() % 2 == 0)
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
                    // Make sure it's still showing:
                    assertShowing("Expression editor still showing", ".expression-editor");
                    assertErrorShowing(true, null);
                    assertShowing("OK double prompt", ".ok-double-prompt");
                }

                actualErrors = new ArrayList<>(TFXUtil.fx(() -> editorDisplay._test_getErrors().stream().filter(e -> e.error.getLength() > 0).collect(Collectors.toList())));
                Collections.sort(actualErrors, Comparator.comparing(e -> e.location));

                for (int i = 0; i < expectedErrors.size(); i++)
                {
                    assertEquals("Error: " + actualErrors.get(i).error.toPlain(), expectedErrors.get(i).displayLocation, actualErrors.get(i).displayLocation);
                }
                
                TFXUtil.doubleOk(this);
                assertNotShowing("Expression editor still showing", ".expression-editor");
                System.out.println("Closed expression editor, opening again");
                // Show again and check error is showing from the outset:
                clickOn("DestCol");
                sleep(2000);
                assertShowing("Expression editor showing again", ".expression-editor");
                assertErrorShowing(true, false);
                // Check it shows if you move into it:
                push(KeyCode.TAB);
                push(KeyCode.HOME);
                boolean seenPopup = false;
                for (int i = 0; i < expression.length(); i++)
                {
                    push(KeyCode.RIGHT);
                    if (isShowingErrorPopup())
                    {
                        seenPopup = true;
                        break;
                    }
                }
                assertTrue("Error popup showed somewhere", seenPopup);
                
                TFXUtil.doubleOk(this);
                assertNotShowing("Expression editor still showing", ".expression-editor");
            }
            
        }
        catch (Exception e)
        {
            // Test fails regardless, so no harm turning checked exception
            // into unchecked for simpler signatures:
            throw new RuntimeException(e);
        }
    }

    private void assertErrorShowing(boolean underlineShowing, @Nullable Boolean errorPopupShowing)
    {
        Scene dialogScene = TFXUtil.fx(() -> getRealFocusedWindow().getScene());
        Collection<Path> errorUnderline = TFXUtil.fx(() -> lookup(".expression-editor .error-underline").<Path>queryAll());
        assertEquals("Underline showing", underlineShowing, errorUnderline.size() > 0);
        if (errorPopupShowing != null)
            assertEquals("Popup showing", errorPopupShowing, isShowingErrorPopup());
    }

    private boolean isShowingErrorPopup()
    {
        // Important to check the .error part too, as it may be showing information or a prompt and that's fine:
        return TFXUtil.fx(() -> lookup(".expression-info-popup.error").tryQuery().isPresent());
    }
    
    static class Error
    {
        private final CanonicalSpan location;
        private final DisplaySpan displayLocation;
        private final ImmutableList<String> expectedMessageParts;

        public Error(@CanonicalLocation int start, @CanonicalLocation int end, ImmutableList<String> expectedMessageParts)
        {
            this.location = new CanonicalSpan(start, end);
            @SuppressWarnings("units")
            DisplaySpan displayLocation = TFXUtil.fx(() -> new DisplaySpan(start, start == end ? end + 1 : end));
            this.displayLocation = displayLocation;
            this.expectedMessageParts = expectedMessageParts;
        }

        public Error(@CanonicalLocation int start, @CanonicalLocation int end, @DisplayLocation int displayStart, @DisplayLocation int displayEnd, ImmutableList<String> expectedMessageParts)
        {
            this.location = new CanonicalSpan(start, end);
            this.displayLocation = TFXUtil.fx(() -> new DisplaySpan(displayStart, displayEnd));
            this.expectedMessageParts = expectedMessageParts;
        }
    }
    
    @SuppressWarnings("units")
    static final Error e(int start, int end, String... errorMessagePart)
    {
        return new Error(start, end, ImmutableList.copyOf(errorMessagePart));
    }

    @SuppressWarnings("units")
    static final Error e(int start, int end, int displayStart, int displayEnd, String... errorMessagePart)
    {
        return new Error(start, end, displayStart, displayEnd, ImmutableList.copyOf(errorMessagePart));
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
