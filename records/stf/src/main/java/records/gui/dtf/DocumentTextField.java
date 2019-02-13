package records.gui.dtf;

import com.google.common.collect.ImmutableList;
import com.sun.javafx.scene.text.HitInfo;
import com.sun.javafx.scene.text.TextLayout;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
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
import javafx.util.Duration;
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
import utility.FXPlatformRunnable;
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
    private final ResizableRectangle fadeOverlay;
    private Document.TrackedPosition anchorPosition;
    private Document.TrackedPosition caretPosition;
    private Document document;
    // Always positive, the amount of offset from the left edge back
    // to the real start of the content.
    private double horizTranslation;
    // Always positive, the amount of offset from the top edge up
    // to the real start of the content.
    private double vertTranslation;
    private final ResizableRectangle clip;
    private boolean expanded;
    private double coreWidth;
    private double coreHeight;
    private final Animation caretBlink;

    public DocumentTextField(@Nullable FXPlatformRunnable onExpand)
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
        caretShape.getStyleClass().add("document-caret");
        fadeOverlay = new ResizableRectangle();
        fadeOverlay.setMouseTransparent(true);
        fadeOverlay.getStyleClass().add("fade-overlay");
        getChildren().addAll(textFlow, caretShape, fadeOverlay);
        
        Nodes.addInputMap(FXUtility.mouse(this), InputMap.<Event>sequence(
            InputMap.<MouseEvent>consume(MouseEvent.ANY, FXUtility.mouse(this)::mouseEvent),
            InputMap.<KeyEvent>consume(KeyEvent.ANY, FXUtility.keyboard(this)::keyboardEvent)
        ));

        caretBlink = new Timeline(
            new KeyFrame(Duration.seconds(0), new KeyValue(caretShape.visibleProperty(), true)),
            new KeyFrame(Duration.seconds(0.8), new KeyValue(caretShape.visibleProperty(), false)),
            new KeyFrame(Duration.seconds(1.6), new KeyValue(caretShape.visibleProperty(), true))
        );
        caretBlink.setCycleCount(Animation.INDEFINITE);
        
        
        FXUtility.addChangeListenerPlatformNN(focusedProperty(), focused -> {
            document.focusChanged(focused);
            if (focused)
                FXUtility.mouse(this).refreshDocument(focused);
            if (onExpand != null)
            {
                Utility.later(this).setExpanded(focused);
                onExpand.run();
            }
            if (focused)
                caretBlink.playFromStart();
            else
            {
                caretBlink.stop();
                caretShape.setVisible(false);
            }
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
        return textLayout == null ? null: textLayout.getHitInfo((float)(x + horizTranslation), (float)(y + vertTranslation));
    }

    private void keyboardEvent(KeyEvent keyEvent)
    {
        if (keyEvent.getEventType() == KeyEvent.KEY_TYPED && isEditable())
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
                Point2D p = getClickPosFor(caretPosition.getPosition(), VPos.TOP).getFirst();
                HitInfo hitInfo = hitTest(p.getX(), p.getY() - 5);
                if (hitInfo != null)
                {
                    if (hitInfo.getInsertionIndex() == caretPosition.getPosition())
                    {
                        // We must be on the top line; move to the beginning:
                        caretPosition.moveTo(0);
                    }
                    else
                    {
                        caretPosition.moveTo(hitInfo.getInsertionIndex());
                    }
                }
            }
            if (keyEvent.getCode() == KeyCode.DOWN)
            {
                Point2D p = getClickPosFor(caretPosition.getPosition(), VPos.BOTTOM).getFirst();
                HitInfo hitInfo = hitTest(p.getX(), p.getY() + 5);
                if (hitInfo != null)
                    caretPosition.moveTo(hitInfo.getInsertionIndex());
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
        double wholeTextHeight = textFlow.prefHeight(getWidth());
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
        
        if (isFocused() && caretBounds.getMinY() - 5 < 0)
        {
            // Caret is off screen to the top:
            vertTranslation = Math.max(0, caretLocalBounds.getMinY() - 10);
        }
        else if (isFocused() && caretBounds.getMaxY() > getHeight() - 5)
        {
            // Caret is off screen to the bottom:
            vertTranslation = Math.min(Math.max(0, wholeTextHeight - getHeight()), caretLocalBounds.getMaxY() + 10 - getHeight());
        }
        else if (!isFocused())
        {
            vertTranslation = 0;
        }

        if (isExpanded())
        {
            textFlow.resizeRelocate(-horizTranslation, -vertTranslation, getWidth(), wholeTextHeight);
        }
        else
        {
            // We avoid needlessly making TextFlows which are thousands of pixels
            // wide by restricting to our own width plus some.
            // (We don't use our own width because this causes text wrapping
            // of long words, but we actually want to see those truncated)
            textFlow.resizeRelocate(-horizTranslation, -vertTranslation, Math.min(getWidth() + 300, wholeTextWidth), getHeight());
        }
        //Log.debug("Text flow: " + textFlow.getWidth() + ", " + textFlow.getHeight() + " for text: " + _test_getGraphicalText());
        FXUtility.setPseudoclass(fadeOverlay, "more-above", vertTranslation > 8);
        FXUtility.setPseudoclass(fadeOverlay, "more-below", wholeTextHeight - vertTranslation > getHeight() + 8);
        fadeOverlay.resize(getWidth(), getHeight());
        clip.resizeRelocate(horizTranslation, vertTranslation, getWidth(), getHeight());
        caretShape.setLayoutX(-horizTranslation);
        caretShape.setLayoutY(-vertTranslation);
    }

    private void updateCaretShape()
    {
        try
        {
            caretShape.getElements().setAll(getTextLayout().getCaretShape(caretPosition.getPosition(), true, 0, 0));
            if (isFocused())
                caretBlink.playFromStart();
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
        Pair<Point2D, Boolean> clickPos = getClickPosFor(targetPos, VPos.CENTER);
        return clickPos.getSecond() ? Optional.of(clickPos.getFirst()) : Optional.empty();
    }

    /**
     * Gets the click position of the target caret position, in DocumentTextField coordinates.
     * 
     * @param targetPos The target caret pos (like a character index)
     * @param vPos The vertical position within the caret: top of it, middle of it, bottom of it?
     * @return The click position, plus a boolean indicating whether or not it is in bounds.
     */
    private Pair<Point2D, Boolean> getClickPosFor(int targetPos, VPos vPos)
    {
        try
        {
            TextLayout textLayout = getTextLayout();
            Bounds bounds = new Path(textLayout.getCaretShape(targetPos, true, 1.0f - (float)horizTranslation, (float)-vertTranslation)).getBoundsInLocal();
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
            return new Pair<>(p, getBoundsInLocal().contains(p));
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
    
    private void setExpanded(boolean expanded)
    {
        FXUtility.setPseudoclass(this, "expanded", expanded);
        this.expanded = expanded;
        this.coreWidth = getWidth();
        this.coreHeight = getHeight();
        requestLayout();
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
