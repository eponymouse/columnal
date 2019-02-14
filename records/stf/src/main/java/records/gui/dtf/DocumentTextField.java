package records.gui.dtf;

import com.google.common.collect.ImmutableList;
import com.sun.javafx.scene.text.HitInfo;
import com.sun.javafx.scene.text.TextLayout;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.collections.ObservableList;
import javafx.event.Event;
import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.effect.BlendMode;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Path;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.util.Duration;
import log.Log;
import org.apache.commons.lang3.SystemUtils;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;
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
    private final DisplayContent displayContent;
    private Document.TrackedPosition anchorPosition;
    private Document.TrackedPosition caretPosition;
    private Document document;
    // Always positive, the amount of offset from the left edge back
    // to the real start of the content.
    private double horizTranslation;
    // Always positive, the amount of offset from the top edge up
    // to the real start of the content.
    private double vertTranslation;
    private boolean expanded;
    
    public DocumentTextField(@Nullable FXPlatformRunnable onExpand)
    {
        getStyleClass().add("document-text-field");
        setFocusTraversable(true);
        this.document = new ReadOnlyDocument("");
        this.displayContent = new DisplayContent(document);
        getChildren().setAll(displayContent);
        anchorPosition = document.trackPosition(0, Bias.FORWARD, FXUtility.mouse(this)::queueUpdateCaretShape);
        caretPosition = document.trackPosition(0, Bias.FORWARD, FXUtility.mouse(this)::queueUpdateCaretShape);
        
        Nodes.addInputMap(FXUtility.mouse(this), InputMap.<Event>sequence(
            InputMap.<MouseEvent>consume(MouseEvent.ANY, FXUtility.mouse(this)::mouseEvent),
            InputMap.<KeyEvent>consume(KeyEvent.ANY, FXUtility.keyboard(this)::keyboardEvent)
        ));
        
        FXUtility.addChangeListenerPlatformNN(focusedProperty(), focused -> {
            document.focusChanged(focused);
            if (!focused)
            {
                caretPosition.moveTo(0);
                anchorPosition.moveTo(0);
            }
            FXUtility.mouse(this).refreshDocument(focused);
            if (onExpand != null)
            {
                Utility.later(this).setExpanded(focused);
                onExpand.run();
            }
            
            displayContent.focusChanged(focused);
        });
    }
    
    private void mouseEvent(MouseEvent mouseEvent)
    {
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
            if (mouseEvent.getEventType() != MouseEvent.MOUSE_DRAGGED && !mouseEvent.isShiftDown())
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
                moveAnchorToCaret();
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
                if (!keyEvent.isShiftDown())
                    moveAnchorToCaret();
            }
            if (keyEvent.getCode() == KeyCode.DOWN)
            {
                Point2D p = getClickPosFor(caretPosition.getPosition(), VPos.BOTTOM).getFirst();
                HitInfo hitInfo = hitTest(p.getX(), p.getY() + 5);
                if (hitInfo != null)
                    caretPosition.moveTo(hitInfo.getInsertionIndex());
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
        anchorPosition.moveTo(caretPosition.getPosition());
        //Log.logStackTrace("Anchor now " + anchorPosition.getPosition());
    }

    public void setDocument(Document document)
    {
        this.document.removeListener(this);
        this.document = document;
        this.document.addListener(this);
        caretPosition = this.document.trackPosition(0, Bias.FORWARD, this::queueUpdateCaretShape);
        anchorPosition = this.document.trackPosition(0, Bias.FORWARD, this::queueUpdateCaretShape);
        moveAnchorToCaret();
        documentChanged();
    }
    
    private void queueUpdateCaretShape()
    {
        if (displayContent.caretAndSelectionNodes != null)
            displayContent.caretAndSelectionNodes.queueUpdateCaretShape();
    }

    @Override
    @OnThread(Tag.FXPlatform)
    public void documentChanged()
    {
        displayContent.textFlow.getChildren().setAll(makeTextNodes(document.getStyledSpans(isFocused())));
        FXUtility.setPseudoclass(this, "has-error", document.hasError());
        displayContent.requestLayout();
    }

    private static List<Text> makeTextNodes(Stream<Pair<Set<String>, String>> styledSpans)
    {
        return styledSpans.map(ss -> {
            Text text = new Text(ss.getSecond());
            text.getStyleClass().add("document-text");
            text.setMouseTransparent(true);
            text.getStyleClass().addAll(ss.getFirst());
            return text;
        }).collect(ImmutableList.<Text>toImmutableList());
    }
    
    @Override
    @OnThread(value = Tag.FXPlatform, ignoreParent = true)
    protected void layoutChildren()
    {
        displayContent.resizeRelocate(0, 0, getWidth(), getHeight());
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
        return displayContent.textFlow.prefHeight(-1);
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

    private TextLayout getTextLayout() throws NoSuchMethodException, IllegalAccessException, InvocationTargetException
    {
        // TODO stop using reflection in Java 9, just call the methods directly
        Method method = displayContent.textFlow.getClass().getDeclaredMethod("getTextLayout");
        method.setAccessible(true);
        @SuppressWarnings("nullness")
        @NonNull TextLayout textLayout = (TextLayout) method.invoke(displayContent.textFlow);
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
            moveAnchorToCaret();
        }
        catch (Exception e)
        {
            Log.log(e);
        }
    }
    
    public String _test_getGraphicalText()
    {
        return displayContent.textFlow.getChildren().stream().filter(t -> t instanceof Text).map(n -> ((Text)n).getText()).collect(Collectors.joining());
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
        displayContent.textFlow.getChildren().setAll(makeTextNodes(document.getStyledSpans(focused)));
        displayContent.requestLayout();
    }
    
    private void setExpanded(boolean expanded)
    {
        FXUtility.setPseudoclass(this, "expanded", expanded);
        this.expanded = expanded;
        displayContent.requestLayout();
    }

    public boolean isExpanded()
    {
        return expanded;
    }
    
    private class DisplayContent extends Region
    {
        private final TextFlow textFlow;

        // We only need these when we are focused, and only one field
        // can ever be focused at once.  So these are null while
        // they are unneeded (while we are unfocused)
        private class CaretAndSelectionNodes
        {
            private final Path caretShape;
            private final Path selectionShape;
            private final ResizableRectangle fadeOverlay;
            private final Path inverter;
            private final Pane selectionPane;
            private final Pane inverterPane;

            private final Animation caretBlink;
            private boolean updateCaretShapeQueued;


            public CaretAndSelectionNodes()
            {
                caretShape = new Path();
                caretShape.setMouseTransparent(true);
                caretShape.setManaged(false);
                caretShape.getStyleClass().add("document-caret");
                caretShape.setVisible(false);
                selectionShape = new Path();
                selectionShape.setMouseTransparent(true);
                //selectionShape.setManaged(false);
                selectionShape.getStyleClass().add("document-selection");
                // The whole issue of the selection text inverting is quite screwed up.
                // The obvious thing to do is to apply CSS styling to the selected text to
                // make it white.  But doing this causes some slight (one pixel) display
                // changes that look really terrible and draw the eye as you select the text.
                // The movements aren't even in the selected part, they can be several lines down!
                // So we must invert the text without altering the TextFlow at all.
                // This can be done using JavaFX's blending modes, but it takes a lot of
                // intricate effort to do this while also applying a coloured selection background.
                // This implementation works, and that is good enough, despite it seeming over-the-top.
                selectionPane = new Pane(selectionShape);
                selectionPane.setBackground(new Background(new BackgroundFill(Color.BLACK, CornerRadii.EMPTY, Insets.EMPTY)));
                selectionPane.setBlendMode(BlendMode.LIGHTEN);
                inverter = new Path();
                inverter.setMouseTransparent(true);
                //inverter.setManaged(false);
                inverter.setFill(Color.WHITE);
                inverter.setStroke(null);
                Pane black = new Pane();
                black.setBackground(new Background(new BackgroundFill(Color.BLACK, CornerRadii.EMPTY, Insets.EMPTY)));
                inverterPane = new Pane(black,inverter);
                //inverterPane.setBackground(new Background(new BackgroundFill(Color.BLACK, CornerRadii.EMPTY, Insets.EMPTY)));
                inverterPane.setBlendMode(BlendMode.DIFFERENCE);

                selectionShape.visibleProperty().bind(DocumentTextField.this.focusedProperty());
                fadeOverlay = new ResizableRectangle();
                fadeOverlay.setMouseTransparent(true);
                fadeOverlay.getStyleClass().add("fade-overlay");

                caretBlink = new Timeline(
                        new KeyFrame(Duration.seconds(0), new KeyValue(caretShape.visibleProperty(), true)),
                        new KeyFrame(Duration.seconds(0.8), new KeyValue(caretShape.visibleProperty(), false)),
                        new KeyFrame(Duration.seconds(1.2), e -> Utility.later(this).updateCaretShape(), new KeyValue(caretShape.visibleProperty(), true))
                );
                caretBlink.setCycleCount(Animation.INDEFINITE);

            }

            public void focusChanged(boolean focused)
            {
                if (focused)
                {
                    caretBlink.playFromStart();
                    queueUpdateCaretShape();
                }
                else
                {
                    caretBlink.stop();
                    updateCaretShapeQueued = false;
                }
            }

            private void queueUpdateCaretShape()
            {
                if (!updateCaretShapeQueued)
                {
                    FXUtility.runAfter(this::updateCaretShape);
                    updateCaretShapeQueued = true;
                }
            }

            private void updateCaretShape()
            {
                if (!updateCaretShapeQueued)
                    return; // We may be a stale call just after losing focus
                
                updateCaretShapeQueued = false;
                try
                {
                    selectionShape.getElements().setAll(getTextLayout().getRange(Math.min(caretPosition.getPosition(), anchorPosition.getPosition()), Math.max(caretPosition.getPosition(), anchorPosition.getPosition()), TextLayout.TYPE_TEXT, 0, 0));
                    inverter.getElements().setAll(selectionShape.getElements());
                    caretShape.getElements().setAll(getTextLayout().getCaretShape(caretPosition.getPosition(), true, 0, 0));
                    if (isFocused())
                        caretBlink.playFromStart();
                }
                catch (Exception e)
                {
                    selectionShape.getElements().clear();
                    inverter.getElements().clear();
                    caretShape.getElements().clear();
                }
                // Caret may have moved off-screen, which is detected and corrected in the layout:
                displayContent.requestLayout();
            }
        }
        private @Nullable CaretAndSelectionNodes caretAndSelectionNodes;
        
        public DisplayContent(Document document)
        {
            ResizableRectangle clip = new ResizableRectangle();
            clip.widthProperty().bind(widthProperty());
            clip.heightProperty().bind(heightProperty());
            setClip(clip);
            setMouseTransparent(true);
            textFlow = new TextFlow();
            textFlow.getStyleClass().add("document-text-flow");
            textFlow.setMouseTransparent(true);
            textFlow.getChildren().setAll(makeTextNodes(document.getStyledSpans(false)));
            
            getChildren().setAll(textFlow);

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
            }

            double wholeTextWidth = textFlow.prefWidth(-1);
            double wholeTextHeight = textFlow.prefHeight(getWidth());

            CaretAndSelectionNodes cs = this.caretAndSelectionNodes;
            if (cs != null)
            {
                Path caretShape = cs.caretShape;
                Bounds caretLocalBounds = caretShape.getBoundsInLocal();
                Bounds caretBounds = caretShape.localToParent(caretLocalBounds);

                //Log.debug("Bounds: " + caretBounds + " in " + getWidth() + "x" + getHeight() + " trans " + horizTranslation + "x" + vertTranslation + " whole " + wholeTextWidth + "x" + wholeTextHeight);
                boolean focused = DocumentTextField.this.isFocused();
                if (focused && caretBounds.getMinX() - 5 < 0)
                {
                    // Caret is off screen to the left:
                    horizTranslation = Math.max(0, caretLocalBounds.getMinX() - 10);
                }
                else if (focused && caretBounds.getMaxX() > getWidth() - 5)
                {
                    // Caret is off screen to the right:
                    horizTranslation = Math.min(Math.max(0, wholeTextWidth - getWidth()), caretLocalBounds.getMaxX() + 10 - getWidth());
                }
                else if (!focused)
                {
                    horizTranslation = 0;
                }

                if (focused && caretBounds.getMinY() - 5 < 0)
                {
                    // Caret is off screen to the top:
                    vertTranslation = Math.max(0, caretLocalBounds.getMinY() - 10);
                }
                else if (focused && caretBounds.getMaxY() > getHeight() - 5)
                {
                    // Caret is off screen to the bottom:
                    vertTranslation = Math.min(Math.max(0, wholeTextHeight - getHeight()), caretLocalBounds.getMaxY() + 10 - getHeight());
                }
                else if (!focused)
                {
                    vertTranslation = 0;
                }
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
            if (cs != null)
            {
                FXUtility.setPseudoclass(cs.fadeOverlay, "more-above", vertTranslation > 8);
                FXUtility.setPseudoclass(cs.fadeOverlay, "more-below", wholeTextHeight - vertTranslation > getHeight() + 8);
                cs.fadeOverlay.resize(getWidth(), getHeight());
                cs.caretShape.setLayoutX(-horizTranslation);
                cs.caretShape.setLayoutY(-vertTranslation);
                cs.selectionShape.setLayoutX(-horizTranslation);
                cs.selectionShape.setLayoutY(-vertTranslation);
                cs.inverter.setLayoutX(-horizTranslation);
                cs.inverter.setLayoutY(-vertTranslation);
                cs.inverterPane.resizeRelocate(0, 0, getWidth(), getHeight());
                cs.selectionPane.resizeRelocate(0, 0, getWidth(), getHeight());
            }
        }

        public void focusChanged(boolean focused)
        {
            if (focused)
            {
                if (caretAndSelectionNodes == null)
                    caretAndSelectionNodes = new CaretAndSelectionNodes();
                CaretAndSelectionNodes cs = this.caretAndSelectionNodes;
                getChildren().setAll(textFlow, cs.inverterPane, cs.selectionPane, cs.caretShape, cs.fadeOverlay);
                cs.focusChanged(true);
                
            }
            else
            {
                if (caretAndSelectionNodes != null)
                    caretAndSelectionNodes.focusChanged(false);
                caretAndSelectionNodes = null;
                getChildren().setAll(textFlow);
            }
        }
    }
}
