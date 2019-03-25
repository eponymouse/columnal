package records.gui.guidance;

import javafx.animation.Animation;
import javafx.animation.FillTransition;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.animation.Transition;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Insets;
import javafx.geometry.Rectangle2D;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.TextFlow;
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
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformRunnable;
import utility.FXPlatformSupplier;
import utility.Pair;
import utility.Utility;
import utility.gui.FXUtility;
import utility.gui.GUI;
import utility.gui.ResizableRectangle;
import utility.gui.TranslationUtility;

import java.util.ArrayList;
import java.util.Iterator;

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
        cancel = GUI.button(ButtonType.CANCEL.getText(), this::hide);
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
        private final @Nullable String lookupToHighlight;
        private final Condition conditionToAdvance;
        private final FXPlatformSupplier<@Nullable Guidance> next;
        private final @Nullable Button extraButton;
        private final ArrayList<Stage> highlightWindows = new ArrayList<>();
        
        public Guidance(StyledString content, Condition conditionToAdvance, @Nullable String lookupToHighlight, FXPlatformSupplier<@Nullable Guidance> next)
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
                if (runOnClick.get() != null)
                {
                    runOnClick.get().run();
                }
            });
            this.next = next;
        }

        public void setHighlight(boolean on)
        {
            // Always remove previous highlight if any:
            for (Stage highlightWindow : highlightWindows)
            {
                highlightWindow.hide();
            }
            highlightWindows.clear();
            
            if (on && lookupToHighlight != null)
            {
                Node node = findNode(lookupToHighlight);
                if (node != null)
                {
                    Rectangle2D screenBounds = FXUtility.boundsToRect(node.localToScreen(node.getBoundsInLocal()));
                    for (int i = 0; i < 4; i++)
                    {
                        Stage highlight = new Stage(StageStyle.TRANSPARENT);
                        highlight.setAlwaysOnTop(true);
                        StackPane stackPane = new StackPane();
                        stackPane.getStyleClass().add("guidance-highlight");
                        Scene scene = new Scene(stackPane);
                        scene.getStylesheets().add(FXUtility.getStylesheet("mainview.css"));
                        highlight.setScene(scene);
                        highlightWindows.add(highlight);
                    }
                    
                    final double thick = 3;
                    final double margin = 2;

                    // Top
                    highlightWindows.get(0).setX(screenBounds.getMinX() - thick - margin);
                    highlightWindows.get(0).setY(screenBounds.getMinY() - thick - margin);
                    highlightWindows.get(0).setWidth(screenBounds.getWidth() + 2 * margin + 2 * thick);
                    highlightWindows.get(0).setHeight(thick);

                    // Bottom
                    highlightWindows.get(1).setX(screenBounds.getMinX() - thick - margin);
                    highlightWindows.get(1).setY(screenBounds.getMaxY() + margin);
                    highlightWindows.get(1).setWidth(screenBounds.getWidth() + 2 * margin + 2 * thick);
                    highlightWindows.get(1).setHeight(thick);

                    // Left
                    highlightWindows.get(2).setX(screenBounds.getMinX() - thick - margin);
                    highlightWindows.get(2).setY(screenBounds.getMinY() - margin);
                    highlightWindows.get(2).setWidth(thick);
                    highlightWindows.get(2).setHeight(screenBounds.getHeight() + 2 * margin);
                    
                    // Right
                    highlightWindows.get(3).setX(screenBounds.getMaxX() + margin);
                    highlightWindows.get(3).setY(screenBounds.getMinY() - margin);
                    highlightWindows.get(3).setWidth(thick);
                    highlightWindows.get(3).setHeight(screenBounds.getHeight() + 2 * margin);

                    for (Stage highlightWindow : highlightWindows)
                    {
                        highlightWindow.show();
                    }
                }
            }
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
        private @MonotonicNonNull Timeline timeline;

        public LookupCondition(String nodeLookup)
        {
            this.nodeLookup = nodeLookup;
        }

        @Override
        public void onSatisfied(FXPlatformRunnable runAfter)
        {
            timeline = new Timeline(new KeyFrame(Duration.millis(500), e -> {
                if (findNode(nodeLookup) != null)
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
        Node foundNode = null;
        @SuppressWarnings("deprecation")
        Iterator<Window> it = Window.impl_getWindows();
        while (it.hasNext())
        {
            Window window = it.next();
            foundNode = window.getScene().lookup(nodeLookup);
            if (foundNode != null)
            {
                break;
            }
        }
        return foundNode;
    }

    private static @Nullable Window findWindow(Class<? extends Window> windowClass)
    {
        @SuppressWarnings("deprecation")
        Iterator<Window> it = Window.impl_getWindows();
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
