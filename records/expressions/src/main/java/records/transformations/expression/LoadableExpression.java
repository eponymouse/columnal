package records.transformations.expression;

import records.gui.expressioneditor.ConsecutiveBase;
import records.gui.expressioneditor.ConsecutiveChild;
import styled.StyledShowable;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.util.stream.Stream;

public interface LoadableExpression<EXPRESSION extends StyledShowable, SEMANTIC_PARENT> extends StyledShowable
{
    @OnThread(Tag.FXPlatform)
    public abstract Stream<SingleLoader<EXPRESSION, SEMANTIC_PARENT>> loadAsConsecutive(BracketedStatus bracketedStatus);
    
    @FunctionalInterface
    public static interface SingleLoader<EXPRESSION extends StyledShowable, SEMANTIC_PARENT>
    {
        @OnThread(Tag.FXPlatform)
        public ConsecutiveChild<EXPRESSION, SEMANTIC_PARENT> load(ConsecutiveBase<EXPRESSION, SEMANTIC_PARENT> parent);
        
        public default SingleLoader<EXPRESSION, SEMANTIC_PARENT> focusWhenShown()
        {
            return p -> {
                ConsecutiveChild<EXPRESSION, SEMANTIC_PARENT> loaded = load(p);
                loaded.focusWhenShown();
                return loaded;
            };
        }
    }
}
