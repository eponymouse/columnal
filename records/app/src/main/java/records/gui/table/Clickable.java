package records.gui.table;

import javafx.geometry.Point2D;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.text.Text;
import org.checkerframework.checker.i18n.qual.LocalizableKey;
import styled.StyledString.Style;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.gui.TranslationUtility;

abstract class Clickable extends Style<Clickable>
{
    private final @LocalizableKey String tooltipKey;
    private final String[] extraStyleClasses;

    public Clickable()
    {
        this("click.to.change");
    }
    
    public Clickable(@LocalizableKey String tooltipKey, String... styleClasses)
    {
        super(Clickable.class);
        this.tooltipKey = tooltipKey;
        this.extraStyleClasses = styleClasses;
    }

    @OnThread(Tag.FXPlatform)
    protected abstract void onClick(MouseButton mouseButton, Point2D screenPoint);

    @Override
    @OnThread(Tag.FXPlatform)
    protected void style(Text t)
    {
        t.getStyleClass().add("styled-text-clickable");
        t.getStyleClass().addAll(extraStyleClasses);
        t.setOnMouseClicked(e -> {
            onClick(e.getButton(), new Point2D(e.getScreenX(), e.getScreenY()));
        });
        Tooltip tooltip = new Tooltip(TranslationUtility.getString(tooltipKey));
        Tooltip.install(t, tooltip);
    }

    @Override
    protected Clickable combine(Clickable with)
    {
        // Cannot combine, so make arbitrary choice:
        return this;
    }

    @Override
    protected boolean equalsStyle(Clickable item)
    {
        return false;
    }
}
