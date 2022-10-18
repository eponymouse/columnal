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

package xyz.columnal.gui.guidance;

import javafx.animation.Animation;
import javafx.animation.FillTransition;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.TextFlow;
import javafx.stage.Popup;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import javafx.util.Duration;
import org.checkerframework.checker.i18n.qual.LocalizableKey;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.EnsuresNonNull;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;
import xyz.columnal.styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.function.fx.FXPlatformRunnable;
import xyz.columnal.utility.function.fx.FXPlatformSupplier;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.Utility;
import xyz.columnal.utility.gui.FXUtility;
import xyz.columnal.utility.gui.GUI;
import xyz.columnal.utility.gui.ResizableRectangle;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@OnThread(Tag.FXPlatform)
public final class GuidanceWindow extends Stage
{
    private final TextFlow contentDisplay;
    private final ButtonBar buttonBar;
    private Guidance curGuidance;
    private final Button cancel;
    private final Animation pulse;

    public GuidanceWindow(Guidance initialGuidance, Window viewWindow)
    {
        super(StageStyle.UNDECORATED);
        this.contentDisplay = new TextFlow();
        contentDisplay.setMouseTransparent(true);
        buttonBar = new ButtonBar();
        BorderPane.setMargin(buttonBar, new Insets(15, 0, 0, 0));
        BorderPane content = GUI.borderTopCenterBottom(null, contentDisplay, buttonBar, "guidance-root");
        FXUtility.enableWindowMove(this, content);
        content.setPrefWidth(300.0);
        StackPane rootStack = new StackPane(content);
        Scene scene = new Scene(rootStack);
        scene.getStylesheets().addAll(FXUtility.getSceneStylesheets("mainview.css"));
        setScene(scene);
        cancel = GUI.button("close", this::hide);
        ButtonBar.setButtonData(cancel, ButtonData.CANCEL_CLOSE);
        buttonBar.getButtons().add(cancel);
        setAlwaysOnTop(true);
        setGuidance(initialGuidance);
        setX(viewWindow.getX() + viewWindow.getWidth() - 350.0);
        setY(viewWindow.getY() + 50.0);

        ResizableRectangle backgroundRect = new ResizableRectangle() {
            @Override
            public double maxHeight(double width)
            {
                return Double.MAX_VALUE;
            }

            @Override
            public double maxWidth(double height)
            {
                return Double.MAX_VALUE;
            }
        };
        rootStack.getChildren().add(0, backgroundRect);
        pulse = new FillTransition(Duration.millis(1200), backgroundRect, Color.gray(0.9), Color.WHITE);
        pulse.setAutoReverse(true);
        pulse.setCycleCount(Animation.INDEFINITE);
        pulse.play();

        setOnHiding(e -> {
            curGuidance.setHighlight(false);
            pulse.stop();
        });
    }
    
    @EnsuresNonNull("curGuidance")
    @RequiresNonNull({"contentDisplay", "buttonBar", "cancel"})
    private void setGuidance(@UnknownInitialization(Object.class) GuidanceWindow this, Guidance guidance)
    {
        if (curGuidance != null)
        {
            curGuidance.setHighlight(false);
            buttonBar.getButtons().setAll(cancel);
            if (pulse != null)
                pulse.stop();
        }
        
        guidance.setHighlight(true);
        contentDisplay.getChildren().setAll(guidance.content.toGUI());
        curGuidance = guidance;
        
        if (guidance.extraButton != null)
        {
            ButtonBar.setButtonData(guidance.extraButton, ButtonData.NEXT_FORWARD);
            buttonBar.getButtons().setAll(cancel, guidance.extraButton);
        }

        guidance.conditionToAdvance.onSatisfied(() -> {
            Guidance nextGuidance = guidance.next.get();
            if (nextGuidance != null)
                setGuidance(nextGuidance);
            else
                Utility.later(GuidanceWindow.this).hide();
        });
    }
    
    @OnThread(Tag.FXPlatform)
    public static class Guidance
    {
        private final StyledString content;
        private final @Nullable TargetFinder lookupToHighlight;
        private final Condition conditionToAdvance;
        private final FXPlatformSupplier<@Nullable Guidance> next;
        private final @Nullable Button extraButton;
        private final ArrayList<Popup> highlightWindows = new ArrayList<>();
        
        public Guidance(StyledString content, Condition conditionToAdvance, @Nullable String lookupToHighlight, FXPlatformSupplier<@Nullable Guidance> next)
        {
            this.content = content;
            this.conditionToAdvance = conditionToAdvance;
            this.lookupToHighlight = lookupToHighlight == null ? null : nodeFinder(lookupToHighlight);
            this.extraButton = null;
            this.next = next;
        }

        public Guidance(StyledString content, Condition conditionToAdvance, TargetFinder lookupToHighlight, FXPlatformSupplier<@Nullable Guidance> next)
        {
            this.content = content;
            this.conditionToAdvance = conditionToAdvance;
            this.lookupToHighlight = lookupToHighlight;
            this.extraButton = null;
            this.next = next;
        }
        
        public Guidance(StyledString content, @LocalizableKey String buttonKey, FXPlatformSupplier<@Nullable Guidance> next)
        {
            this.content = content;
            this.lookupToHighlight = null;
            SimpleObjectProperty<@Nullable FXPlatformRunnable> runOnClick = new SimpleObjectProperty<>(); 
            this.conditionToAdvance = onAdvance -> {
                runOnClick.set(onAdvance);
            };
            this.extraButton = GUI.button(buttonKey, () -> {
                FXPlatformRunnable onClick = runOnClick.get();
                if (onClick != null)
                {
                    onClick.run();
                }
            });
            this.next = next;
        }

        public void setHighlight(boolean on)
        {
            // Always remove previous highlight if any:
            for (Popup highlightWindow : highlightWindows)
            {
                highlightWindow.hide();
            }
            highlightWindows.clear();
            
            if (on && lookupToHighlight != null)
            {
                @Nullable Pair<Window, Bounds> lookupBounds = lookupToHighlight.findSceneAndScreenBounds();
                
                if (lookupBounds != null)
                {
                    Rectangle2D screenBounds = FXUtility.boundsToRect(lookupBounds.getSecond());
                    List<Rectangle> rectangles = new ArrayList<>();
                    for (int i = 0; i < 4; i++)
                    {
                        Popup highlight = new Popup();
                        Rectangle r = new ResizableRectangle();
                        highlight.getContent().setAll(r);
                        r.setFill(Color.BLUE);
                        rectangles.add(r);
                        highlightWindows.add(highlight);
                    }
                    
                    final double thick = 3;
                    final double margin = 2;
                    
                    // Sizing window doesn't work for Popup so we must size rectangle instead

                    // Top
                    highlightWindows.get(0).setX(screenBounds.getMinX() - thick - margin);
                    highlightWindows.get(0).setY(screenBounds.getMinY() - thick - margin);
                    rectangles.get(0).setWidth(screenBounds.getWidth() + 2 * margin + 2 * thick);
                    rectangles.get(0).setHeight(thick);

                    // Bottom
                    highlightWindows.get(1).setX(screenBounds.getMinX() - thick - margin);
                    highlightWindows.get(1).setY(screenBounds.getMaxY() + margin);
                    rectangles.get(1).setWidth(screenBounds.getWidth() + 2 * margin + 2 * thick);
                    rectangles.get(1).setHeight(thick);

                    // Left
                    highlightWindows.get(2).setX(screenBounds.getMinX() - thick - margin);
                    highlightWindows.get(2).setY(screenBounds.getMinY() - margin);
                    rectangles.get(2).setWidth(thick);
                    rectangles.get(2).setHeight(screenBounds.getHeight() + 2 * margin);
                    
                    // Right
                    highlightWindows.get(3).setX(screenBounds.getMaxX() + margin);
                    highlightWindows.get(3).setY(screenBounds.getMinY() - margin);
                    rectangles.get(3).setWidth(thick);
                    rectangles.get(3).setHeight(screenBounds.getHeight() + 2 * margin);

                    for (Popup highlightWindow : highlightWindows)
                    {
                        highlightWindow.show(lookupBounds.getFirst());
                    }
                }
            }
        }

        public static interface TargetFinder
        {
            public @Nullable Pair<Window, Bounds> findSceneAndScreenBounds();
        }
    }
    
    public static interface Condition
    {
        @OnThread(Tag.FXPlatform)
        public void onSatisfied(FXPlatformRunnable runOnceSatsified);
    }
    
    @OnThread(Tag.FXPlatform)
    public static class WindowCondition implements Condition
    {
        private final Class<? extends Window> windowClass;
        private @MonotonicNonNull Timeline timeline;

        public WindowCondition(Class<? extends Window> windowClass)
        {
            this.windowClass = windowClass;
        }
        
        @Override
        public void onSatisfied(FXPlatformRunnable runAfter)
        {    
            timeline = new Timeline(new KeyFrame(Duration.millis(500), e -> {
                if (findWindow(windowClass) != null)
                {
                    if (timeline != null)
                        timeline.stop();
                    FXUtility.runAfter(runAfter);
                }
            }));
            timeline.setCycleCount(Animation.INDEFINITE);
            timeline.play();
            
        }
    }
    
    @OnThread(Tag.FXPlatform)
    public static class LookupKeyCondition extends LookupCondition
    {
        public LookupKeyCondition(@LocalizableKey String nodeLookupKey)
        {
            super("." + GUI.makeId(nodeLookupKey));
        }
    }

    @OnThread(Tag.FXPlatform)
    public static class LookupCondition implements Condition
    {
        private final String nodeLookup;
        private final boolean targetShow;
        private @MonotonicNonNull Timeline timeline;

        public LookupCondition(String nodeLookup, boolean showing)
        {
            this.nodeLookup = nodeLookup;
            this.targetShow = showing;
        }
        
        public LookupCondition(String nodeLookup)
        {
            this(nodeLookup, true);
        }

        @Override
        public void onSatisfied(FXPlatformRunnable runAfter)
        {
            timeline = new Timeline(new KeyFrame(Duration.millis(300), e -> {
                boolean showing = findNode(nodeLookup) != null;
                if (showing == targetShow)
                {
                    if (timeline != null)
                        timeline.stop();
                    FXUtility.runAfter(runAfter);
                }
            }));
            timeline.setCycleCount(Animation.INDEFINITE);
            timeline.play();

        }
    }

    private static @Nullable Node findNode(String nodeLookup)
    {
        Iterator<Window> it = Window.getWindows().iterator();
        while (it.hasNext())
        {
            Window window = it.next();
            Scene scene = window.getScene();
            if (scene != null)
            {
                Node foundNode = scene.lookup(nodeLookup);
                if (foundNode != null)
                {
                    return foundNode;
                }
            }
        }
        return null;
    }
    
    private static GuidanceWindow.Guidance.TargetFinder nodeFinder(String nodeLookup)
    {
        return () -> {
            Node node = findNode(nodeLookup);
            if (node == null || node.getScene() == null || node.getScene().getWindow() == null)
                return null;
            else
                return new Pair<>(node.getScene().getWindow(), node.localToScreen(node.getBoundsInLocal()));
        };
    }

    private static @Nullable Window findWindow(Class<? extends Window> windowClass)
    {
        Iterator<Window> it = Window.getWindows().iterator();
        while (it.hasNext())
        {
            Window window = it.next();
            if (windowClass.isInstance(window))
            {
                return window;
            }
        }
        return null;
    }
}
