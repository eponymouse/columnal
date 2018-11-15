package records.transformations.expression;

import records.gui.expressioneditor.ClipboardSaver;
import records.gui.expressioneditor.ConsecutiveBase;
import records.gui.expressioneditor.ConsecutiveChild;
import styled.StyledShowable;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.util.stream.Stream;

public interface LoadableExpression<EXPRESSION extends StyledShowable, SAVER extends ClipboardSaver> extends StyledShowable
{
    @OnThread(Tag.FXPlatform)
    public abstract Stream<SingleLoader<EXPRESSION, SAVER>> loadAsConsecutive(BracketedStatus bracketedStatus);
    
    @FunctionalInterface
    public static interface SingleLoader<EXPRESSION extends StyledShowable, SAVER extends ClipboardSaver>
    {
        @OnThread(Tag.FXPlatform)
        public ConsecutiveChild<EXPRESSION, SAVER> load(ConsecutiveBase<EXPRESSION, SAVER> parent);
        
        public default SingleLoader<EXPRESSION, SAVER> focusWhenShown()
        {
            return p -> {
                ConsecutiveChild<EXPRESSION, SAVER> loaded = load(p);
                loaded.focusWhenShown();
                return loaded;
            };
        }
    }
}
