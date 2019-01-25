package records.gui.kit;

import com.google.common.collect.ImmutableList;
import com.sun.javafx.scene.text.HitInfo;
import com.sun.javafx.scene.text.TextLayout;
import javafx.event.Event;
import javafx.geometry.Bounds;
import javafx.geometry.Orientation;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import javafx.scene.shape.Path;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Shape;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import log.Log;
import org.apache.commons.lang3.SystemUtils;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.fxmisc.wellbehaved.event.InputHandler;
import org.fxmisc.wellbehaved.event.InputHandler.Result;
import org.fxmisc.wellbehaved.event.InputMap;
import org.fxmisc.wellbehaved.event.Nodes;
import records.gui.kit.Document.DocumentListener;
import records.gui.kit.Document.TrackedPosition;
import records.gui.kit.Document.TrackedPosition.Bias;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
import utility.gui.FXUtility;
import utility.gui.ResizableRectangle;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// Will become a replacement for FlexibleTextField
@OnThread(Tag.FXPlatform)
public class DocumentTextField extends Region implements DocumentListener
{
    private final TextFlow textFlow;
    private final Path caretShape;
    private Document.TrackedPosition anchorPosition;
    private Document.TrackedPosition caretPosition;
    private Document document;
    private double horizTranslation;
    private final ResizableRectangle clip;

    public DocumentTextField()
    {
        getStyleClass().add("document-text-field");
        setFocusTraversable(true);
        this.document = new ReadOnlyDocument("");
        this.clip = new ResizableRectangle();
        setClip(clip);
        textFlow = new TextFlow();
        textFlow.setMouseTransparent(true);
        textFlow.getChildren().setAll(makeTextNodes(document.getStyledSpans()));
        anchorPosition = caretPosition = document.trackPosition(0, Bias.FORWARD, FXUtility.mouse(this)::updateCaretShape);
        caretShape = new Path();
        caretShape.setMouseTransparent(true);
        caretShape.setManaged(false);
        caretShape.visibleProperty().bind(focusedProperty());
        caretShape.getStyleClass().add("dynamic-caret");
        getChildren().addAll(textFlow, caretShape);

        Nodes.addInputMap(FXUtility.mouse(this), InputMap.<Event>sequence(
            InputMap.<MouseEvent>consume(MouseEvent.ANY, FXUtility.mouse(this)::mouseEvent),
            InputMap.<KeyEvent>consume(KeyEvent.ANY, FXUtility.keyboard(this)::keyboardEvent)
        ));
        FXUtility.addChangeListenerPlatformNN(focusedProperty(), focused -> {
            document.focusChanged(focused);
        });
    }
    
    private void mouseEvent(MouseEvent mouseEvent)
    {
        if (mouseEvent.getEventType() == MouseEvent.MOUSE_RELEASED)
            Log.debug("Got mouse event: " + mouseEvent);
        
        if (mouseEvent.getEventType() == MouseEvent.MOUSE_PRESSED
            //|| (mouseEvent.getEventType() == MouseEvent.MOUSE_CLICKED && mouseEvent.isStillSincePress())
            || mouseEvent.getEventType() == MouseEvent.MOUSE_DRAGGED)
        {
            Log.debug("Got mouse event: " + mouseEvent + " " + mouseEvent.isStillSincePress());
            requestFocus();
            // Position the caret at the clicked position:
            
            final TextLayout textLayout;
            try
            {
                textLayout = getTextLayout();
            }
            catch (Exception e)
            {
                Log.log(e);
                return;
            }
            HitInfo hitInfo = textLayout.getHitInfo((float)mouseEvent.getX(), (float)mouseEvent.getY());
            caretPosition.moveTo(hitInfo.getInsertionIndex());
            if (mouseEvent.getEventType() != MouseEvent.MOUSE_DRAGGED)
                moveAnchorToCaret();
        }
    }
    
    private void keyboardEvent(KeyEvent keyEvent)
    {
        if (keyEvent.getEventType() == KeyEvent.KEY_TYPED)
        {
            // Borrowed from TextInputControlBehavior:
            // Sometimes we get events with no key character, in which case
            // we need to bail.
            String character = keyEvent.getCharacter();
            if (character.length() == 0)
                return;

            // Filter out control keys except control+Alt on PC or Alt on Mac
            if (keyEvent.isControlDown() || keyEvent.isAltDown() || (SystemUtils.IS_OS_MAC && keyEvent.isMetaDown()))
            {
                if (!((keyEvent.isControlDown() || SystemUtils.IS_OS_MAC) && keyEvent.isAltDown()))
                    return;
            }

            // Ignore characters in the control range and the ASCII delete
            // character as well as meta key presses
            if (character.charAt(0) > 0x1F
                && character.charAt(0) != 0x7F
                && !keyEvent.isMetaDown()) // Not sure about this one (Note: this comment is in JavaFX source)
            {
                document.replaceText(Math.min(caretPosition.getPosition(), anchorPosition.getPosition()), Math.max(caretPosition.getPosition(), anchorPosition.getPosition()), character);
            }
        }
        else if (keyEvent.getEventType() == KeyEvent.KEY_PRESSED)
        {
            if (keyEvent.getCode() == KeyCode.RIGHT && caretPosition.getPosition() + 1 <= document.getLength())
            {
                caretPosition.moveBy(1);
                if (!keyEvent.isShiftDown())
                    moveAnchorToCaret();
            }
            if (keyEvent.getCode() == KeyCode.LEFT && caretPosition.getPosition() - 1 >= 0)
            {
                caretPosition.moveBy(-1);
                if (!keyEvent.isShiftDown())
                    moveAnchorToCaret();
            }
            if (keyEvent.getCode() == KeyCode.HOME)
            {
                caretPosition.moveTo(0);
                if (!keyEvent.isShiftDown())
                    moveAnchorToCaret();
            }
            if (keyEvent.getCode() == KeyCode.A && keyEvent.isShortcutDown())
            {
                anchorPosition.moveTo(0);
                caretPosition.moveTo(document.getLength());
            }
            
            if ((keyEvent.getCode() == KeyCode.BACK_SPACE || keyEvent.getCode() == KeyCode.DELETE) && caretPosition.getPosition() != anchorPosition.getPosition())
            {
                document.replaceText(Math.min(caretPosition.getPosition(), anchorPosition.getPosition()), Math.max(caretPosition.getPosition(), anchorPosition.getPosition()), "");
            }
            else if (keyEvent.getCode() == KeyCode.BACK_SPACE && caretPosition.getPosition() > 0)
            {
                document.replaceText(caretPosition.getPosition() - 1, caretPosition.getPosition(), "");
            }
            else if (keyEvent.getCode() == KeyCode.DELETE && caretPosition.getPosition() < document.getLength())
            {
                document.replaceText(caretPosition.getPosition(), caretPosition.getPosition() + 1, "");
            }
            
            if (keyEvent.getCode() == KeyCode.ESCAPE || keyEvent.getCode() == KeyCode.ENTER || keyEvent.getCode() == KeyCode.TAB)
            {
                document.defocus();
            }
        }
    }

    private void moveAnchorToCaret()
    {
        anchorPosition = document.trackPosition(caretPosition.getPosition(), Bias.FORWARD, this::updateCaretShape);
        //Log.logStackTrace("Anchor now " + anchorPosition.getPosition());
    }

    public void setDocument(Document document)
    {
        this.document.removeListener(this);
        this.document = document;
        this.document.addListener(this);
        caretPosition = this.document.trackPosition(0, Bias.FORWARD, this::updateCaretShape);
        moveAnchorToCaret();
        documentChanged();
    }

    public void documentChanged()
    {
        textFlow.getChildren().setAll(makeTextNodes(document.getStyledSpans()));
        FXUtility.setPseudoclass(this, "has-error", document.hasError());
        requestLayout();
    }

    private static List<Text> makeTextNodes(Stream<Pair<Set<String>, String>> styledSpans)
    {
        return styledSpans.map(ss -> {
            Text text = new Text(ss.getSecond());
            text.setMouseTransparent(true);
            text.getStyleClass().addAll(ss.getFirst());
            return text;
        }).collect(ImmutableList.<Text>toImmutableList());
    }

    @Override
    protected void layoutChildren()
    {
        double wholeTextWidth = textFlow.prefWidth(-1);
        Bounds caretLocalBounds = caretShape.getBoundsInLocal();
        Bounds caretBounds = caretShape.localToParent(caretLocalBounds);
        
        //Log.debug("Bounds: " + caretBounds + " in " + getWidth() + " trans " + horizTranslation + " whole " + wholeTextWidth);
        if (isFocused() && caretBounds.getMinX() - 5 < 0)
        {
            // Caret is off screen to the left:
            horizTranslation = Math.max(0, caretLocalBounds.getMinX() - 10);
        }
        else if (isFocused() && caretBounds.getMaxX() > getWidth() - 5)
        {
            // Caret is off screen to the right:
            horizTranslation = Math.min(Math.max(0, wholeTextWidth - getWidth()), caretLocalBounds.getMaxX() + 10 - getWidth());
        }
        else if (!isFocused())
        {
            horizTranslation = 0;
        }
        
        textFlow.resizeRelocate(-horizTranslation, 0, wholeTextWidth, getHeight());
        clip.resize(getWidth(), getHeight());
        caretShape.setLayoutX(-horizTranslation);
    }

    private void updateCaretShape()
    {
        try
        {
            caretShape.getElements().setAll(getTextLayout().getCaretShape(caretPosition.getPosition(), true, 0, 0));
        }
        catch (Exception e)
        {
            caretShape.getElements().clear();
        }
        requestLayout();
    }

    public int _test_getAnchorPosition()
    {
        return anchorPosition.getPosition();
    }

    public int _test_getCaretPosition()
    {
        return caretPosition.getPosition();
    }

    @Override
    protected double computePrefHeight(double width)
    {
        return textFlow.prefHeight(-1);
    }

    @Override
    protected double computePrefWidth(double height)
    {
        return 300;
    }

    @SuppressWarnings("nullness")
    public Optional<Point2D> _test_getClickPosFor(int targetPos)
    {
        try
        {
            TextLayout textLayout = getTextLayout();
            Point2D p = FXUtility.getCentre(new Path(textLayout.getCaretShape(targetPos, true, 1.0f, 0)).getBoundsInLocal());
            if (getBoundsInLocal().contains(p))
                return Optional.of(p);
            else
                return Optional.empty();
        }
        catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException | NullPointerException e)
        {
            throw new RuntimeException(e);
        }
    }

    public TextLayout getTextLayout() throws NoSuchMethodException, IllegalAccessException, InvocationTargetException
    {
        // TODO stop using reflection in Java 9, just call the methods directly
        Method method = textFlow.getClass().getDeclaredMethod("getTextLayout");
        method.setAccessible(true);
        @SuppressWarnings("nullness")
        @NonNull TextLayout textLayout = (TextLayout) method.invoke(textFlow);
        return textLayout;
    }

    public boolean isEditable()
    {
        return document.isEditable();
    }
    
    public void home()
    {
        caretPosition.moveTo(0);
    }
    
    public void moveTo(Point2D point2D)
    {
        try
        {
            TextLayout textLayout = getTextLayout();
            caretPosition.moveTo(textLayout.getHitInfo((float)point2D.getX(), (float)point2D.getY()).getInsertionIndex());
        }
        catch (Exception e)
        {
            Log.log(e);
        }
    }
    
    public String getText()
    {
        return document.getText();
    }

    public Optional<Bounds> _test_getCharacterBoundsOnScreen(int charAfter)
    {
        try
        {
            Path path = new Path(getTextLayout().getRange(charAfter, charAfter + 1, TextLayout.TYPE_TEXT, 0, 0));
            return Optional.of(path.localToScreen(path.getBoundsInLocal()));
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    public List<Pair<Set<String>, String>> _test_getStyleSpans(int from, int to)
    {
        List<Pair<Set<String>, String>> styledSpans = new ArrayList<>(document.getStyledSpans().collect(Collectors.<Pair<Set<String>, String>>toList()));
        int leftToSkip = from;
        int leftToRetain = to - from;
        int index = 0;
        while (index < styledSpans.size())
        {
            if (leftToSkip > styledSpans.get(index).getSecond().length() || leftToRetain <= 0)
            {
                styledSpans.remove(index);
                continue;
            }
            else if (leftToSkip > 0)
            {
                styledSpans.set(index, styledSpans.get(index).mapSecond(s -> s.substring(leftToSkip)));
            }
            if (leftToRetain > 0)
            {
                leftToRetain -= styledSpans.get(index).getSecond().length();
                if (leftToRetain < 0)
                {
                    int leftToRetainFinal = leftToRetain;
                    styledSpans.set(index, styledSpans.get(index).mapSecond(s -> s.substring(0, s.length() + leftToRetainFinal)));
                    leftToRetain = 0;
                }
            }
            
            index += 1;
        }
        // Mop up any empty spans:
        for (Iterator<Pair<Set<String>, String>> iterator = styledSpans.iterator(); iterator.hasNext(); )
        {
            Pair<Set<String>, String> styledSpan = iterator.next();
            if (styledSpan.getSecond().isEmpty())
                iterator.remove();
        }
        
        return styledSpans;
    }
}
