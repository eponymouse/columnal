package records.transformations.expression;

import records.gui.expressioneditor.Consecutive;
import records.gui.expressioneditor.Consecutive.ConsecutiveStartContent;
import records.gui.expressioneditor.ConsecutiveBase;
import records.gui.expressioneditor.ExpressionNodeParent;
import records.gui.expressioneditor.OperandNode;
import records.gui.expressioneditor.OperatorEntry;
import styled.StyledShowable;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
import utility.Utility;

import java.util.List;

public interface LoadableExpression<EXPRESSION extends LoadableExpression<EXPRESSION, SEMANTIC_PARENT>, SEMANTIC_PARENT> extends StyledShowable
{
    public abstract Pair<List<SingleLoader<EXPRESSION, SEMANTIC_PARENT, OperandNode<EXPRESSION, SEMANTIC_PARENT>>>, List<SingleLoader<EXPRESSION, SEMANTIC_PARENT, OperatorEntry<EXPRESSION, SEMANTIC_PARENT>>>> loadAsConsecutive(boolean implicitlyRoundBracketed);
    
    public abstract SingleLoader<EXPRESSION, SEMANTIC_PARENT, OperandNode<EXPRESSION, SEMANTIC_PARENT>> loadAsSingle();

    public static interface SingleLoader<EXPRESSION extends LoadableExpression<EXPRESSION, SEMANTIC_PARENT> & StyledShowable, SEMANTIC_PARENT, R>
    {
        @OnThread(Tag.FXPlatform)
        public R load(ConsecutiveBase<EXPRESSION, SEMANTIC_PARENT> parent, SEMANTIC_PARENT semanticParent);

        @OnThread(Tag.FXPlatform)
        public static <EXPRESSION extends LoadableExpression<EXPRESSION, SEMANTIC_PARENT>, SEMANTIC_PARENT> Consecutive.ConsecutiveStartContent<EXPRESSION, SEMANTIC_PARENT>
            withSemanticParent(Pair<List<SingleLoader<EXPRESSION, SEMANTIC_PARENT, OperandNode<EXPRESSION, SEMANTIC_PARENT>>>, List<SingleLoader<EXPRESSION, SEMANTIC_PARENT, OperatorEntry<EXPRESSION, SEMANTIC_PARENT>>>> operandsAndOps, SEMANTIC_PARENT semanticParent)
        {
            return new ConsecutiveStartContent<>(
                Utility.mapList(operandsAndOps.getFirst(), x -> c -> x.load(c, semanticParent)),
                Utility.mapList(operandsAndOps.getSecond(), x -> c -> x.load(c, semanticParent))
            );
        }
    }
}
