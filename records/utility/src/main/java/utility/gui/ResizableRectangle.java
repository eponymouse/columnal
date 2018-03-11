package utility.gui;

import javafx.scene.shape.Rectangle;

/**
 * A Rectangle which can be resized by its parent layout.
 */
public class ResizableRectangle extends Rectangle
{
    @Override
    public void resize(double width, double height)
    {
        setWidth(width);
        setHeight(height);
    }

    @Override
    public boolean isResizable()
    {
        return true;
    }
}
