package utility.gui;

import com.sun.javafx.scene.text.HitInfo;
import com.sun.javafx.scene.text.TextLayout;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.scene.effect.BlendMode;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.shape.Path;
import javafx.scene.shape.PathElement;
import javafx.scene.text.Text;
import javafx.util.Duration;
import log.Log;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Utility;
import utility.Utility.IndexRange;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Base helper class for text editors
 */
public abstract class TextEditorBase extends Region
{
    protected final HelpfulTextFlow textFlow;
    // Always positive, the amount of offset from the left edge back
    // to the real start of the content.
    protected double horizTranslation;
    // Always positive, the amount of offset from the top edge up
    // to the real start of the content.
    protected double vertTranslation;
    protected boolean expanded;
    protected boolean scrollable;

    // We only need these when we are focused, and only one field
    // can ever be focused at once.  So these are null while
    // they are unneeded (while we are unfocused)
    protected class CaretAndSelectionNodes
    {
        private final Path caretShape;
        private final Path selectionShape;
        private final ResizableRectangle fadeOverlay;
        private final Path inverter;
        private final Pane selectionPane;
        private final Pane inverterPane;
        private final Pane errorUnderlinePane;

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
            inverterPane = new Pane(inverter);
            inverterPane.setBackground(new Background(new BackgroundFill(Color.BLACK, CornerRadii.EMPTY, Insets.EMPTY)));
            inverterPane.setBlendMode(BlendMode.DIFFERENCE);
            
            errorUnderlinePane = new Pane();
            errorUnderlinePane.setMouseTransparent(true);

            selectionShape.visibleProperty().bind(focusedProperty());
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
        
        private Path makeErrorUnderline(boolean containsCaret, PathElement[] pathElements)
        {
            Path errorUnderline = new Path(pathElements);
            errorUnderline.setMouseTransparent(true);
            errorUnderline.setManaged(false);
            errorUnderline.getStyleClass().add("error-underline");
            FXUtility.setPseudoclass(errorUnderline, "contains-caret", containsCaret);
            return errorUnderline;
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

        public void queueUpdateCaretShape()
        {
            if (!updateCaretShapeQueued)
            {
                FXUtility.runAfterNextLayout(CaretAndSelectionNodes.this::updateCaretShape);
                requestLayout();
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
                TextLayout textLayout = textFlow.getInternalTextLayout();
                selectionShape.getElements().setAll(textLayout.getRange(Math.min(getDisplayCaretPosition(), getDisplayAnchorPosition()), Math.max(getDisplayCaretPosition(), getDisplayAnchorPosition()), TextLayout.TYPE_TEXT, 0, 0));
                inverter.getElements().setAll(selectionShape.getElements());
                caretShape.getElements().setAll(textLayout.getCaretShape(getDisplayCaretPosition(), true, 0, 0));
                errorUnderlinePane.getChildren().setAll(makeSpans(getErrorCharacters()).stream().map(r -> makeErrorUnderline(r.start <= getDisplayCaretPosition() && getDisplayCaretPosition() <= r.end, textLayout.getRange(r.start, r.end, TextLayout.TYPE_TEXT, 0, 0))).collect(Collectors.<Path>toList()));
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
            requestLayout();
        }

        private List<IndexRange> makeSpans(BitSet errorCharacters)
        {
            ArrayList<IndexRange> r = new ArrayList<>();
            int spanStart = errorCharacters.nextSetBit(0);
            while (spanStart != -1)
            {
                int spanEnd = errorCharacters.nextClearBit(spanStart);
                r.add(new IndexRange(spanStart, spanEnd));
                spanStart = errorCharacters.nextSetBit(spanEnd);
            }
            return r;
        }
    }
    protected final CaretAndSelectionNodes caretAndSelectionNodes;
    
    public TextEditorBase(List<Text> textNodes)
    {
        this.caretAndSelectionNodes = new CaretAndSelectionNodes();
        getStyleClass().add("text-editor");
        ResizableRectangle clip = new ResizableRectangle();
        clip.widthProperty().bind(widthProperty());
        clip.heightProperty().bind(heightProperty());
        setClip(clip);
        textFlow = new HelpfulTextFlow();
        textFlow.getStyleClass().add("document-text-flow");
        textFlow.setMouseTransparent(true);
        textFlow.getChildren().setAll(textNodes);
        
        getChildren().setAll(textFlow, caretAndSelectionNodes.errorUnderlinePane);

    }
    
    @OnThread(Tag.FXPlatform)
    public abstract int getDisplayCaretPosition();

    @OnThread(Tag.FXPlatform)
    protected @Nullable HitInfo hitTest(double x, double y)
    {
        TextLayout textLayout;
        try
        {
            textLayout = textFlow.getInternalTextLayout();
        }
        catch (Exception e)
        {
            Log.log(e);
            textLayout = null;
        }
        return textLayout == null ? null: textLayout.getHitInfo((float)(x + horizTranslation), (float)(y + vertTranslation));
    }

    @OnThread(Tag.FXPlatform)
    public abstract int getDisplayAnchorPosition();

    @OnThread(Tag.FXPlatform)
    public abstract BitSet getErrorCharacters();

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

        // 10 is fudge factor; if you set exactly its desired width,
        // it can sometimes wrap the text anyway.
        double wholeTextWidth = textFlow.prefWidth(-1) + 10;
        double wholeTextHeight = textFlow.prefHeight(getWidth());

        CaretAndSelectionNodes cs = this.caretAndSelectionNodes;
        if (cs != null)
        {
            Path caretShape = cs.caretShape;
            Bounds caretLocalBounds = caretShape.getBoundsInLocal();
            Bounds caretBounds = caretShape.localToParent(caretLocalBounds);

            //Log.debug("Bounds: " + caretBounds + " in " + getWidth() + "x" + getHeight() + " trans " + horizTranslation + "x" + vertTranslation + " whole " + wholeTextWidth + "x" + wholeTextHeight);
            boolean focused = isFocused();
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

    @OnThread(value = Tag.FXPlatform)
    public void focusChanged(boolean focused)
    {
        CaretAndSelectionNodes cs = caretAndSelectionNodes;
        if (focused)
        {
            getChildren().setAll(textFlow, cs.inverterPane, cs.selectionPane, cs.errorUnderlinePane, cs.caretShape, cs.fadeOverlay);
            cs.focusChanged(true);
        }
        else
        {
            horizTranslation = 0;
            vertTranslation = 0;
            getChildren().setAll(textFlow, cs.errorUnderlinePane);
        }
    }
}
