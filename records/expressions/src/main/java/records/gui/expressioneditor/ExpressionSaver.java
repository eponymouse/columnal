package records.gui.expressioneditor;

import annotation.recorded.qual.Recorded;
import annotation.recorded.qual.UnknownIfRecorded;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.gui.expressioneditor.GeneralExpressionEntry.Keyword;
import records.gui.expressioneditor.GeneralExpressionEntry.Op;
import records.gui.expressioneditor.OperandOps.NaryOperatorSection;
import records.gui.expressioneditor.OperandOps.OperatorExpressionInfo;
import records.gui.expressioneditor.OperandOps.OperatorSection;
import records.transformations.expression.ArrayExpression;
import records.transformations.expression.BracketedStatus;
import records.transformations.expression.ErrorAndTypeRecorder;
import records.transformations.expression.Expression;
import records.transformations.expression.IdentExpression;
import records.transformations.expression.IfThenElseExpression;
import records.transformations.expression.InvalidOperatorExpression;
import records.transformations.expression.LoadableExpression;
import records.transformations.expression.MatchAnythingExpression;
import records.transformations.expression.QuickFix;
import records.transformations.expression.QuickFix.ReplacementTarget;
import records.transformations.expression.TupleExpression;
import styled.StyledString;
import utility.Either;
import utility.FXPlatformConsumer;
import utility.Pair;
import utility.Utility;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Stack;
import java.util.function.Function;
import java.util.function.Supplier;

public abstract class ExpressionSaver implements ErrorAndTypeRecorder
{
    class Context {}
    
    // Ends a mini-expression
    private static interface Terminator
    {
        // content may be an invalidopsexpression.  Note that calling makeContent pops the scope.
        public void terminate(Supplier<Expression> makeContent, Keyword terminator, ErrorDisplayer<Expression, ExpressionSaver> keywordErrorDisplayer, FXPlatformConsumer<Context> keywordContext);
    }
    
    public class WithInfo<T>
    {
        private final ErrorDisplayer<Expression, ExpressionSaver> errorDisplayer;
        private final FXPlatformConsumer<Context> withContext;
        private final T item;

        public WithInfo(T item, ErrorDisplayer<Expression, ExpressionSaver> errorDisplayer, FXPlatformConsumer<Context> withContext)
        {
            this.errorDisplayer = errorDisplayer;
            this.withContext = withContext;
            this.item = item;
        }
    }
    
    private final Stack<Pair<ArrayList<Either<Expression, Op>>, Terminator>> currentScopes = new Stack<>();
    
    public ExpressionSaver()
    {
        currentScopes.push(new Pair<>(new ArrayList<>(), (makeContent, terminator, keywordErrorDisplayer, keywordContext) -> {
            keywordErrorDisplayer.addErrorAndFixes(StyledString.s("Closing " + terminator + " without opening"), ImmutableList.of());
            currentScopes.peek().getFirst().add(Either.left(new InvalidOperatorExpression(ImmutableList.of(Either.left(terminator.getContent())))));
        }));
    }
    
    public Expression finish()
    {
        if (currentScopes.size() > 1)
        {
            // TODO give some sort of error.... somewhere?  On the opening item?
            return new InvalidOperatorExpression(ImmutableList.of(Either.right(new IdentExpression("TODO unterminated"))));
        }
        else
        {
            return makeExpression(currentScopes.pop().getFirst());
        }
    }
    
    // Note: if we are copying to clipboard, callback will not be called
    public void saveKeyword(Keyword keyword, ErrorDisplayer<Expression, ExpressionSaver> errorDisplayer, FXPlatformConsumer<Context> withContext)
    {
        if (keyword == Keyword.ANYTHING)
        {
            saveOperand(new MatchAnythingExpression(), errorDisplayer, withContext);
        }
        else if (keyword == Keyword.OPEN_ROUND)
        {
            // TODO support function calls here as special case
            // TODO support commas (list of expressions to expect?
            currentScopes.push(new Pair<>(new ArrayList<>(), expect(Keyword.CLOSE_ROUND, BracketedStatus.DIRECT_ROUND_BRACKETED, Either::left)));
        }
        else if (keyword == Keyword.OPEN_SQUARE)
        {
            // TODO support commas (list of expressions to expect?
            currentScopes.push(new Pair<>(new ArrayList<>(), expect(Keyword.CLOSE_SQUARE, BracketedStatus.DIRECT_SQUARE_BRACKETED, Either::left)));
        }
        else if (keyword == Keyword.IF)
        {
            currentScopes.push(new Pair<>(new ArrayList<>(), expect(Keyword.THEN, BracketedStatus.MISC, condition ->
                Either.right(expect(Keyword.ELSE, BracketedStatus.MISC, thenPart -> 
                    Either.right(expect(Keyword.ENDIF, BracketedStatus.MISC, elsePart -> {
                        return Either.left(new IfThenElseExpression(condition, thenPart, elsePart));
                    })
                )    
            )))));
        }
        else
        {
            // Should be a terminator:
            Pair<ArrayList<Either<Expression, Op>>, Terminator> cur = currentScopes.peek();
            cur.getSecond().terminate(() -> makeExpression(cur.getFirst()), keyword, errorDisplayer, withContext);
        }
    }

    // Returns a list of comma separated expressions
    private Expression makeExpression(List<Either<Expression, Op>> content)
    {
        if (content.isEmpty())
            return new InvalidOperatorExpression(ImmutableList.of());
        
        // Although it's duplication, we keep a list for if it turns out invalid, and two lists for if it is valid:
        boolean[] valid = new boolean[] {true};
        final ArrayList<Either<String, Expression>> invalid = new ArrayList<>();
        final ArrayList<Expression> validOperands = new ArrayList<>();
        final ArrayList<Op> validOperators = new ArrayList<>();

        boolean lastWasExpression = false; // Think of it as an invisible empty prefix operator
        
        for (Either<Expression, Op> item : content)
        {
            item.either_(expression -> {
                invalid.add(Either.right(expression));
                validOperands.add(expression);
                
                if (lastWasExpression)
                {
                    // TODO missing operator error
                    valid[0] = false;    
                }
            }, op -> {
                invalid.add(Either.left(op.getContent()));
                validOperators.add(op);
                
                if (!lastWasExpression)
                {
                    // TODO missing operand error
                    valid[0] = false;
                }
            });
        }
        
        if (valid[0])
        {
            // Single expression?
            if (validOperands.size() == 1 && validOperators.size() == 0)
                return validOperands.get(0);
                        
            // Now we need to check the operators can work together as one group:
            
            //return makeExpressionWithOperators(null, null, this, validOperands, validOperators, BracketedStatus.MISC);
            
        }
        
        return new InvalidOperatorExpression(ImmutableList.copyOf(invalid));
    }

    // Expects a keyword matching closer.  If so, call the function with the current scope's expression, and you'll get back a final expression or a
    // terminator for a new scope, compiled using the scope content and given bracketed status
    public Terminator expect(Keyword expected, BracketedStatus bracketedStatus, Function<Expression, Either<Expression, Terminator>> onClose)
    {
        return (makeContent, terminator, keywordErrorDisplayer, keywordContext) -> {
            if (terminator == expected)
            {
                // All is well:
                Either<Expression, Terminator> result = onClose.apply(makeContent.get());
                result.either_(e -> currentScopes.peek().getFirst().add(Either.left(e)), t -> currentScopes.push(new Pair<>(new ArrayList<>(), t)));
            }
            else
            {
                // Error!
                keywordErrorDisplayer.addErrorAndFixes(StyledString.s("Expected " + expected + " but found " + terminator), ImmutableList.of());
                // Important to call makeContent before adding to scope on the next line:
                ImmutableList.Builder<Either<String, Expression>> items = ImmutableList.builder();
                items.add(Either.right(makeContent.get()));
                items.add(Either.left(terminator.getContent()));
                InvalidOperatorExpression invalid = new InvalidOperatorExpression(items.build());
                currentScopes.peek().getFirst().add(Either.left(invalid));
            }
        };
    }
    
    
    public void saveOperator(Op operator, ErrorDisplayer<Expression, ExpressionSaver> errorDisplayer, FXPlatformConsumer<Context> withContext)
    {
        currentScopes.peek().getFirst().add(Either.right(operator));
    }
    public void saveOperand(Expression singleItem, ErrorDisplayer<Expression, ExpressionSaver> errorDisplayer, FXPlatformConsumer<Context> withContext)
    {
        currentScopes.peek().getFirst().add(Either.left(singleItem));
    }
    
    
    /**
     * Get likely types and completions for given child.  For example,
     * if the expression is column Name = _ (where the RHS
     * is the child in question) we might offer Text and most frequent values
     * of the Name column.
     *
     * The completions are not meant to be all possible values of the given
     * type (e.g. literals, available columns, etc), as that can be figured out
     * from the type regardless of context.  This is only items which make
     * particular sense in this particular context, e.g. a commonly passed argument
     * to a function.
     */
    //List<Pair<DataType, List<String>>> getSuggestedContext(EEDisplayNode child) throws InternalException, UserException;

    /**
     * Gets all special keywords available in child operators,
     * e.g. "then", paired with their description.
     */
    //default ImmutableList<Pair<String, @Localized String>> operatorKeywords()
    //{
    //    return ImmutableList.of();
    //}

    /**
     * Can this direct child node declare a variable?  i.e. is it part of a pattern?
     */
    //boolean canDeclareVariable(EEDisplayNode chid);


    /**
     * If all operators are from the same {@link records.gui.expressioneditor.OperandOps.OperatorExpressionInfo}, returns a normal expression with those operators.
     * Otherwise, it returns an invalid operator expression (as specified by the lambda), AND if feasible, suggests
     * likely quick fixes based on the suggested priority ordering given by the list parameter's ordering.
     *
     * @param candidates The ordering of the outer list indicates likely bracketing priority, with items earlier
     *                   in the list more likely to be bracketed (thus earlier means binds tighter).  So for example,
     *                   plus will come earlier in the list than equals, because given "a + b = c", we're more likely
     *                   to want to bracket "(a + b) = c" than "a + (b = c)".
     */
    /*
    static <EXPRESSION extends LoadableExpression<EXPRESSION, SEMANTIC_PARENT>, SEMANTIC_PARENT> @Nullable EXPRESSION makeExpressionWithOperators(OperandOps<EXPRESSION, SEMANTIC_PARENT> operandOps, ImmutableList<ImmutableList<OperatorExpressionInfo<EXPRESSION, SEMANTIC_PARENT>>> candidates, ErrorAndTypeRecorder errorAndTypeRecorder, ImmutableList<@Recorded EXPRESSION> expressionExps, List<String> ops, BracketedStatus bracketedStatus)
    {
        if (ops.size() != expressionExps.size() - 1)
        {
            // Other issues: we're missing an argument!
            return null;
        }
        
        if (ops.isEmpty())
        {
            // TODO should we make array expression here if square bracketed?
            return expressionExps.get(0);
        }
        
        // First, split it into sections based on cohesive parts that have the same operators:
        List<OperatorSection<EXPRESSION, SEMANTIC_PARENT>> operatorSections = new ArrayList<>();
        nextOp: for (int i = 0; i < ops.size(); i++)
        {
            if (operatorSections.isEmpty() || !operatorSections.get(operatorSections.size() - 1).addOperator(ops.get(i), i))
            {
                // Make new section:
                for (int candidateIndex = 0; candidateIndex < candidates.size(); candidateIndex++)
                {
                    for (OperatorExpressionInfo<EXPRESSION, SEMANTIC_PARENT> operatorExpressionInfo : candidates.get(candidateIndex))
                    {
                        if (operatorExpressionInfo.includes(ops.get(i)))
                        {
                            operatorSections.add(operatorExpressionInfo.makeOperatorSection(candidateIndex, ops.get(i), i));
                            continue nextOp; 
                        }
                    }
                }
                // If we get here, it's an unrecognised operator, so return an invalid expression:
                return null;
            }
        }
        
        if (operatorSections.size() == 1)
        {
            // All operators are coherent with each other, can just return single expression:
            @Nullable EXPRESSION single = operatorSections.get(0).makeExpression(expressionExps, bracketedStatus);
            if (single != null)
                return single;
            
            // Maybe with the possibility of different brackets?
            if (bracketedStatus == BracketedStatus.MISC)
            {
                List<LoadableExpression<EXPRESSION, SEMANTIC_PARENT>> possibles = new ArrayList<>();
                for (BracketedStatus status : Arrays.asList(BracketedStatus.DIRECT_ROUND_BRACKETED, BracketedStatus.DIRECT_SQUARE_BRACKETED))
                {
                    @Nullable LoadableExpression<EXPRESSION, SEMANTIC_PARENT> possible = operatorSections.get(0).makeExpression(expressionExps, status);
                    if (possible != null)
                        possibles.add(possible);
                }
                if (!possibles.isEmpty())
                {
                    EXPRESSION invalidOpExpression = operandOps.makeInvalidOpExpression(expressionExps, ops);
                    errorAndTypeRecorder.recordError(invalidOpExpression, StyledString.s("Surrounding brackets required"));
                    errorAndTypeRecorder.recordQuickFixes(invalidOpExpression, Utility.mapList(possibles, e -> new QuickFix<>("fix.bracketAs", ReplacementTarget.CURRENT, e)));
                    return invalidOpExpression;
                }
            }
        }

        EXPRESSION invalidOpExpression = operandOps.makeInvalidOpExpression(expressionExps, ops);
        errorAndTypeRecorder.recordError(invalidOpExpression, StyledString.s("Mixed operators: brackets required"));
        
        if (operatorSections.size() == 3
            && operatorSections.get(0).possibleOperators.equals(operatorSections.get(2).possibleOperators)
            && operatorSections.get(0) instanceof NaryOperatorSection
            && operatorSections.get(2) instanceof OperandOps.NaryOperatorSection
            && operatorSections.get(1).operatorSetPrecedence <= operatorSections.get(0).operatorSetPrecedence
            )
        {
            // The sections either side match up, and the middle is same or lower precedence, so we can bracket
            // the middle and put it into one valid expression.  Hurrah!
            @SuppressWarnings("recorded")
            @Nullable @Recorded EXPRESSION middle = operatorSections.get(1).makeExpression(expressionExps, bracketedStatus);
            if (middle != null)
            {
                @Nullable EXPRESSION replacement = ((NaryOperatorSection<EXPRESSION, SEMANTIC_PARENT>) operatorSections.get(0)).makeExpressionMiddleMerge(
                    middle,
                    (NaryOperatorSection<EXPRESSION, SEMANTIC_PARENT>) operatorSections.get(2),
                    expressionExps, bracketedStatus
                );

                if (replacement != null)
                {
                    errorAndTypeRecorder.recordQuickFixes(invalidOpExpression, Collections.singletonList(
                        new QuickFix<>("fix.bracketAs", ReplacementTarget.CURRENT, replacement)
                    ));
                }
            }
        }
        else
        {            
            // We may be able to suggest some brackets
            Collections.sort(operatorSections, Comparator.comparing(os -> os.operatorSetPrecedence));
            int precedence = operatorSections.get(0).operatorSetPrecedence;

            for (int i = 0; i < operatorSections.size(); i++)
            {
                OperatorSection<EXPRESSION, SEMANTIC_PARENT> operatorSection = operatorSections.get(i);
                if (operatorSection.operatorSetPrecedence != precedence)
                    break;

                // We try all the bracketing states, preferring un-bracketed, for valid replacements: 

                @SuppressWarnings("recorded")
                @Nullable @Recorded EXPRESSION sectionExpression = operatorSection.makeExpression(expressionExps, bracketedStatus);
                if (sectionExpression == null)
                    continue;
                
                // The replacement if we just bracketed this section:
                @UnknownIfRecorded LoadableExpression<EXPRESSION, SEMANTIC_PARENT> replacement;
                // There's three possibilities.  One is that if there is one other section, or two that match each other,
                // we could make a valid expression.  Otherwise we're going to be invalid even with a bracket.
                if (operatorSections.size() == 2)
                {
                    // We need our new expression, plus the bits we're not including
                    if (operatorSection.getFirstOperandIndex() == 0)
                    {
                        replacement = operatorSections.get(1 - i).makeExpressionReplaceLHS(
                            sectionExpression,
                            expressionExps,
                            bracketedStatus
                        );
                    }
                    else
                    {
                        replacement = operatorSections.get(1 - i).makeExpressionReplaceRHS(
                            sectionExpression,
                            expressionExps,
                            bracketedStatus
                        );
                    }
                }
                //else if (operatorSections.size() == 3 && ...) -- Handled above
                else
                {
                    // Just have to make an invalid op expression, then:
                    ArrayList<@Recorded EXPRESSION> newExps = new ArrayList<>(expressionExps);
                    ArrayList<String> newOps = new ArrayList<>(ops);
                    
                    newExps.subList(operatorSection.getFirstOperandIndex(), operatorSection.getLastOperandIndex() + 1).clear();
                    newExps.add(operatorSection.getFirstOperandIndex(), sectionExpression);
                    newOps.subList(operatorSection.getFirstOperandIndex(), operatorSection.getLastOperandIndex()).clear();
                    
                    replacement = operandOps.makeInvalidOpExpression(ImmutableList.copyOf(newExps), newOps);
                }

                if (replacement != null)
                {
                    errorAndTypeRecorder.recordQuickFixes(invalidOpExpression, Collections.singletonList(
                        new QuickFix<>("fix.bracketAs", ReplacementTarget.CURRENT, replacement)
                    ));
                }
            }
        }
        return invalidOpExpression;
    }
    */
}
