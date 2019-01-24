package test.gui.stf;

import com.google.common.collect.ImmutableList;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.When;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Text;
import log.Log;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.Assume;
import org.junit.runner.RunWith;
import records.gui.kit.DisplayDocument;
import records.gui.kit.DynamicTextField;
import records.gui.kit.ReadOnlyDocument;
import test.TestUtil;
import test.gen.GenRandom;
import test.gen.GenString;
import test.gui.util.FXApplicationTest;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.gui.FXUtility;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(JUnitQuickcheck.class)
public class TestDynamicTextField extends FXApplicationTest
{
    private final DynamicTextField field = new DynamicTextField();
    
    @Property(trials=5, shrink = false)
    public void testBasic(@From(GenString.class) String s, @From(GenRandom.class) Random r)
    {
        s = removeNonPrintable(s);
        
        if (s.length() > 100)
            s = s.substring(0, s.length() % 100);

        String sFinal = s;
        TestUtil.fx_(() ->{
            windowToUse.setScene(new Scene(field));
            windowToUse.show();
            windowToUse.sizeToScene();
            field.setDocument(new DisplayDocument(sFinal));
        });
        assertThat(field.getWidth(), Matchers.greaterThanOrEqualTo(100.0));
        assertThat(field.getHeight(), Matchers.greaterThanOrEqualTo(10.0));
        
        String unfocused = getText();
        // While unfocused, could be truncated for efficiency:
        assertThat(s, Matchers.startsWith(unfocused));
        
        clickOn(field);
        assertTrue(field.isFocused());
        String focused = getText();
        // Once focused, should have complete text:
        assertEquals(s, focused);
        
        push(KeyCode.HOME);
        assertEquals(0, field._test_getCaretPosition());
        int moveRightTo = s.isEmpty() ? 0 : r.nextInt(Math.min(s.length(), 25));
        for (int i = 0; i < moveRightTo; i++)
        {
            push(KeyCode.RIGHT);
            assertEquals(i + 1, field._test_getCaretPosition());
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
            Optional<Point2D> clickTarget = TestUtil.fx(() -> 
                field._test_getClickPosFor(pos).map(field::localToScreen)
            );
            clickTarget.ifPresent(this::clickOn);
            if (clickTarget.isPresent())
                assertEquals(pos, getFieldCaretPos());
            // Prevent it counting as multi click:
            TestUtil.sleep(1000);
        }

        for (int i = 0; i < 5; i++)
        {
            int aPos = s.isEmpty() ? 0 : editPositions.get(r.nextInt(editPositions.size()));
            int bPos = s.isEmpty() ? 0 : editPositions.get(r.nextInt(editPositions.size()));
            Optional<Point2D> aClick = TestUtil.fx(() ->
                field._test_getClickPosFor(aPos).map(field::localToScreen)
            );
            Optional<Point2D> bClick = TestUtil.fx(() ->
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
            TestUtil.sleep(1000);
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
        }
    }

    @OnThread(Tag.Any)
    public int getFieldAnchorPos()
    {
        return (int) TestUtil.<Integer>fx(() -> (Integer)field._test_getAnchorPosition());
    }

    @OnThread(Tag.Any)
    public int getFieldCaretPos()
    {
        return (int) TestUtil.<Integer>fx(() -> (Integer)field._test_getCaretPosition());
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

    private String removeNonPrintable(String s)
    {
        // Adapted from https://stackoverflow.com/a/18603020/412908
        int[] filteredCodePoints = s.codePoints().filter(codePoint -> {
                    return !ImmutableList.<Integer>of((int)Character.CONTROL, (int)Character.FORMAT, (int)Character.PRIVATE_USE, (int)Character.SURROGATE, (int)Character.UNASSIGNED).contains(Character.getType(codePoint));
                }).toArray();
        return new String(filteredCodePoints, 0, filteredCodePoints.length);
    }
    
    @Property(trials=3)
    public void testHorizScroll(@From(GenString.class) String s)
    {
        s = removeNonPrintable(s);
        Assume.assumeFalse(s.isEmpty());
        while (s.length() < 50)
            s += s;

        String sFinal = s;
        TestUtil.fx_(() -> {
            field.setPrefWidth(100);
            field.setMaxWidth(100);
            windowToUse.setScene(new Scene(new StackPane(field)));
            windowToUse.show();
            windowToUse.sizeToScene();
            field.setDocument(new DisplayDocument(sFinal));
        });
        
        MatcherAssert.assertThat(TestUtil.fx(() -> field.getWidth()), Matchers.lessThanOrEqualTo(110.0));

        clickOn(field);
        push(KeyCode.HOME);
        Bounds fieldBounds = TestUtil.fx(() -> field.localToScreen(field.getBoundsInLocal()));
        Node caret = lookup(".dynamic-caret").query();
        List<Rectangle2D> allBounds = new ArrayList<>();
        for (int i = 0; i < 50; i++)
        {
            push(KeyCode.RIGHT);

            Bounds caretBounds = TestUtil.fx(() -> caret.localToScreen(caret.getBoundsInLocal()));
            allBounds.add(FXUtility.boundsToRect(caretBounds));
            assertTrue("Field: " + fieldBounds + " caret: " + caretBounds, fieldBounds.contains(FXUtility.getCentre(caretBounds)));
        }
        for (int i = 0; i < 40; i++)
        {
            push(KeyCode.LEFT);
            Bounds caretBounds = TestUtil.fx(() -> caret.localToScreen(caret.getBoundsInLocal()));
            allBounds.add(FXUtility.boundsToRect(caretBounds));
            assertTrue(fieldBounds.contains(caretBounds));
        }
        
        assertThat(allBounds.stream().distinct().count(), Matchers.greaterThan(10L));
    }

    // TODO test truncating very long strings when unfocused
    // TODO test unfocused document switching
    

    private String getText()
    {
        return TestUtil.fx(() -> lookup((Predicate<Node>) t -> t instanceof Text).queryAll().stream().sorted(Comparator.comparing(n -> n.getLayoutX())).map(n -> ((Text)n).getText()).collect(Collectors.joining()));
    }
}
