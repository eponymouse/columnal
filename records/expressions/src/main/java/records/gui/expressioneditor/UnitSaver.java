package records.gui.expressioneditor;

import annotation.recorded.qual.Recorded;
import annotation.recorded.qual.UnknownIfRecorded;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import javafx.scene.input.DataFormat;
import org.checkerframework.checker.i18n.qual.LocalizableKey;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.PolyNull;
import records.gui.expressioneditor.ErrorDisplayerRecord.Span;
import records.gui.expressioneditor.UnitEntry.UnitBracket;
import records.gui.expressioneditor.UnitEntry.UnitOp;
import records.gui.expressioneditor.UnitSaver.Context;
import records.transformations.expression.*;
import styled.StyledString;
import utility.Either;
import utility.FXPlatformConsumer;
import utility.Pair;
import utility.Utility;
import utility.gui.TranslationUtility;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class UnitSaver extends SaverBase<UnitExpression, UnitSaver, UnitOp, UnitBracket, Context, Void>// implements ErrorAndTypeRecorder
{
    final ImmutableList<OperatorExpressionInfo> OPERATORS = ImmutableList.of(
        new OperatorExpressionInfo(ImmutableList.of(
            opD(UnitOp.MULTIPLY, "op.times")), UnitSaver::makeTimes),
        new OperatorExpressionInfo(
            opD(UnitOp.DIVIDE, "op.divide"), UnitSaver::makeDivide),
        new OperatorExpressionInfo(
            opD(UnitOp.RAISE, "op.raise"), UnitSaver::makeRaise));

    public UnitSaver(ConsecutiveBase<UnitExpression, UnitSaver> parent, boolean showFoundErrors)
    {
        super(parent, showFoundErrors);
    }
    
    private UnitSaver()
    {
    }
    
    private static UnitExpression makeTimes(ImmutableList<@Recorded UnitExpression> expressions, List<Pair<UnitOp, ConsecutiveChild<UnitExpression, UnitSaver>>> operators)
    {
        return new UnitTimesExpression(expressions);
    }

    private static UnitExpression makeDivide(@Recorded UnitExpression lhs, ConsecutiveChild<UnitExpression, UnitSaver> opNode, @Recorded UnitExpression rhs, BracketAndNodes<UnitExpression, UnitSaver, ?> bracketedStatus, ErrorDisplayerRecord errorDisplayerRecord)
    {
        return new UnitDivideExpression(lhs, rhs);
    }

    private static UnitExpression makeRaise(@Recorded UnitExpression lhs, ConsecutiveChild<UnitExpression, UnitSaver> opNode, @Recorded UnitExpression rhs, BracketAndNodes<UnitExpression, UnitSaver, ?> bracketedStatus, ErrorDisplayerRecord errorDisplayerRecord)
    {
        if (rhs instanceof UnitExpressionIntLiteral)
            return new UnitRaiseExpression(lhs, ((UnitExpressionIntLiteral) rhs).getNumber());
        else
            return new InvalidOperatorUnitExpression(ImmutableList.<@Recorded UnitExpression>of(
                    lhs, errorDisplayerRecord.<InvalidSingleUnitExpression>recordUnit(opNode, opNode, new InvalidSingleUnitExpression("^")), rhs
            ));
    };

    @Override
    public BracketAndNodes<UnitExpression, UnitSaver, Void> expectSingle(@UnknownInitialization(Object.class) UnitSaver this, ErrorDisplayerRecord errorDisplayerRecord, ConsecutiveChild<UnitExpression, UnitSaver> start, ConsecutiveChild<UnitExpression, UnitSaver> end)
    {
        return new BracketAndNodes<>(new ApplyBrackets<Void, UnitExpression>()
        {
            @Override
            public @PolyNull @Recorded UnitExpression apply(@PolyNull Void items)
            {
                // Should not be possible anyway
                if (items == null)
                    return null;
                else
                    throw new IllegalStateException();
            }

            @Override
            public @PolyNull @Recorded UnitExpression applySingle(@PolyNull @Recorded UnitExpression singleItem)
            {
                return singleItem;
            }
        }, start, end, ImmutableList.of());
    }

    //UnitManager getUnitManager();

    class Context {}
    
    @Override
    protected @Recorded UnitExpression makeExpression(ConsecutiveChild<UnitExpression, UnitSaver> start, ConsecutiveChild<UnitExpression, UnitSaver> end, List<Either<@Recorded UnitExpression, OpAndNode>> content, BracketAndNodes<UnitExpression, UnitSaver, Void> brackets)
    {
        if (content.isEmpty())
            return record(start, end, new InvalidOperatorUnitExpression(ImmutableList.of()));

        CollectedItems collectedItems = processItems(content);

        if (collectedItems.isValid())
        {
            ArrayList<@Recorded UnitExpression> validOperands = collectedItems.getValidOperands();
            ArrayList<OpAndNode> validOperators = collectedItems.getValidOperators();
            
            // Single expression?
            if (validOperands.size() == 1 && validOperators.size() == 0)
                return validOperands.get(0);

            // Raise is a special case as it doesn't need to be bracketed:
            for (int i = 0; i < validOperators.size(); i++)
            {
                if (validOperators.get(i).op.equals(UnitOp.RAISE))
                {
                    if (validOperands.get(i) instanceof SingleUnitExpression && i + 1 < validOperands.size() && validOperands.get(i + 1) instanceof UnitExpressionIntLiteral)
                    {
                        validOperators.remove(i);
                        @Recorded UnitExpressionIntLiteral power = (UnitExpressionIntLiteral) validOperands.remove(i + 1);
                        Span<UnitExpression, UnitSaver> recorder = errorDisplayerRecord.recorderFor(validOperands.get(i));
                        validOperands.set(i, record(recorder.start, errorDisplayerRecord.recorderFor(power).end, new UnitRaiseExpression(validOperands.get(i), power.getNumber())));
                    }
                }
            }
            
            // Now we need to check the operators can work together as one group:
            @Nullable UnitExpression e = makeExpressionWithOperators(ImmutableList.of(OPERATORS), errorDisplayerRecord, (ImmutableList<Either<OpAndNode, @Recorded UnitExpression>> arg) ->
                    makeInvalidOp(brackets.start, brackets.end, arg)
                , ImmutableList.copyOf(validOperands), ImmutableList.copyOf(validOperators), brackets);
            if (e != null)
            {
                return record(start, end, e);
            }

        }

        return collectedItems.makeInvalid(start, end, InvalidOperatorUnitExpression::new);
    }

    @Override
    protected UnitExpression opToInvalid(UnitOp unitOp)
    {
        return new InvalidSingleUnitExpression(unitOp.getContent());
    }

    @Override
    protected @Nullable Supplier<@Recorded UnitExpression> canBeUnary(OpAndNode operator, UnitExpression followingOperand)
    {
        return null;
    }

    @Override
    protected @Recorded UnitExpression makeInvalidOp(ConsecutiveChild<UnitExpression, UnitSaver> start, ConsecutiveChild<UnitExpression, UnitSaver> end, ImmutableList<Either<OpAndNode, @Recorded UnitExpression>> items)
    {
        return errorDisplayerRecord.recordUnit(start, end, new InvalidOperatorUnitExpression(Utility.<Either<OpAndNode, @Recorded UnitExpression>, @Recorded UnitExpression>mapListI(items, x -> x.<@Recorded UnitExpression>either(op -> errorDisplayerRecord.recordUnit(op.sourceNode, op.sourceNode, new InvalidSingleUnitExpression(op.op.getContent())), y -> y))));
    }

    private static Pair<UnitOp, @Localized String> opD(UnitOp op, @LocalizableKey String key)
    {
        return new Pair<>(op, TranslationUtility.getString(key));
    }

    public void saveBracket(UnitBracket bracket, ConsecutiveChild<UnitExpression, UnitSaver> errorDisplayer, FXPlatformConsumer<Context> withContext)
    {
        if (bracket == UnitBracket.OPEN_ROUND)
        {
            currentScopes.push(new Scope(errorDisplayer, new Terminator()
            {
                @Override
                public void terminate(FetchContent<UnitExpression, UnitSaver, Void> makeContent, @Nullable UnitBracket terminator, ConsecutiveChild<UnitExpression, UnitSaver> keywordErrorDisplayer, FXPlatformConsumer<Context> keywordContext)
                {
                    BracketAndNodes<UnitExpression, UnitSaver, Void> brackets = expectSingle(errorDisplayerRecord, errorDisplayer, keywordErrorDisplayer);
                    if (terminator == UnitBracket.CLOSE_ROUND)
                    {
                        // All is well:
                        @Recorded UnitExpression result = makeContent.fetchContent(brackets);
                        currentScopes.peek().items.add(Either.left(result));
                    } 
                    else
                    {
                        // Error!
                        if (isShowingErrors())
                            keywordErrorDisplayer.addErrorAndFixes(StyledString.s("Expected ) but found " + terminator), ImmutableList.of());
                        // Important to call makeContent before adding to scope on the next line:
                        ImmutableList.Builder<@Recorded UnitExpression> items = ImmutableList.builder();
                        items.add(record(errorDisplayer, errorDisplayer, new InvalidSingleUnitExpression(bracket.getContent())));
                        items.add(makeContent.fetchContent(brackets));
                        if (terminator != null)
                            items.add(record(errorDisplayer, errorDisplayer, new InvalidSingleUnitExpression(terminator.getContent())));
                        @Recorded UnitExpression invalid = record(brackets.start, keywordErrorDisplayer, new InvalidOperatorUnitExpression(items.build()));
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
            cur.terminator.terminate((BracketAndNodes<UnitExpression, UnitSaver, Void> brackets) -> makeExpression(brackets.start, brackets.end, cur.items, brackets), bracket, errorDisplayer, withContext);
        }
    }

    @Override
    protected UnitExpression keywordToInvalid(UnitBracket unitBracket)
    {
        return new InvalidSingleUnitExpression(unitBracket.getContent());
    }

    @Override
    protected Span<UnitExpression, UnitSaver> recorderFor(@Recorded UnitExpression unitExpression)
    {
        return errorDisplayerRecord.recorderFor(unitExpression);
    }

    @Override
    protected @Recorded UnitExpression record(ConsecutiveChild<UnitExpression, UnitSaver> start, ConsecutiveChild<UnitExpression, UnitSaver> end, UnitExpression unitExpression)
    {
        return errorDisplayerRecord.recordUnit(start, end, unitExpression);
    }

    public static ImmutableList<OperatorExpressionInfo> getOperators()
    {
        return new UnitSaver().OPERATORS;
    }

    @Override
    protected Map<DataFormat, Object> toClipboard(@UnknownIfRecorded UnitExpression expression)
    {
        return ImmutableMap.of(
                UnitEditor.UNIT_CLIPBOARD_TYPE, expression.save(true, true),
                DataFormat.PLAIN_TEXT, expression.save(false, true)
        );
    }
}
