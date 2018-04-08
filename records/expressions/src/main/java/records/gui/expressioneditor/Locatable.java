package records.gui.expressioneditor;

import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import org.checkerframework.checker.nullness.qual.Nullable;
import styled.StyledShowable;
import utility.Pair;

public interface Locatable
{
    public void visitLocatable(LocatableVisitor visitor);
    
    // This can be implemented to return closest insert-before position, or item-at-target
    public interface LocatableVisitor
    {
        public <C extends StyledShowable> void register(ConsecutiveChild<? extends C, ?> graphicalItem, Class<C> childType);
        
        // TODO add another method for clause nodes to call
    }
}
