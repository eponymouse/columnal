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

package xyz.columnal.utility.gui;

import annotation.units.DisplayLocation;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Point2D;
import javafx.scene.Scene;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.effect.BlendMode;
import javafx.scene.input.Clipboard;
import javafx.scene.input.DataFormat;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.shape.Path;
import javafx.scene.shape.PathElement;
import javafx.scene.text.HitInfo;
import javafx.scene.text.Text;
import javafx.util.Duration;
import xyz.columnal.log.Log;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.Utility;
import xyz.columnal.utility.Utility.IndexRange;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Base helper class for text editors
 */
@OnThread(value = Tag.FXPlatform, ignoreParent = true)
public abstract class TextEditorBase extends Region
{
    protected final HelpfulTextFlow textFlow;
    private final ContextMenu contextMenu = new ContextMenu();
    // Can't use contextMenu.isShowing() because it may return false at the point
    // we lose focus to the menu:
    // This is true if showing menu and was focused when requested:
    private boolean showingContextMenuWhileFocused;

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
                Scene scene = getScene();
                if (scene != null)
                    FXUtility.runAfterNextLayout(scene, () -> updateCaretShape(false));
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
                selectionShape.getElements().setAll(textFlow.rangeShape(Math.min(getDisplayCaretPosition(), getDisplayAnchorPosition()), Math.max(getDisplayCaretPosition(), getDisplayAnchorPosition())));
                inverter.getElements().setAll(selectionShape.getElements());
                caretShape.getElements().setAll(textFlow.caretShape(getDisplayCaretPosition(), true));
                errorUnderlinePane.getChildren().setAll(makeSpans(getErrorCharacters()).stream().map(r -> makeErrorUnderline(r.start <= getDisplayCaretPosition() && getDisplayCaretPosition() <= r.end, textFlow.rangeShape(r.start, r.end))).collect(Collectors.<Path>toList()));
                backgroundsPane.getChildren().setAll(Utility.mapListI(getBackgrounds(), b -> makeBackground(textFlow.rangeShape(b.startIncl, b.endExcl), b.styleClasses)));
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
        
        // Don't understand why calling getChildren() directly doesn't satisfy the checker
        Utility.later(this).
            getChildren().setAll(caretAndSelectionNodes.backgroundsPane, caretAndSelectionNodes.errorUnderlinePane, textFlow);

        
        setOnContextMenuRequested(e -> {
            if (!contextMenu.isShowing())
            {
                showingContextMenuWhileFocused = isFocused();
                FXUtility.mouse(this).prepareContextMenu(isFocused());
                FXUtility.mouse(this).focusChanged(isEffectivelyFocused());
                contextMenu.show(textFlow, e.getScreenX(), e.getScreenY());
            }
        });
        contextMenu.setOnHidden(e -> {
            showingContextMenuWhileFocused = false;
            FXUtility.mouse(this).focusChanged(isEffectivelyFocused());
        });
        FXUtility.addChangeListenerPlatformNN(focusedProperty(), focused -> {
            FXUtility.mouse(this).focusChanged(isEffectivelyFocused());
        });
    }
    
    private void prepareContextMenu(boolean focused)
    {
        MenuItem cutItem = GUI.menuItem("cut", FXUtility.mouse(this)::cut);
        MenuItem copyItem = GUI.menuItem("copy", FXUtility.mouse(this)::copy);
        MenuItem pasteItem = GUI.menuItem("paste", FXUtility.mouse(this)::paste);
        boolean hasSelection = !FXUtility.mouse(this).getSelectedText().isEmpty();
        String clipContent = Clipboard.getSystemClipboard().getString();
        boolean hasPaste = clipContent != null && !clipContent.isEmpty();
        cutItem.setDisable(!(hasSelection || !focused));
        copyItem.setDisable(!(hasSelection || !focused));
        pasteItem.setDisable(!hasPaste);
        contextMenu.getItems().setAll(
                cutItem,
                copyItem,
                pasteItem
        );
        contextMenu.getItems().addAll(getAdditionalMenuItems(focused));
    }

    protected abstract ImmutableList<MenuItem> getAdditionalMenuItems(boolean focused);

    @OnThread(Tag.FXPlatform)
    protected boolean isEffectivelyFocused(@UnknownInitialization(Region.class) TextEditorBase this)
    {
        return isFocused() || showingContextMenuWhileFocused;
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
        Point2D translated = translateHit(x, y);
        return textFlow.hitTest(new Point2D((float) translated.getX(), (float) translated.getY()));
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

    @OnThread(Tag.FXPlatform)
    protected abstract String getSelectedText();
    
    @OnThread(Tag.FXPlatform)
    protected abstract void replaceSelection(String replacement);

    @OnThread(Tag.FXPlatform)
    protected void cut()
    {
        copy();
        replaceSelection("");
    }

    @OnThread(Tag.FXPlatform)
    protected void copy()
    {
        String selection = getSelectedText();
        if (!selection.isEmpty())
        {
            Clipboard.getSystemClipboard().setContent(ImmutableMap.of(DataFormat.PLAIN_TEXT, selection));
        }
    }

    @OnThread(Tag.FXPlatform)
    protected void paste()
    {
        String content = Clipboard.getSystemClipboard().getString();
        if (content != null && !content.isEmpty())
        {
            replaceSelection(content);
        }
    }
}
