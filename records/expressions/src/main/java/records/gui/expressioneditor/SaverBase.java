package records.gui.expressioneditor;

import annotation.recorded.qual.Recorded;
import annotation.recorded.qual.UnknownIfRecorded;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.initialization.qual.Initialized;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.transformations.expression.BracketedStatus;
import records.transformations.expression.LoadableExpression;
import records.transformations.expression.QuickFix;
import styled.StyledShowable;
import styled.StyledString;
import utility.Either;
import utility.FXPlatformConsumer;
import utility.Pair;
import utility.Utility;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Stack;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

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
    /**
     * Gets all special keywords available in child operators,
     * e.g. "then", paired with their description.
     */
    //default ImmutableList<Pair<String, @Localized String>> operatorKeywords()
    //{
    //    return ImmutableList.of();
    //}

    private static <EXPRESSION, OP> ImmutableList<Either<OP, @Recorded EXPRESSION>> interleave(ImmutableList<@Recorded EXPRESSION> expressions, ImmutableList<OP> ops)
    {
        ImmutableList.Builder<Either<OP, @Recorded EXPRESSION>> r = ImmutableList.builder();

        for (int i = 0; i < expressions.size(); i++)
        {
            r.add(Either.right(expressions.get(i)));
            if (i < ops.size())
                r.add(Either.left(ops.get(i)));
        }
        
        return r.build();
    }

    /**
     * Can this direct child node declare a variable?  i.e. is it part of a pattern?
     */
    //boolean canDeclareVariable(EEDisplayNode chid);

    public static interface MakeNary<EXPRESSION extends StyledShowable, SAVER, OP>
    {
        // Only called if the list is valid (one more expression than operators, strictly interleaved
        public @Nullable EXPRESSION makeNary(ImmutableList<@Recorded EXPRESSION> expressions, List<OP> operators, BracketAndNodes<EXPRESSION, SAVER> bracketedStatus);
    }

    public static interface MakeBinary<EXPRESSION extends StyledShowable, SAVER>
    {
        public EXPRESSION makeBinary(@Recorded EXPRESSION lhs, @Recorded EXPRESSION rhs, BracketAndNodes<EXPRESSION, SAVER> bracketedStatus);
    }

    // One set of operators that can be used to make a particular expression
    protected class OperatorExpressionInfo
    {
        public final ImmutableList<Pair<OP, @Localized String>> operators;
        public final Either<MakeNary<EXPRESSION, SAVER, OP>, MakeBinary<EXPRESSION, SAVER>> makeExpression;

        public OperatorExpressionInfo(ImmutableList<Pair<OP, @Localized String>> operators, MakeNary<EXPRESSION, SAVER, OP> makeExpression)
        {
            this.operators = operators;
            this.makeExpression = Either.left(makeExpression);
        }

        // I know it's a bit odd, but we distinguish the N-ary constructor from the binary constructor by 
        // making the operators a non-list here.  The only expression with multiple operators
        // is add-subtract, which is N-ary.  And you can't have a binary expression with multiple different operators...
        protected OperatorExpressionInfo(Pair<OP, @Localized String> operator, MakeBinary<EXPRESSION, SAVER> makeExpression)
        {
            this.operators = ImmutableList.of(operator);
            this.makeExpression = Either.right(makeExpression);
        }

        public boolean includes(@NonNull OP op)
        {
            return operators.stream().anyMatch(p -> op.equals(p.getFirst()));
        }

        public OperatorSection makeOperatorSection(ErrorDisplayerRecord errorDisplayerRecord, int operatorSetPrecedence, OP initialOperator, int initialIndex)
        {
            return makeExpression.either(
                nAry -> new NaryOperatorSection(errorDisplayerRecord, operators, operatorSetPrecedence, nAry, initialIndex, initialOperator),
                binary -> new BinaryOperatorSection(errorDisplayerRecord, operators, operatorSetPrecedence, binary, initialIndex)
            );
        }
    }

    public abstract class OperatorSection
    {
        protected final ErrorDisplayerRecord errorDisplayerRecord;
        protected final ImmutableList<Pair<OP, @Localized String>> possibleOperators;
        // The ordering in the candidates list:
        public final int operatorSetPrecedence;

        protected OperatorSection(ErrorDisplayerRecord errorDisplayerRecord, ImmutableList<Pair<OP, @Localized String>> possibleOperators, int operatorSetPrecedence)
        {
            this.errorDisplayerRecord = errorDisplayerRecord;
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
        abstract @Nullable @Recorded EXPRESSION makeExpression(ImmutableList<@Recorded EXPRESSION> expressions, BracketAndNodes<EXPRESSION, SAVER> brackets);

        abstract int getFirstOperandIndex();

        abstract int getLastOperandIndex();
        
        abstract @Nullable @Recorded EXPRESSION makeExpressionReplaceLHS(@Recorded EXPRESSION lhs, ImmutableList<@Recorded EXPRESSION> allOriginalExps, BracketAndNodes<EXPRESSION, SAVER> brackets);
        abstract @Nullable @Recorded EXPRESSION makeExpressionReplaceRHS(@Recorded EXPRESSION rhs, ImmutableList<@Recorded EXPRESSION> allOriginalExps, BracketAndNodes<EXPRESSION, SAVER> brackets);
    }

    protected final class BinaryOperatorSection extends OperatorSection
    {
        protected final MakeBinary<EXPRESSION, SAVER> makeExpression;
        private final int operatorIndex;

        BinaryOperatorSection(ErrorDisplayerRecord errorDisplayerRecord, ImmutableList<Pair<OP, @Localized String>> operators, int candidatePrecedence, MakeBinary<EXPRESSION, SAVER> makeExpression, int initialIndex)
        {
            super(errorDisplayerRecord, operators, candidatePrecedence);
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
        @Recorded EXPRESSION makeExpression(ImmutableList<@Recorded EXPRESSION> expressions, BracketAndNodes<EXPRESSION, SAVER> brackets)
        {
            return makeBinary(expressions.get(operatorIndex), expressions.get(operatorIndex + 1), brackets);
        }

        protected @Recorded EXPRESSION makeBinary(@Recorded EXPRESSION lhs, @Recorded EXPRESSION rhs, BracketAndNodes<EXPRESSION, SAVER> brackets)
        {
            return record(brackets.start, brackets.end, makeExpression.makeBinary(lhs, rhs, brackets));
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
        @Recorded EXPRESSION makeExpressionReplaceLHS(@Recorded EXPRESSION lhs, ImmutableList<@Recorded EXPRESSION> expressions, BracketAndNodes<EXPRESSION, SAVER> brackets)
        {
            return makeBinary(lhs , expressions.get(operatorIndex + 1), brackets);
        }

        @Override
        @Recorded EXPRESSION makeExpressionReplaceRHS(@Recorded EXPRESSION rhs, ImmutableList<@Recorded EXPRESSION> expressions, BracketAndNodes<EXPRESSION, SAVER> brackets)
        {
            return makeBinary(expressions.get(operatorIndex), rhs, brackets);
        }
    }

    protected final class NaryOperatorSection extends OperatorSection
    {
        protected final MakeNary<EXPRESSION, SAVER, OP> makeExpression;
        private final ArrayList<OP> actualOperators = new ArrayList<>();
        private final int startingOperatorIndexIncl;
        private int endingOperatorIndexIncl;

        NaryOperatorSection(ErrorDisplayerRecord errorDisplayerRecord, ImmutableList<Pair<OP, @Localized String>> operators, int candidatePrecedence, MakeNary<EXPRESSION, SAVER, OP> makeExpression, int initialIndex, OP initialOperator)
        {
            super(errorDisplayerRecord, operators, candidatePrecedence);
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
        @Nullable @Recorded EXPRESSION makeExpression(ImmutableList<@Recorded EXPRESSION> expressions, BracketAndNodes<EXPRESSION, SAVER> brackets)
        {
            // Given a + b + c, if end operator is 1, we want to include operand index 2, hence we pass excl index 3,
            // so it's last operator-inclusive, plus 2.
            ImmutableList<@Recorded EXPRESSION> selected = expressions.subList(startingOperatorIndexIncl, endingOperatorIndexIncl + 2);
            return makeNary(selected, actualOperators, brackets);
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
        @Nullable @Recorded EXPRESSION makeExpressionReplaceLHS(@Recorded EXPRESSION lhs, ImmutableList<@Recorded EXPRESSION> expressions, BracketAndNodes<EXPRESSION, SAVER> brackets)
        {
            ArrayList<@Recorded EXPRESSION> args = new ArrayList<>(expressions.subList(startingOperatorIndexIncl, endingOperatorIndexIncl + 2));
            args.set(0, lhs);
            return makeNary(ImmutableList.copyOf(args), actualOperators, brackets);
        }

        @Override
        @Nullable @Recorded EXPRESSION makeExpressionReplaceRHS(@Recorded EXPRESSION rhs, ImmutableList<@Recorded EXPRESSION> expressions, BracketAndNodes<EXPRESSION, SAVER> brackets)
        {
            ArrayList<@Recorded EXPRESSION> args = new ArrayList<>(expressions.subList(startingOperatorIndexIncl, endingOperatorIndexIncl + 2));
            args.set(args.size() - 1, rhs);
            return makeNary(ImmutableList.copyOf(args), actualOperators, brackets);
        }

        /**
         * Uses this expression as the LHS, and a custom middle and RHS that must have matching operators.  Used
         * to join two NaryOpExpressions while bracketing an item in the middle.
         */

        public @Nullable @Recorded EXPRESSION makeExpressionMiddleMerge(@Recorded EXPRESSION middle, NaryOperatorSection rhs, List<@Recorded EXPRESSION> expressions, BracketAndNodes<EXPRESSION, SAVER> brackets)
        {
            List<@Recorded EXPRESSION> args = new ArrayList<>();
            // Add our args, minus the end one:
            args.addAll(expressions.subList(startingOperatorIndexIncl, endingOperatorIndexIncl + 1));
            // Add the middle:
            args.add(middle);
            // Add RHS, minus the start one:
            args.addAll(expressions.subList(rhs.startingOperatorIndexIncl + 1, rhs.endingOperatorIndexIncl + 2));
            return makeExpression.makeNary(ImmutableList.copyOf(args), Utility.concatI(actualOperators, rhs.actualOperators), brackets);
        }

        protected @Nullable @Recorded EXPRESSION makeNary(ImmutableList<@Recorded EXPRESSION> expressions, List<OP> operators, BracketAndNodes<EXPRESSION, SAVER> bracketedStatus)
        {
            EXPRESSION expression = makeExpression.makeNary(expressions, operators, bracketedStatus);
            if (expression == null)
                return null;
            else
                return record(bracketedStatus.start, bracketedStatus.end, expression);
        }
    }

    /**
     * A function to give back the content of a scope being ended.
     */
    public static interface FetchContent<EXPRESSION extends StyledShowable, SAVER>
    {
        /**
         * 
         * @param bracketInfo The bracket context for the scope being ended.
         * @return The expression for the scope being ended.
         */
        @Recorded EXPRESSION fetchContent(BracketAndNodes<EXPRESSION, SAVER> bracketInfo);
    }

    /**
     * A lamba-interface like class with a method to call when you encounter
     * a keyword that should terminate the current scope.
     * 
     * Cannot be an interface because it's not static because
     * it uses many type variables from the outer SaverBase.
     */
    protected abstract class Terminator
    {
        /**
         * 
         * @param makeContent A function which, given a BracketedStatus wrapped with error displayers,
         *                    will give you the expression content of the just-finished scope. 
         * @param terminator The keyword which is terminating the current scope.
         * @param keywordErrorDisplayer The error displayer for the keyword.
         * @param keywordContext The callback with the context for the keyword.
         */
        public abstract void terminate(FetchContent<EXPRESSION, SAVER> makeContent, @Nullable KEYWORD terminator, ConsecutiveChild<EXPRESSION, SAVER> keywordErrorDisplayer, FXPlatformConsumer<CONTEXT> keywordContext);
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

    /**
     * BracketedStatus, paired with start and end for error recording purposes only.
     * @param <EXPRESSION>
     * @param <SAVER>
     */
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

        public BracketAndNodes<EXPRESSION, SAVER> withStatus(BracketedStatus status)
        {
            return new BracketAndNodes<>(status, start, end);
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

    // Only used during the hack to get the operators
    protected SaverBase()
    {
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
            public void terminate(FetchContent<EXPRESSION, SAVER> makeContent, @Nullable KEYWORD terminator, ConsecutiveChild<EXPRESSION, SAVER> keywordErrorDisplayer, FXPlatformConsumer<CONTEXT> keywordContext)
            {
                ConsecutiveChild<EXPRESSION, SAVER> start = parent.getAllChildren().get(0);
                ConsecutiveChild<EXPRESSION, SAVER> end = keywordErrorDisplayer;
                end.addErrorAndFixes(StyledString.s("Closing " + terminator + " without opening"), ImmutableList.of());
                @Initialized SaverBase<EXPRESSION, SAVER, OP, KEYWORD, CONTEXT> thisSaver = Utility.later(SaverBase.this);
                currentScopesFinal.peek().items.add(Either.left(makeContent.fetchContent(new BracketAndNodes<>(BracketedStatus.MISC, start, end))));
                if (terminator != null)
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
            closed.terminator.terminate(brackets -> makeExpression(closed.openingNode, brackets.end, closed.items, brackets), null, closed.openingNode, c -> {});
        }

        Scope closed = currentScopes.pop();
        BracketAndNodes<EXPRESSION, SAVER> brackets = new BracketAndNodes<>(BracketedStatus.TOP_LEVEL, closed.openingNode, errorDisplayer);
        return makeExpression(closed.openingNode, errorDisplayer, closed.items, brackets);
    }

    // Note: if we are copying to clipboard, callback will not be called
    public void saveOperand(EXPRESSION singleItem, ConsecutiveChild<EXPRESSION, SAVER> start, ConsecutiveChild<EXPRESSION, SAVER> end, FXPlatformConsumer<CONTEXT> withContext)
    {
        currentScopes.peek().items.add(Either.left(record(start, end, singleItem)));
    }

    public CollectedItems processItems(List<Either<@Recorded EXPRESSION, OpAndNode>> content)
    {
        return new CollectedItems(content);
    }

    protected class CollectedItems
    {
        private boolean valid;
        private final ArrayList<Either<OP, @Recorded EXPRESSION>> invalid;
        private final ArrayList<@Recorded EXPRESSION> validOperands;
        private final ArrayList<OP> validOperators;

        public boolean isValid()
        {
            return valid;
        }

        public ArrayList<Either<OP, EXPRESSION>> getInvalid()
        {
            return invalid;
        }

        public ArrayList<EXPRESSION> getValidOperands()
        {
            return validOperands;
        }

        public ArrayList<OP> getValidOperators()
        {
            return validOperators;
        }

        private CollectedItems(List<Either<@Recorded EXPRESSION, OpAndNode>> content)
        {
            // Although it's duplication, we keep a list for if it turns out invalid, and two lists for if it is valid:
            // Valid means that operands interleave exactly with operators, and there is an operand at beginning and end.
            valid = true;
            invalid = new ArrayList<>();
            validOperands = new ArrayList<>();
            validOperators = new ArrayList<>();

            boolean lastWasExpression[] = new boolean[] {false}; // Think of it as an invisible empty prefix operator

            for (Either<@Recorded EXPRESSION, OpAndNode> item : content)
            {
                item.either_(expression -> {
                    invalid.add(Either.right(expression));
                    validOperands.add(expression);

                    if (lastWasExpression[0])
                    {
                        // TODO missing operator error
                        valid = false;
                    }
                    lastWasExpression[0] = true;
                }, op -> {
                    invalid.add(Either.left(op.op));
                    validOperators.add(op.op);

                    if (!lastWasExpression[0])
                    {
                        // TODO missing operand error
                        valid = false;
                    }
                    lastWasExpression[0] = false;
                });
            }
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
    public @Nullable @Recorded EXPRESSION makeExpressionWithOperators(
        ImmutableList<ImmutableList<OperatorExpressionInfo>> candidates, ErrorDisplayerRecord errorDisplayerRecord,
        Function<ImmutableList<Either<@NonNull OP, @Recorded EXPRESSION>>, @Recorded EXPRESSION> makeInvalidOpExpression,
        ImmutableList<@Recorded EXPRESSION> expressionExps, ImmutableList<@NonNull OP> ops, BracketAndNodes<EXPRESSION, SAVER> brackets, Function<@Recorded EXPRESSION, @Recorded EXPRESSION> makeSingletonList)
    {
        if (ops.size() != expressionExps.size() - 1)
        {
            // Other issues: we're missing an argument!
            return null;
        }

        if (ops.isEmpty())
        {
            if (brackets.bracketedStatus == BracketedStatus.DIRECT_SQUARE_BRACKETED)
                return makeSingletonList.apply(expressionExps.get(0));
            else
                return expressionExps.get(0);
        }

        // First, split it into sections based on cohesive parts that have the same operators:
        List<OperatorSection> operatorSections = new ArrayList<>();
        nextOp: for (int i = 0; i < ops.size(); i++)
        {
            if (operatorSections.isEmpty() || !operatorSections.get(operatorSections.size() - 1).addOperator(ops.get(i), i))
            {
                // Make new section:
                for (int candidateIndex = 0; candidateIndex < candidates.size(); candidateIndex++)
                {
                    for (OperatorExpressionInfo operatorExpressionInfo : candidates.get(candidateIndex))
                    {
                        if (operatorExpressionInfo.includes(ops.get(i)))
                        {
                            operatorSections.add(operatorExpressionInfo.makeOperatorSection(errorDisplayerRecord, candidateIndex, ops.get(i), i));
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
            @Nullable @Recorded EXPRESSION single = operatorSections.get(0).makeExpression(expressionExps, brackets);
            if (single != null)
                return single;

            // Maybe with the possibility of different brackets?
            if (brackets.bracketedStatus == BracketedStatus.MISC || brackets.bracketedStatus == BracketedStatus.TOP_LEVEL)
            {
                List<EXPRESSION> possibles = new ArrayList<>();
                for (BracketedStatus status : Arrays.asList(BracketedStatus.DIRECT_ROUND_BRACKETED, BracketedStatus.DIRECT_SQUARE_BRACKETED))
                {
                    @Nullable EXPRESSION possible = operatorSections.get(0).makeExpression(expressionExps, brackets.withStatus(status));
                    if (possible != null)
                        possibles.add(possible);
                }
                if (!possibles.isEmpty())
                {
                    @Recorded EXPRESSION invalidOpExpression = makeInvalidOpExpression.apply(interleave(expressionExps, ops));
                    errorDisplayerRecord.getRecorder().recordError(invalidOpExpression, StyledString.s("Surrounding brackets required"));
                    errorDisplayerRecord.getRecorder().recordQuickFixes(invalidOpExpression, Utility.mapList(possibles, e -> new QuickFix<>("fix.bracketAs", invalidOpExpression, () -> e)));
                    return invalidOpExpression;
                }
            }
        }

        @Recorded EXPRESSION invalidOpExpression = makeInvalidOpExpression.apply(interleave(expressionExps, ops));
        errorDisplayerRecord.getRecorder().recordError(invalidOpExpression, StyledString.s("Mixed operators: brackets required"));

        if (operatorSections.size() == 3
            && operatorSections.get(0).possibleOperators.equals(operatorSections.get(2).possibleOperators)
            && operatorSections.get(0) instanceof SaverBase.NaryOperatorSection
            && operatorSections.get(2) instanceof SaverBase.NaryOperatorSection
            && operatorSections.get(1).operatorSetPrecedence <= operatorSections.get(0).operatorSetPrecedence
            )
        {
            // The sections either side match up, and the middle is same or lower precedence, so we can bracket
            // the middle and put it into one valid expression.  Hurrah!
            @SuppressWarnings("recorded")
            @Nullable @Recorded EXPRESSION middle = operatorSections.get(1).makeExpression(expressionExps, brackets);
            if (middle != null)
            {
                @Nullable @Recorded EXPRESSION replacement = ((NaryOperatorSection) operatorSections.get(0)).makeExpressionMiddleMerge(
                    middle,
                    (NaryOperatorSection) operatorSections.get(2),
                    expressionExps, brackets
                );

                if (replacement != null)
                {
                    @NonNull EXPRESSION replacementFinal = replacement;
                    errorDisplayerRecord.getRecorder().recordQuickFixes(invalidOpExpression, Collections.singletonList(
                        new QuickFix<>("fix.bracketAs", invalidOpExpression, () -> replacementFinal)
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
                OperatorSection operatorSection = operatorSections.get(i);
                if (operatorSection.operatorSetPrecedence != precedence)
                    break;

                // We try all the bracketing states, preferring un-bracketed, for valid replacements: 

                @SuppressWarnings("recorded")
                @Nullable @Recorded EXPRESSION sectionExpression = operatorSection.makeExpression(expressionExps, brackets);
                if (sectionExpression == null)
                    continue;

                // The replacement if we just bracketed this section:
                @UnknownIfRecorded EXPRESSION replacement;
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
                            brackets
                        );
                    }
                    else
                    {
                        replacement = operatorSections.get(1 - i).makeExpressionReplaceRHS(
                            sectionExpression,
                            expressionExps,
                            brackets
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
                    errorDisplayerRecord.getRecorder().recordQuickFixes(invalidOpExpression, Collections.singletonList(
                        new QuickFix<>("fix.bracketAs", invalidOpExpression, () -> replacement)
                    ));
                }
            }
        }
        return invalidOpExpression;
    }

    // Expects a keyword matching closer.  If so, call the function with the current scope's expression, and you'll get back a final expression or a
    // terminator for a new scope, compiled using the scope content and given bracketed status
    public Terminator expect(KEYWORD expected, Function<ConsecutiveChild<EXPRESSION, SAVER>, BracketAndNodes<EXPRESSION, SAVER>> makeBrackets, BiFunction<@Recorded EXPRESSION, ConsecutiveChild<EXPRESSION, SAVER>, Either<@Recorded EXPRESSION, Terminator>> onSuccessfulClose, Supplier<ImmutableList<EXPRESSION>> prefixItemsOnFailedClose)
    {
        return new Terminator() {
            @Override
            public void terminate(FetchContent<EXPRESSION, SAVER> makeContent, @Nullable KEYWORD terminator, ConsecutiveChild<EXPRESSION, SAVER> keywordErrorDisplayer, FXPlatformConsumer<CONTEXT> keywordContext)
            {
                if (Objects.equal(expected, terminator))
                {
                    // All is well:
                    Either<@Recorded EXPRESSION, Terminator> result = onSuccessfulClose.apply(makeContent.fetchContent(makeBrackets.apply(keywordErrorDisplayer)), keywordErrorDisplayer);
                    result.either_(e -> currentScopes.peek().items.add(Either.left(e)), t -> currentScopes.push(new Scope(keywordErrorDisplayer, t)));
                }
                else
                {
                    // Error!
                    keywordErrorDisplayer.addErrorAndFixes(StyledString.s("Expected " + expected + " but found " + terminator), ImmutableList.of());
                    @Nullable ConsecutiveChild<EXPRESSION, SAVER> start = currentScopes.peek().openingNode;
                    // Important to call makeContent before adding to scope on the next line:
                    ImmutableList.Builder<Either<OP, @Recorded EXPRESSION>> items = ImmutableList.builder();
                    items.addAll(Utility.mapListI(prefixItemsOnFailedClose.get(), Either::right));
                    items.add(Either.right(makeContent.fetchContent(makeBrackets.apply(keywordErrorDisplayer))));
                    if (terminator != null)
                        items.add(Either.right(makeSingleInvalid(terminator)));
                    @Recorded EXPRESSION invalid = makeInvalidOp(start, keywordErrorDisplayer, items.build());
                    currentScopes.peek().items.add(Either.left(invalid));
                }
            }};
    }

    protected abstract EXPRESSION makeSingleInvalid(KEYWORD terminator);
}
