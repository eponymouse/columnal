package records.gui.expressioneditor;

import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.error.InternalException;
import records.error.UserException;
import records.gui.expressioneditor.ConsecutiveBase.BracketedStatus;
import records.transformations.expression.LoadableExpression;
import utility.Either;
import utility.Pair;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public interface OperandOps<EXPRESSION extends LoadableExpression<EXPRESSION, SEMANTIC_PARENT>, SEMANTIC_PARENT>
{
    public OperandNode<EXPRESSION, SEMANTIC_PARENT> makeGeneral(ConsecutiveBase<EXPRESSION, SEMANTIC_PARENT> parent, SEMANTIC_PARENT semanticParent, @Nullable String initialContent);

    public ImmutableList<Pair<String, @Localized String>> getValidOperators(SEMANTIC_PARENT semanticParent);

    public boolean isOperatorAlphabet(char character, SEMANTIC_PARENT semanticParent);

    public Class<EXPRESSION> getOperandClass();

    @NonNull EXPRESSION makeUnfinished(String s);

    EXPRESSION makeExpression(ErrorDisplayer<EXPRESSION> displayer, ErrorDisplayerRecord errorDisplayers, ImmutableList<EXPRESSION> expressionExps, List<String> ops, BracketedStatus bracketedStatus);

    String save(EXPRESSION expression);

    OperandNode<EXPRESSION, SEMANTIC_PARENT> loadOperand(String src, ConsecutiveBase<EXPRESSION, SEMANTIC_PARENT> parent) throws UserException, InternalException;
    
    public static interface MakeNary<EXPRESSION>
    {
        public EXPRESSION makeNary(ImmutableList<EXPRESSION> expressions, List<String> operators);
    }

    public static interface MakeBinary<EXPRESSION>
    {
        public EXPRESSION makeBinary(EXPRESSION lhs, EXPRESSION rhs);
    }
    
    // One set of operators that can be used to make a particular expression
    static class OperatorExpressionInfo<EXPRESSION>
    {
        public final ImmutableList<Pair<String, @Localized String>> operators;
        public final Either<MakeNary<EXPRESSION>, MakeBinary<EXPRESSION>> makeExpression;

        OperatorExpressionInfo(ImmutableList<Pair<String, @Localized String>> operators, MakeNary<EXPRESSION> makeExpression)
        {
            this.operators = operators;
            this.makeExpression = Either.left(makeExpression);
        }

        // I know it's a bit odd, but we distinguish the N-ary constructor from the binary constructor by 
        // making the operators a non-list here.  The only expression with multiple operators
        // is add-subtract, which is N-ary.  And you can't have a binary expression with multiple different operators...
        OperatorExpressionInfo(Pair<String, @Localized String> operator, MakeBinary<EXPRESSION> makeExpression)
        {
            this.operators = ImmutableList.of(operator);
            this.makeExpression = Either.right(makeExpression);
        }

        public boolean includes(String op)
        {
            return operators.stream().anyMatch(p -> p.getFirst().equals(op));
        }
        
        public OperatorSection<EXPRESSION> makeOperatorSection(BracketedStatus bracketedStatus, int operatorSetPrecedence, int initialIndex)
        {
            return makeExpression.either(
                nAry -> new NaryOperatorSection<EXPRESSION>(operators, operatorSetPrecedence, nAry, initialIndex, bracketedStatus),
                binary -> new BinaryOperatorSection<EXPRESSION>(operators, operatorSetPrecedence, binary, initialIndex, bracketedStatus)
            );
        }
    }
    
    static abstract class OperatorSection<EXPRESSION>
    {
        protected final ImmutableList<Pair<String, @Localized String>> possibleOperators;
        protected final BracketedStatus bracketedStatus;
        // The ordering in the candidates list:
        private final int operatorSetPrecedence;

        protected OperatorSection(ImmutableList<Pair<String, @Localized String>> possibleOperators, int operatorSetPrecedence, BracketedStatus bracketedStatus)
        {
            this.possibleOperators = possibleOperators;
            this.operatorSetPrecedence = operatorSetPrecedence;
            this.bracketedStatus = bracketedStatus;
        }

        /**
         * Attempts to add the operator to this section.  If it can be added, it is added, and true is returned.
         * If it can't be added, nothing is changed, and false is returned.
         */
        abstract boolean addOperator(String operator, int indexOfOperator);

        /**
         * Given the operators already added, makes an expression.  Will only use the indexes that pertain
         * to the operators that got added.
         */
        abstract EXPRESSION makeExpression(ImmutableList<EXPRESSION> expressions);
    }
    
    static class BinaryOperatorSection<EXPRESSION> extends OperatorSection<EXPRESSION>
    {
        private final MakeBinary<EXPRESSION> makeExpression;
        private final int operatorIndex;

        private BinaryOperatorSection(ImmutableList<Pair<String, @Localized String>> operators, int candidatePrecedence, MakeBinary<EXPRESSION> makeExpression, int initialIndex, BracketedStatus bracketedStatus)
        {
            super(operators, candidatePrecedence, bracketedStatus);
            this.makeExpression = makeExpression;
            this.operatorIndex = initialIndex;
        }

        @Override
        boolean addOperator(String operator, int indexOfOperator)
        {
            // Can never add another operator to a binary operator:
            return false;
        }

        @Override
        EXPRESSION makeExpression(ImmutableList<EXPRESSION> expressions)
        {
            return makeExpression.makeBinary(expressions.get(operatorIndex), expressions.get(operatorIndex + 1));
        }
    }
    
    static class NaryOperatorSection<EXPRESSION> extends OperatorSection<EXPRESSION>
    {
        private final MakeNary<EXPRESSION> makeExpression;
        private final ArrayList<String> actualOperators = new ArrayList<>();
        private final int startingOperatorIndexIncl;
        private int endingOperatorIndexIncl;

        NaryOperatorSection(ImmutableList<Pair<String, @Localized String>> operators, int candidatePrecedence, MakeNary<EXPRESSION> makeExpression, int initialIndex, BracketedStatus bracketedStatus)
        {
            super(operators, candidatePrecedence, bracketedStatus);
            this.makeExpression = makeExpression;
            this.startingOperatorIndexIncl = initialIndex;
            this.endingOperatorIndexIncl = initialIndex;
        }

        @Override
        boolean addOperator(String operator, int indexOfOperator)
        {
            if (possibleOperators.stream().anyMatch(p -> p.getFirst().equals(operator)) && indexOfOperator == endingOperatorIndexIncl + 1)
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
        EXPRESSION makeExpression(ImmutableList<EXPRESSION> expressions)
        {
            // Given a + b + c, if end operator is 1, we want to include operand index 2, hence we pass excl index 3,
            // so it's last operator-inclusive, plus 2.
            return makeExpression.makeNary(expressions.subList(startingOperatorIndexIncl, endingOperatorIndexIncl + 2), actualOperators);
        }
    }

    /**
     * If all operators are from the same {@link OperatorExpressionInfo}, returns a normal expression with those operators.
     * Otherwise, it returns an invalid operator expression (as specified by the lambda), AND if feasible, suggests
     * likely quick fixes based on the suggested priority ordering given by the list parameter's ordering.
     * 
     * @param candidates The ordering of the outer list indicates likely bracketing priority, with items earlier
     *                   in the list more likely to be bracketed (thus earlier means binds tighter).  So for example,
     *                   plus will come earlier in the list than equals, because given "a + b = c", we're more likely
     *                   to want to bracket "(a + b) = c" than "a + (b = c)".
     */
    static <EXPRESSION> @Nullable EXPRESSION makeExpressionWithOperators(ImmutableList<ImmutableList<OperatorExpressionInfo<EXPRESSION>>> candidates, ErrorDisplayerRecord errorDisplayers, ImmutableList<EXPRESSION> expressionExps, List<String> ops, BracketedStatus bracketedStatus)
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
        List<OperatorSection<EXPRESSION>> operatorSections = new ArrayList<>();
        nextOp: for (int i = 0; i < ops.size(); i++)
        {
            if (operatorSections.isEmpty() || !operatorSections.get(operatorSections.size() - 1).addOperator(ops.get(i), i))
            {
                // Make new section:
                for (int candidateIndex = 0; candidateIndex < candidates.size(); candidateIndex++)
                {
                    for (OperatorExpressionInfo<EXPRESSION> operatorExpressionInfo : candidates.get(candidateIndex))
                    {
                        if (operatorExpressionInfo.includes(ops.get(i)))
                        {
                            operatorSections.add(operatorExpressionInfo.makeOperatorSection(bracketedStatus, candidateIndex, i));
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
            return operatorSections.get(0).makeExpression(expressionExps); 
        }
        else
        {
            // We may be able to suggest some brackets
            Collections.sort(operatorSections, Comparator.comparing(os -> os.operatorSetPrecedence));
            int precedence = operatorSections.get(0).operatorSetPrecedence;

            for (OperatorSection<EXPRESSION> operatorSection : operatorSections)
            {
                if (operatorSection.operatorSetPrecedence != precedence)
                    break;
                errorDisplayers.
            }
            
            return null;
        }
    }
}
