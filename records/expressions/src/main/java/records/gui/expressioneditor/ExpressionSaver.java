package records.gui.expressioneditor;

import annotation.recorded.qual.Recorded;
import annotation.recorded.qual.UnknownIfRecorded;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.i18n.qual.LocalizableKey;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.gui.expressioneditor.ExpressionSaver.Context;
import records.gui.expressioneditor.GeneralExpressionEntry.Keyword;
import records.gui.expressioneditor.GeneralExpressionEntry.Op;
import records.transformations.expression.AddSubtractExpression;
import records.transformations.expression.AddSubtractExpression.AddSubtractOp;
import records.transformations.expression.AndExpression;
import records.transformations.expression.ArrayExpression;
import records.transformations.expression.BracketedStatus;
import records.transformations.expression.CallExpression;
import records.transformations.expression.ComparisonExpression;
import records.transformations.expression.ComparisonExpression.ComparisonOperator;
import records.transformations.expression.DivideExpression;
import records.transformations.expression.EqualExpression;
import records.transformations.expression.ErrorAndTypeRecorder;
import records.transformations.expression.Expression;
import records.transformations.expression.IfThenElseExpression;
import records.transformations.expression.ImplicitLambdaArg;
import records.transformations.expression.InvalidOperatorExpression;
import records.transformations.expression.MatchAnythingExpression;
import records.transformations.expression.MatchExpression;
import records.transformations.expression.MatchExpression.MatchClause;
import records.transformations.expression.MatchExpression.Pattern;
import records.transformations.expression.NotEqualExpression;
import records.transformations.expression.NumericLiteral;
import records.transformations.expression.OrExpression;
import records.transformations.expression.PlusMinusPatternExpression;
import records.transformations.expression.QuickFix;
import records.transformations.expression.RaiseExpression;
import records.transformations.expression.StringConcatExpression;
import records.transformations.expression.TimesExpression;
import records.transformations.expression.TupleExpression;
import records.transformations.expression.UnitLiteralExpression;
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
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ExpressionSaver extends SaverBase<Expression, ExpressionSaver, Op, Keyword, Context> implements ErrorAndTypeRecorder
{
    class Context {}   
    
    public ExpressionSaver(ConsecutiveBase<Expression, ExpressionSaver> parent)
    {
        super(parent);
    }
    
    // Only used when getting the operators
    private ExpressionSaver()
    {
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
            currentScopes.push(new Scope(errorDisplayer, expect(Keyword.CLOSE_ROUND, close -> new BracketAndNodes<>(BracketedStatus.DIRECT_ROUND_BRACKETED, errorDisplayer, close), (bracketed, bracketEnd) -> {
                ArrayList<Either<@Recorded Expression, OpAndNode>> precedingItems = currentScopes.peek().items;
                // Function calls are a special case:
                if (precedingItems.size() >= 1 && precedingItems.get(precedingItems.size() - 1).either(ExpressionOps::isCallTarget, op -> false))
                {
                    @Nullable @Recorded Expression callTarget = precedingItems.remove(precedingItems.size() - 1).<@Nullable @Recorded Expression>either(e -> e, op -> null);
                    // Shouldn't ever be null:
                    if (callTarget != null)
                    {
                        return Either.left(errorDisplayerRecord.record(errorDisplayerRecord.recorderFor(callTarget).start, bracketEnd, new CallExpression(callTarget, bracketed)));
                    }
                }
                return Either.left(errorDisplayerRecord.record(errorDisplayer, bracketEnd, bracketed));
            })));
        }
        else if (keyword == Keyword.OPEN_SQUARE)
        {
            currentScopes.push(new Scope(errorDisplayer, expect(Keyword.CLOSE_SQUARE, close -> new BracketAndNodes<>(BracketedStatus.DIRECT_SQUARE_BRACKETED, errorDisplayer, close), (e, c) -> Either.left(e))));
        }
        else if (keyword == Keyword.IF)
        {
            currentScopes.push(new Scope(errorDisplayer, expect(Keyword.THEN, miscBrackets(errorDisplayer), (condition, conditionEnd) ->
                Either.right(expect(Keyword.ELSE, miscBrackets(conditionEnd), (thenPart, thenEnd) -> 
                    Either.right(expect(Keyword.ENDIF, miscBrackets(thenEnd), (elsePart, elseEnd) -> {
                        return Either.left(errorDisplayerRecord.record(errorDisplayer, elseEnd, new IfThenElseExpression(condition, thenPart, elsePart)));
                    })
                )    
            )))));
        }
        else if (keyword == Keyword.MATCH)
        {            
            currentScopes.push(new Scope(errorDisplayer, expectOneOf(errorDisplayer, ImmutableList.of(new Case(errorDisplayer)))));
        }
        else
        {
            // Should be a terminator:
            Scope cur = currentScopes.pop();
            if (currentScopes.size() == 0)
            {
                addTopLevelScope(errorDisplayer.getParent());
            }
            cur.terminator.terminate(brackets -> makeExpression(cur.openingNode, brackets.end, cur.items, brackets), keyword, errorDisplayer, withContext);
        }
    }

    @Override
    protected @Recorded Expression makeExpression(ConsecutiveChild<Expression, ExpressionSaver> start, ConsecutiveChild<Expression, ExpressionSaver> end, List<Either<@Recorded Expression, OpAndNode>> content, BracketAndNodes<Expression, ExpressionSaver> brackets)
    {
        if (content.isEmpty())
        {
            if (brackets.bracketedStatus == BracketedStatus.DIRECT_SQUARE_BRACKETED)
                return errorDisplayerRecord.record(brackets.start, brackets.end, new ArrayExpression(ImmutableList.of()));
            else
                return errorDisplayerRecord.record(start, end, new InvalidOperatorExpression(ImmutableList.of()));
        }
        
        CollectedItems collectedItems = processItems(content);
        
        if (collectedItems.isValid())
        {
            ArrayList<Expression> validOperands = collectedItems.getValidOperands();
            ArrayList<Op> validOperators = collectedItems.getValidOperators();
            @Nullable @Recorded Expression e;
            // Single expression?
            if (validOperands.size() == 1 && validOperators.size() == 0)
            {
                e = validOperands.get(0);
                if (brackets.bracketedStatus == BracketedStatus.DIRECT_SQUARE_BRACKETED)
                    e = errorDisplayerRecord.record(brackets.start, brackets.end, new ArrayExpression(ImmutableList.<@Recorded Expression>of(e)));
            }
            else
            {
                // Now we need to check the operators can work together as one group:

                e = makeExpressionWithOperators(OPERATORS, errorDisplayerRecord, (ImmutableList<Either<Op, @Recorded Expression>> es) -> makeInvalidOp(start, end, es), ImmutableList.copyOf(validOperands), ImmutableList.copyOf(validOperators), brackets, arg -> 
                    errorDisplayerRecord.record(brackets.start, brackets.end, new ArrayExpression(ImmutableList.of(arg)))
                );
            }
            if (e != null)
            {
                return e;
            }
            
        }
        
        return errorDisplayerRecord.record(start, end, new InvalidOperatorExpression(
            Utility.mapListI(collectedItems.getInvalid(), e -> e.mapBoth(o -> o.getContent(), x -> x))
        ));
    }

    @Override
    protected @Recorded Expression makeInvalidOp(ConsecutiveChild<Expression, ExpressionSaver> start, ConsecutiveChild<Expression, ExpressionSaver> end, ImmutableList<Either<Op, @Recorded Expression>> items)
    {
        return errorDisplayerRecord.record(start, end, new InvalidOperatorExpression(Utility.<Either<Op, @Recorded Expression>, Either<String, @Recorded Expression>>mapListI(items, x -> x.<String, @Recorded Expression>mapBoth(op -> op.getContent(), y -> y))));
    }

    @Override
    protected Expression keywordToInvalid(Keyword keyword)
    {
        return new InvalidOperatorExpression(ImmutableList.of(Either.left(keyword.getContent())));
    }

    // Expects a keyword matching closer.  If so, call the function with the current scope's expression, and you'll get back a final expression or a
    // terminator for a new scope, compiled using the scope content and given bracketed status
    public Terminator expect(Keyword expected, Function<ConsecutiveChild<Expression, ExpressionSaver>, BracketAndNodes<Expression, ExpressionSaver>> makeBrackets, BiFunction<@Recorded Expression, ConsecutiveChild<Expression, ExpressionSaver>, Either<@Recorded Expression, Terminator>> onClose)
    {
        return new Terminator() {
        @Override
        public void terminate(Function<BracketAndNodes<Expression, ExpressionSaver>, @Recorded Expression> makeContent, Keyword terminator, ConsecutiveChild<Expression, ExpressionSaver> keywordErrorDisplayer, FXPlatformConsumer<Context> keywordContext)
        {
            if (terminator == expected)
            {
                // All is well:
                Either<@Recorded Expression, Terminator> result = onClose.apply(makeContent.apply(makeBrackets.apply(keywordErrorDisplayer)), keywordErrorDisplayer);
                result.either_(e -> currentScopes.peek().items.add(Either.left(e)), t -> currentScopes.push(new Scope(keywordErrorDisplayer, t)));
            }
            else
            {
                // Error!
                keywordErrorDisplayer.addErrorAndFixes(StyledString.s("Expected " + expected + " but found " + terminator), ImmutableList.of());
                @Nullable ConsecutiveChild<Expression, ExpressionSaver> start = currentScopes.peek().openingNode;
                // Important to call makeContent before adding to scope on the next line:
                ImmutableList.Builder<Either<String, @Recorded Expression>> items = ImmutableList.builder();
                items.add(Either.right(makeContent.apply(makeBrackets.apply(keywordErrorDisplayer))));
                items.add(Either.left(terminator.getContent()));
                @Recorded InvalidOperatorExpression invalid = errorDisplayerRecord.record(start, keywordErrorDisplayer, new InvalidOperatorExpression(items.build()));
                currentScopes.peek().items.add(Either.left(invalid));
            }
        }};
    }
    
    // Looks for a keyword, then takes the expression before the keyword and gives next step.
    private abstract class Choice
    {
        public final Keyword keyword;

        protected Choice(Keyword keyword)
        {
            this.keyword = keyword;
        }

        public abstract Either<@Recorded Expression, Terminator> foundKeyword(@Recorded Expression expressionBefore, ConsecutiveChild<Expression, ExpressionSaver> node);
    }
    
    // Looks for @case
    private class Case extends Choice
    {
        private final ConsecutiveChild<Expression, ExpressionSaver> matchKeyword;
        // If null, we are the first case.  Otherwise we are a later case,
        // in which case we are Right with the given patterns
        private final @Nullable Pair<@Recorded Expression, ImmutableList<Pattern>> matchAndPatterns;
        // Previous complete clauses
        private final ImmutableList<Function<MatchExpression, MatchClause>> previousClauses;
        
        // Looks for first @case after a @match
        public Case(ConsecutiveChild<Expression, ExpressionSaver> matchKeyword)
        {
            super(Keyword.CASE);
            this.matchKeyword = matchKeyword;
            matchAndPatterns = null;
            previousClauses = ImmutableList.of();
        }
        
        // Matches a later @case, meaning we follow a @then and an outcome
        public Case(ConsecutiveChild<Expression, ExpressionSaver> matchKeyword, @Recorded Expression matchFrom, ImmutableList<Function<MatchExpression, MatchClause>> previousClauses, ImmutableList<Pattern> patternsForCur)
        {
            super(Keyword.CASE);
            this.matchKeyword = matchKeyword;
            matchAndPatterns = new Pair<>(matchFrom, patternsForCur);
            this.previousClauses = previousClauses;
        }

        @Override
        public Either<@Recorded Expression, Terminator> foundKeyword(@Recorded Expression expressionBefore, ConsecutiveChild<Expression, ExpressionSaver> node)
        {
            final @Recorded Expression m;
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
            return Either.right(expectOneOf(node, ImmutableList.of(
                new Then(matchKeyword, m, newClauses, ImmutableList.of(), Keyword.CASE),
                new Given(matchKeyword, m, newClauses, ImmutableList.of()),
                new OrCase(matchKeyword, m, newClauses, ImmutableList.of(), null)
            )));
        }
    }
    
    // Looks for @given to add a guard
    private class Given extends Choice
    {
        private final ConsecutiveChild<Expression, ExpressionSaver> matchKeyword;
        private final @Recorded Expression matchFrom;
        private final ImmutableList<Function<MatchExpression, MatchClause>> previousClauses;
        private final ImmutableList<Pattern> previousCases;

        public Given(ConsecutiveChild<Expression, ExpressionSaver> matchKeyword, @Recorded Expression matchFrom, ImmutableList<Function<MatchExpression, MatchClause>> previousClauses, ImmutableList<Pattern> previousCases)
        {
            super(Keyword.GIVEN);
            this.matchKeyword = matchKeyword;
            this.matchFrom = matchFrom;
            this.previousClauses = previousClauses;
            this.previousCases = previousCases;
        }

        @Override
        public Either<@Recorded Expression, Terminator> foundKeyword(@Recorded Expression expressionBefore, ConsecutiveChild<Expression, ExpressionSaver> node)
        {
            // Expression here is the pattern, which comes before the guard:
            return Either.right(expectOneOf(node, ImmutableList.of(
                new OrCase(matchKeyword, matchFrom, previousClauses, previousCases, expressionBefore),
                new Then(matchKeyword, matchFrom, previousClauses, Utility.appendToList(previousCases, new Pattern(expressionBefore, null)), Keyword.GIVEN)
            )));
        }
    }
    
    // Looks for @orcase
    private class OrCase extends Choice
    {
        private final ConsecutiveChild<Expression, ExpressionSaver> matchKeyword;
        private final @Recorded Expression matchFrom;
        private final ImmutableList<Function<MatchExpression, MatchClause>> previousClauses;
        private final ImmutableList<Pattern> previousCases;
        private final @Nullable @Recorded Expression curMatch; // if null, nothing so far, if non-null we are a guard

        private OrCase(ConsecutiveChild<Expression, ExpressionSaver> matchKeyword, @Recorded Expression matchFrom, ImmutableList<Function<MatchExpression, MatchClause>> previousClauses, ImmutableList<Pattern> previousCases, @Nullable @Recorded Expression curMatch)
        {
            super(Keyword.ORCASE);
            this.matchKeyword = matchKeyword;
            this.matchFrom = matchFrom;
            this.previousClauses = previousClauses;
            this.previousCases = previousCases;
            this.curMatch = curMatch;
        }

        @Override
        public Either<@Recorded Expression, Terminator> foundKeyword(@Recorded Expression expressionBefore, ConsecutiveChild<Expression, ExpressionSaver> node)
        {
            ImmutableList<Pattern> newCases = Utility.appendToList(previousCases, curMatch == null ?
                // We are the pattern:
                new Pattern(expressionBefore, null) :
                // We are the guard:    
                new Pattern(curMatch, expressionBefore)
            );
            return Either.right(expectOneOf(node, ImmutableList.of(
                new Given(matchKeyword, matchFrom, previousClauses, newCases),
                new OrCase(matchKeyword, matchFrom, previousClauses, newCases, null),
                new Then(matchKeyword, matchFrom, previousClauses, newCases, Keyword.ORCASE)
            )));
        }
    }
    
    // Looks for @then (in match expressions; if-then-else is handled separately) 
    private class Then extends Choice
    {
        private final ConsecutiveChild<Expression, ExpressionSaver> matchKeywordNode;
        private final @Recorded Expression matchFrom;
        private final ImmutableList<Function<MatchExpression, MatchClause>> previousClauses;
        private final ImmutableList<Pattern> previousPatterns;
        private final Keyword precedingKeyword;
        

        // Preceding keyword may be CASE, GIVEN or ORCASE:
        private Then(ConsecutiveChild<Expression, ExpressionSaver> matchKeywordNode, @Recorded Expression matchFrom, ImmutableList<Function<MatchExpression, MatchClause>> previousClauses, ImmutableList<Pattern> previousPatterns, Keyword precedingKeyword)
        {
            super(Keyword.THEN);
            this.matchKeywordNode = matchKeywordNode;
            this.matchFrom = matchFrom;
            this.previousClauses = previousClauses;
            this.previousPatterns = previousPatterns;
            this.precedingKeyword = precedingKeyword;
        }

        @Override
        public Either<@Recorded Expression, Terminator> foundKeyword(@Recorded Expression expressionBefore, ConsecutiveChild<Expression, ExpressionSaver> node)
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
            return Either.right(expectOneOf(node, ImmutableList.of(
                new Case(matchKeywordNode, matchFrom, previousClauses, newPatterns),
                new Choice(Keyword.ENDMATCH) {
                    @Override
                    public Either<@Recorded Expression, Terminator> foundKeyword(@Recorded Expression lastExpression, ConsecutiveChild<Expression, ExpressionSaver> node)
                    {
                        MatchExpression matchExpression = new MatchExpression(matchFrom, Utility.appendToList(previousClauses, me -> me.new MatchClause(newPatterns, lastExpression)));
                        return Either.left(errorDisplayerRecord.record(matchKeywordNode, node, matchExpression));
                    }
                }
            )));
        }
    }
    

    public Terminator expectOneOf(ConsecutiveChild<Expression, ExpressionSaver> start, ImmutableList<Choice> choices)
    {
        return new Terminator()
        {
        @Override
        public void terminate(Function<BracketAndNodes<Expression, ExpressionSaver>, @Recorded Expression> makeContent, Keyword terminator, ConsecutiveChild<Expression, ExpressionSaver> keywordErrorDisplayer, FXPlatformConsumer<Context> keywordContext)
        {
            BracketAndNodes<Expression, ExpressionSaver> brackets = miscBrackets(start, keywordErrorDisplayer);
            
            for (Choice choice : choices)
            {
                if (choice.keyword.equals(terminator))
                {
                    // All is well:
                    Either<@Recorded Expression, Terminator> result = choice.foundKeyword(makeContent.apply(brackets), keywordErrorDisplayer);
                    result.either_(e -> currentScopes.peek().items.add(Either.left(e)), t -> currentScopes.push(new Scope(keywordErrorDisplayer, t)));
                    return;
                }
            }
            
            // Error!
            keywordErrorDisplayer.addErrorAndFixes(StyledString.s("Expected " + choices.stream().map(e -> e.keyword.getContent()).collect(Collectors.joining(" or ")) + " but found " + terminator), ImmutableList.of());
            // Important to call makeContent before adding to scope on the next line:
            ImmutableList.Builder<Either<String, @Recorded Expression>> items = ImmutableList.builder();
            items.add(Either.right(makeContent.apply(brackets)));
            items.add(Either.left(terminator.getContent()));
            @Recorded InvalidOperatorExpression invalid = errorDisplayerRecord.record(start, keywordErrorDisplayer, new InvalidOperatorExpression(items.build()));
            currentScopes.peek().items.add(Either.left(invalid));
        }};
    }


    @Override
    public void saveOperand(@UnknownIfRecorded Expression singleItem, ConsecutiveChild<Expression, ExpressionSaver> start, ConsecutiveChild<Expression,ExpressionSaver> end, FXPlatformConsumer<Context> withContext)
    {
        ArrayList<Either<@Recorded Expression, OpAndNode>> curItems = currentScopes.peek().items;
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
        
        super.saveOperand(singleItem, start, end, withContext);
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

    private static Pair<Op, @Localized String> opD(Op op, @LocalizableKey String key)
    {
        return new Pair<>(op, TranslationUtility.getString(key));
    }
    
    // Remember: earlier means more likely to be inner-bracketed.  Outer list is groups of operators
    // with equal bracketing likelihood/precedence.
    @SuppressWarnings("recorded")
    final ImmutableList<ImmutableList<OperatorExpressionInfo>> OPERATORS = ImmutableList.of(
        // Raise does come above arithmetic, because I think it is more likely that 1 * 2 ^ 3 is actually 1 * (2 ^ 3)
        ImmutableList.of(
            new OperatorExpressionInfo(
                opD(Op.RAISE, "op.raise")
                , (lhs, rhs, _b) -> new RaiseExpression(lhs, rhs))
        ),

        // Arithmetic operators are all one group.  I know we could separate +- from */, but if you see
        // an expression like 1 + 2 * 3, I'm not sure either bracketing is obviously more likely than the other.
        ImmutableList.of(
            new OperatorExpressionInfo(ImmutableList.of(
                opD(Op.ADD, "op.plus"),
                opD(Op.SUBTRACT, "op.minus")
            ), ExpressionSaver::makeAddSubtract),
            new OperatorExpressionInfo(ImmutableList.of(
                opD(Op.MULTIPLY, "op.times")
            ), ExpressionSaver::makeTimes),
            new OperatorExpressionInfo(
                opD(Op.DIVIDE, "op.divide")
                , (lhs, rhs, _b) -> new DivideExpression(lhs, rhs))
        ),

        // String concatenation lower than arithmetic.  If you write "val: (" ; 1 * 2; ")" then what you meant
        // is "val: (" ; to.string(1 * 2); ")" which requires an extra function call, but bracketing the arithmetic
        // will be the first step, and much more likely than ("val: (" ; 1) * (2; ")")
        ImmutableList.of(
            new OperatorExpressionInfo(ImmutableList.of(
                opD(Op.STRING_CONCAT, "op.stringConcat")
            ), ExpressionSaver::makeStringConcat)
        ),

        // It's moot really whether this is before or after string concat, but feels odd putting them in same group:
        ImmutableList.of(
            new OperatorExpressionInfo(
                opD(Op.PLUS_MINUS, "op.plusminus")
                , (lhs, rhs, _b) -> new PlusMinusPatternExpression(lhs, rhs))
        ),

        // Equality and comparison operators:
        ImmutableList.of(
            new OperatorExpressionInfo(ImmutableList.of(
                opD(Op.EQUALS, "op.equal")
            ), ExpressionSaver::makeEqual),
            new OperatorExpressionInfo(
                opD(Op.NOT_EQUAL, "op.notEqual")
                , (lhs, rhs, _b) -> new NotEqualExpression(lhs, rhs)),
            new OperatorExpressionInfo(ImmutableList.of(
                opD(Op.LESS_THAN, "op.lessThan"),
                opD(Op.LESS_THAN_OR_EQUAL, "op.lessThanOrEqual")
            ), ExpressionSaver::makeComparisonLess),
            new OperatorExpressionInfo(ImmutableList.of(
                opD(Op.GREATER_THAN, "op.greaterThan"),
                opD(Op.GREATER_THAN_OR_EQUAL, "op.greaterThanOrEqual")
            ), ExpressionSaver::makeComparisonGreater)
        ),

        // Boolean and, or expressions come near-last.  If you see a = b & c = d, it's much more likely you wanted (a = b) & (c = d) than
        // a = (b & c) = d.
        ImmutableList.of(
            new OperatorExpressionInfo(ImmutableList.of(
                opD(Op.AND, "op.and")
            ), ExpressionSaver::makeAnd),
            new OperatorExpressionInfo(ImmutableList.of(
                opD(Op.OR, "op.or")
            ), ExpressionSaver::makeOr)
        ),

        // But the very last is the comma separator.  If you see (a & b, c | d), almost certain that you want a tuple
        // like that, rather than a & (b, c) | d.  Especially since tuples can't be fed to any binary operators besides comparison!
        ImmutableList.of(
            new OperatorExpressionInfo(
                opD(Op.COMMA, "op.separator")
                , (lhs, rhs, _b) -> /* Dummy, see below: */ lhs)
            {
                @Override
                public OperatorSection makeOperatorSection(ErrorDisplayerRecord errorDisplayerRecord, int operatorSetPrecedence, Op initialOperator, int initialIndex)
                {
                    return new NaryOperatorSection(errorDisplayerRecord, operators, operatorSetPrecedence, /* Dummy: */ (args, _ops, brackets) -> {
                        switch (brackets.bracketedStatus)
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

    private static Expression makeAddSubtract(ImmutableList<@Recorded Expression> args, List<Op> ops, BracketAndNodes brackets)
    {
        return new AddSubtractExpression(args, Utility.mapList(ops, op -> op.equals(Op.ADD) ? AddSubtractOp.ADD : AddSubtractOp.SUBTRACT));
    }

    private static Expression makeTimes(ImmutableList<@Recorded Expression> args, List<Op> _ops, BracketAndNodes brackets)
    {
        return new TimesExpression(args);
    }

    private static Expression makeStringConcat(ImmutableList<@Recorded Expression> args, List<Op> _ops, BracketAndNodes brackets)
    {
        return new StringConcatExpression(args);
    }

    private static Expression makeEqual(ImmutableList<@Recorded Expression> args, List<Op> _ops, BracketAndNodes brackets)
    {
        return new EqualExpression(args);
    }

    private static Expression makeComparisonLess(ImmutableList<@Recorded Expression> args, List<Op> ops, BracketAndNodes brackets)
    {
        return new ComparisonExpression(args, Utility.mapListI(ops, op -> op.equals(Op.LESS_THAN) ? ComparisonOperator.LESS_THAN : ComparisonOperator.LESS_THAN_OR_EQUAL_TO));
    }

    private static Expression makeComparisonGreater(ImmutableList<@Recorded Expression> args, List<Op> ops, BracketAndNodes brackets)
    {
        return new ComparisonExpression(args, Utility.mapListI(ops, op -> op.equals(Op.GREATER_THAN) ? ComparisonOperator.GREATER_THAN : ComparisonOperator.GREATER_THAN_OR_EQUAL_TO));
    }

    private static Expression makeAnd(ImmutableList<@Recorded Expression> args, List<Op> _ops, BracketAndNodes brackets)
    {
        return new AndExpression(args);
    }

    private static Expression makeOr(ImmutableList<@Recorded Expression> args, List<Op> _ops, BracketAndNodes brackets)
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

    @Override
    protected Expression record(ConsecutiveChild<Expression, ExpressionSaver> start, ConsecutiveChild<Expression, ExpressionSaver> end, Expression expression)
    {
        return errorDisplayerRecord.record(start, end, expression);
    }
    
    public static ImmutableList<ImmutableList<OperatorExpressionInfo>> getOperators()
    {
        return new ExpressionSaver().OPERATORS;
    }
}
