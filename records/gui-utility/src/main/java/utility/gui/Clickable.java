package utility.gui;

import javafx.geometry.Point2D;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.text.Text;
import org.checkerframework.checker.i18n.qual.LocalizableKey;
import org.checkerframework.checker.nullness.qual.Nullable;
import styled.StyledString.Style;
import threadchecker.OnThread;
import threadchecker.Tag;
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

    @Override
    @OnThread(Tag.FXPlatform)
    protected void style(Text t)
    {
        t.getStyleClass().add("styled-text-clickable");
        t.getStyleClass().addAll(extraStyleClasses);
        t.setOnMouseClicked(e -> {
            setHovering(false, new Point2D(e.getScreenX(), e.getScreenY()));
            onClick(e.getButton(), new Point2D(e.getScreenX(), e.getScreenY()));
        });
        t.setOnMouseEntered(e -> setHovering(true, new Point2D(e.getScreenX(), e.getScreenY())));
        t.setOnMouseMoved(e -> setHovering(true, new Point2D(e.getScreenX(), e.getScreenY())));
        t.setOnMouseExited(e -> setHovering(false, new Point2D(e.getScreenX(), e.getScreenY())));
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
