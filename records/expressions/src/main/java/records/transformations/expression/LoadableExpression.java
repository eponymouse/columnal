package records.transformations.expression;

import com.google.common.collect.ImmutableList;
import records.gui.expressioneditor.ConsecutiveBase;
import records.gui.expressioneditor.ConsecutiveBase.BracketedStatus;
import records.gui.expressioneditor.EntryNode;
import styled.StyledShowable;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.util.stream.Stream;

public interface LoadableExpression<EXPRESSION extends StyledShowable, SEMANTIC_PARENT> extends StyledShowable
{
    public abstract Stream<SingleLoader<EXPRESSION, SEMANTIC_PARENT>> loadAsConsecutive(BracketedStatus bracketedStatus);
    
    @FunctionalInterface
    public static interface SingleLoader<EXPRESSION extends StyledShowable, SEMANTIC_PARENT>
    {
        @OnThread(Tag.FXPlatform)
        public EntryNode<EXPRESSION, SEMANTIC_PARENT> load(ConsecutiveBase<EXPRESSION, SEMANTIC_PARENT> parent, SEMANTIC_PARENT semanticParent);
    }
}
