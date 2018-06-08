package records.gui.expressioneditor;

import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.i18n.qual.LocalizableKey;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;
import records.gui.expressioneditor.ExpressionSaver.OperatorExpressionInfo;
import records.gui.expressioneditor.GeneralExpressionEntry.Keyword;
import records.gui.expressioneditor.UnitEntry.UnitBracket;
import records.gui.expressioneditor.UnitEntry.UnitOp;
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
import java.util.Stack;
import java.util.function.Function;

public abstract class UnitSaver implements ErrorAndTypeRecorder
{
    final static ImmutableList<OperatorExpressionInfo<UnitExpression, UnitSaver, UnitOp>> OPERATORS = ImmutableList.of(new OperatorExpressionInfo<UnitExpression, UnitSaver, UnitOp>(ImmutableList.of(
            opD(UnitOp.MULTIPLY, "op.times")), UnitSaver::makeTimes),
    new OperatorExpressionInfo<UnitExpression, UnitSaver, UnitOp>(
            opD(UnitOp.DIVIDE, "op.divide"), UnitSaver::makeDivide),
    new OperatorExpressionInfo<UnitExpression, UnitSaver, UnitOp>(
            opD(UnitOp.RAISE, "op.raise"), UnitSaver::makeRaise));

    private static UnitExpression makeTimes(ImmutableList<UnitExpression> expressions, List<UnitOp> operators, BracketedStatus bracketedStatus)
    {
        return new UnitTimesExpression(expressions);
    }

    private static UnitExpression makeDivide(UnitExpression lhs, UnitExpression rhs, BracketedStatus bracketedStatus)
    {
        return new UnitDivideExpression(lhs, rhs);
    }

    private static UnitExpression makeRaise(UnitExpression lhs, UnitExpression rhs, BracketedStatus bracketedStatus)
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

    private final Stack<Pair<ArrayList<Either<UnitExpression, UnitOp>>, Terminator>> currentScopes = new Stack<>();

    // Ends a mini-expression
    private static interface Terminator
    {
        public void terminate(Function<BracketedStatus, UnitExpression> makeContent, UnitBracket terminator, ErrorDisplayer<UnitExpression, UnitSaver> keywordErrorDisplayer, FXPlatformConsumer<Context> keywordContext);
    }
    
    public UnitSaver()
    {
        addTopLevelScope();
    }

    @RequiresNonNull("currentScopes")
    private void addTopLevelScope(@UnknownInitialization(Object.class) UnitSaver this)
    {
        currentScopes.add(new Pair<>(new ArrayList<>(), (makeContent, terminator, keywordErrorDisplayer, keywordContext) -> {
            keywordErrorDisplayer.addErrorAndFixes(StyledString.s("Closing " + terminator + " without opening"), ImmutableList.of());
            currentScopes.peek().getFirst().add(Either.left(new InvalidOperatorUnitExpression(ImmutableList.of(Either.left(terminator.getContent())))));
        }));
    }

    public UnitExpression finish()
    {
        if (currentScopes.size() > 1)
        {
            // TODO give some sort of error.... somewhere?  On the opening item?
            return new InvalidOperatorUnitExpression(ImmutableList.of(Either.right(new SingleUnitExpression("TODO unterminated"))));
        }
        else
        {
            return makeExpression(currentScopes.pop().getFirst(), BracketedStatus.TOP_LEVEL);
        }
    }

    private UnitExpression makeExpression(List<Either<UnitExpression, UnitOp>> content, BracketedStatus bracketedStatus)
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

        for (Either<UnitExpression, UnitOp> item : content)
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
                invalid.add(Either.left(op.getContent()));
                validOperators.add(op);

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

            @Nullable UnitExpression e = ExpressionSaver.<UnitExpression, UnitSaver, UnitOp>makeExpressionWithOperators(ImmutableList.of(OPERATORS), this, UnitSaver::makeInvalidOp, ImmutableList.copyOf(validOperands), ImmutableList.copyOf(validOperators), bracketedStatus, arg -> arg);
            if (e != null)
                return e;

        }

        return new InvalidOperatorUnitExpression(ImmutableList.copyOf(invalid));
    }

    private static UnitExpression makeInvalidOp(ImmutableList<Either<UnitOp, UnitExpression>> items)
    {
        return new InvalidOperatorUnitExpression(Utility.mapListI(items, x -> x.mapBoth(op -> op.getContent(), y -> y)));
    }

    private static Pair<UnitOp, @Localized String> opD(UnitOp op, @LocalizableKey String key)
    {
        return new Pair<>(op, TranslationUtility.getString(key));
    }

    // Note: if we are copying to clipboard, callback will not be called
    public void saveOperator(UnitOp operator, ErrorDisplayer<UnitExpression, UnitSaver> errorDisplayer, FXPlatformConsumer<Context> withContext)
    {
        currentScopes.peek().getFirst().add(Either.right(operator));
    }
    public void saveOperand(UnitExpression singleItem, ErrorDisplayer<UnitExpression, UnitSaver> errorDisplayer, FXPlatformConsumer<Context> withContext)
    {
        currentScopes.peek().getFirst().add(Either.left(singleItem));
    }

    public void saveBracket(UnitBracket bracket, ErrorDisplayer<UnitExpression, UnitSaver> errorDisplayer, FXPlatformConsumer<Context> withContext)
    {
        if (bracket == UnitBracket.OPEN_ROUND)
        {
            currentScopes.push(new Pair<>(new ArrayList<>(), (makeContent, terminator, keywordErrorDisplayer, keywordContext) -> {
                if (terminator == UnitBracket.CLOSE_ROUND)
                {
                    // All is well:
                    UnitExpression result = makeContent.apply(BracketedStatus.DIRECT_ROUND_BRACKETED);
                    currentScopes.peek().getFirst().add(Either.left(result));
                }
                else
                {
                    // Error!
                    keywordErrorDisplayer.addErrorAndFixes(StyledString.s("Expected ) but found " + terminator), ImmutableList.of());
                    // Important to call makeContent before adding to scope on the next line:
                    ImmutableList.Builder<Either<String, UnitExpression>> items = ImmutableList.builder();
                    items.add(Either.right(makeContent.apply(BracketedStatus.DIRECT_ROUND_BRACKETED)));
                    items.add(Either.left(terminator.getContent()));
                    InvalidOperatorUnitExpression invalid = new InvalidOperatorUnitExpression(items.build());
                    currentScopes.peek().getFirst().add(Either.left(invalid));
                }
            }));
        }
        else
        {
            Pair<ArrayList<Either<UnitExpression, UnitOp>>, Terminator> cur = currentScopes.pop();
            if (currentScopes.size() == 0)
            {
                addTopLevelScope();
            }
            cur.getSecond().terminate(bracketedStatus -> makeExpression(cur.getFirst(), bracketedStatus), bracket, errorDisplayer, withContext);
        }
    }
}
