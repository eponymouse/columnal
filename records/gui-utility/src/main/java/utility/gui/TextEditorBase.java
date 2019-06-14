package utility.gui;

import annotation.units.DisplayLocation;
import com.google.common.collect.ImmutableList;
import com.sun.javafx.scene.text.HitInfo;
import com.sun.javafx.scene.text.TextLayout;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Point2D;
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

    // We only need these when we are focused, and only one field
    // can ever be focused at once.  So these are null while
    // they are unneeded (while we are unfocused)
    protected class CaretAndSelectionNodes
    {
        public final Path caretShape;
        public final Path selectionShape;
        public final ResizableRectangle fadeOverlay;
        public final Path inverter;
        public final Pane selectionPane;
        public final Pane inverterPane;
        private final Pane errorUnderlinePane;
        private final Pane backgroundsPane;

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
            selectionPane.getStyleClass().add("selection-pane");
            selectionPane.setBlendMode(BlendMode.LIGHTEN);
            inverter = new Path();
            inverter.setMouseTransparent(true);
            //inverter.setManaged(false);
            inverter.setFill(Color.WHITE);
            inverter.setStroke(null);
            inverterPane = new Pane(inverter);
            inverterPane.getStyleClass().add("inverter-pane");
            inverterPane.setBlendMode(BlendMode.DIFFERENCE);
            
            errorUnderlinePane = new Pane();
            errorUnderlinePane.setMouseTransparent(true);
            errorUnderlinePane.getStyleClass().add("error-underline-pane");
            backgroundsPane = new Pane();
            backgroundsPane.setMouseTransparent(true);

            selectionShape.visibleProperty().bind(focusedProperty());
            fadeOverlay = new ResizableRectangle();
            fadeOverlay.setMouseTransparent(true);
            fadeOverlay.getStyleClass().add("fade-overlay");

            caretBlink = new Timeline(
                    new KeyFrame(Duration.seconds(0), new KeyValue(caretShape.visibleProperty(), true)),
                    new KeyFrame(Duration.seconds(0.8), e -> Utility.later(this).updateCaretShape(true), new KeyValue(caretShape.visibleProperty(), false)),
                    new KeyFrame(Duration.seconds(1.2), new KeyValue(caretShape.visibleProperty(), true))
            );
            caretBlink.setCycleCount(Animation.INDEFINITE);
            
            FXUtility.addChangeListenerPlatformNNAndCallNow(textFlow.insetsProperty(), insets -> {
                for (Pane pane : ImmutableList.<@NonNull Pane>of(selectionPane, inverterPane, errorUnderlinePane, backgroundsPane))
                {
                    pane.setTranslateX(insets.getLeft());
                    pane.setTranslateY(insets.getTop());
                }
                caretShape.setTranslateX(insets.getLeft());
                caretShape.setTranslateY(insets.getTop());
            });
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
        
        private Path makeBackground(PathElement[] pathElements, ImmutableList<String> styleClasses)
        {
            Path background = new Path(pathElements);
            background.setMouseTransparent(true);
            background.setManaged(false);
            background.getStyleClass().addAll(styleClasses);
            return background;
        }

        public void focusChanged(boolean focused)
        {
            if (focused)
            {
                queueUpdateCaretShape(true);
            }
            else
            {
                caretBlink.stop();
                updateCaretShapeQueued = false;
            }
        }

        public void queueUpdateCaretShape(boolean restartCaretBlink)
        {
            if (restartCaretBlink)
                caretBlink.playFromStart();
            
            if (!updateCaretShapeQueued)
            {
                FXUtility.runAfterNextLayout(() -> updateCaretShape(false));
                requestLayout();
                updateCaretShapeQueued = true;
            }
        }

        private void updateCaretShape(boolean withoutQueue)
        {
            if (!updateCaretShapeQueued && !withoutQueue)
                return; // We may be a stale call just after losing focus
            
            updateCaretShapeQueued = false;
            try
            {
                TextLayout textLayout = textFlow.getInternalTextLayout();
                selectionShape.getElements().setAll(textLayout.getRange(Math.min(getDisplayCaretPosition(), getDisplayAnchorPosition()), Math.max(getDisplayCaretPosition(), getDisplayAnchorPosition()), TextLayout.TYPE_TEXT, 0, 0));
                inverter.getElements().setAll(selectionShape.getElements());
                caretShape.getElements().setAll(textLayout.getCaretShape(getDisplayCaretPosition(), true, 0, 0));
                errorUnderlinePane.getChildren().setAll(makeSpans(getErrorCharacters()).stream().map(r -> makeErrorUnderline(r.start <= getDisplayCaretPosition() && getDisplayCaretPosition() <= r.end, textLayout.getRange(r.start, r.end, TextLayout.TYPE_TEXT, 0, 0))).collect(Collectors.<Path>toList()));
                backgroundsPane.getChildren().setAll(Utility.mapListI(getBackgrounds(), b -> makeBackground(textLayout.getRange(b.startIncl, b.endExcl, TextLayout.TYPE_TEXT, 0, 0), b.styleClasses)));
                if (isFocused())
                    caretBlink.play();
            }
            catch (Exception e)
            {
                // We don't expect any exceptions here...
                Log.log(e);
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
        getStyleClass().add("text-editor");
        ResizableRectangle clip = new ResizableRectangle();
        clip.widthProperty().bind(widthProperty());
        clip.heightProperty().bind(heightProperty());
        setClip(clip);
        textFlow = new HelpfulTextFlow();
        textFlow.getStyleClass().add("document-text-flow");
        textFlow.setMouseTransparent(true);
        textFlow.getChildren().setAll(textNodes);
        // Must construct this after textFlow:
        this.caretAndSelectionNodes = new CaretAndSelectionNodes();
        
        getChildren().setAll(caretAndSelectionNodes.backgroundsPane, caretAndSelectionNodes.errorUnderlinePane, textFlow);

    }

    @Override
    @OnThread(value = Tag.FXPlatform, ignoreParent = true)
    public Orientation getContentBias()
    {
        return Orientation.HORIZONTAL;
    }

    @OnThread(Tag.FXPlatform)
    public abstract @DisplayLocation int getDisplayCaretPosition();

    @OnThread(Tag.FXPlatform)
    protected abstract Point2D translateHit(double x, double y);
    
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
        if (textLayout == null)
            return null;
        else
        {
            Point2D translated = translateHit(x, y);
            return textLayout.getHitInfo((float) translated.getX(), (float) translated.getY());
        }
    }

    @OnThread(Tag.FXPlatform)
    public abstract @DisplayLocation int getDisplayAnchorPosition();

    @OnThread(Tag.FXPlatform)
    public abstract BitSet getErrorCharacters();
    
    public static class BackgroundInfo
    {
        private final int startIncl;
        private final int endExcl;
        private final ImmutableList<String> styleClasses;

        public BackgroundInfo(int startIncl, int endExcl, ImmutableList<String> styleClasses)
        {
            this.startIncl = startIncl;
            this.endExcl = endExcl;
            this.styleClasses = styleClasses;
        }
    }
    
    @OnThread(Tag.FXPlatform)
    public abstract ImmutableList<BackgroundInfo> getBackgrounds();
    
    @Override
    @OnThread(value = Tag.FXPlatform, ignoreParent = true)
    protected abstract void layoutChildren();

    @OnThread(value = Tag.FXPlatform)
    public void focusChanged(boolean focused)
    {
        CaretAndSelectionNodes cs = caretAndSelectionNodes;
        if (focused)
        {
            getChildren().setAll(cs.backgroundsPane, cs.errorUnderlinePane, textFlow, cs.inverterPane, cs.selectionPane, cs.caretShape, cs.fadeOverlay);
            cs.focusChanged(true);
        }
        else
        {
            getChildren().setAll(cs.backgroundsPane, cs.errorUnderlinePane, textFlow);
        }
    }

    @OnThread(Tag.FXPlatform)
    public double calcWidthToFitContent()
    {
        return textFlow.prefWidth(-1);
    }
}
