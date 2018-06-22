package records.transformations.expression.type;

import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import records.gui.expressioneditor.ConsecutiveChild;
import records.gui.expressioneditor.ExpressionSaver.BracketAndNodes;
import records.gui.expressioneditor.TypeEntry.Keyword;
import records.transformations.expression.BracketedStatus;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.FXPlatformConsumer;

import java.util.ArrayList;
import java.util.Stack;
import java.util.function.Function;

@OnThread(Tag.FXPlatform)
public class TypeSaver
{
    public class Context {}

    // Ends a square/round-bracketed expression:
    private static interface Terminator
    {
        // Pass BracketedStatus and last item of span:
        public void terminate(Function<BracketAndNodes<TypeExpression, TypeSaver>, @Recorded TypeExpression> makeContent, Keyword terminator, ConsecutiveChild<TypeExpression, TypeSaver> keywordErrorDisplayer, FXPlatformConsumer<Context> keywordContext);
    }

    private static class Scope
    {
        // If Right, it's a comma (our only operator)
        public final ArrayList<Either<@Recorded TypeExpression, ConsecutiveChild<TypeExpression, TypeSaver>>> items;
        public final Terminator terminator;
        public final ConsecutiveChild<TypeExpression, TypeSaver> openingNode;

        public Scope(ConsecutiveChild<TypeExpression, TypeSaver> openingNode, Terminator terminator)
        {
            this.items = new ArrayList<>();
            this.terminator = terminator;
            this.openingNode = openingNode;
        }
    }

    private final Stack<Scope> currentScopes = new Stack<>();
    
    public void saveKeyword(Keyword keyword, ConsecutiveChild<TypeExpression, TypeSaver> errorDisplayer, FXPlatformConsumer<Context> withContext)
    {
        
    }
    
    public void saveOperand(TypeExpression operand, ConsecutiveChild<TypeExpression, TypeSaver> errorDisplayer, FXPlatformConsumer<Context> withContext)
    {
        
    }

    public @Recorded TypeExpression finish(ConsecutiveChild<TypeExpression, TypeSaver> errorDisplayer)
    {
        while (currentScopes.size() > 1)
        {
            // TODO give some sort of error.... somewhere?  On the opening item?
            Scope closed = currentScopes.pop();
            currentScopes.peek().items.add(Either.left(makeInvalidOp(closed.openingNode, errorDisplayer, closed.items.stream().map(e -> e.map(x -> x.op)).map(Either::swap).collect(ImmutableList.toImmutableList()))));
        }

        Scope closed = currentScopes.pop();
        BracketAndNodes<TypeExpression, TypeSaver> brackets = new BracketAndNodes<>(BracketedStatus.TOP_LEVEL, closed.openingNode, errorDisplayer);
        return makeExpression(closed.openingNode, errorDisplayer, closed.items, brackets);
    }
}
