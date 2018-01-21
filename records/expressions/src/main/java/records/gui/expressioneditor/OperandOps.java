package records.gui.expressioneditor;

import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.i18n.qual.LocalizableKey;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.error.InternalException;
import records.error.UserException;
import records.gui.expressioneditor.ConsecutiveBase.BracketedStatus;
import records.transformations.expression.Expression;
import records.transformations.expression.LoadableExpression;
import utility.Either;
import utility.Pair;
import utility.gui.TranslationUtility;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

public interface OperandOps<EXPRESSION extends LoadableExpression<EXPRESSION, SEMANTIC_PARENT>, SEMANTIC_PARENT>
{
    public OperandNode<EXPRESSION, SEMANTIC_PARENT> makeGeneral(ConsecutiveBase<EXPRESSION, SEMANTIC_PARENT> parent, SEMANTIC_PARENT semanticParent, @Nullable String initialContent);

    public ImmutableList<Pair<String, @Localized String>> getValidOperators(SEMANTIC_PARENT semanticParent);

    public boolean isOperatorAlphabet(char character, SEMANTIC_PARENT semanticParent);

    public Class<EXPRESSION> getOperandClass();

    @NonNull EXPRESSION makeUnfinished(String s);

    EXPRESSION makeExpression(ErrorDisplayer<EXPRESSION> displayer, ErrorDisplayerRecord errorDisplayers, List<EXPRESSION> expressionExps, List<String> ops, BracketedStatus bracketedStatus);

    String save(EXPRESSION expression);

    OperandNode<EXPRESSION, SEMANTIC_PARENT> loadOperand(String src, ConsecutiveBase<EXPRESSION, SEMANTIC_PARENT> parent) throws UserException, InternalException;
    
    // One set of operators that can be used to make a particular expression
    static class OperatorExpressionInfo<EXPRESSION>
    {
        public final ImmutableList<Pair<String, @Localized String>> operators;
        public final Either<BiFunction<List<EXPRESSION>, List<String>, EXPRESSION>, BiFunction<EXPRESSION, EXPRESSION, EXPRESSION>> makeExpression;

        OperatorExpressionInfo(ImmutableList<Pair<String, @Localized String>> operators, BiFunction<List<EXPRESSION>, List<String>, EXPRESSION> makeExpression)
        {
            this.operators = operators;
            this.makeExpression = Either.left(makeExpression);
        }

        // I know it's a bit odd, but we distinguish the N-ary constructor from the binary constructor by 
        // making the operators a non-list here.  The only expression with multiple operators
        // is add-subtract, which is N-ary.  And you can't have a binary expression with multiple different operators...
        OperatorExpressionInfo(Pair<String, @Localized String> operator, BiFunction<EXPRESSION, EXPRESSION, EXPRESSION> makeExpression)
        {
            this.operators = ImmutableList.of(operator);
            this.makeExpression = Either.right(makeExpression);
        }

        public boolean includes(String op)
        {
            return operators.stream().anyMatch(p -> p.getFirst().equals(op));
        }
    }
    
    static class OperatorSection<EXPRESSION>
    {
        public final OperatorExpressionInfo<EXPRESSION> operatorSet;
        // The ordering in the candidates list:
        public final int operatorSetPrecedence;
        
        public int startingOperatorIndex;
        public int endingOperatorIndex;

        private OperatorSection(OperatorExpressionInfo<EXPRESSION> operatorSet, int candidatePrecedence, int initialIndex)
        {
            this.operatorSet = operatorSet;
            this.operatorSetPrecedence = candidatePrecedence;
            this.startingOperatorIndex = initialIndex;
            this.endingOperatorIndex = initialIndex;
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
    static <EXPRESSION> @Nullable EXPRESSION makeExpressionWithOperators(List<List<OperatorExpressionInfo<EXPRESSION>>> candidates, ErrorDisplayer<EXPRESSION> displayer, ErrorDisplayerRecord errorDisplayers, List<EXPRESSION> expressionExps, List<String> ops, BracketedStatus bracketedStatus)
    {
        if (ops.isEmpty())
        {
            return expressionExps.get(0);
        }
        
        // First, split it into sections based on cohesive parts that have the same operators:
        List<OperatorSection<EXPRESSION>> operatorSections = new ArrayList<>();
        nextOp: for (int i = 0; i < ops.size(); i++)
        {
            if (operatorSections.isEmpty() || !operatorSections.get(operatorSections.size() - 1).operatorSet.includes(ops.get(i)))
            {
                // Make new section:
                for (int candidateIndex = 0; candidateIndex < candidates.size(); candidateIndex++)
                {
                    for (OperatorExpressionInfo<EXPRESSION> operatorExpressionInfo : candidates.get(candidateIndex))
                    {
                        if (operatorExpressionInfo.includes(ops.get(i)))
                        {
                            operatorSections.add(new OperatorSection<>(operatorExpressionInfo, candidateIndex, i));
                            continue nextOp; 
                        }
                    }
                }
                // If we get here, it's an unrecognised operator, so return an invalid expression:
                return null;
            }
            else
            {
                // Extend existing section:
                operatorSections.get(operatorSections.size() - 1).endingOperatorIndex = i;
            }
        }
        
        if (operatorSections.size() == 1)
        {
            // All operators are coherent with each other, can just return single expression:
            return operatorSections.get(0).operatorSet.makeExpression.apply(ops); 
        }
        else
        {
            // TODO we might need to suggest some brackets
            
            return null;
        }
    }
}
