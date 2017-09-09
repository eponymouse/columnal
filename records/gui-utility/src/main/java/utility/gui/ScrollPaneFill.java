package utility.gui;

import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.Nullable;
import threadchecker.OnThread;
import threadchecker.Tag;

/**
 * A ScrollPane which expands its content to fill the width and height of the scroll pane,
 * if the scroll pane is larger than the contained content.
 *
 * Note that setting fitWidthProperty() alone does not do this: that property constrains the
 * width of the item to always fit the width.  This class allows a horizontal scroll
 * if the content is too big, but expands when the content is too small.  (Same for fitHeightProperty())
 *
 * See https://reportmill.wordpress.com/2014/06/03/make-scrollpane-content-fill-viewport-bounds/
 * for origin of trick.
 */
public class ScrollPaneFill extends ScrollPane
{
    public ScrollPaneFill()
    {
        getStyleClass().add("scroll-pane-fill");
        FXUtility.addChangeListenerPlatform(viewportBoundsProperty(), b -> fillWidth(b));
    }

    @OnThread(Tag.FXPlatform)
    private void fillWidth(@UnknownInitialization(ScrollPane.class) ScrollPaneFill this, @Nullable Bounds viewportBounds)
    {
        if (viewportBounds != null)
        {
            Node content = getContent();
            setFitToWidth(content.prefWidth(-1) < viewportBounds.getWidth());
            setFitToHeight(content.prefHeight(-1) < viewportBounds.getHeight());
        }
    }

    @OnThread(Tag.FXPlatform)
    public void fillWidth()
    {
        fillWidth(getViewportBounds());
    }
}
