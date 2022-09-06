package xyz.columnal.utility.gui;

import javafx.scene.shape.Rectangle;
import threadchecker.OnThread;
import threadchecker.Tag;

/**
 * A Rectangle which can be resized by its parent layout.
 */
@OnThread(Tag.FX)
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
