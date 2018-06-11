package records.gui.expressioneditor;

import annotation.recorded.qual.Recorded;
import annotation.recorded.qual.UnknownIfRecorded;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.i18n.qual.LocalizableKey;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;
import records.gui.expressioneditor.GeneralExpressionEntry.Keyword;
import records.gui.expressioneditor.GeneralExpressionEntry.Op;
import records.transformations.expression.*;
import records.transformations.expression.AddSubtractExpression.AddSubtractOp;
import records.transformations.expression.ComparisonExpression.ComparisonOperator;
import records.transformations.expression.MatchExpression.MatchClause;
import records.transformations.expression.MatchExpression.Pattern;
import records.transformations.expression.QuickFix.ReplacementTarget;
import records.typeExp.TypeExp;
import styled.StyledShowable;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.FXPlatformConsumer;
import utility.Pair;
import utility.Utility;
import utility.gui.TranslationUtility;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Stack;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ExpressionSaver implements ErrorAndTypeRecorder
{
    class Context {}
    
    // Ends a mini-expression
    private static interface Terminator
    {
        public void terminate(Function<BracketedStatus, Expression> makeContent, Keyword terminator, ConsecutiveChild<Expression, ExpressionSaver> keywordErrorDisplayer, FXPlatformConsumer<Context> keywordContext);
    }
    
    private static class OpAndNode
    {
        public final Op op;
        public final ConsecutiveChild<Expression, ExpressionSaver> sourceNode;

        public OpAndNode(Op op, ConsecutiveChild<Expression, ExpressionSaver> sourceNode)
        {
            this.op = op;
            this.sourceNode = sourceNode;
        }
    }
    
    private static class Scope
    {
        public final ArrayList<Either<@Recorded Expression, OpAndNode>> items;
        public final Terminator terminator;
        public final @Nullable ConsecutiveChild<Expression, ExpressionSaver> openingNode;

        public Scope(@Nullable ConsecutiveChild<Expression, ExpressionSaver> openingNode, Terminator terminator)
        {
            this.items = new ArrayList<>();
            this.terminator = terminator;
            this.openingNode = openingNode;
        }
    }
    
    private final Stack<Scope> currentScopes = new Stack<>();
    private final ErrorDisplayerRecord errorDisplayerRecord = new ErrorDisplayerRecord();
    
    public ExpressionSaver(ConsecutiveBase<Expression, ExpressionSaver> parent)
    {
        addTopLevelScope(parent);
    }

    @RequiresNonNull("currentScopes")
    public void addTopLevelScope(@UnknownInitialization(Object.class) ExpressionSaver this, ConsecutiveBase<Expression, ExpressionSaver> parent)
    {
        currentScopes.push(new Scope(null, (makeContent, terminator, keywordErrorDisplayer, keywordContext) -> {
            keywordErrorDisplayer.addErrorAndFixes(StyledString.s("Closing " + terminator + " without opening"), ImmutableList.of());
            currentScopes.peek().items.add(Either.left(errorDisplayerRecord.record(parent, null, keywordErrorDisplayer, new InvalidOperatorExpression(ImmutableList.of(Either.left(terminator.getContent()))))));
        }));
    }

    public @Recorded Expression finish(ConsecutiveChild<Expression, ExpressionSaver> errorDisplayer)
    {
        while (currentScopes.size() > 1)
        {
            // TODO give some sort of error.... somewhere?  On the opening item?
            Scope closed = currentScopes.pop();
            currentScopes.peek().items.add(Either.left(errorDisplayerRecord.record(errorDisplayer.getParent(), closed.openingNode, errorDisplayer, makeInvalidOp(closed.items.stream().map(e -> e.map(x -> x.op)).map(Either::swap).collect(ImmutableList.toImmutableList())))));
        }

        Scope closed = currentScopes.pop();
        return errorDisplayerRecord.record(errorDisplayer.getParent(), closed.openingNode, errorDisplayer, makeExpression(closed.items, BracketedStatus.TOP_LEVEL));
    }
    
    // Note: if we are copying to clipboard, callback will not be called
    public void saveKeyword(Keyword keyword, ConsecutiveChild<Expression, ExpressionSaver> errorDisplayer, FXPlatformConsumer<Context> withContext)
    {
        if (keyword == Keyword.ANYTHING)
        {
            saveOperand(new MatchAnythingExpression(), errorDisplayer, errorDisplayer, withContext);
        }
        else if (keyword == Keyword.QUEST)
        {
            saveOperand(new ImplicitLambdaArg(), errorDisplayer, errorDisplayer, withContext);
        }
        else if (keyword == Keyword.OPEN_ROUND)
        {
            currentScopes.push(new Scope(errorDisplayer, expect(Keyword.CLOSE_ROUND, BracketedStatus.DIRECT_ROUND_BRACKETED, (bracketed, bracketEnd) -> {
                ArrayList<Either<@Recorded Expression, OpAndNode>> precedingItems = currentScopes.peek().items;
                // Function calls are a special case:
                if (precedingItems.size() >= 1 && precedingItems.get(precedingItems.size() - 1).either(ExpressionOps::isCallTarget, op -> false))
                {
                    @Nullable @Recorded Expression callTarget = precedingItems.remove(precedingItems.size() - 1).<@Nullable @Recorded Expression>either(e -> e, op -> null);
                    // Shouldn't ever be null:
                    if (callTarget != null)
                    {
                        return Either.left(errorDisplayerRecord.record(errorDisplayer.getParent(),errorDisplayerRecord.recorderFor(callTarget).start, bracketEnd, new CallExpression(callTarget, bracketed)));
                    }
                }
                return Either.left(errorDisplayerRecord.record(errorDisplayer.getParent(), errorDisplayer, bracketEnd, bracketed));
            })));
        }
        else if (keyword == Keyword.OPEN_SQUARE)
        {
            currentScopes.push(new Scope(errorDisplayer, expect(Keyword.CLOSE_SQUARE, BracketedStatus.DIRECT_SQUARE_BRACKETED, (e, c) -> Either.left(e))));
        }
        else if (keyword == Keyword.IF)
        {
            currentScopes.push(new Scope(errorDisplayer, expect(Keyword.THEN, BracketedStatus.MISC, (condition, conditionEnd) ->
                Either.right(expect(Keyword.ELSE, BracketedStatus.MISC, (thenPart, thenEnd) -> 
                    Either.right(expect(Keyword.ENDIF, BracketedStatus.MISC, (elsePart, elseEnd) -> {
                        return Either.left(errorDisplayerRecord.record(errorDisplayer.getParent(),errorDisplayer, elseEnd, new IfThenElseExpression(condition, thenPart, elsePart)));
                    })
                )    
            )))));
        }
        else if (keyword == Keyword.MATCH)
        {            
            currentScopes.push(new Scope(errorDisplayer, expectOneOf(ImmutableList.of(new Case()))));
        }
        else
        {
            // Should be a terminator:
            Scope cur = currentScopes.pop();
            if (currentScopes.size() == 0)
            {
                addTopLevelScope(errorDisplayer.getParent());
            }
            cur.terminator.terminate(bracketedStatus -> makeExpression(cur.items, bracketedStatus), keyword, errorDisplayer, withContext);
        }
    }

    private Expression makeExpression(List<Either<@Recorded Expression, OpAndNode>> content, BracketedStatus bracketedStatus)
    {
        if (content.isEmpty())
        {
            if (bracketedStatus == BracketedStatus.DIRECT_SQUARE_BRACKETED)
                return new ArrayExpression(ImmutableList.of());
            else
                return new InvalidOperatorExpression(ImmutableList.of());
        }
        
        // Although it's duplication, we keep a list for if it turns out invalid, and two lists for if it is valid:
        // Valid means that operands interleave exactly with operators, and there is an operand at beginning and end.
        boolean[] valid = new boolean[] {true};
        final ArrayList<Either<String, @Recorded Expression>> invalid = new ArrayList<>();
        final ArrayList<@Recorded Expression> validOperands = new ArrayList<>();
        final ArrayList<Op> validOperators = new ArrayList<>();

        boolean lastWasExpression[] = new boolean[] {false}; // Think of it as an invisible empty prefix operator
        
        for (Either<Expression, OpAndNode> item : content)
        {
            item.either_(expression -> {
                invalid.add(Either.right(expression));
                validOperands.add(expression);
                
                if (lastWasExpression[0])
                {
                    // TODO missing operator error
                    valid[0] = false;    
                }
                lastWasExpression[0] = true;
            }, op -> {
                invalid.add(Either.left(op.op.getContent()));
                validOperators.add(op.op);
                
                if (!lastWasExpression[0])
                {
                    // TODO missing operand error
                    valid[0] = false;
                }
                lastWasExpression[0] = false;
            });
        }
        
        if (valid[0])
        {
            @Nullable Expression e;
            // Single expression?
            if (validOperands.size() == 1 && validOperators.size() == 0)
            {
                e = validOperands.get(0);
                if (bracketedStatus == BracketedStatus.DIRECT_SQUARE_BRACKETED)
                    e = new ArrayExpression(ImmutableList.of(e));
            }
            else
            {
                // Now we need to check the operators can work together as one group:

                e = ExpressionSaver.<Expression, ExpressionSaver, Op>makeExpressionWithOperators(OPERATORS, this, ExpressionSaver::makeInvalidOp, ImmutableList.copyOf(validOperands), ImmutableList.copyOf(validOperators), bracketedStatus, arg -> new ArrayExpression(ImmutableList.of(arg)));
            }
            if (e != null)
            {
                return e;
            }
            
        }
        
        return new InvalidOperatorExpression(ImmutableList.copyOf(invalid));
    }

    private static Expression makeInvalidOp(ImmutableList<Either<Op, @Recorded Expression>> items)
    {
        return new InvalidOperatorExpression(Utility.mapListI(items, x -> x.mapBoth(op -> op.getContent(), y -> y)));
    }

    // Expects a keyword matching closer.  If so, call the function with the current scope's expression, and you'll get back a final expression or a
    // terminator for a new scope, compiled using the scope content and given bracketed status
    public Terminator expect(Keyword expected, BracketedStatus bracketedStatus, BiFunction<Expression, ConsecutiveChild<Expression, ExpressionSaver>, Either<@Recorded Expression, Terminator>> onClose)
    {
        return (makeContent, terminator, keywordErrorDisplayer, keywordContext) -> {
            if (terminator == expected)
            {
                // All is well:
                Either<@Recorded Expression, Terminator> result = onClose.apply(makeContent.apply(bracketedStatus), keywordErrorDisplayer);
                result.either_(e -> currentScopes.peek().items.add(Either.left(e)), t -> currentScopes.push(new Scope(keywordErrorDisplayer, t)));
            }
            else
            {
                // Error!
                keywordErrorDisplayer.addErrorAndFixes(StyledString.s("Expected " + expected + " but found " + terminator), ImmutableList.of());
                // Important to call makeContent before adding to scope on the next line:
                ImmutableList.Builder<Either<String, @Recorded Expression>> items = ImmutableList.builder();
                items.add(Either.right(makeContent.apply(bracketedStatus)));
                items.add(Either.left(terminator.getContent()));
                InvalidOperatorExpression invalid = new InvalidOperatorExpression(items.build());
                currentScopes.peek().items.add(Either.left(invalid));
            }
        };
    }
    
    // Looks for a keyword, then takes the expression before the keyword and gives next step.
    private abstract class Choice
    {
        public final Keyword keyword;

        protected Choice(Keyword keyword)
        {
            this.keyword = keyword;
        }

        public abstract Either<@Recorded Expression, Terminator> foundKeyword(@Recorded Expression expressionBefore);
    }
    
    // Looks for @case
    private class Case extends Choice
    {
        // If null, we are the first case.  Otherwise we are a later case,
        // in which case we are Right with the given patterns
        private final @Nullable Pair<Expression, ImmutableList<Pattern>> matchAndPatterns;
        // Previous complete clauses
        private final ImmutableList<Function<MatchExpression, MatchClause>> previousClauses;
        
        // Looks for first @case after a @match
        public Case()
        {
            super(Keyword.CASE);
            matchAndPatterns = null;
            previousClauses = ImmutableList.of();
        }
        
        // Matches a later @case, meaning we follow a @then and an outcome
        public Case(Expression matchFrom, ImmutableList<Function<MatchExpression, MatchClause>> previousClauses, ImmutableList<Pattern> patternsForCur)
        {
            super(Keyword.CASE);
            matchAndPatterns = new Pair<>(matchFrom, patternsForCur);
            this.previousClauses = previousClauses;
        }

        @Override
        public Either<@Recorded Expression, Terminator> foundKeyword(@Recorded Expression expressionBefore)
        {
            final Expression m;
            final ImmutableList<Function<MatchExpression, MatchClause>> newClauses;
            // If we are first case, use the expression as the match expression:
            if (matchAndPatterns == null)
            {
                m = expressionBefore;
                newClauses = previousClauses;
            }
            else
            {
                // Otherwise this is the outcome for the most recent clause:
                m = matchAndPatterns.getFirst();
                ImmutableList<Pattern> patterns = matchAndPatterns.getSecond();
                newClauses = Utility.appendToList(previousClauses, me -> me.new MatchClause(patterns, expressionBefore));
            }
            return Either.right(expectOneOf(ImmutableList.of(
                new Then(m, newClauses, ImmutableList.of(), Keyword.CASE),
                new Given(m, newClauses, ImmutableList.of()),
                new OrCase(m, newClauses, ImmutableList.of(), null)
            )));
        }
    }
    
    // Looks for @given to add a guard
    private class Given extends Choice
    {
        private final Expression matchFrom;
        private final ImmutableList<Function<MatchExpression, MatchClause>> previousClauses;
        private final ImmutableList<Pattern> previousCases;

        public Given(Expression matchFrom, ImmutableList<Function<MatchExpression, MatchClause>> previousClauses, ImmutableList<Pattern> previousCases)
        {
            super(Keyword.GIVEN);
            this.matchFrom = matchFrom;
            this.previousClauses = previousClauses;
            this.previousCases = previousCases;
        }

        @Override
        public Either<@Recorded Expression, Terminator> foundKeyword(@Recorded Expression expressionBefore)
        {
            // Expression here is the pattern, which comes before the guard:
            return Either.right(expectOneOf(ImmutableList.of(
                new OrCase(matchFrom, previousClauses, previousCases, expressionBefore),
                new Then(matchFrom, previousClauses, Utility.appendToList(previousCases, new Pattern(expressionBefore, null)), Keyword.GIVEN)
            )));
        }
    }
    
    // Looks for @orcase
    private class OrCase extends Choice
    {
        private final Expression matchFrom;
        private final ImmutableList<Function<MatchExpression, MatchClause>> previousClauses;
        private final ImmutableList<Pattern> previousCases;
        private final @Nullable Expression curMatch; // if null, nothing so far, if non-null we are a guard

        private OrCase(Expression matchFrom, ImmutableList<Function<MatchExpression, MatchClause>> previousClauses, ImmutableList<Pattern> previousCases, @Nullable Expression curMatch)
        {
            super(Keyword.ORCASE);
            this.matchFrom = matchFrom;
            this.previousClauses = previousClauses;
            this.previousCases = previousCases;
            this.curMatch = curMatch;
        }

        @Override
        public Either<@Recorded Expression, Terminator> foundKeyword(@Recorded Expression expressionBefore)
        {
            ImmutableList<Pattern> newCases = Utility.appendToList(previousCases, curMatch == null ?
                // We are the pattern:
                new Pattern(expressionBefore, null) :
                // We are the guard:    
                new Pattern(curMatch, expressionBefore)
            );
            return Either.right(expectOneOf(ImmutableList.of(
                new Given(matchFrom, previousClauses, newCases),
                new OrCase(matchFrom, previousClauses, newCases, null),
                new Then(matchFrom, previousClauses, newCases, Keyword.ORCASE)
            )));
        }
    }
    
    // Looks for @then (in match expressions; if-then-else is handled separately) 
    private class Then extends Choice
    {
        private final Expression matchFrom;
        private final ImmutableList<Function<MatchExpression, MatchClause>> previousClauses;
        private final ImmutableList<Pattern> previousPatterns;
        private final Keyword precedingKeyword;

        // Preceding keyword may be CASE, GIVEN or ORCASE:
        private Then(Expression matchFrom, ImmutableList<Function<MatchExpression, MatchClause>> previousClauses, ImmutableList<Pattern> previousPatterns, Keyword precedingKeyword)
        {
            super(Keyword.THEN);
            this.matchFrom = matchFrom;
            this.previousClauses = previousClauses;
            this.previousPatterns = previousPatterns;
            this.precedingKeyword = precedingKeyword;
        }

        @Override
        public Either<@Recorded Expression, Terminator> foundKeyword(@Recorded Expression expressionBefore)
        {
            final ImmutableList<Pattern> newPatterns;
            if (precedingKeyword == Keyword.GIVEN)
            {
                // The expression is a guard for the most recent pattern:
                newPatterns = Utility.appendToList(previousPatterns.subList(0, previousPatterns.size() - 1), new Pattern(previousPatterns.get(previousPatterns.size() - 1).getPattern(), expressionBefore));
            }
            else //if (precedingKeyword == Keyword.ORCASE || precedingKeyword == Keyword.CASE)
            {
                // The expression is a pattern:
                newPatterns = Utility.appendToList(previousPatterns, new Pattern(expressionBefore, null));
            }
            return Either.right(expectOneOf(ImmutableList.of(
                new Case(matchFrom, previousClauses, newPatterns),
                new Choice(Keyword.ENDMATCH) {
                    @Override
                    public Either<@Recorded Expression, Terminator> foundKeyword(@Recorded Expression lastExpression)
                    {
                        return Either.left(new MatchExpression(matchFrom, Utility.appendToList(previousClauses, me -> me.new MatchClause(newPatterns, lastExpression))));
                    }
                }
            )));
        }
    }
    

    public Terminator expectOneOf(ImmutableList<Choice> choices)
    {
        return (makeContent, terminator, keywordErrorDisplayer, keywordContext) -> {
            for (Choice choice : choices)
            {
                if (choice.keyword.equals(terminator))
                {
                    // All is well:
                    Either<Expression, Terminator> result = choice.foundKeyword(makeContent.apply(BracketedStatus.MISC));
                    result.either_(e -> currentScopes.peek().items.add(Either.left(e)), t -> currentScopes.push(new Scope(keywordErrorDisplayer, t)));
                    return;
                }
            }
            
            // Error!
            keywordErrorDisplayer.addErrorAndFixes(StyledString.s("Expected " + choices.stream().map(e -> e.keyword.getContent()).collect(Collectors.joining(" or ")) + " but found " + terminator), ImmutableList.of());
            // Important to call makeContent before adding to scope on the next line:
            ImmutableList.Builder<Either<String, Expression>> items = ImmutableList.builder();
            items.add(Either.right(makeContent.apply(BracketedStatus.MISC)));
            items.add(Either.left(terminator.getContent()));
            InvalidOperatorExpression invalid = new InvalidOperatorExpression(items.build());
            currentScopes.peek().items.add(Either.left(invalid));
        };
    }
    
    
    public void saveOperator(Op operator, ConsecutiveChild<Expression, ExpressionSaver> errorDisplayer, FXPlatformConsumer<Context> withContext)
    {
        currentScopes.peek().items.add(Either.right(new OpAndNode(operator, errorDisplayer)));
    }
    
    public void saveOperand(Expression singleItem, ConsecutiveChild<Expression, ExpressionSaver> start, ConsecutiveChild<Expression,ExpressionSaver> end, FXPlatformConsumer<Context> withContext)
    {
        ArrayList<Either<Expression, OpAndNode>> curItems = currentScopes.peek().items;
        if (singleItem instanceof UnitLiteralExpression && curItems.size() >= 1)
        {
            Either<Expression, OpAndNode> recent = curItems.get(curItems.size() - 1);
            @Nullable NumericLiteral num = recent.<@Nullable NumericLiteral>either(e -> e instanceof NumericLiteral ? (NumericLiteral)e : null, o -> null);
            if (num != null && num.getUnitExpression() == null)
            {
                curItems.set(curItems.size() - 1, Either.left(new NumericLiteral(num.getNumber(), ((UnitLiteralExpression)singleItem).getUnit())));
                return;
            }
        }
        
        curItems.add(Either.left(singleItem));
        errorDisplayerRecord.record(start.getParent(), start, end, singleItem);
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

    public static interface MakeNary<EXPRESSION extends LoadableExpression<EXPRESSION, SEMANTIC_PARENT>, SEMANTIC_PARENT, OP>
    {
        public @Nullable EXPRESSION makeNary(ImmutableList<@Recorded EXPRESSION> expressions, List<OP> operators, BracketedStatus bracketedStatus);
    }

    public static interface MakeBinary<EXPRESSION extends LoadableExpression<EXPRESSION, SEMANTIC_PARENT>, SEMANTIC_PARENT>
    {
        public EXPRESSION makeBinary(@Recorded EXPRESSION lhs, @Recorded EXPRESSION rhs, BracketedStatus bracketedStatus);
    }
        

    // One set of operators that can be used to make a particular expression
    static class OperatorExpressionInfo<EXPRESSION extends LoadableExpression<EXPRESSION, SEMANTIC_PARENT>, SEMANTIC_PARENT, @NonNull OP>
    {
        public final ImmutableList<Pair<OP, @Localized String>> operators;
        public final Either<MakeNary<EXPRESSION, SEMANTIC_PARENT, OP>, MakeBinary<EXPRESSION, SEMANTIC_PARENT>> makeExpression;

        OperatorExpressionInfo(ImmutableList<Pair<OP, @Localized String>> operators, MakeNary<EXPRESSION, SEMANTIC_PARENT, OP> makeExpression)
        {
            this.operators = operators;
            this.makeExpression = Either.left(makeExpression);
        }

        // I know it's a bit odd, but we distinguish the N-ary constructor from the binary constructor by 
        // making the operators a non-list here.  The only expression with multiple operators
        // is add-subtract, which is N-ary.  And you can't have a binary expression with multiple different operators...
        OperatorExpressionInfo(Pair<OP, @Localized String> operator, MakeBinary<EXPRESSION, SEMANTIC_PARENT> makeExpression)
        {
            this.operators = ImmutableList.of(operator);
            this.makeExpression = Either.right(makeExpression);
        }

        public boolean includes(@NonNull OP op)
        {
            return operators.stream().anyMatch(p -> op.equals(p.getFirst()));
        }

        public OperatorSection<EXPRESSION, SEMANTIC_PARENT, OP> makeOperatorSection(int operatorSetPrecedence, OP initialOperator, int initialIndex)
        {
            return makeExpression.either(
                nAry -> new NaryOperatorSection<EXPRESSION, SEMANTIC_PARENT, OP>(operators, operatorSetPrecedence, nAry, initialIndex, initialOperator),
                binary -> new BinaryOperatorSection<EXPRESSION, SEMANTIC_PARENT, OP>(operators, operatorSetPrecedence, binary, initialIndex)
            );
        }
    }

    static abstract class OperatorSection<EXPRESSION extends LoadableExpression<EXPRESSION, SEMANTIC_PARENT>, SEMANTIC_PARENT, @NonNull OP>
    {
        protected final ImmutableList<Pair<OP, @Localized String>> possibleOperators;
        // The ordering in the candidates list:
        public final int operatorSetPrecedence;

        protected OperatorSection(ImmutableList<Pair<OP, @Localized String>> possibleOperators, int operatorSetPrecedence)
        {
            this.possibleOperators = possibleOperators;
            this.operatorSetPrecedence = operatorSetPrecedence;
        }

        /**
         * Attempts to add the operator to this section.  If it can be added, it is added, and true is returned.
         * If it can't be added, nothing is changed, and false is returned.
         */
        abstract boolean addOperator(@NonNull OP operator, int indexOfOperator);

        /**
         * Given the operators already added, makes an expression.  Will only use the indexes that pertain
         * to the operators that got added, you should pass the entire list of the expression args.
         */
        abstract @Nullable EXPRESSION makeExpression(ImmutableList<@Recorded EXPRESSION> expressions, BracketedStatus bracketedStatus);

        abstract int getFirstOperandIndex();

        abstract int getLastOperandIndex();

        abstract @Nullable EXPRESSION makeExpressionReplaceLHS(@Recorded EXPRESSION lhs, ImmutableList<@Recorded EXPRESSION> allOriginalExps, BracketedStatus bracketedStatus);
        abstract @Nullable EXPRESSION makeExpressionReplaceRHS(@Recorded EXPRESSION rhs, ImmutableList<@Recorded EXPRESSION> allOriginalExps, BracketedStatus bracketedStatus);
    }

    static class BinaryOperatorSection<EXPRESSION extends LoadableExpression<EXPRESSION, SEMANTIC_PARENT>, SEMANTIC_PARENT, OP> extends OperatorSection<EXPRESSION, SEMANTIC_PARENT, OP>
    {
        private final MakeBinary<EXPRESSION, SEMANTIC_PARENT> makeExpression;
        private final int operatorIndex;

        private BinaryOperatorSection(ImmutableList<Pair<OP, @Localized String>> operators, int candidatePrecedence, MakeBinary<EXPRESSION, SEMANTIC_PARENT> makeExpression, int initialIndex)
        {
            super(operators, candidatePrecedence);
            this.makeExpression = makeExpression;
            this.operatorIndex = initialIndex;
        }

        @Override
        boolean addOperator(OP operator, int indexOfOperator)
        {
            // Can never add another operator to a binary operator:
            return false;
        }

        @Override
        EXPRESSION makeExpression(ImmutableList<@Recorded EXPRESSION> expressions, BracketedStatus bracketedStatus)
        {
            return makeExpression.makeBinary(expressions.get(operatorIndex), expressions.get(operatorIndex + 1), bracketedStatus);
        }

        @Override
        int getFirstOperandIndex()
        {
            return operatorIndex;
        }

        @Override
        int getLastOperandIndex()
        {
            return operatorIndex + 1;
        }

        @Override
        EXPRESSION makeExpressionReplaceLHS(@Recorded EXPRESSION lhs, ImmutableList<@Recorded EXPRESSION> expressions, BracketedStatus bracketedStatus)
        {
            return makeExpression.makeBinary(lhs , expressions.get(operatorIndex + 1), bracketedStatus);
        }

        @Override
        EXPRESSION makeExpressionReplaceRHS(@Recorded EXPRESSION rhs, ImmutableList<@Recorded EXPRESSION> expressions, BracketedStatus bracketedStatus)
        {
            return makeExpression.makeBinary(expressions.get(operatorIndex), rhs, bracketedStatus);
        }
    }

    static class NaryOperatorSection<EXPRESSION extends LoadableExpression<EXPRESSION, SEMANTIC_PARENT>, SEMANTIC_PARENT, @NonNull OP> extends OperatorSection<EXPRESSION, SEMANTIC_PARENT, OP>
    {
        private final MakeNary<EXPRESSION, SEMANTIC_PARENT, OP> makeExpression;
        private final ArrayList<OP> actualOperators = new ArrayList<>();
        private final int startingOperatorIndexIncl;
        private int endingOperatorIndexIncl;

        NaryOperatorSection(ImmutableList<Pair<OP, @Localized String>> operators, int candidatePrecedence, MakeNary<EXPRESSION, SEMANTIC_PARENT, OP> makeExpression, int initialIndex, OP initialOperator)
        {
            super(operators, candidatePrecedence);
            this.makeExpression = makeExpression;
            this.startingOperatorIndexIncl = initialIndex;
            this.endingOperatorIndexIncl = initialIndex;
            this.actualOperators.add(initialOperator);
        }

        @Override
        boolean addOperator(@NonNull OP operator, int indexOfOperator)
        {
            if (possibleOperators.stream().anyMatch(p -> operator.equals(p.getFirst())) && indexOfOperator == endingOperatorIndexIncl + 1)
            {
                endingOperatorIndexIncl = indexOfOperator;
                actualOperators.add(operator);
                return true;
            }
            else
            {
                return false;
            }
        }

        @Override
        @Nullable EXPRESSION makeExpression(ImmutableList<@Recorded EXPRESSION> expressions, BracketedStatus bracketedStatus)
        {
            // Given a + b + c, if end operator is 1, we want to include operand index 2, hence we pass excl index 3,
            // so it's last operator-inclusive, plus 2.
            return makeExpression.makeNary(expressions.subList(startingOperatorIndexIncl, endingOperatorIndexIncl + 2), actualOperators, bracketedStatus);
        }

        @Override
        int getFirstOperandIndex()
        {
            return startingOperatorIndexIncl;
        }

        @Override
        int getLastOperandIndex()
        {
            return endingOperatorIndexIncl + 1;
        }

        @Override
        @Nullable EXPRESSION makeExpressionReplaceLHS(@Recorded EXPRESSION lhs, ImmutableList<@Recorded EXPRESSION> expressions, BracketedStatus bracketedStatus)
        {
            ArrayList<@Recorded EXPRESSION> args = new ArrayList<>(expressions.subList(startingOperatorIndexIncl, endingOperatorIndexIncl + 2));
            args.set(0, lhs);
            return makeExpression.makeNary(ImmutableList.copyOf(args), actualOperators, bracketedStatus);
        }

        @Override
        @Nullable EXPRESSION makeExpressionReplaceRHS(@Recorded EXPRESSION rhs, ImmutableList<@Recorded EXPRESSION> expressions, BracketedStatus bracketedStatus)
        {
            ArrayList<@Recorded EXPRESSION> args = new ArrayList<>(expressions.subList(startingOperatorIndexIncl, endingOperatorIndexIncl + 2));
            args.set(args.size() - 1, rhs);
            return makeExpression.makeNary(ImmutableList.copyOf(args), actualOperators, bracketedStatus);
        }

        /**
         * Uses this expression as the LHS, and a custom middle and RHS that must have matching operators.  Used
         * to join two NaryOpExpressions while bracketing an item in the middle.
         */

        public @Nullable EXPRESSION makeExpressionMiddleMerge(@Recorded EXPRESSION middle, NaryOperatorSection<EXPRESSION, SEMANTIC_PARENT, OP> rhs, List<@Recorded EXPRESSION> expressions, BracketedStatus bracketedStatus)
        {
            List<@Recorded EXPRESSION> args = new ArrayList<>();
            // Add our args, minus the end one:
            args.addAll(expressions.subList(startingOperatorIndexIncl, endingOperatorIndexIncl + 1));
            // Add the middle:
            args.add(middle);
            // Add RHS, minus the start one:
            args.addAll(expressions.subList(rhs.startingOperatorIndexIncl + 1, rhs.endingOperatorIndexIncl + 2));
            return makeExpression.makeNary(ImmutableList.copyOf(args), Utility.concatI(actualOperators, rhs.actualOperators), bracketedStatus);
        }
    }
    
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
    static <EXPRESSION extends LoadableExpression<EXPRESSION, SEMANTIC_PARENT>, SEMANTIC_PARENT, @NonNull OP> @Nullable EXPRESSION makeExpressionWithOperators(
        ImmutableList<ImmutableList<OperatorExpressionInfo<EXPRESSION, SEMANTIC_PARENT, OP>>> candidates, ErrorAndTypeRecorder errorAndTypeRecorder,
        Function<ImmutableList<Either<@NonNull OP, EXPRESSION>>, EXPRESSION> makeInvalidOpExpression,
        ImmutableList<@Recorded EXPRESSION> expressionExps, ImmutableList<@NonNull OP> ops, BracketedStatus bracketedStatus, Function<EXPRESSION, EXPRESSION> makeSingletonList)
    {
        if (ops.size() != expressionExps.size() - 1)
        {
            // Other issues: we're missing an argument!
            return null;
        }
        
        if (ops.isEmpty())
        {
            if (bracketedStatus == BracketedStatus.DIRECT_SQUARE_BRACKETED)
                return makeSingletonList.apply(expressionExps.get(0));
            else
                return expressionExps.get(0);
        }
        
        // First, split it into sections based on cohesive parts that have the same operators:
        List<OperatorSection<EXPRESSION, SEMANTIC_PARENT, OP>> operatorSections = new ArrayList<>();
        nextOp: for (int i = 0; i < ops.size(); i++)
        {
            if (operatorSections.isEmpty() || !operatorSections.get(operatorSections.size() - 1).addOperator(ops.get(i), i))
            {
                // Make new section:
                for (int candidateIndex = 0; candidateIndex < candidates.size(); candidateIndex++)
                {
                    for (OperatorExpressionInfo<EXPRESSION, SEMANTIC_PARENT, OP> operatorExpressionInfo : candidates.get(candidateIndex))
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
                    EXPRESSION invalidOpExpression = makeInvalidOpExpression.apply(interleave(expressionExps, ops));
                    errorAndTypeRecorder.recordError(invalidOpExpression, StyledString.s("Surrounding brackets required"));
                    errorAndTypeRecorder.recordQuickFixes(invalidOpExpression, Utility.mapList(possibles, e -> new QuickFix<>("fix.bracketAs", ReplacementTarget.CURRENT, e)));
                    return invalidOpExpression;
                }
            }
        }

        EXPRESSION invalidOpExpression = makeInvalidOpExpression.apply(interleave(expressionExps, ops));
        errorAndTypeRecorder.recordError(invalidOpExpression, StyledString.s("Mixed operators: brackets required"));
        
        if (operatorSections.size() == 3
            && operatorSections.get(0).possibleOperators.equals(operatorSections.get(2).possibleOperators)
            && operatorSections.get(0) instanceof NaryOperatorSection
            && operatorSections.get(2) instanceof NaryOperatorSection
            && operatorSections.get(1).operatorSetPrecedence <= operatorSections.get(0).operatorSetPrecedence
            )
        {
            // The sections either side match up, and the middle is same or lower precedence, so we can bracket
            // the middle and put it into one valid expression.  Hurrah!
            @SuppressWarnings("recorded")
            @Nullable @Recorded EXPRESSION middle = operatorSections.get(1).makeExpression(expressionExps, bracketedStatus);
            if (middle != null)
            {
                @Nullable EXPRESSION replacement = ((NaryOperatorSection<EXPRESSION, SEMANTIC_PARENT, OP>) operatorSections.get(0)).makeExpressionMiddleMerge(
                    middle,
                    (NaryOperatorSection<EXPRESSION, SEMANTIC_PARENT, OP>) operatorSections.get(2),
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
                OperatorSection<EXPRESSION, SEMANTIC_PARENT, OP> operatorSection = operatorSections.get(i);
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
                    ArrayList<OP> newOps = new ArrayList<>(ops);
                    
                    newExps.subList(operatorSection.getFirstOperandIndex(), operatorSection.getLastOperandIndex() + 1).clear();
                    newExps.add(operatorSection.getFirstOperandIndex(), sectionExpression);
                    newOps.subList(operatorSection.getFirstOperandIndex(), operatorSection.getLastOperandIndex()).clear();
                    
                    replacement = makeInvalidOpExpression.apply(interleave(ImmutableList.copyOf(newExps), ImmutableList.copyOf(newOps)));
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

    private static <EXPRESSION, OP> ImmutableList<Either<OP, EXPRESSION>> interleave(ImmutableList<EXPRESSION> expressions, ImmutableList<OP> ops)
    {
        ImmutableList.Builder<Either<OP, EXPRESSION>> r = ImmutableList.builder();

        for (int i = 0; i < expressions.size(); i++)
        {
            r.add(Either.right(expressions.get(i)));
            if (i < ops.size())
                r.add(Either.left(ops.get(i)));
        }
        
        return r.build();
    }

    private static Pair<Op, @Localized String> opD(Op op, @LocalizableKey String key)
    {
        return new Pair<>(op, TranslationUtility.getString(key));
    }
    
    // Remember: earlier means more likely to be inner-bracketed.  Outer list is groups of operators
    // with equal bracketing likelihood/precedence.
    @SuppressWarnings("recorded")
    final static ImmutableList<ImmutableList<OperatorExpressionInfo<Expression, ExpressionSaver, Op>>> OPERATORS = ImmutableList.of(
        // Raise does come above arithmetic, because I think it is more likely that 1 * 2 ^ 3 is actually 1 * (2 ^ 3)
        ImmutableList.of(
            new OperatorExpressionInfo<Expression, ExpressionSaver, Op>(
                opD(Op.RAISE, "op.raise")
                , (lhs, rhs, _b) -> new RaiseExpression(lhs, rhs))
        ),

        // Arithmetic operators are all one group.  I know we could separate +- from */, but if you see
        // an expression like 1 + 2 * 3, I'm not sure either bracketing is obviously more likely than the other.
        ImmutableList.of(
            new OperatorExpressionInfo<>(ImmutableList.of(
                opD(Op.ADD, "op.plus"),
                opD(Op.SUBTRACT, "op.minus")
            ), ExpressionSaver::makeAddSubtract),
            new OperatorExpressionInfo<Expression, ExpressionSaver, Op>(ImmutableList.of(
                opD(Op.MULTIPLY, "op.times")
            ), ExpressionSaver::makeTimes),
            new OperatorExpressionInfo<Expression, ExpressionSaver, Op>(
                opD(Op.DIVIDE, "op.divide")
                , (lhs, rhs, _b) -> new DivideExpression(lhs, rhs))
        ),

        // String concatenation lower than arithmetic.  If you write "val: (" ; 1 * 2; ")" then what you meant
        // is "val: (" ; to.string(1 * 2); ")" which requires an extra function call, but bracketing the arithmetic
        // will be the first step, and much more likely than ("val: (" ; 1) * (2; ")")
        ImmutableList.of(
            new OperatorExpressionInfo<>(ImmutableList.of(
                opD(Op.STRING_CONCAT, "op.stringConcat")
            ), ExpressionSaver::makeStringConcat)
        ),

        // It's moot really whether this is before or after string concat, but feels odd putting them in same group:
        ImmutableList.of(
            new OperatorExpressionInfo<Expression, ExpressionSaver, Op>(
                opD(Op.PLUS_MINUS, "op.plusminus")
                , (lhs, rhs, _b) -> new PlusMinusPatternExpression(lhs, rhs))
        ),

        // Equality and comparison operators:
        ImmutableList.of(
            new OperatorExpressionInfo<>(ImmutableList.of(
                opD(Op.EQUALS, "op.equal")
            ), ExpressionSaver::makeEqual),
            new OperatorExpressionInfo<Expression, ExpressionSaver, Op>(
                opD(Op.NOT_EQUAL, "op.notEqual")
                , (lhs, rhs, _b) -> new NotEqualExpression(lhs, rhs)),
            new OperatorExpressionInfo<>(ImmutableList.of(
                opD(Op.LESS_THAN, "op.lessThan"),
                opD(Op.LESS_THAN_OR_EQUAL, "op.lessThanOrEqual")
            ), ExpressionSaver::makeComparisonLess),
            new OperatorExpressionInfo<>(ImmutableList.of(
                opD(Op.GREATER_THAN, "op.greaterThan"),
                opD(Op.GREATER_THAN_OR_EQUAL, "op.greaterThanOrEqual")
            ), ExpressionSaver::makeComparisonGreater)
        ),

        // Boolean and, or expressions come near-last.  If you see a = b & c = d, it's much more likely you wanted (a = b) & (c = d) than
        // a = (b & c) = d.
        ImmutableList.of(
            new OperatorExpressionInfo<>(ImmutableList.of(
                opD(Op.AND, "op.and")
            ), ExpressionSaver::makeAnd),
            new OperatorExpressionInfo<Expression, ExpressionSaver, Op>(ImmutableList.of(
                opD(Op.OR, "op.or")
            ), ExpressionSaver::makeOr)
        ),

        // But the very last is the comma separator.  If you see (a & b, c | d), almost certain that you want a tuple
        // like that, rather than a & (b, c) | d.  Especially since tuples can't be fed to any binary operators besides comparison!
        ImmutableList.of(
            new OperatorExpressionInfo<Expression, ExpressionSaver, Op>(
                opD(Op.COMMA, "op.separator")
                , (lhs, rhs, _b) -> /* Dummy, see below: */ lhs)
            {
                @Override
                public OperatorSection<Expression, ExpressionSaver, Op> makeOperatorSection(int operatorSetPrecedence, Op initialOperator, int initialIndex)
                {
                    return new NaryOperatorSection<Expression, ExpressionSaver, Op>(operators, operatorSetPrecedence, /* Dummy: */ (args, _ops, bracketedStatus) -> {
                        switch (bracketedStatus)
                        {
                            case DIRECT_SQUARE_BRACKETED:
                                return new ArrayExpression(args);
                            case DIRECT_ROUND_BRACKETED:
                                return new TupleExpression(args);
                        }
                        return null;
                    }, initialIndex, initialOperator);

                }
            }
        )
    );

    private static Expression makeAddSubtract(ImmutableList<@Recorded Expression> args, List<Op> ops, BracketedStatus _b)
    {
        return new AddSubtractExpression(args, Utility.mapList(ops, op -> op.equals(Op.ADD) ? AddSubtractOp.ADD : AddSubtractOp.SUBTRACT));
    }

    private static Expression makeTimes(ImmutableList<@Recorded Expression> args, List<Op> _ops, BracketedStatus _b)
    {
        return new TimesExpression(args);
    }

    private static Expression makeStringConcat(ImmutableList<@Recorded Expression> args, List<Op> _ops, BracketedStatus _b)
    {
        return new StringConcatExpression(args);
    }

    private static Expression makeEqual(ImmutableList<@Recorded Expression> args, List<Op> _ops, BracketedStatus _b)
    {
        return new EqualExpression(args);
    }

    private static Expression makeComparisonLess(ImmutableList<@Recorded Expression> args, List<Op> ops, BracketedStatus _b)
    {
        return new ComparisonExpression(args, Utility.mapListI(ops, op -> op.equals(Op.LESS_THAN) ? ComparisonOperator.LESS_THAN : ComparisonOperator.LESS_THAN_OR_EQUAL_TO));
    }

    private static Expression makeComparisonGreater(ImmutableList<@Recorded Expression> args, List<Op> ops, BracketedStatus _b)
    {
        return new ComparisonExpression(args, Utility.mapListI(ops, op -> op.equals(Op.GREATER_THAN) ? ComparisonOperator.GREATER_THAN : ComparisonOperator.GREATER_THAN_OR_EQUAL_TO));
    }

    private static Expression makeAnd(ImmutableList<@Recorded Expression> args, List<Op> _ops, BracketedStatus _b)
    {
        return new AndExpression(args);
    }

    private static Expression makeOr(ImmutableList<@Recorded Expression> args, List<Op> _ops, BracketedStatus _b)
    {
        return new OrExpression(args);
    }


    @Override
    @OnThread(Tag.Any)
    public <EXPRESSION> void recordError(EXPRESSION src, StyledString error)
    {
        errorDisplayerRecord.getRecorder().recordError(src, error);
    }
    
    @Override
    @OnThread(Tag.Any)
    public <EXPRESSION extends StyledShowable, SEMANTIC_PARENT> void recordQuickFixes(EXPRESSION src, List<QuickFix<EXPRESSION, SEMANTIC_PARENT>> quickFixes)
    {
        errorDisplayerRecord.getRecorder().recordQuickFixes(src, quickFixes);
    }

    @Override
    @OnThread(Tag.Any)
    public @Recorded @NonNull TypeExp recordTypeNN(Expression expression, @NonNull TypeExp typeExp)
    {
        return errorDisplayerRecord.getRecorder().recordTypeNN(expression, typeExp);
    }    
}
