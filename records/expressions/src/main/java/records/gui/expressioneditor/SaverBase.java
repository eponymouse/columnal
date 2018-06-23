package records.gui.expressioneditor;

import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.initialization.qual.Initialized;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.NonNull;
import records.gui.expressioneditor.ExpressionSaver.Context;
import records.gui.expressioneditor.GeneralExpressionEntry.Keyword;
import records.gui.expressioneditor.GeneralExpressionEntry.Op;
import records.transformations.expression.BracketedStatus;
import records.transformations.expression.Expression;
import records.transformations.expression.InvalidOperatorExpression;
import styled.StyledShowable;
import styled.StyledString;
import utility.Either;
import utility.FXPlatformConsumer;
import utility.Utility;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.function.Function;

/**
 * 
 * @param <EXPRESSION> The actual expression items in the tree
 * @param <SAVER> A saver (the subclass of this class) for the expressions
 * @param <OP> Operators that go between operands in an expression
 * @param <KEYWORD> Keywords that can alter scope levels, either beginning or ending scopes
 * @param <CONTEXT> Context for reporting back to the expressions.
 */
public abstract class SaverBase<EXPRESSION extends StyledShowable, SAVER, OP, KEYWORD, CONTEXT>
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
    
    protected SaverBase(ConsecutiveBase<EXPRESSION, SAVER> parent)
    {
        addTopLevelScope(parent);
    }
    
    public void saveOperator(OP operator, ConsecutiveChild<EXPRESSION, SAVER> errorDisplayer, FXPlatformConsumer<CONTEXT> withContext)
    {
        currentScopes.peek().items.add(Either.right(new OpAndNode(operator, errorDisplayer)));
    }

    protected abstract @Recorded EXPRESSION makeInvalidOp(ConsecutiveChild<EXPRESSION, SAVER> start, ConsecutiveChild<EXPRESSION, SAVER> end, ImmutableList<Either<OP, @Recorded EXPRESSION>> items);

    public void addTopLevelScope(@UnknownInitialization(SaverBase.class) SaverBase<EXPRESSION, SAVER, OP, KEYWORD, CONTEXT> this, ConsecutiveBase<EXPRESSION, SAVER> parent)
    {
        @SuppressWarnings("nullness") // Pending fix for Checker Framework #2052
        final @NonNull Stack<Scope> currentScopesFinal = this.currentScopes;
        currentScopesFinal.push(new Scope(parent.getAllChildren().get(0), new Terminator()
        {
            @Override
            public void terminate(Function<BracketAndNodes<EXPRESSION, SAVER>, @Recorded EXPRESSION> makeContent, KEYWORD terminator, ConsecutiveChild<EXPRESSION, SAVER> keywordErrorDisplayer, FXPlatformConsumer<CONTEXT> keywordContext)
            {
                ConsecutiveChild<EXPRESSION, SAVER> start = parent.getAllChildren().get(0);
                ConsecutiveChild<EXPRESSION, SAVER> end = keywordErrorDisplayer;
                end.addErrorAndFixes(StyledString.s("Closing " + terminator + " without opening"), ImmutableList.of());
                @Initialized SaverBase<EXPRESSION, SAVER, OP, KEYWORD, CONTEXT> thisSaver = Utility.later(SaverBase.this);
                currentScopesFinal.peek().items.add(Either.left(thisSaver.record(start, end, thisSaver.keywordToInvalid(terminator))));
            }
        }));
    }

    protected abstract EXPRESSION record(ConsecutiveChild<EXPRESSION, SAVER> start, ConsecutiveChild<EXPRESSION, SAVER> end, EXPRESSION expression);

    protected abstract EXPRESSION keywordToInvalid(KEYWORD keyword);

    protected abstract @Recorded EXPRESSION makeExpression(ConsecutiveChild<EXPRESSION, SAVER> start, ConsecutiveChild<EXPRESSION, SAVER> end, List<Either<@Recorded EXPRESSION, OpAndNode>> content, BracketAndNodes<EXPRESSION, SAVER> brackets);
    
    public @Recorded EXPRESSION finish(ConsecutiveChild<EXPRESSION, SAVER> errorDisplayer)
    {
        while (currentScopes.size() > 1)
        {
            // TODO give some sort of error.... somewhere?  On the opening item?
            Scope closed = currentScopes.pop();
            currentScopes.peek().items.add(Either.left(makeInvalidOp(closed.openingNode, errorDisplayer, closed.items.stream().map(e -> e.map(x -> x.op)).map(Either::swap).collect(ImmutableList.toImmutableList()))));
        }

        Scope closed = currentScopes.pop();
        BracketAndNodes<EXPRESSION, SAVER> brackets = new BracketAndNodes<>(BracketedStatus.TOP_LEVEL, closed.openingNode, errorDisplayer);
        return makeExpression(closed.openingNode, errorDisplayer, closed.items, brackets);
    }
}
