package utility.gui;

import javafx.geometry.Point2D;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.text.Text;
import javafx.util.Duration;
import org.checkerframework.checker.i18n.qual.LocalizableKey;
import org.checkerframework.checker.nullness.qual.Nullable;
import styled.StyledString.Style;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformRunnable;
import utility.FXPlatformSupplier;
import utility.TranslationUtility;

@OnThread(Tag.FXPlatform)
public abstract class Clickable extends Style<Clickable>
{
    private final @Nullable @LocalizableKey String tooltipKey;
    private final String[] extraStyleClasses;

    public Clickable()
    {
        this("click.to.change");
    }
    
    public Clickable(@Nullable @LocalizableKey String tooltipKey, String... styleClasses)
    {
        super(Clickable.class);
        this.tooltipKey = tooltipKey;
        this.extraStyleClasses = styleClasses;
    }

    @OnThread(Tag.FXPlatform)
    protected abstract void onClick(MouseButton mouseButton, Point2D screenPoint);
    
    protected void setHovering(boolean hovering, Point2D screenPos)
    {
    }
    
    private class HoverTracker
    {
        private boolean hovering = false;
        private @Nullable FXPlatformRunnable cancelHover = null;
        
        public void start(MouseEvent e)
        {
            stop(e);
            cancelHover = FXUtility.runAfterDelay(Duration.millis(300), () -> {
                if (!hovering)
                {
                    hovering = true;
                    cancelHover = null;
                    setHovering(true, new Point2D(e.getScreenX(), e.getScreenY()));
                }
            });
        }
        
        public void stop(MouseEvent e)
        {
            hovering = false;
            if (cancelHover != null)
            {
                cancelHover.run();
                cancelHover = null;
            }
            setHovering(false, new Point2D(e.getScreenX(), e.getScreenY()));
        }
    }

    @Override
    @OnThread(Tag.FXPlatform)
    protected void style(Text t)
    {
        t.getStyleClass().add("styled-text-clickable");
        t.getStyleClass().addAll(extraStyleClasses);
        HoverTracker hoverTracker = new HoverTracker();
        t.setOnMouseClicked(e -> {
            hoverTracker.stop(e);
            setHovering(false, new Point2D(e.getScreenX(), e.getScreenY()));
            onClick(e.getButton(), new Point2D(e.getScreenX(), e.getScreenY()));
        });
        
        t.setOnMouseEntered(e -> hoverTracker.start(e));
        t.setOnMouseMoved(e -> hoverTracker.start(e));
        t.setOnMouseExited(e -> hoverTracker.stop(e));
        if (tooltipKey != null)
        {
            Tooltip tooltip = new Tooltip(TranslationUtility.getString(tooltipKey));
            Tooltip.install(t, tooltip);
        }
    }

    @Override
    @OnThread(Tag.Any)
    protected Clickable combine(Clickable with)
    {
        // Cannot combine, so make arbitrary choice:
        return this;
    }
    
    @Override
    @OnThread(Tag.Any)
    protected boolean equalsStyle(Clickable item)
    {
        return false;
    }
}
