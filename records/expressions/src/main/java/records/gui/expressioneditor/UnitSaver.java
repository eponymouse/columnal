package records.gui.expressioneditor;

import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.i18n.qual.LocalizableKey;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.gui.expressioneditor.ExpressionSaver.BinaryOperatorSection;
import records.gui.expressioneditor.ExpressionSaver.MakeBinary;
import records.gui.expressioneditor.ExpressionSaver.MakeNary;
import records.gui.expressioneditor.ExpressionSaver.NaryOperatorSection;
import records.gui.expressioneditor.ExpressionSaver.OperatorExpressionInfoBase;
import records.gui.expressioneditor.ExpressionSaver.OperatorSection;
import records.gui.expressioneditor.UnitEntry.UnitBracket;
import records.gui.expressioneditor.UnitEntry.UnitOp;
import records.gui.expressioneditor.UnitSaver.Context;
import records.transformations.expression.BracketedStatus;
import records.transformations.expression.ErrorAndTypeRecorder;
import records.transformations.expression.InvalidOperatorUnitExpression;
import records.transformations.expression.SingleUnitExpression;
import records.transformations.expression.UnitDivideExpression;
import records.transformations.expression.UnitExpression;
import records.transformations.expression.UnitExpressionIntLiteral;
import records.transformations.expression.UnitRaiseExpression;
import records.transformations.expression.UnitTimesExpression;
import styled.StyledString;
import utility.Either;
import utility.FXPlatformConsumer;
import utility.Pair;
import utility.Utility;
import utility.gui.TranslationUtility;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public abstract class UnitSaver extends SaverBase<UnitExpression, UnitSaver, UnitOp, UnitBracket, Context> implements ErrorAndTypeRecorder
{
    final static ImmutableList<OperatorExpressionInfoBase<UnitExpression, UnitSaver, UnitOp>> OPERATORS = ImmutableList.of(
        new OperatorExpressionInfoUnit(ImmutableList.of(
            opD(UnitOp.MULTIPLY, "op.times")), UnitSaver::makeTimes),
    new OperatorExpressionInfoUnit(
            opD(UnitOp.DIVIDE, "op.divide"), UnitSaver::makeDivide),
    new OperatorExpressionInfoUnit(
            opD(UnitOp.RAISE, "op.raise"), UnitSaver::makeRaise));

    public UnitSaver(ConsecutiveBase<UnitExpression, UnitSaver> parent)
    {
        super(parent);
    }

    private static class OperatorExpressionInfoUnit extends OperatorExpressionInfoBase<UnitExpression, UnitSaver, UnitOp>
    {
        OperatorExpressionInfoUnit(ImmutableList<Pair<UnitOp, @Localized String>> operators, MakeNary<UnitExpression, UnitSaver, UnitOp> makeExpression)
        {
            super(operators, makeExpression);
        }

        OperatorExpressionInfoUnit(Pair<UnitOp, @Localized String> operator, MakeBinary<UnitExpression, UnitSaver> makeExpression)
        {
            super(operator, makeExpression);
        }

        @Override
        public OperatorSection<UnitExpression, UnitSaver, UnitOp> makeOperatorSection(ErrorDisplayerRecord errorDisplayerRecord, int operatorSetPrecedence, UnitOp initialOperator, int initialIndex)
        {
            return makeExpression.either(
                nAry -> new NaryOperatorSectionUnit(errorDisplayerRecord, operators, operatorSetPrecedence, nAry, initialIndex, initialOperator),
                binary -> new BinaryOperatorSectionUnit(errorDisplayerRecord, operators, operatorSetPrecedence, binary, initialIndex)
            );
        }
    }

    static class BinaryOperatorSectionUnit extends BinaryOperatorSection<UnitExpression, UnitSaver, UnitOp>
    {
        private BinaryOperatorSectionUnit(ErrorDisplayerRecord errorDisplayerRecord, ImmutableList<Pair<UnitOp, @Localized String>> operators, int candidatePrecedence, MakeBinary<UnitExpression, UnitSaver> makeExpression, int initialIndex)
        {
            super(errorDisplayerRecord, operators, candidatePrecedence, makeExpression, initialIndex);
        }

        protected @Recorded UnitExpression makeBinary(@Recorded UnitExpression lhs, @Recorded UnitExpression rhs, BracketAndNodes<UnitExpression, UnitSaver> brackets)
        {
            return errorDisplayerRecord.recordUnit(brackets.start, brackets.end, makeExpression.makeBinary(lhs, rhs, brackets));
        }
    }

    static class NaryOperatorSectionUnit extends NaryOperatorSection<UnitExpression, UnitSaver, UnitOp>
    {
        NaryOperatorSectionUnit(ErrorDisplayerRecord errorDisplayerRecord, ImmutableList<Pair<UnitOp, @Localized String>> operators, int candidatePrecedence, MakeNary<UnitExpression, UnitSaver, UnitOp> makeExpression, int initialIndex, UnitOp initialOperator)
        {
            super(errorDisplayerRecord, operators, candidatePrecedence, makeExpression, initialIndex, initialOperator);
        }

        @Override
        protected @Nullable @Recorded UnitExpression makeNary(ImmutableList<@Recorded UnitExpression> expressions, List<UnitOp> operators, BracketAndNodes<UnitExpression, UnitSaver> bracketedStatus)
        {
            UnitExpression expression = makeExpression.makeNary(expressions, operators, bracketedStatus);
            if (expression == null)
                return null;
            else
                return errorDisplayerRecord.recordUnit(bracketedStatus.start, bracketedStatus.end, expression);
        }
    }


    private static UnitExpression makeTimes(ImmutableList<@Recorded UnitExpression> expressions, List<UnitOp> operators, BracketAndNodes<UnitExpression, UnitSaver> bracketedStatus)
    {
        return new UnitTimesExpression(expressions);
    }

    private static UnitExpression makeDivide(UnitExpression lhs, UnitExpression rhs, BracketAndNodes<UnitExpression, UnitSaver> bracketedStatus)
    {
        return new UnitDivideExpression(lhs, rhs);
    }

    private static UnitExpression makeRaise(UnitExpression lhs, UnitExpression rhs, BracketAndNodes<UnitExpression, UnitSaver> bracketedStatus)
    {
        if (rhs instanceof UnitExpressionIntLiteral)
            return new UnitRaiseExpression(lhs, ((UnitExpressionIntLiteral) rhs).getNumber());
        else
            return new InvalidOperatorUnitExpression(ImmutableList.of(
                    Either.right(lhs), Either.left("^"), Either.right(rhs)
            ));
    };

    //UnitManager getUnitManager();

    class Context {}
    
    @Override
    protected UnitExpression makeExpression(ConsecutiveChild<UnitExpression, UnitSaver> start, ConsecutiveChild<UnitExpression, UnitSaver> end, List<Either<@Recorded UnitExpression, OpAndNode>> content, BracketAndNodes<UnitExpression, UnitSaver> brackets)
    {
        if (content.isEmpty())
            return new InvalidOperatorUnitExpression(ImmutableList.of());

        // Although it's duplication, we keep a list for if it turns out invalid, and two lists for if it is valid:
        // Valid means that operands interleave exactly with operators, and there is an operand at beginning and end.
        boolean[] valid = new boolean[] {true};
        final ArrayList<Either<String, UnitExpression>> invalid = new ArrayList<>();
        final ArrayList<UnitExpression> validOperands = new ArrayList<>();
        final ArrayList<UnitOp> validOperators = new ArrayList<>();

        boolean lastWasExpression[] = new boolean[] {false}; // Think of it as an invisible empty prefix operator

        for (Either<UnitExpression, OpAndNode> item : content)
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
            // Single expression?
            if (validOperands.size() == 1 && validOperators.size() == 0)
                return validOperands.get(0);

            // Raise is a special case as it doesn't need to be bracketed:
            for (int i = 0; i < validOperators.size(); i++)
            {
                if (validOperators.get(i).equals(UnitOp.RAISE))
                {
                    if (validOperands.get(i) instanceof SingleUnitExpression && validOperands.get(i + 1) instanceof UnitExpressionIntLiteral)
                    {
                        validOperators.remove(i);
                        UnitExpressionIntLiteral power = (UnitExpressionIntLiteral) validOperands.remove(i + 1);
                        validOperands.set(i, new UnitRaiseExpression(validOperands.get(i), power.getNumber()));
                    }
                }
            }
            
            // Now we need to check the operators can work together as one group:
            @Nullable UnitExpression e = ExpressionSaver.<UnitExpression, UnitSaver, UnitOp>makeExpressionWithOperators(ImmutableList.of(OPERATORS), errorDisplayerRecord, arg ->
                    record(brackets.start, brackets.end, makeInvalidOp(brackets.start, brackets.end, arg))
                , ImmutableList.copyOf(validOperands), ImmutableList.copyOf(validOperators), brackets, arg -> arg);
            if (e != null)
            {
                return e;
            }

        }

        return new InvalidOperatorUnitExpression(ImmutableList.copyOf(invalid));
    }

    @Override
    protected UnitExpression makeInvalidOp(ConsecutiveChild<UnitExpression, UnitSaver> start, ConsecutiveChild<UnitExpression, UnitSaver> end, ImmutableList<Either<UnitOp, @Recorded UnitExpression>> items)
    {
        return new InvalidOperatorUnitExpression(Utility.mapListI(items, x -> x.mapBoth(op -> op.getContent(), y -> y)));
    }

    private static Pair<UnitOp, @Localized String> opD(UnitOp op, @LocalizableKey String key)
    {
        return new Pair<>(op, TranslationUtility.getString(key));
    }

    // Note: if we are copying to clipboard, callback will not be called
    public void saveOperand(UnitExpression singleItem, ErrorDisplayer<UnitExpression, UnitSaver> errorDisplayer, FXPlatformConsumer<Context> withContext)
    {
        currentScopes.peek().items.add(Either.left(singleItem));
    }

    public void saveBracket(UnitBracket bracket, ConsecutiveChild<UnitExpression, UnitSaver> errorDisplayer, FXPlatformConsumer<Context> withContext)
    {
        if (bracket == UnitBracket.OPEN_ROUND)
        {
            currentScopes.push(new Scope(errorDisplayer, new Terminator()
            {
                @Override
                public void terminate(Function<BracketAndNodes<UnitExpression, UnitSaver>, @Recorded UnitExpression> makeContent, UnitBracket terminator, ConsecutiveChild<UnitExpression, UnitSaver> keywordErrorDisplayer, FXPlatformConsumer<Context> keywordContext)
                {
                    BracketAndNodes<UnitExpression, UnitSaver> brackets = new BracketAndNodes<>(BracketedStatus.DIRECT_ROUND_BRACKETED, errorDisplayer, keywordErrorDisplayer);
                    if (terminator == UnitBracket.CLOSE_ROUND)
                    {
                        // All is well:

                        UnitExpression result = makeContent.apply(brackets);
                        currentScopes.peek().items.add(Either.left(result));
                    } else
                    {
                        // Error!
                        keywordErrorDisplayer.addErrorAndFixes(StyledString.s("Expected ) but found " + terminator), ImmutableList.of());
                        // Important to call makeContent before adding to scope on the next line:
                        ImmutableList.Builder<Either<String, UnitExpression>> items = ImmutableList.builder();
                        items.add(Either.right(makeContent.apply(brackets)));
                        items.add(Either.left(terminator.getContent()));
                        InvalidOperatorUnitExpression invalid = new InvalidOperatorUnitExpression(items.build());
                        currentScopes.peek().items.add(Either.left(invalid));
                    }
                }
            }));
        }
        else
        {
            Scope cur = currentScopes.pop();
            if (currentScopes.size() == 0)
            {
                addTopLevelScope(errorDisplayer.getParent());
            }
            cur.terminator.terminate((BracketAndNodes<UnitExpression, UnitSaver> brackets) -> makeExpression(brackets.start, brackets.end, cur.items, brackets), bracket, errorDisplayer, withContext);
        }
    }

    @Override
    protected UnitExpression keywordToInvalid(UnitBracket unitBracket)
    {
        return new InvalidOperatorUnitExpression(ImmutableList.of(Either.left(unitBracket.getContent())));
    }

    @Override
    protected UnitExpression record(ConsecutiveChild<UnitExpression, UnitSaver> start, ConsecutiveChild<UnitExpression, UnitSaver> end, UnitExpression unitExpression)
    {
        return errorDisplayerRecord.recordUnit(start, end, unitExpression);
    }
}
