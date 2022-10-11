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

package test.gui.stf;

import com.google.common.collect.ImmutableList;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Text;
import test.gui.TFXUtil;
import xyz.columnal.log.Log;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import xyz.columnal.gui.dtf.DisplayDocument;
import xyz.columnal.gui.dtf.DocumentTextField;
import test.gen.GenRandom;
import test.gen.GenString;
import test.gui.util.FXApplicationTest;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.gui.FXUtility;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(JUnitQuickcheck.class)
public class TestDocumentTextField extends FXApplicationTest
{
    @SuppressWarnings("nullness")
    private DocumentTextField field;
    
    @Before
    public void makeField()
    {
        field = TFXUtil.fx(() -> new DocumentTextField(null));
    }
    
    @Property(trials=5, shrink = false)
    public void testBasic(@From(GenString.class) String s, @From(GenRandom.class) Random r)
    {
        s = removeNonPrintableAndRtoL(s);
        
        if (s.length() > 100)
            s = s.substring(0, s.length() % 100);

        initialiseField(s);

        String unfocused = getText();
        // While unfocused, could be truncated for efficiency:
        assertThat(s, Matchers.startsWith(unfocused));
        
        clickOn(field);
        assertTrue(TFXUtil.fx(() -> field.isFocused()));
        String focused = getText();
        // Once focused, should have complete text:
        assertEquals(s, focused);
        
        push(KeyCode.HOME);
        assertEquals((Integer)0, TFXUtil.<Integer>fx(() -> field.getCaretPosition()));
        int moveRightTo = s.isEmpty() ? 0 : r.nextInt(Math.min(s.length(), 25));
        for (int i = 0; i < moveRightTo; i++)
        {
            push(KeyCode.RIGHT);
            assertEquals(Integer.valueOf(i + 1), TFXUtil.<Integer>fx(() -> field.getCaretPosition()));
        }
        
        write("a z");
        assertEquals(s.substring(0, moveRightTo) + "a z" + s.substring(moveRightTo), getText());
        for (int i = 0; i < 2; i++)
        {
            push(KeyCode.BACK_SPACE);
        }
        assertEquals(s.substring(0, moveRightTo) + "a" + s.substring(moveRightTo), getText());
        push(KeyCode.LEFT);
        push(KeyCode.DELETE);
        assertEquals(s, getText());

        List<Integer> editPositions = calcEditPositions(s);
        for (int i = 0; i < 5; i++)
        {
            int pos = s.isEmpty() ? 0 : editPositions.get(r.nextInt(editPositions.size()));
            Optional<Point2D> clickTarget = TFXUtil.fx(() -> 
                field._test_getClickPosFor(pos).map(field::localToScreen)
            );
            clickTarget.ifPresent(this::clickOn);
            if (clickTarget.isPresent())
                assertEquals(pos, getFieldCaretPos());
            // Prevent it counting as multi click:
            TFXUtil.sleep(1000);
        }

        for (int i = 0; i < 5; i++)
        {
            editPositions = calcEditPositions(s);
            int aPos = s.isEmpty() ? 0 : editPositions.get(r.nextInt(editPositions.size()));
            int bPos = s.isEmpty() ? 0 : editPositions.get(r.nextInt(editPositions.size()));
            Optional<Point2D> aClick = TFXUtil.fx(() ->
                field._test_getClickPosFor(aPos).map(field::localToScreen)
            );
            Optional<Point2D> bClick = TFXUtil.fx(() ->
                field._test_getClickPosFor(bPos).map(field::localToScreen)
            );
            if (aClick.isPresent() && bClick.isPresent())
            {
                Log.debug("Dragging from " + aClick.get() + " to " + bClick.get());
                drag(aClick.get());
                moveBy(0, 20);
                dropTo(bClick.get());
                assertEquals("Selecting " + aPos + " to " + bPos, aPos, getFieldAnchorPos());
                assertEquals("Selecting " + aPos + " to " + bPos, bPos, getFieldCaretPos());
            }
            // Prevent it counting as multi click:
            TFXUtil.sleep(1000);
            push(KeyCode.HOME);
            for (int j = 0; j < aPos; j++)
            {
                push(KeyCode.RIGHT);
            }
            press(KeyCode.SHIFT);
            for (int j = 0; j < Math.abs(bPos - aPos); j++)
            {
                push(bPos < aPos ? KeyCode.LEFT : KeyCode.RIGHT);
            }
            release(KeyCode.SHIFT);
            assertEquals("Selecting " + aPos + " to " + bPos, aPos, getFieldAnchorPos());
            assertEquals("Selecting " + aPos + " to " + bPos, bPos, getFieldCaretPos());

            if (aPos == 0 && bPos == 0)
                continue;
            int start = Math.min(aPos, bPos);
            int end = Math.max(aPos, bPos);
            String beforeDelete = s;
            String msg = "Deleting " + start + " to " + end + " from {{" + beforeDelete + "}}";
            switch (r.nextInt(4))
            {
                case 0:
                    if (start != end || start > 0)
                    {
                        push(KeyCode.BACK_SPACE);
                        if (start == end)
                            start -= 1;
                        s = s.substring(0, start) + s.substring(end);

                        assertEquals(msg, s, getText());
                        assertEquals(msg, start, getFieldCaretPos());
                        assertEquals(msg, start, getFieldAnchorPos());
                    }
                    break;
                case 1:
                    if (start != end || end < s.length())
                    {
                        push(KeyCode.DELETE);
                        if (start == end)
                            end += 1;
                        s = s.substring(0, start) + s.substring(end);
                        assertEquals(msg, s, getText());
                        assertEquals(msg, start, getFieldCaretPos());
                        assertEquals(msg, start, getFieldAnchorPos());
                    }
                    break;
            }
        }
    }

    private void initialiseField(String initialText)
    {
        TFXUtil.fx_(() ->{
            windowToUse.setScene(new Scene(field));
            windowToUse.show();
            windowToUse.sizeToScene();
            field.setDocument(new DisplayDocument(initialText) {
                @Override
                public void setAndSave(String content)
                {
                }
            });
        });
        assertThat(TFXUtil.fx(() -> field.getWidth()), Matchers.greaterThanOrEqualTo(100.0));
        assertThat(TFXUtil.fx(() -> field.getHeight()), Matchers.greaterThanOrEqualTo(10.0));
    }

    @Test
    public void testDelete1()
    {
        initialiseField("");
        clickOn(field);
        write("13abcd");
        push(KeyCode.HOME);
        push(KeyCode.RIGHT);
        push(KeyCode.RIGHT);
        push(KeyCode.SHIFT, KeyCode.RIGHT);
        push(KeyCode.SHIFT, KeyCode.RIGHT);
        push(KeyCode.SHIFT, KeyCode.RIGHT);
        push(KeyCode.SHIFT, KeyCode.RIGHT);
        push(KeyCode.BACK_SPACE);
        assertEquals("13", getText());
    }
    
    @Property(trials=5)
    public void testMove(@From(GenString.class) String s, @From(GenRandom.class) Random r)
    {
        s = removeNonPrintableAndRtoL(s);

        if (s.length() > 100)
            s = s.substring(0, s.length() % 100);

        initialiseField(s);
        clickOn(field);
        push(KeyCode.HOME);
        int caret = 0;
        int anchor = 0;
        for (int i = 0; i < 20; i++)
        {
            boolean withShift = r.nextBoolean();
            final String message;
            final int expectedCaret;
            if (withShift)
                press(KeyCode.SHIFT);
            switch (r.nextInt(4))
            {
                case 0:
                    message = "Pushing left from " + caret + " / " + s.length();
                    push(KeyCode.LEFT);
                    expectedCaret = Math.max(0, caret - 1);
                    break;
                case 1:
                    message = "Pushing right from " + caret + " / " + s.length();
                    push(KeyCode.RIGHT);
                    expectedCaret = Math.min(caret + 1, s.length());
                    break;
                case 2:
                    message = "Pushing home from " + caret + " / " + s.length();
                    push(KeyCode.HOME);
                    expectedCaret = 0;
                    break;
                default:
                    message = "Pushing end from " + caret + " / " + s.length();
                    push(KeyCode.END);
                    expectedCaret = s.length();
                    break;
            }
            if (withShift)
                release(KeyCode.SHIFT);
            assertEquals(message, expectedCaret, getFieldCaretPos());
            assertEquals(message, withShift ? anchor : expectedCaret, getFieldAnchorPos());
            caret = expectedCaret;
            if (!withShift)
                anchor = caret;
        }

    }

    @OnThread(Tag.Any)
    public int getFieldAnchorPos()
    {
        return (int) TFXUtil.<Integer>fx(() -> (Integer)field.getAnchorPosition());
    }

    @OnThread(Tag.Any)
    public int getFieldCaretPos()
    {
        return (int) TFXUtil.<Integer>fx(() -> (Integer)field.getCaretPosition());
    }

    private List<Integer> calcEditPositions(String s)
    {
        ArrayList<Integer> r = new ArrayList<>(s.length() + 1);
        for (int cur = 0; cur < s.length(); cur = s.offsetByCodePoints(cur, 1))
        {
            r.add(cur);
        }
        r.add(s.length());
        return r;
    }

    private String removeNonPrintableAndRtoL(String s)
    {
        // Adapted from https://stackoverflow.com/a/18603020/412908
        int[] filteredCodePoints = s.codePoints().filter(codePoint -> {
                    return !ImmutableList.<Integer>of((int)Character.CONTROL, (int)Character.FORMAT, (int)Character.PRIVATE_USE, (int)Character.SURROGATE, (int)Character.UNASSIGNED).contains(Character.getType(codePoint))
                        && Character.getDirectionality(codePoint) != Character.DIRECTIONALITY_RIGHT_TO_LEFT;
                }).toArray();
        return new String(filteredCodePoints, 0, filteredCodePoints.length);
    }
    
    @Property(trials=3)
    public void testHorizScroll(@From(GenString.class) String s)
    {
        s = removeNonPrintableAndRtoL(s);
        Assume.assumeFalse(s.isEmpty());
        while (s.length() < 50)
            s += s;

        String sFinal = s;
        TFXUtil.fx_(() -> {
            field.setPrefWidth(100);
            field.setMaxWidth(100);
            windowToUse.setScene(new Scene(new StackPane(field)));
            windowToUse.show();
            windowToUse.sizeToScene();
            field.setDocument(new DisplayDocument(sFinal) {
                @Override
                public void setAndSave(String content)
                {
                }
            });
        });
        
        MatcherAssert.assertThat(TFXUtil.fx(() -> field.getWidth()), Matchers.lessThanOrEqualTo(110.0));

        clickOn(field);
        push(KeyCode.HOME);
        Bounds fieldBounds = TFXUtil.fx(() -> field.localToScreen(field.getBoundsInLocal()));
        Node caret = waitForOne(".document-caret");
        List<Rectangle2D> allBounds = new ArrayList<>();
        for (int i = 0; i < 50; i++)
        {
            push(KeyCode.RIGHT);
            sleep(500);

            Bounds caretBounds = TFXUtil.fx(() -> caret.localToScreen(caret.getBoundsInLocal()));
            allBounds.add(FXUtility.boundsToRect(caretBounds));
            assertTrue("Field: " + fieldBounds + " caret: " + caretBounds, fieldBounds.intersects(caretBounds));
        }
        for (int i = 0; i < 40; i++)
        {
            push(KeyCode.LEFT);
            sleep(500);
            Bounds caretBounds = TFXUtil.fx(() -> caret.localToScreen(caret.getBoundsInLocal()));
            allBounds.add(FXUtility.boundsToRect(caretBounds));
            assertTrue(fieldBounds.intersects(caretBounds));
        }
        
        assertThat(allBounds.stream().distinct().count(), Matchers.greaterThan(10L));
    }

    // TODO test truncating very long strings when unfocused
    // TODO test unfocused document switching
    

    private String getText()
    {
        return TFXUtil.fx(() -> lookup((Predicate<Node>) t -> t instanceof Text).queryAll().stream().sorted(Comparator.comparing(n -> n.getLayoutX())).map(n -> ((Text)n).getText()).collect(Collectors.joining()));
    }
}
