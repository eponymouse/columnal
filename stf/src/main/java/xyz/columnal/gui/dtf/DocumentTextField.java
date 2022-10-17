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

package xyz.columnal.gui.dtf;

import annotation.units.CanonicalLocation;
import annotation.units.DisplayLocation;
import com.google.common.collect.ImmutableList;
import javafx.event.Event;
import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.geometry.Dimension2D;
import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import javafx.geometry.VPos;
import javafx.scene.control.MenuItem;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.shape.Path;
import javafx.scene.text.HitInfo;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import xyz.columnal.log.Log;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.fxmisc.wellbehaved.event.InputMap;
import org.fxmisc.wellbehaved.event.Nodes;
import xyz.columnal.gui.dtf.Document.DocumentListener;
import xyz.columnal.gui.dtf.Document.TrackedPosition.Bias;
import xyz.columnal.styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.function.fx.FXPlatformRunnable;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.Utility;
import xyz.columnal.utility.gui.FXUtility;
import xyz.columnal.utility.gui.TextEditorBase;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// Will become a replacement for FlexibleTextField
@OnThread(Tag.FXPlatform)
public class DocumentTextField extends TextEditorBase implements DocumentListener
{
    private Document.TrackedPosition anchorPosition;
    private Document.TrackedPosition caretPosition;
    private Document document;

    // Always positive, the amount of offset from the left edge back
    // to the real start of the content.
    protected double horizTranslation;
    // Always positive, the amount of offset from the top edge up
    // to the real start of the content.
    protected double vertTranslation;
    protected boolean expanded;
    protected boolean scrollable;
    protected TextAlignment unfocusedAlignment = TextAlignment.LEFT;
    private @Nullable Double idealWidth = null;
    private final @Nullable FXPlatformRunnable onExpand;

    public DocumentTextField(@Nullable FXPlatformRunnable onExpand)
    {
        super(makeTextNodes(new ReadOnlyDocument("").getStyledSpans(false)));
        this.onExpand = onExpand;
        this.scrollable = true;
        getStyleClass().add("document-text-field");
        setFocusTraversable(true);
        this.document = new ReadOnlyDocument("");
        anchorPosition = document.trackPosition(0, Bias.FORWARD, FXUtility.mouse(this)::queueUpdateCaretShape);
        caretPosition = document.trackPosition(0, Bias.FORWARD, FXUtility.mouse(this)::queueUpdateCaretShape);
        
        Nodes.addInputMap(FXUtility.mouse(this), InputMap.<Event>sequence(
            InputMap.<MouseEvent>consume(MouseEvent.ANY, FXUtility.mouse(this)::mouseEvent),
            InputMap.<KeyEvent>consume(KeyEvent.ANY, FXUtility.keyboard(this)::keyboardEvent)
        ));
    }

    @Override
    public @OnThread(value = Tag.FXPlatform) void focusChanged(boolean focused)
    {
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
        super.focusChanged(focused);
        if (!focused)
        {
            horizTranslation = 0;
            vertTranslation = 0;
        }
    }

    protected void mouseEvent(MouseEvent mouseEvent)
    {
        //if (mouseEvent.getEventType() == MouseEvent.MOUSE_RELEASED)
        //    Log.debug("Got mouse event: " + mouseEvent);
        
        if ((mouseEvent.getEventType() == MouseEvent.MOUSE_PRESSED
            //|| (mouseEvent.getEventType() == MouseEvent.MOUSE_CLICKED && mouseEvent.isStillSincePress())
            || mouseEvent.getEventType() == MouseEvent.MOUSE_DRAGGED)
                && mouseEvent.getButton() == MouseButton.PRIMARY)
        {
            //Log.debug("Got mouse event: " + mouseEvent + " " + mouseEvent.isStillSincePress());
            // Position the caret at the clicked position:

            HitInfo hitInfo = hitTest(mouseEvent.getX(), mouseEvent.getY());
            if (hitInfo == null)
                return;
            // Focusing may change content so important to hit-test first:
            requestFocus();
            if (mouseEvent.getEventType() == MouseEvent.MOUSE_PRESSED && mouseEvent.getClickCount() == 2 && mouseEvent.isStillSincePress())
            {
                selectAll();
            }
            else
            {
                // And important to map caret pos after change:
                caretPosition.moveTo(Math.min(document.getLength(), document.mapCaretPos(hitInfo.getInsertionIndex())));
                if (mouseEvent.getEventType() != MouseEvent.MOUSE_DRAGGED && !mouseEvent.isShiftDown())
                    moveAnchorToCaret();
            }
        }
    }

    private void keyboardEvent(KeyEvent keyEvent)
    {
        if (keyEvent.getEventType() == KeyEvent.KEY_TYPED && isEditable())
        {
            if (FXUtility.checkKeyTyped(keyEvent))
            {
                replaceSelection(keyEvent.getCharacter());
                moveAnchorToCaret();
            }
        }
        else if (keyEvent.getEventType() == KeyEvent.KEY_PRESSED)
        {
            if (keyEvent.getCode() == KeyCode.RIGHT)
            {
                if (caretPosition.getPosition() + 1 <= document.getLength())
                    caretPosition.moveBy(1);
                if (!keyEvent.isShiftDown())
                    moveAnchorToCaret();
            }
            if (keyEvent.getCode() == KeyCode.LEFT)
            {
                if (caretPosition.getPosition() - 1 >= 0)
                    caretPosition.moveBy(-1);
                if (!keyEvent.isShiftDown())
                    moveAnchorToCaret();
            }
            if (keyEvent.getCode() == KeyCode.UP)
            {
                Point2D p = textFlow.getClickPosFor(getDisplayCaretPosition(), VPos.TOP, new Dimension2D(-horizTranslation, -vertTranslation)).getFirst();
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
                Point2D p = textFlow.getClickPosFor(getDisplayCaretPosition(), VPos.BOTTOM, new Dimension2D(-horizTranslation, -vertTranslation)).getFirst();
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
            if (keyEvent.getCode() == KeyCode.END)
            {
                caretPosition.moveTo(document.getLength());
                if (!keyEvent.isShiftDown())
                    moveAnchorToCaret();
            }
            
            if (keyEvent.getCode() == KeyCode.A && keyEvent.isShortcutDown())
            {
                anchorPosition.moveTo(0);
                caretPosition.moveTo(document.getLength());
            }

            if (keyEvent.getCode() == KeyCode.X && keyEvent.isShortcutDown())
            {
                cut();
            }
            if (keyEvent.getCode() == KeyCode.C && keyEvent.isShortcutDown())
            {
                copy();
            }
            if (keyEvent.getCode() == KeyCode.V && keyEvent.isShortcutDown())
            {
                paste();
            }

            // Note escape triggers this, but then also a later block too.  Important not to add an else!
            if ((keyEvent.getCode() == KeyCode.Z && keyEvent.isShortcutDown()) || keyEvent.getCode() == KeyCode.ESCAPE)
            {
                String undo = document.getUndo();
                if (undo != null)
                {
                    selectAll();
                    replaceSelection(undo);
                }
            }


            if ((keyEvent.getCode() == KeyCode.BACK_SPACE || keyEvent.getCode() == KeyCode.DELETE) && caretPosition.getPosition() != anchorPosition.getPosition())
            {
                replaceSelection("");
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
                document.defocus(keyEvent.getCode());
            }
        }
    }

    @Override
    @OnThread(Tag.FXPlatform)
    protected void replaceSelection(String character)
    {
        document.replaceText(Math.min(caretPosition.getPosition(), anchorPosition.getPosition()), Math.min(document.getLength(), Math.max(caretPosition.getPosition(), anchorPosition.getPosition())), character);
    }

    @Override
    protected @OnThread(Tag.FXPlatform) String getSelectedText()
    {
        return document.getText().substring(Math.min(getCaretPosition(), getAnchorPosition()), Math.max(getCaretPosition(), getAnchorPosition()));
    }

    private void moveAnchorToCaret()
    {
        anchorPosition.moveTo(caretPosition.getPosition());
        //Log.logStackTrace("Anchor now " + anchorPosition.getPosition());
    }

    public void setDocument(Document document)
    {
        this.idealWidth = null;
        this.unfocusedAlignment = TextAlignment.LEFT;
        textFlow.setTextAlignment(TextAlignment.LEFT);
        this.document.removeListener(this);
        this.document = document;
        this.document.addListener(this);
        caretPosition = this.document.trackPosition(0, Bias.FORWARD, this::queueUpdateCaretShape);
        anchorPosition = this.document.trackPosition(0, Bias.FORWARD, this::queueUpdateCaretShape);
        moveAnchorToCaret();
        documentChanged(this.document);
    }
    
    private void queueUpdateCaretShape()
    {
        if (caretAndSelectionNodes != null)
            caretAndSelectionNodes.queueUpdateCaretShape(false);
    }

    @Override
    @OnThread(Tag.FXPlatform)
    public void documentChanged(Document document)
    {
        textFlow.getChildren().setAll(makeTextNodes(document.getStyledSpans(isEffectivelyFocused())));
        FXUtility.setPseudoclass(this, "has-error", document.hasError());
        requestLayout();
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
    
    @OnThread(Tag.FXPlatform)
    public @CanonicalLocation int getAnchorPosition()
    {
        return anchorPosition.getPosition();
    }
    
    @OnThread(Tag.FXPlatform)
    public @CanonicalLocation int getCaretPosition()
    {
        return caretPosition.getPosition();
    }

    @Override
    @SuppressWarnings("units") // Because display and canonical are the same
    public @OnThread(Tag.FXPlatform) @DisplayLocation int getDisplayCaretPosition()
    {
        return getCaretPosition();
    }

    @Override
    @SuppressWarnings("units") // Because display and canonical are the same
    public @OnThread(Tag.FXPlatform) @DisplayLocation int getDisplayAnchorPosition()
    {
        return getAnchorPosition();
    }

    @Override
    public @OnThread(Tag.FXPlatform) BitSet getErrorCharacters()
    {
        return new BitSet();
    }

    @Override
    public @OnThread(Tag.FXPlatform) ImmutableList<BackgroundInfo> getBackgrounds()
    {
        return ImmutableList.of();
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
        if (idealWidth != null)
            return idealWidth;
        return 300;
    }

    @SuppressWarnings("units")
    public Optional<Point2D> _test_getClickPosFor(int targetPos)
    {
        Pair<Point2D, Boolean> clickPos = textFlow.getClickPosFor(targetPos, VPos.CENTER, new Dimension2D(-horizTranslation, -vertTranslation));
        return clickPos.getSecond() ? Optional.of(clickPos.getFirst()) : Optional.empty();
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
            int index = textFlow.hitTest(new Point2D((float) point2D.getX(), (float) point2D.getY())).getInsertionIndex();
            caretPosition.moveTo(isEffectivelyFocused() ? index : document.mapCaretPos(index));
            moveAnchorToCaret();
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

    public Bounds _test_getCharacterBoundsOnScreen(int charAfter)
    {
        try
        {
            Path path = new Path(textFlow.rangeShape(charAfter, charAfter + 1));
            Bounds actualBounds = localToScreen(FXUtility.offsetBoundsBy(path.getBoundsInLocal(), (float)textFlow.getInsets().getLeft(), 0));
            Rectangle2D clipped = FXUtility.intersectRect(
                    FXUtility.boundsToRect(actualBounds),
                    FXUtility.boundsToRect(localToScreen(getBoundsInLocal())));
            return new BoundingBox(clipped.getMinX(), clipped.getMinY(), clipped.getWidth(), clipped.getHeight());
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    public List<Pair<Set<String>, String>> _test_getStyleSpans()
    {
        return document.getStyledSpans(isEffectivelyFocused()).collect(Collectors.<Pair<Set<String>, String>>toList());
    }

    public List<Pair<Set<String>, String>> _test_getStyleSpans(int from, int to)
    {
        List<Pair<Set<String>, String>> styledSpans = new ArrayList<>(document.getStyledSpans(isEffectivelyFocused()).collect(Collectors.<Pair<Set<String>, String>>toList()));
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
        textFlow.setTextAlignment(focused ? TextAlignment.LEFT : unfocusedAlignment);
        requestLayout();
    }
    
    private void setExpanded(boolean expanded)
    {
        FXUtility.setPseudoclass(this, "expanded", expanded);
        this.expanded = expanded;
        requestLayout();
    }

    public boolean isExpanded()
    {
        return expanded;
    }

    public void selectAll()
    {
        anchorPosition.moveTo(0);
        caretPosition.moveTo(document.getLength());
    }
    
    public void replaceAll(String newContent, boolean save)
    {
        if (save)
            document.setAndSave(newContent);
        else
            document.replaceText(0, document.getLength(), newContent);
    }

    @Override
    protected @OnThread(Tag.FXPlatform) Point2D translateHit(double x, double y)
    {
        return new Point2D(x + horizTranslation, y + vertTranslation);
    }

    @Override
    @OnThread(value = Tag.FXPlatform, ignoreParent = true)
    protected void layoutChildren()
    {
        if (expanded || idealWidth != null)
        {
            textFlow.setPrefWidth(getWidth());
            textFlow.setMaxWidth(getWidth());
        }
        else
        {
            textFlow.setPrefWidth(USE_COMPUTED_SIZE);
            textFlow.setMaxWidth(Double.MAX_VALUE);
        }

        // 10 is fudge factor; if you set exactly its desired width,
        // it can sometimes wrap the text anyway.
        double wholeTextWidth = textFlow.prefWidth(-1);
        double wholeTextHeight = textFlow.prefHeight(getWidth());

        CaretAndSelectionNodes cs = this.caretAndSelectionNodes;
        if (cs != null)
        {
            Path caretShape = cs.caretShape;
            Bounds caretLocalBounds = caretShape.getBoundsInLocal();
            Bounds caretBounds = caretShape.localToParent(caretLocalBounds);

            //Log.debug("Bounds: " + caretBounds + " in " + getWidth() + "x" + getHeight() + " trans " + horizTranslation + "x" + vertTranslation + " whole " + wholeTextWidth + "x" + wholeTextHeight);
            boolean focused = isEffectivelyFocused();
            if (scrollable)
            {
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
        }

        if (expanded)
        {
            textFlow.resizeRelocate(-horizTranslation, -vertTranslation, getWidth(), wholeTextHeight);
        }
        else
        {
            
            if (idealWidth != null)
            {
                // If ideal width is set, size exactly:
                textFlow.resizeRelocate(-horizTranslation, -vertTranslation, Math.max(getWidth(), Math.min(getWidth() + 300, wholeTextWidth)), getHeight());
            }
            else
            {
                // Otherwise, making it bigger than needed to avoid wrapping the text in view.
                // We avoid needlessly making TextFlows which are thousands of pixels
                // wide by restricting to our own width plus some.
                // (We don't use our own width because this causes text wrapping
                // of long words, but we actually want to see those truncated) 
                textFlow.resizeRelocate(-horizTranslation, -vertTranslation, Math.min(getWidth() + 300, wholeTextWidth + 30.0), getHeight());
            }
            
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

    public void setUnfocusedAlignment(TextAlignment textAlignment)
    {
        this.unfocusedAlignment = textAlignment;
        if (!isEffectivelyFocused())
        {
            textFlow.setTextAlignment(textAlignment);
        }
    }

    public void setIdealWidth(double idealWidth)
    {
        this.idealWidth = idealWidth;
    }

    @Override
    public @OnThread(Tag.FXPlatform) double calcWidthToFitContent()
    {
        if (idealWidth != null)
            return idealWidth;
        else
            return super.calcWidthToFitContent();
    }

    public @Nullable StyledString getHoverText()
    {
        if (calcWidthToFitContent() > getWidth() + 3)
            return StyledString.s(document.getText());
        else
            return null;
    }

    @Override
    protected ImmutableList<MenuItem> getAdditionalMenuItems(boolean focused)
    {
        return document.getAdditionalMenuItems(focused);
    }
}
