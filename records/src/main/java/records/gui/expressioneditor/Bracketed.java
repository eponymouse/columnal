package records.gui.expressioneditor;

import javafx.beans.value.ObservableObjectValue;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.Nullable;
import utility.FXPlatformFunction;
import utility.Pair;
import utility.gui.FXUtility;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Created by neil on 20/12/2016.
 */
public class Bracketed extends Consecutive implements OperandNode
{
    public Bracketed(List<FXPlatformFunction<ConsecutiveBase, OperandNode>> initial, ConsecutiveBase parent, @Nullable Node prefixNode, @Nullable Node suffixNode)
    {
        super(parent, prefixNode, suffixNode, "bracket");
    }

    @Override
    protected void initializeContent(@UnknownInitialization(ConsecutiveBase.class) Bracketed this)
    {
    }

    @Override
    public Bracketed focusWhenShown()
    {
        super.focusWhenShown();
        return this;
    }

    @Override
    public @Nullable ObservableObjectValue<@Nullable String> getStyleWhenInner()
    {
        return null;
    }

    @Override
    public ConsecutiveBase getParent()
    {
        // Safe cast, given that our constructor requires ConsecutiveBase:
        return (ConsecutiveBase)parent;
    }

    @Override
    public void setSelected(boolean selected)
    {
        // Not sure how to style this, yet
    }

    @Override
    public void setHoverDropLeft(boolean on)
    {
        FXUtility.setPseudoclass(nodes().get(0), "exp-hover-drop-left", on);
    }

    @Override
    public Bracketed prompt(String prompt)
    {
        super.prompt(prompt);
        return this;
    }

    @Override
    public Pair<ConsecutiveChild, Double> findClosestDrop(Point2D loc)
    {
        return Stream.of(new Pair<ConsecutiveChild, Double>(this, FXUtility.distanceToLeft(nodes().get(0), loc)), super.findClosestDrop(loc)).min(Comparator.comparing(Pair::getSecond)).get();
    }
}
