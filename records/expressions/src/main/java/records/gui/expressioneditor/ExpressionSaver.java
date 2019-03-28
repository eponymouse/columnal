package records.gui.expressioneditor;

import annotation.recorded.qual.Recorded;
import annotation.recorded.qual.UnknownIfRecorded;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import javafx.scene.input.DataFormat;
import org.checkerframework.checker.i18n.qual.LocalizableKey;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.PolyNull;
import records.data.TableAndColumnRenames;
import records.gui.expressioneditor.ErrorDisplayerRecord.Span;
import records.gui.expressioneditor.ExpressionSaver.BracketContent;
import records.gui.expressioneditor.ExpressionSaver.Context;
import records.gui.expressioneditor.GeneralExpressionEntry.Keyword;
import records.gui.expressioneditor.GeneralExpressionEntry.Op;
import records.transformations.expression.*;
import records.transformations.expression.AddSubtractExpression.AddSubtractOp;
import records.transformations.expression.ComparisonExpression.ComparisonOperator;
import records.transformations.expression.MatchExpression.MatchClause;
import records.transformations.expression.MatchExpression.Pattern;
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
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ExpressionSaver extends SaverBase<Expression, ExpressionSaver, Op, Keyword, Context, BracketContent> implements ErrorAndTypeRecorder
{
    public class Context {}
    
    public class BracketContent
    {
        private final ImmutableList<@Recorded Expression> expressions;

        public BracketContent(ImmutableList<@Recorded Expression> expressions)
        {
            this.expressions = expressions;
        }
    }
    
    public ExpressionSaver(ConsecutiveBase<Expression, ExpressionSaver> parent, boolean showFoundErrors)
    {
        super(parent, showFoundErrors);
    }
    
    // Only used when getting the operators
    private ExpressionSaver()
    {
    }

    @Override
    protected BracketAndNodes<Expression, ExpressionSaver, BracketContent> expectSingle(@UnknownInitialization(Object.class) ExpressionSaver this, ErrorDisplayerRecord errorDisplayerRecord, ConsecutiveChild<Expression, ExpressionSaver> start, ConsecutiveChild<Expression, ExpressionSaver> end)
    {
        return new BracketAndNodes<>(new ApplyBrackets<BracketContent, Expression>()
        {
            @Override
            public @Nullable @Recorded Expression apply(@NonNull BracketContent items)
            {
                if (items.expressions.size() == 1)
                    return items.expressions.get(0);
                else
                    return null;
            }

            @Override
            public @NonNull @Recorded Expression applySingle(@NonNull @Recorded Expression singleItem)
            {
                return singleItem;
            }
        }, start, end, ImmutableList.of(tupleBracket(errorDisplayerRecord, start, end), makeList(errorDisplayerRecord, start, end)));
    }
    
    private ApplyBrackets<BracketContent, Expression> makeList(@UnknownInitialization(Object.class) ExpressionSaver this, ErrorDisplayerRecord errorDisplayerRecord, ConsecutiveChild<Expression, ExpressionSaver> start, ConsecutiveChild<Expression, ExpressionSaver> end)
    {
        return new ApplyBrackets<BracketContent, Expression>()
        {
            @Override
            public @Nullable @Recorded Expression apply(@NonNull BracketContent items)
            {
                return errorDisplayerRecord.record(start, end, new ArrayExpression(items.expressions));
            }

            @Override
            public @NonNull @Recorded Expression applySingle(@NonNull @Recorded Expression singleItem)
            {
                return errorDisplayerRecord.record(start, end, new ArrayExpression(ImmutableList.<@Recorded Expression>of(singleItem)));
            }
        };
    }

    // Note: if we are copying to clipboard, callback will not be called
    public void saveKeyword(Keyword keyword, ConsecutiveChild<Expression, ExpressionSaver> errorDisplayer, FXPlatformConsumer<Context> withContext)
    {
        Supplier<ImmutableList<@Recorded Expression>> prefixKeyword = () -> ImmutableList.of(record(errorDisplayer, errorDisplayer, new InvalidIdentExpression(keyword.getContent())));
        
        if (keyword == Keyword.QUEST)
        {
            saveOperand(new ImplicitLambdaArg(), errorDisplayer, errorDisplayer, withContext);
        }
        else if (keyword == Keyword.OPEN_ROUND)
        {
            Supplier<ImmutableList<@Recorded Expression>> invalidPrefix = prefixKeyword;
            Function<ConsecutiveChild<Expression, ExpressionSaver>, ApplyBrackets<BracketContent, Expression>> applyBrackets = c -> tupleBracket(errorDisplayerRecord, errorDisplayer, c);
            ArrayList<Either<@Recorded Expression, OpAndNode>> precedingItems = currentScopes.peek().items;
            // Function calls are a special case:
            if (precedingItems.size() >= 1 && precedingItems.get(precedingItems.size() - 1).either(ExpressionOps::isCallTarget, op -> false))
            {
                @Nullable @Recorded Expression callTarget = precedingItems.remove(precedingItems.size() - 1).<@Nullable @Recorded Expression>either(e -> e, op -> null);
                // Shouldn't ever be null:
                if (callTarget != null)
                {
                    applyBrackets = c -> new ApplyBrackets<BracketContent, Expression>()
                    {
                        @Override
                        public @Nullable @Recorded Expression apply(@NonNull BracketContent args)
                        {
                            return errorDisplayerRecord.record(errorDisplayerRecord.recorderFor(callTarget).start, c, new CallExpression(callTarget, args.expressions));
                        }

                        @Override
                        public @NonNull @Recorded Expression applySingle(@NonNull @Recorded Expression singleItem)
                        {
                            return errorDisplayerRecord.record(errorDisplayerRecord.recorderFor(callTarget).start, c, new CallExpression(callTarget, ImmutableList.<@Recorded Expression>of(singleItem)));
                        }
                    };
                    invalidPrefix = () -> {
                        return Utility.prependToList(callTarget, prefixKeyword.get());
                    };
                }
            }
            Function<ConsecutiveChild<Expression, ExpressionSaver>, ApplyBrackets<BracketContent, Expression>> applyBracketsFinal = applyBrackets;
            currentScopes.push(new Scope(errorDisplayer, expect(Keyword.CLOSE_ROUND, close -> new BracketAndNodes<>(applyBracketsFinal.apply(close), errorDisplayer, close, ImmutableList.of()), (bracketed, bracketEnd) -> {
                return Either.<@Recorded Expression, Terminator>left(errorDisplayerRecord.record(errorDisplayer, bracketEnd, bracketed));
            }, invalidPrefix)));
        }
        else if (keyword == Keyword.OPEN_SQUARE)
        {
            currentScopes.push(new Scope(errorDisplayer,
                expect(Keyword.CLOSE_SQUARE,
                    close -> new BracketAndNodes<Expression, ExpressionSaver, BracketContent>(makeList(errorDisplayerRecord, errorDisplayer, close), errorDisplayer, close, ImmutableList.of()),
                    (e, c) -> Either.<@Recorded Expression, Terminator>left(e), prefixKeyword)));
        }
        else if (keyword == Keyword.IF)
        {
            ImmutableList.Builder<@Recorded Expression> invalid = ImmutableList.builder();
            invalid.addAll(prefixKeyword.get());
            currentScopes.push(new Scope(errorDisplayer, expect(Keyword.THEN, miscBrackets(errorDisplayer), (condition, conditionEnd) -> {
                invalid.add(condition);
                invalid.add(record(conditionEnd, conditionEnd, new InvalidIdentExpression(Keyword.THEN.getContent())));
                return Either.right(expect(Keyword.ELSE, miscBrackets(conditionEnd), (thenPart, thenEnd) -> {
                    invalid.add(thenPart);
                    invalid.add(record(thenEnd, thenEnd, new InvalidIdentExpression(Keyword.ELSE.getContent())));
                    return Either.right(expect(Keyword.ENDIF, miscBrackets(thenEnd), (elsePart, elseEnd) -> {
                        return Either.<@Recorded Expression, Terminator>left(errorDisplayerRecord.record(errorDisplayer, elseEnd, new IfThenElseExpression(condition, thenPart, elsePart)));
                    }, invalid::build));
                }, invalid::build));
            }, invalid::build)));
        }
        else if (keyword == Keyword.MATCH)
        {            
            currentScopes.push(new Scope(errorDisplayer, expectOneOf(errorDisplayer, ImmutableList.of(new Case(errorDisplayer)), Stream.<Supplier<@Recorded Expression>>of(() -> record(errorDisplayer, errorDisplayer, new InvalidIdentExpression(keyword.getContent()))))));
        }
        else
        {
            // Should be a terminator:
            Scope cur = currentScopes.pop();
            if (currentScopes.size() == 0)
            {
                addTopLevelScope(errorDisplayer.getParent());
            }
            cur.terminator.terminate(new FetchContent<Expression, ExpressionSaver, BracketContent>()
            {
                @Override
                public @Recorded Expression fetchContent(BracketAndNodes<Expression, ExpressionSaver, BracketContent> brackets)
                {
                    return ExpressionSaver.this.makeExpression(cur.openingNode, brackets.end, cur.items, brackets);
                }
            }, keyword, errorDisplayer, withContext);
        }
    }

    @Override
    protected BracketAndNodes<Expression, ExpressionSaver, BracketContent> unclosedBrackets(BracketAndNodes<Expression, ExpressionSaver, BracketContent> closed)
    {
        return new BracketAndNodes<Expression, ExpressionSaver, BracketContent>(new ApplyBrackets<BracketContent, Expression>()
        {
            @Nullable
            @Override
            public @Recorded Expression apply(@NonNull BracketContent items)
            {
                return record(closed.start, closed.end, new InvalidOperatorExpression(items.expressions));
            }

            @NonNull
            @Override
            public @Recorded Expression applySingle(@NonNull @Recorded Expression singleItem)
            {
                return singleItem;
            }
        }, closed.start, closed.end, ImmutableList.of(closed.applyBrackets));
    }

    private ApplyBrackets<BracketContent, Expression> tupleBracket(@UnknownInitialization(Object.class) ExpressionSaver this, ErrorDisplayerRecord errorDisplayerRecord, ConsecutiveChild<Expression, ExpressionSaver> start, ConsecutiveChild<Expression, ExpressionSaver> end)
    {
        return new ApplyBrackets<BracketContent, Expression>()
        {
            @Override
            public @Recorded @Nullable Expression apply(@NonNull BracketContent items)
            {
                if (items.expressions.size() == 1)
                    return items.expressions.get(0);
                else
                    return errorDisplayerRecord.record(start, end, new TupleExpression(items.expressions));
            }

            @Override
            public @NonNull @Recorded Expression applySingle(@NonNull @Recorded Expression singleItem)
            {
                return singleItem;
            }
        };
    }

    @Override
    protected @Recorded Expression makeExpression(ConsecutiveChild<Expression, ExpressionSaver> start, ConsecutiveChild<Expression, ExpressionSaver> end, List<Either<@Recorded Expression, OpAndNode>> content, BracketAndNodes<Expression, ExpressionSaver, BracketContent> brackets)
    {
        if (content.isEmpty())
        {
            @Nullable @Recorded Expression bracketedEmpty = brackets.applyBrackets.apply(new BracketContent(ImmutableList.of()));
            if (bracketedEmpty != null)
                return bracketedEmpty;
            else
                return errorDisplayerRecord.record(brackets.start, brackets.end, new InvalidOperatorExpression(ImmutableList.of()));
        }
        
        CollectedItems collectedItems = processItems(content);

        @Nullable @Recorded Expression e = null;
        if (collectedItems.isValid())
        {
            ArrayList<@Recorded Expression> validOperands = collectedItems.getValidOperands();
            ArrayList<OpAndNode> validOperators = collectedItems.getValidOperators();
            
            // Single expression?
            if (validOperands.size() == 1 && validOperators.size() == 0)
            {
                e = brackets.applyBrackets.apply(new BracketContent(ImmutableList.copyOf(validOperands)));
            }
            else
            {
                // Now we need to check the operators can work together as one group:
                e = makeExpressionWithOperators(OPERATORS, errorDisplayerRecord, (ImmutableList<Either<OpAndNode, @Recorded Expression>> es) -> makeInvalidOp(start, end, es), ImmutableList.copyOf(validOperands), ImmutableList.copyOf(validOperators), brackets);
            }            
        }
        
        if (e == null)
        {
            @Recorded Expression invalid = collectedItems.makeInvalid(start, end, InvalidOperatorExpression::new);
            e = brackets.applyBrackets.applySingle(invalid);
        }
        
        return e;
    }

    @Override
    protected Expression opToInvalid(Op op)
    {
        return new InvalidIdentExpression(op.getContent());
    }

    @Override
    protected @Recorded Expression makeInvalidOp(ConsecutiveChild<Expression, ExpressionSaver> start, ConsecutiveChild<Expression, ExpressionSaver> end, ImmutableList<Either<OpAndNode, @Recorded Expression>> items)
    {
        InvalidOperatorExpression invalidOperatorExpression = new InvalidOperatorExpression(Utility.<Either<OpAndNode, @Recorded Expression>, @Recorded Expression>mapListI(items, x -> x.<@Recorded Expression>either(op -> errorDisplayerRecord.record(op.sourceNode, op.sourceNode, new InvalidIdentExpression(op.op.getContent())), y -> y)));
        return errorDisplayerRecord.record(start, end, invalidOperatorExpression);
    }

    @Override
    protected Expression keywordToInvalid(Keyword keyword)
    {
        return new InvalidIdentExpression(keyword.getContent());
    }
    
    // Looks for a keyword, then takes the expression before the keyword and gives next step.
    private abstract class Choice
    {
        public final Keyword keyword;

        protected Choice(Keyword keyword)
        {
            this.keyword = keyword;
        }

        public abstract Either<@Recorded Expression, Terminator> foundKeyword(@Recorded Expression expressionBefore, ConsecutiveChild<Expression, ExpressionSaver> node, Stream<Supplier<@Recorded Expression>> prefixIfInvalid);
    }
    
    // Looks for @case
    private class Case extends Choice
    {
        private final ConsecutiveChild<Expression, ExpressionSaver> matchKeyword;
        // If null, we are the first case.  Otherwise we are a later case,
        // in which case we are Right with the given patterns
        private final @Nullable Pair<@Recorded Expression, ImmutableList<Pattern>> matchAndPatterns;
        // Previous complete clauses
        private final ImmutableList<MatchClause> previousClauses;
        
        // Looks for first @case after a @match
        public Case(ConsecutiveChild<Expression, ExpressionSaver> matchKeyword)
        {
            super(Keyword.CASE);
            this.matchKeyword = matchKeyword;
            matchAndPatterns = null;
            previousClauses = ImmutableList.of();
        }
        
        // Matches a later @case, meaning we follow a @then and an outcome
        public Case(ConsecutiveChild<Expression, ExpressionSaver> matchKeyword, @Recorded Expression matchFrom, ImmutableList<MatchClause> previousClauses, ImmutableList<Pattern> patternsForCur)
        {
            super(Keyword.CASE);
            this.matchKeyword = matchKeyword;
            matchAndPatterns = new Pair<>(matchFrom, patternsForCur);
            this.previousClauses = previousClauses;
        }

        @Override
        public Either<@Recorded Expression, Terminator> foundKeyword(@Recorded Expression expressionBefore, ConsecutiveChild<Expression, ExpressionSaver> node, Stream<Supplier<@Recorded Expression>> prefixIfInvalid)
        {
            final @Recorded Expression m;
            final ImmutableList<MatchClause> newClauses;
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
                newClauses = Utility.appendToList(previousClauses, new MatchClause(patterns, expressionBefore));
            }
            return Either.right(expectOneOf(node, ImmutableList.of(
                new Then(matchKeyword, m, newClauses, ImmutableList.of(), Keyword.CASE),
                new Given(matchKeyword, m, newClauses, ImmutableList.of()),
                new OrCase(matchKeyword, m, newClauses, ImmutableList.of(), null)
            ), prefixIfInvalid));
        }
    }
    
    // Looks for @given to add a guard
    private class Given extends Choice
    {
        private final ConsecutiveChild<Expression, ExpressionSaver> matchKeyword;
        private final @Recorded Expression matchFrom;
        private final ImmutableList<MatchClause> previousClauses;
        private final ImmutableList<Pattern> previousCases;

        public Given(ConsecutiveChild<Expression, ExpressionSaver> matchKeyword, @Recorded Expression matchFrom, ImmutableList<MatchClause> previousClauses, ImmutableList<Pattern> previousCases)
        {
            super(Keyword.GIVEN);
            this.matchKeyword = matchKeyword;
            this.matchFrom = matchFrom;
            this.previousClauses = previousClauses;
            this.previousCases = previousCases;
        }

        @Override
        public Either<@Recorded Expression, Terminator> foundKeyword(@Recorded Expression expressionBefore, ConsecutiveChild<Expression, ExpressionSaver> node, Stream<Supplier<@Recorded Expression>> prefixIfInvalid)
        {
            // Expression here is the pattern, which comes before the guard:
            return Either.right(expectOneOf(node, ImmutableList.of(
                new OrCase(matchKeyword, matchFrom, previousClauses, previousCases, expressionBefore),
                new Then(matchKeyword, matchFrom, previousClauses, Utility.appendToList(previousCases, new Pattern(expressionBefore, null)), Keyword.GIVEN)
            ), prefixIfInvalid));
        }
    }
    
    // Looks for @orcase
    private class OrCase extends Choice
    {
        private final ConsecutiveChild<Expression, ExpressionSaver> matchKeyword;
        private final @Recorded Expression matchFrom;
        private final ImmutableList<MatchClause> previousClauses;
        private final ImmutableList<Pattern> previousCases;
        private final @Nullable @Recorded Expression curMatch; // if null, nothing so far, if non-null we are a guard

        private OrCase(ConsecutiveChild<Expression, ExpressionSaver> matchKeyword, @Recorded Expression matchFrom, ImmutableList<MatchClause> previousClauses, ImmutableList<Pattern> previousCases, @Nullable @Recorded Expression curMatch)
        {
            super(Keyword.ORCASE);
            this.matchKeyword = matchKeyword;
            this.matchFrom = matchFrom;
            this.previousClauses = previousClauses;
            this.previousCases = previousCases;
            this.curMatch = curMatch;
        }

        @Override
        public Either<@Recorded Expression, Terminator> foundKeyword(@Recorded Expression expressionBefore, ConsecutiveChild<Expression, ExpressionSaver> node, Stream<Supplier<@Recorded Expression>> prefixIfInvalid)
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
            ), prefixIfInvalid));
        }
    }
    
    // Looks for @then (in match expressions; if-then-else is handled separately) 
    private class Then extends Choice
    {
        private final ConsecutiveChild<Expression, ExpressionSaver> matchKeywordNode;
        private final @Recorded Expression matchFrom;
        private final ImmutableList<MatchClause> previousClauses;
        private final ImmutableList<Pattern> previousPatterns;
        private final Keyword precedingKeyword;
        

        // Preceding keyword may be CASE, GIVEN or ORCASE:
        private Then(ConsecutiveChild<Expression, ExpressionSaver> matchKeywordNode, @Recorded Expression matchFrom, ImmutableList<MatchClause> previousClauses, ImmutableList<Pattern> previousPatterns, Keyword precedingKeyword)
        {
            super(Keyword.THEN);
            this.matchKeywordNode = matchKeywordNode;
            this.matchFrom = matchFrom;
            this.previousClauses = previousClauses;
            this.previousPatterns = previousPatterns;
            this.precedingKeyword = precedingKeyword;
        }

        @Override
        public Either<@Recorded Expression, Terminator> foundKeyword(@Recorded Expression expressionBefore, ConsecutiveChild<Expression, ExpressionSaver> node, Stream<Supplier<@Recorded Expression>> prefixIfInvalid)
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
                    public Either<@Recorded Expression, Terminator> foundKeyword(@Recorded Expression lastExpression, ConsecutiveChild<Expression, ExpressionSaver> node, Stream<Supplier<@Recorded Expression>> prefixIfInvalid)
                    {
                        MatchExpression matchExpression = new MatchExpression(matchFrom, Utility.appendToList(previousClauses, new MatchClause(newPatterns, lastExpression)));
                        return Either.<@Recorded Expression, Terminator>left(errorDisplayerRecord.record(matchKeywordNode, node, matchExpression));
                    }
                }
            ), prefixIfInvalid));
        }
    }
    

    public Terminator expectOneOf(ConsecutiveChild<Expression, ExpressionSaver> start, ImmutableList<Choice> choices, Stream<Supplier<@Recorded Expression>> prefixIfInvalid)
    {
        return new Terminator()
        {
        @Override
        public void terminate(FetchContent<Expression, ExpressionSaver, BracketContent> makeContent, @Nullable Keyword terminator, ConsecutiveChild<Expression, ExpressionSaver> keywordErrorDisplayer, FXPlatformConsumer<Context> keywordContext)
        {
            BracketAndNodes<Expression, ExpressionSaver, BracketContent> brackets = miscBrackets(start, keywordErrorDisplayer);
            
            for (Choice choice : choices)
            {
                if (choice.keyword.equals(terminator))
                {
                    // All is well:
                    @Recorded Expression expressionBefore = makeContent.fetchContent(brackets);
                    Either<@Recorded Expression, Terminator> result = choice.foundKeyword(expressionBefore, keywordErrorDisplayer, Stream.<Supplier<@Recorded Expression>>concat(prefixIfInvalid, Stream.<Supplier<@Recorded Expression>>of(() -> expressionBefore, () -> record(keywordErrorDisplayer, keywordErrorDisplayer, new InvalidIdentExpression(choice.keyword.getContent())))));
                    result.either_(e -> currentScopes.peek().items.add(Either.left(e)), t -> currentScopes.push(new Scope(keywordErrorDisplayer, t)));
                    return;
                }
            }
            
            // Error!
            if (isShowingErrors())
                keywordErrorDisplayer.addErrorAndFixes(StyledString.s("Expected " + choices.stream().map(e -> e.keyword.getContent()).collect(Collectors.joining(" or ")) + " but found " + (terminator == null ? "<end>" : terminator.getContent())), ImmutableList.of());
            // Important to call makeContent before adding to scope on the next line:
            ImmutableList.Builder<@Recorded Expression> items = ImmutableList.builder();
            items.addAll(prefixIfInvalid.<@Recorded Expression>map(s -> s.get()).collect(Collectors.<@Recorded Expression>toList()));
            items.add(makeContent.fetchContent(brackets));
            if (terminator != null)
                items.add(errorDisplayerRecord.record(keywordErrorDisplayer, keywordErrorDisplayer, new InvalidIdentExpression(terminator.getContent())));
            @Recorded InvalidOperatorExpression invalid = errorDisplayerRecord.<InvalidOperatorExpression>recordG(start, keywordErrorDisplayer, new InvalidOperatorExpression(items.build()));
            currentScopes.peek().items.add(Either.left(invalid));
        }};
    }


    @Override
    public void saveOperand(@UnknownIfRecorded Expression singleItem, ConsecutiveChild<Expression, ExpressionSaver> start, ConsecutiveChild<Expression,ExpressionSaver> end, FXPlatformConsumer<Context> withContext)
    {
        ArrayList<Either<@Recorded Expression, OpAndNode>> curItems = currentScopes.peek().items;
        if (singleItem instanceof UnitLiteralExpression && curItems.size() >= 1)
        {
            Either<@Recorded Expression, OpAndNode> recent = curItems.get(curItems.size() - 1);
            @Nullable @Recorded NumericLiteral num = recent.<@Nullable @Recorded NumericLiteral>either(e -> e instanceof NumericLiteral ? (NumericLiteral)e : null, o -> null);
            if (num != null && num.getUnitExpression() == null)
            {
                Span<Expression, ExpressionSaver> recorder = errorDisplayerRecord.recorderFor(num);
                curItems.set(curItems.size() - 1, Either.left(errorDisplayerRecord.record(recorder.start, recorder.end, new NumericLiteral(num.getNumber(), ((UnitLiteralExpression)singleItem).getUnit()))));
                return;
            }
        }
        
        super.saveOperand(singleItem, start, end, withContext);
    }

    @Override
    protected @Nullable Supplier<@Recorded Expression> canBeUnary(OpAndNode operator, @Recorded Expression followingOperand)
    {
        if (ImmutableList.of(Op.ADD, Op.SUBTRACT).contains(operator.op)
                && followingOperand instanceof NumericLiteral)
        {
            @Recorded NumericLiteral numericLiteral = (NumericLiteral) followingOperand;
            if (operator.op == Op.SUBTRACT)
                return () -> record(operator.sourceNode, errorDisplayerRecord.recorderFor(numericLiteral).end, new NumericLiteral(Utility.negate(numericLiteral.getNumber()), numericLiteral.getUnitExpression()));
            else
                return () -> numericLiteral; // No change needed for unary plus
        }
        else
            return null;
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
    final ImmutableList<ImmutableList<OperatorExpressionInfo>> OPERATORS = ImmutableList.<ImmutableList<OperatorExpressionInfo>>of(
        // Raise does come above arithmetic, because I think it is more likely that 1 * 2 ^ 3 is actually 1 * (2 ^ 3)
        ImmutableList.<OperatorExpressionInfo>of(
            new OperatorExpressionInfo(
                opD(Op.RAISE, "op.raise")
                , (lhs, _n, rhs, _b, _e) -> new RaiseExpression(lhs, rhs))
        ),

        // Arithmetic operators are all one group.  I know we could separate +- from */, but if you see
        // an expression like 1 + 2 * 3, I'm not sure either bracketing is obviously more likely than the other.
        ImmutableList.<OperatorExpressionInfo>of(
            new OperatorExpressionInfo(ImmutableList.of(
                opD(Op.ADD, "op.plus"),
                opD(Op.SUBTRACT, "op.minus")
            ), ExpressionSaver::makeAddSubtract),
            new OperatorExpressionInfo(ImmutableList.of(
                opD(Op.MULTIPLY, "op.times")
            ), ExpressionSaver::makeTimes),
            new OperatorExpressionInfo(
                opD(Op.DIVIDE, "op.divide")
                , (lhs, _n, rhs, _b, _e) -> new DivideExpression(lhs, rhs))
        ),

        // String concatenation lower than arithmetic.  If you write "val: (" ; 1 * 2; ")" then what you meant
        // is "val: (" ; to.string(1 * 2); ")" which requires an extra function call, but bracketing the arithmetic
        // will be the first step, and much more likely than ("val: (" ; 1) * (2; ")")
        ImmutableList.<OperatorExpressionInfo>of(
            new OperatorExpressionInfo(ImmutableList.of(
                opD(Op.STRING_CONCAT, "op.stringConcat")
            ), ExpressionSaver::makeStringConcat)
        ),

        // It's moot really whether this is before or after string concat, but feels odd putting them in same group:
        ImmutableList.<OperatorExpressionInfo>of(
            new OperatorExpressionInfo(
                opD(Op.PLUS_MINUS, "op.plusminus")
                , (lhs, _n, rhs, _b, _e) -> new PlusMinusPatternExpression(lhs, rhs))
        ),

        // Equality and comparison operators:
        ImmutableList.<OperatorExpressionInfo>of(
            new OperatorExpressionInfo(ImmutableList.of(
                opD(Op.EQUALS, "op.equal")
            ), ExpressionSaver::makeEqual),
            new OperatorExpressionInfo(
                opD(Op.NOT_EQUAL, "op.notEqual")
                , (lhs, _n, rhs, _b, _e) -> new NotEqualExpression(lhs, rhs)),
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
        ImmutableList.<OperatorExpressionInfo>of(
            new OperatorExpressionInfo(ImmutableList.of(
                opD(Op.AND, "op.and")
            ), ExpressionSaver::makeAnd),
            new OperatorExpressionInfo(ImmutableList.of(
                opD(Op.OR, "op.or")
            ), ExpressionSaver::makeOr)
        ),

        // But the very last is the comma separator.  If you see (a & b, c | d), almost certain that you want a tuple
        // like that, rather than a & (b, c) | d.  Especially since tuples can't be fed to any binary operators besides comparison!
        ImmutableList.<OperatorExpressionInfo>of(
            new OperatorExpressionInfo(
                opD(Op.COMMA, "op.separator")
                , (lhs, _n, rhs, _b, _e) -> /* Dummy, see below: */ lhs)
            {
                @Override
                public OperatorSection makeOperatorSection(ErrorDisplayerRecord errorDisplayerRecord, int operatorSetPrecedence, OpAndNode initialOperator, int initialIndex)
                {
                    return new NaryOperatorSection(errorDisplayerRecord, operators, operatorSetPrecedence, /* Dummy: */ (args, _ops, brackets, edr) -> {
                        return brackets.applyBrackets.apply(new BracketContent(args));
                    }, initialIndex, initialOperator);

                }
            }
        )
    );

    private static Expression makeAddSubtract(ImmutableList<@Recorded Expression> args, List<Pair<Op, ConsecutiveChild<Expression, ExpressionSaver>>> ops)
    {
        return new AddSubtractExpression(args, Utility.mapList(ops, op -> op.getFirst().equals(Op.ADD) ? AddSubtractOp.ADD : AddSubtractOp.SUBTRACT));
    }

    private static Expression makeTimes(ImmutableList<@Recorded Expression> args, List<Pair<Op, ConsecutiveChild<Expression, ExpressionSaver>>> _ops)
    {
        return new TimesExpression(args);
    }

    private static Expression makeStringConcat(ImmutableList<@Recorded Expression> args, List<Pair<Op, ConsecutiveChild<Expression, ExpressionSaver>>> _ops)
    {
        return new StringConcatExpression(args);
    }

    private static Expression makeEqual(ImmutableList<@Recorded Expression> args, List<Pair<Op, ConsecutiveChild<Expression, ExpressionSaver>>> _ops)
    {
        return new EqualExpression(args);
    }

    private static Expression makeComparisonLess(ImmutableList<@Recorded Expression> args, List<Pair<Op, ConsecutiveChild<Expression, ExpressionSaver>>> ops)
    {
        return new ComparisonExpression(args, Utility.mapListI(ops, op -> op.getFirst().equals(Op.LESS_THAN) ? ComparisonOperator.LESS_THAN : ComparisonOperator.LESS_THAN_OR_EQUAL_TO));
    }

    private static Expression makeComparisonGreater(ImmutableList<@Recorded Expression> args, List<Pair<Op, ConsecutiveChild<Expression, ExpressionSaver>>> ops)
    {
        return new ComparisonExpression(args, Utility.mapListI(ops, op -> op.getFirst().equals(Op.GREATER_THAN) ? ComparisonOperator.GREATER_THAN : ComparisonOperator.GREATER_THAN_OR_EQUAL_TO));
    }

    private static Expression makeAnd(ImmutableList<@Recorded Expression> args, List<Pair<Op, ConsecutiveChild<Expression, ExpressionSaver>>> _ops)
    {
        return new AndExpression(args);
    }

    private static Expression makeOr(ImmutableList<@Recorded Expression> args, List<Pair<Op, ConsecutiveChild<Expression, ExpressionSaver>>> _ops)
    {
        return new OrExpression(args);
    }

    @Override
    protected Span<Expression, ExpressionSaver> recorderFor(@Recorded Expression expression)
    {
        return errorDisplayerRecord.recorderFor(expression);
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
    protected @Recorded Expression record(ConsecutiveChild<Expression, ExpressionSaver> start, ConsecutiveChild<Expression, ExpressionSaver> end, Expression expression)
    {
        return errorDisplayerRecord.record(start, end, expression);
    }
    
    public static ImmutableList<ImmutableList<OperatorExpressionInfo>> getOperators()
    {
        return new ExpressionSaver().OPERATORS;
    }

    @Override
    protected Map<DataFormat, Object> toClipboard(@UnknownIfRecorded Expression expression)
    {
        return ImmutableMap.of(
            ExpressionEditor.EXPRESSION_CLIPBOARD_TYPE, expression.save(true, BracketedStatus.TOP_LEVEL, TableAndColumnRenames.EMPTY),
            DataFormat.PLAIN_TEXT, expression.save(false, BracketedStatus.TOP_LEVEL, TableAndColumnRenames.EMPTY)
        );
    }
}
