package utility.gui;

import javafx.css.PseudoClass;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Circle;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import javafx.scene.shape.Shape;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformRunnable;

/**
 * A cross shape, like the close on a web browser tab header
 */
@OnThread(Tag.FX)
public class SmallDeleteButton extends StackPane
{
    // Composed of two shapes: a cross, with a circle underneath
    private final Shape circle;
    private final Shape cross;

    @SuppressWarnings("initialization")
    public SmallDeleteButton()
    {
        circle = new Circle(8);
        cross = new Path(
            new MoveTo(0, 0),
            new LineTo(8, 8),
            new MoveTo(8, 0),
            new LineTo(0, 8));
        getStyleClass().add("small-delete");
        circle.getStyleClass().add("small-delete-circle");
        cross.getStyleClass().add("small-delete-cross");
        circle.setMouseTransparent(true);
        cross.setMouseTransparent(true);
        getChildren().addAll(circle, cross);
    }

    public void setOnAction(FXPlatformRunnable onAction)
    {
        setOnMouseClicked(e -> onAction.run());
    }
}
