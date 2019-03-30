package utility.gui;

import com.sun.javafx.scene.text.TextLayout;
import javafx.geometry.Bounds;
import javafx.geometry.Dimension2D;
import javafx.geometry.Point2D;
import javafx.geometry.VPos;
import javafx.scene.shape.Path;
import javafx.scene.text.TextFlow;
import org.checkerframework.checker.nullness.qual.NonNull;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

@OnThread(Tag.FXPlatform)
public class HelpfulTextFlow extends TextFlow
{
    @OnThread(Tag.FXPlatform)
    public TextLayout getInternalTextLayout() throws NoSuchMethodException, IllegalAccessException, InvocationTargetException
    {
        // TODO stop using reflection in Java 9, just call the methods directly
        Method method = TextFlow.class.getDeclaredMethod("getTextLayout");
        method.setAccessible(true);
        @SuppressWarnings("nullness")
        @NonNull TextLayout textLayout = (TextLayout) method.invoke(this);
        return textLayout;
    }

    /**
     * Gets the click position of the target caret position, in local coordinates.
     *
     * @param targetPos The target caret pos (like a character index)
     * @param vPos The vertical position within the caret: top of it, middle of it, bottom of it?
     * @return The click position in local coordinates, plus a boolean indicating whether or not it is in bounds.
     */
    @OnThread(Tag.FXPlatform)
    public Pair<Point2D, Boolean> getClickPosFor(int targetPos, VPos vPos, Dimension2D translateBy)
    {
        try
        {
            TextLayout textLayout = getInternalTextLayout();
            Bounds bounds = new Path(textLayout.getCaretShape(targetPos, true, 1.0f + (float)translateBy.getWidth(), (float)translateBy.getHeight())).getBoundsInLocal();
            Point2D p;
            switch (vPos)
            {
                case TOP:
                    p = new Point2D((bounds.getMinX() + bounds.getMaxX()) / 2.0, bounds.getMinY());
                    break;
                case BOTTOM:
                    p = new Point2D((bounds.getMinX() + bounds.getMaxX()) / 2.0, bounds.getMaxY());
                    break;
                case CENTER:
                default:
                    p = FXUtility.getCentre(bounds);
                    break;
            }
            return new Pair<>(p, getBoundsInLocal().contains(p));
        }
        catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException | NullPointerException e)
        {
            throw new RuntimeException(e);
        }
    }

}
