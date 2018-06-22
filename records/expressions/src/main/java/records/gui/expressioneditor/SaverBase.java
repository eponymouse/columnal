package records.gui.expressioneditor;

import annotation.recorded.qual.Recorded;
import records.gui.expressioneditor.ExpressionSaver.Context;
import records.gui.expressioneditor.GeneralExpressionEntry.Op;
import records.transformations.expression.BracketedStatus;
import records.transformations.expression.Expression;
import styled.StyledShowable;
import utility.Either;
import utility.FXPlatformConsumer;

import java.util.ArrayList;
import java.util.Stack;
import java.util.function.Function;

public class SaverBase<EXPRESSION extends StyledShowable, SAVER, OP, KEYWORD, CONTEXT>
{
    // Ends a mini-expression
    protected abstract class Terminator
    {
        // Pass BracketedStatus and last item of span:
        public abstract void terminate(Function<BracketAndNodes<EXPRESSION, SAVER>, @Recorded EXPRESSION> makeContent, KEYWORD terminator, ConsecutiveChild<EXPRESSION, SAVER> keywordErrorDisplayer, FXPlatformConsumer<CONTEXT> keywordContext);
    }
    
    // Op is typically an enum so we can't identity-hash-map it to a node, hence this wrapper
    protected class OpAndNode
    {
        public final OP op;
        public final ConsecutiveChild<EXPRESSION, SAVER> sourceNode;

        public OpAndNode(OP op, ConsecutiveChild<EXPRESSION, SAVER> sourceNode)
        {
            this.op = op;
            this.sourceNode = sourceNode;
        }
    }

    protected class Scope
    {
        public final ArrayList<Either<@Recorded EXPRESSION, OpAndNode>> items;
        public final Terminator terminator;
        public final ConsecutiveChild<EXPRESSION, SAVER> openingNode;

        public Scope(ConsecutiveChild<EXPRESSION, SAVER> openingNode, Terminator terminator)
        {
            this.items = new ArrayList<>();
            this.terminator = terminator;
            this.openingNode = openingNode;
        }
    }

    public static class BracketAndNodes<EXPRESSION extends StyledShowable, SAVER>
    {
        public final BracketedStatus bracketedStatus;
        public final ConsecutiveChild<EXPRESSION, SAVER> start;
        public final ConsecutiveChild<EXPRESSION, SAVER> end;

        public BracketAndNodes(BracketedStatus bracketedStatus, ConsecutiveChild<EXPRESSION, SAVER> start, ConsecutiveChild<EXPRESSION, SAVER> end)
        {
            this.bracketedStatus = bracketedStatus;
            this.start = start;
            this.end = end;
        }
    }
        
    public BracketAndNodes<EXPRESSION, SAVER> miscBrackets(ConsecutiveChild<EXPRESSION, SAVER> start, ConsecutiveChild<EXPRESSION, SAVER> end)
    {
        return new BracketAndNodes<>(BracketedStatus.MISC, start, end);
    }

    public Function<ConsecutiveChild<EXPRESSION, SAVER>, BracketAndNodes<EXPRESSION, SAVER>> miscBrackets(ConsecutiveChild<EXPRESSION, SAVER> start)
    {
        return end -> new BracketAndNodes<>(BracketedStatus.MISC, start, end);
    }

    protected final Stack<Scope> currentScopes = new Stack<>();
    protected final ErrorDisplayerRecord errorDisplayerRecord = new ErrorDisplayerRecord();
    
    public void saveOperator(OP operator, ConsecutiveChild<EXPRESSION, SAVER> errorDisplayer, FXPlatformConsumer<CONTEXT> withContext)
    {
        currentScopes.peek().items.add(Either.right(new OpAndNode(operator, errorDisplayer)));
    }
}
