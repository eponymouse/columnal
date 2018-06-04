package records.gui.expressioneditor;

import annotation.recorded.qual.Recorded;
import annotation.recorded.qual.UnknownIfRecorded;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.TableAndColumnRenames;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.expression.BracketedStatus;
import records.transformations.expression.ErrorAndTypeRecorder;
import records.transformations.expression.QuickFix;
import records.transformations.expression.QuickFix.ReplacementTarget;
import records.transformations.expression.LoadableExpression;
import styled.StyledShowable;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.Pair;
import utility.Utility;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public interface OperandOps<EXPRESSION extends StyledShowable, SEMANTIC_PARENT>
{
    public EntryNode<EXPRESSION, SEMANTIC_PARENT> makeGeneral(ConsecutiveBase<EXPRESSION, SEMANTIC_PARENT> parent, @Nullable String initialContent);

    public boolean isOperatorAlphabet(char character);

    public Class<EXPRESSION> getOperandClass();

    @UnknownIfRecorded EXPRESSION makeExpression(ErrorDisplayerRecord errorDisplayers, ImmutableList<@Recorded EXPRESSION> expressionExps, List<String> ops, BracketedStatus bracketedStatus);

    String save(EXPRESSION expression, TableAndColumnRenames renames);

    // The toString() method of the saver can be used to get the string content
    SEMANTIC_PARENT saveToClipboard();

    public static interface MakeNary<EXPRESSION extends LoadableExpression<EXPRESSION, SEMANTIC_PARENT>, SEMANTIC_PARENT>
    {
        public @Nullable EXPRESSION makeNary(ImmutableList<@Recorded EXPRESSION> expressions, List<String> operators, BracketedStatus bracketedStatus);
    }

    public static interface MakeBinary<EXPRESSION extends LoadableExpression<EXPRESSION, SEMANTIC_PARENT>, SEMANTIC_PARENT>
    {
        public EXPRESSION makeBinary(@Recorded EXPRESSION lhs, @Recorded EXPRESSION rhs, BracketedStatus bracketedStatus);
    }
    
    // One set of operators that can be used to make a particular expression
    static class OperatorExpressionInfo<EXPRESSION extends LoadableExpression<EXPRESSION, SEMANTIC_PARENT>, SEMANTIC_PARENT>
    {
        public final ImmutableList<Pair<String, @Localized String>> operators;
        public final Either<MakeNary<EXPRESSION, SEMANTIC_PARENT>, MakeBinary<EXPRESSION, SEMANTIC_PARENT>> makeExpression;

        OperatorExpressionInfo(ImmutableList<Pair<String, @Localized String>> operators, MakeNary<EXPRESSION, SEMANTIC_PARENT> makeExpression)
        {
            this.operators = operators;
            this.makeExpression = Either.left(makeExpression);
        }

        // I know it's a bit odd, but we distinguish the N-ary constructor from the binary constructor by 
        // making the operators a non-list here.  The only expression with multiple operators
        // is add-subtract, which is N-ary.  And you can't have a binary expression with multiple different operators...
        OperatorExpressionInfo(Pair<String, @Localized String> operator, MakeBinary<EXPRESSION, SEMANTIC_PARENT> makeExpression)
        {
            this.operators = ImmutableList.of(operator);
            this.makeExpression = Either.right(makeExpression);
        }

        public boolean includes(String op)
        {
            return operators.stream().anyMatch((Pair<String, @Localized String> p) -> p.getFirst().equals(op));
        }
        
        public OperatorSection<EXPRESSION, SEMANTIC_PARENT> makeOperatorSection(int operatorSetPrecedence, String initialOperator, int initialIndex)
        {
            return makeExpression.either(
                nAry -> new NaryOperatorSection<EXPRESSION, SEMANTIC_PARENT>(operators, operatorSetPrecedence, nAry, initialIndex, initialOperator),
                binary -> new BinaryOperatorSection<EXPRESSION, SEMANTIC_PARENT>(operators, operatorSetPrecedence, binary, initialIndex)
            );
        }
    }
    
    static abstract class OperatorSection<EXPRESSION extends LoadableExpression<EXPRESSION, SEMANTIC_PARENT>, SEMANTIC_PARENT>
    {
        protected final ImmutableList<Pair<String, @Localized String>> possibleOperators;
        // The ordering in the candidates list:
        private final int operatorSetPrecedence;

        protected OperatorSection(ImmutableList<Pair<String, @Localized String>> possibleOperators, int operatorSetPrecedence)
        {
            this.possibleOperators = possibleOperators;
            this.operatorSetPrecedence = operatorSetPrecedence;
        }

        /**
         * Attempts to add the operator to this section.  If it can be added, it is added, and true is returned.
         * If it can't be added, nothing is changed, and false is returned.
         */
        abstract boolean addOperator(String operator, int indexOfOperator);

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
    
    static class BinaryOperatorSection<EXPRESSION extends LoadableExpression<EXPRESSION, SEMANTIC_PARENT>, SEMANTIC_PARENT> extends OperatorSection<EXPRESSION, SEMANTIC_PARENT>
    {
        private final MakeBinary<EXPRESSION, SEMANTIC_PARENT> makeExpression;
        private final int operatorIndex;
        
        private BinaryOperatorSection(ImmutableList<Pair<String, @Localized String>> operators, int candidatePrecedence, MakeBinary<EXPRESSION, SEMANTIC_PARENT> makeExpression, int initialIndex)
        {
            super(operators, candidatePrecedence);
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
    
    static class NaryOperatorSection<EXPRESSION extends LoadableExpression<EXPRESSION, SEMANTIC_PARENT>, SEMANTIC_PARENT> extends OperatorSection<EXPRESSION, SEMANTIC_PARENT>
    {
        private final MakeNary<EXPRESSION, SEMANTIC_PARENT> makeExpression;
        private final ArrayList<String> actualOperators = new ArrayList<>();
        private final int startingOperatorIndexIncl;
        private int endingOperatorIndexIncl;

        NaryOperatorSection(ImmutableList<Pair<String, @Localized String>> operators, int candidatePrecedence, MakeNary<EXPRESSION, SEMANTIC_PARENT> makeExpression, int initialIndex, String initialOperator)
        {
            super(operators, candidatePrecedence);
            this.makeExpression = makeExpression;
            this.startingOperatorIndexIncl = initialIndex;
            this.endingOperatorIndexIncl = initialIndex;
            this.actualOperators.add(initialOperator);
        }

        @Override
        boolean addOperator(String operator, int indexOfOperator)
        {
            if (possibleOperators.stream().anyMatch((Pair<String, @Localized String> p) -> p.getFirst().equals(operator)) && indexOfOperator == endingOperatorIndexIncl + 1)
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
        
        public @Nullable EXPRESSION makeExpressionMiddleMerge(@Recorded EXPRESSION middle, NaryOperatorSection<EXPRESSION, SEMANTIC_PARENT> rhs, List<@Recorded EXPRESSION> expressions, BracketedStatus bracketedStatus)
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
     * If all operators are from the same {@link OperatorExpressionInfo}, returns a normal expression with those operators.
     * Otherwise, it returns an invalid operator expression (as specified by the lambda), AND if feasible, suggests
     * likely quick fixes based on the suggested priority ordering given by the list parameter's ordering.
     * 
     * @param candidates The ordering of the outer list indicates likely bracketing priority, with items earlier
     *                   in the list more likely to be bracketed (thus earlier means binds tighter).  So for example,
     *                   plus will come earlier in the list than equals, because given "a + b = c", we're more likely
     *                   to want to bracket "(a + b) = c" than "a + (b = c)".
     */
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

    @OnThread(Tag.Any)
    public static String makeCssClass(Object replacement)
    {
        return "id-munged-" + replacement.toString().codePoints().mapToObj(i -> Integer.toString(i)).collect(Collectors.joining("-"));
    }
}
