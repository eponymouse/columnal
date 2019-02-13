package records.gui.dtf;

import com.google.common.collect.ImmutableList;
import com.sun.javafx.scene.text.HitInfo;
import com.sun.javafx.scene.text.TextLayout;
import javafx.event.Event;
import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import javafx.geometry.VPos;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import javafx.scene.shape.Path;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import log.Log;
import org.apache.commons.lang3.SystemUtils;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.fxmisc.wellbehaved.event.InputMap;
import org.fxmisc.wellbehaved.event.Nodes;
import records.gui.dtf.Document.DocumentListener;
import records.gui.dtf.Document.TrackedPosition.Bias;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
import utility.Utility;
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
    private boolean expanded;
    private double coreWidth;
    private double coreHeight;

    public DocumentTextField()
    {
        getStyleClass().add("document-text-field");
        setFocusTraversable(true);
        this.document = new ReadOnlyDocument("");
        this.clip = new ResizableRectangle();
        textFlow = new TextFlow();
        textFlow.setClip(clip);
        textFlow.setMouseTransparent(true);
        textFlow.getChildren().setAll(makeTextNodes(document.getStyledSpans(false)));
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
            if (focused)
                FXUtility.mouse(this).refreshDocument(focused);
            Utility.later(this).setExpanded(focused);
        });
    }
    
    private void mouseEvent(MouseEvent mouseEvent)
    {
        if (mouseEvent.getEventType() == MouseEvent.MOUSE_PRESSED && (mouseEvent.getX() >= coreWidth || mouseEvent.getY() >= coreHeight))
            return; // Don't process events in our expanded region
        
        //if (mouseEvent.getEventType() == MouseEvent.MOUSE_RELEASED)
        //    Log.debug("Got mouse event: " + mouseEvent);
        
        if (mouseEvent.getEventType() == MouseEvent.MOUSE_PRESSED
            //|| (mouseEvent.getEventType() == MouseEvent.MOUSE_CLICKED && mouseEvent.isStillSincePress())
            || mouseEvent.getEventType() == MouseEvent.MOUSE_DRAGGED)
        {
            Log.debug("Got mouse event: " + mouseEvent + " " + mouseEvent.isStillSincePress());
            // Position the caret at the clicked position:

            HitInfo hitInfo = hitTest(mouseEvent.getX(), mouseEvent.getY());
            if (hitInfo == null)
                return;
            // Focusing may change content so important to hit-test first:
            requestFocus();
            // And important to map caret pos after change:
            caretPosition.moveTo(document.mapCaretPos(hitInfo.getInsertionIndex()));
            if (mouseEvent.getEventType() != MouseEvent.MOUSE_DRAGGED)
                moveAnchorToCaret();
        }
    }

    private @Nullable HitInfo hitTest(double x, double y)
    {
        TextLayout textLayout;
        try
        {
            textLayout = getTextLayout();
        }
        catch (Exception e)
        {
            Log.log(e);
            textLayout = null;
        }
        return textLayout == null ? null: textLayout.getHitInfo((float)x, (float)y);
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
            if (keyEvent.getCode() == KeyCode.UP)
            {
                Point2D p = getClickPosFor(caretPosition.getPosition(), VPos.TOP).orElse(null);
                if (p != null)
                {
                    HitInfo hitInfo = hitTest(p.getX(), p.getY() - 5);
                    if (hitInfo != null)
                        caretPosition.moveTo(hitInfo.getInsertionIndex());
                }
            }
            if (keyEvent.getCode() == KeyCode.DOWN)
            {
                Point2D p = getClickPosFor(caretPosition.getPosition(), VPos.BOTTOM).orElse(null);
                if (p != null)
                {
                    HitInfo hitInfo = hitTest(p.getX(), p.getY() + 5);
                    if (hitInfo != null)
                        caretPosition.moveTo(hitInfo.getInsertionIndex());
                }
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

    @Override
    @OnThread(Tag.FXPlatform)
    public void documentChanged()
    {
        textFlow.getChildren().setAll(makeTextNodes(document.getStyledSpans(isFocused())));
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
    @OnThread(value = Tag.FXPlatform, ignoreParent = true)
    protected void layoutChildren()
    {
        if (expanded)
        {
            textFlow.setPrefWidth(getWidth());
        }
        else
        {
            textFlow.setPrefWidth(USE_COMPUTED_SIZE);
            coreWidth = getWidth();
            coreHeight = getHeight();
        }
        
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
    @OnThread(value = Tag.FXPlatform, ignoreParent = true)
    protected double computePrefHeight(double width)
    {
        return textFlow.prefHeight(-1);
    }

    @Override
    @OnThread(value = Tag.FXPlatform, ignoreParent = true)
    protected double computePrefWidth(double height)
    {
        return 300;
    }

    public Optional<Point2D> _test_getClickPosFor(int targetPos)
    {
        return getClickPosFor(targetPos, VPos.CENTER);
    }
    
    private Optional<Point2D> getClickPosFor(int targetPos, VPos vPos)
    {
        try
        {
            TextLayout textLayout = getTextLayout();
            Bounds bounds = new Path(textLayout.getCaretShape(targetPos, true, 1.0f, 0)).getBoundsInLocal();
            Point2D p;
            switch (vPos)
            {
                case TOP:
                    p = new Point2D((bounds.getMinX() + bounds.getMaxX()) / 2.0, bounds.getMinY());
                    break;
                case BOTTOM:
                    p = new Point2D((bounds.getMinX() + bounds.getMaxX()) / 2.0, bounds.getMaxY());
                    break;
                case CENTER:
                default:
                    p = FXUtility.getCentre(bounds);
                    break;
            }
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
            int index = textLayout.getHitInfo((float) point2D.getX(), (float) point2D.getY()).getInsertionIndex();
            caretPosition.moveTo(isFocused() ? index : document.mapCaretPos(index));
        }
        catch (Exception e)
        {
            Log.log(e);
        }
    }
    
    public String _test_getGraphicalText()
    {
        return textFlow.getChildren().stream().filter(t -> t instanceof Text).map(n -> ((Text)n).getText()).collect(Collectors.joining());
    }

    public Optional<Bounds> _test_getCharacterBoundsOnScreen(int charAfter)
    {
        try
        {
            Path path = new Path(getTextLayout().getRange(charAfter, charAfter + 1, TextLayout.TYPE_TEXT, 0, 0));
            Bounds actualBounds = localToScreen(path.getBoundsInLocal());
            Rectangle2D clipped = FXUtility.intersectRect(
                    FXUtility.boundsToRect(actualBounds),
                    FXUtility.boundsToRect(localToScreen(getBoundsInLocal())));
            return Optional.of(new BoundingBox(clipped.getMinX(), clipped.getMinY(), clipped.getWidth(), clipped.getHeight()));
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    public List<Pair<Set<String>, String>> _test_getStyleSpans()
    {
        return document.getStyledSpans(isFocused()).collect(Collectors.<Pair<Set<String>, String>>toList());
    }

    public List<Pair<Set<String>, String>> _test_getStyleSpans(int from, int to)
    {
        List<Pair<Set<String>, String>> styledSpans = new ArrayList<>(document.getStyledSpans(isFocused()).collect(Collectors.<Pair<Set<String>, String>>toList()));
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
    
    @SuppressWarnings("unchecked")
    public <T> @Nullable RecogniserDocument<T> getRecogniserDocument(Class<T> itemClass)
    {
        if (document instanceof RecogniserDocument<?> && ((RecogniserDocument<?>)document).getItemClass().equals(itemClass))
            return (RecogniserDocument<T>)document;
        else
            return null;
    }

    public void refreshDocument(boolean focused)
    {
        textFlow.getChildren().setAll(makeTextNodes(document.getStyledSpans(focused)));
        requestLayout();
    }
    
    public void setExpanded(boolean expanded)
    {
        FXUtility.setPseudoclass(this, "expanded", expanded);
        this.expanded = expanded;
        this.coreWidth = getWidth();
        this.coreHeight = getHeight();
    }

    public boolean isExpanded()
    {
        return expanded;
    }

    public void setCoreSize(double coreWidth, double coreHeight)
    {
        this.coreWidth = coreWidth;
        this.coreHeight = coreHeight;
    }
}
