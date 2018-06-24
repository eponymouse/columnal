package records.transformations.expression.type;

import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.gui.expressioneditor.ConsecutiveBase;
import records.gui.expressioneditor.ConsecutiveChild;
import records.gui.expressioneditor.ErrorDisplayerRecord;
import records.gui.expressioneditor.ExpressionSaver;
import records.gui.expressioneditor.SaverBase;
import records.gui.expressioneditor.TypeEntry.Keyword;
import records.gui.expressioneditor.TypeEntry.Operator;
import records.transformations.expression.BracketedStatus;
import records.transformations.expression.type.TypeSaver.Context;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.FXPlatformConsumer;
import utility.Pair;
import utility.Utility;

import java.util.ArrayList;
import java.util.List;

@OnThread(Tag.FXPlatform)
public class TypeSaver extends SaverBase<TypeExpression, TypeSaver, Operator, Keyword, Context>
{
    final ImmutableList<OperatorExpressionInfo> OPERATORS = ImmutableList.of(
        new OperatorExpressionInfo(ImmutableList.of(new Pair<>(Operator.COMMA, "")), TypeSaver::makeNary)
    );

    private static TypeExpression makeNary(ImmutableList<TypeExpression> typeExpressions, List<Operator> operators, BracketAndNodes<TypeExpression, TypeSaver> brackets)
    {
        if (brackets.bracketedStatus == BracketedStatus.DIRECT_ROUND_BRACKETED)
        {
            return new TupleTypeExpression(typeExpressions);
        } else
        {
            Builder<Either<String, TypeExpression>> items = ImmutableList.builderWithExpectedSize(typeExpressions.size() + operators.size());
            for (int i = 0; i < typeExpressions.size(); i++)
            {
                items.add(Either.right(typeExpressions.get(i)));
                if (i < operators.size())
                    items.add(Either.left(operators.get(i).getContent()));
            }
            if (brackets.bracketedStatus == BracketedStatus.DIRECT_ROUND_BRACKETED)
                return new ListTypeExpression(new InvalidOpTypeExpression(items.build()));
            else
                return new InvalidOpTypeExpression(items.build());
        }
    }
    
    public TypeSaver(ConsecutiveBase<TypeExpression, TypeSaver> parent)
    {
        super(parent);
    }

    public class Context {}

    public void saveKeyword(Keyword keyword, ConsecutiveChild<TypeExpression, TypeSaver> errorDisplayer, FXPlatformConsumer<Context> withContext)
    {
        // TODO
    }

    @Override
    protected TypeExpression makeInvalidOp(ConsecutiveChild<TypeExpression, TypeSaver> start, ConsecutiveChild<TypeExpression, TypeSaver> end, ImmutableList<Either<Operator, @Recorded TypeExpression>> items)
    {
        return errorDisplayerRecord.recordType(start, end, new InvalidOpTypeExpression(Utility.mapListI(items, x -> x.mapBoth(u -> ",", y -> y))));
    }

    @Override
    protected TypeExpression record(ConsecutiveChild<TypeExpression, TypeSaver> start, ConsecutiveChild<TypeExpression, TypeSaver> end, TypeExpression typeExpression)
    {
        return errorDisplayerRecord.recordType(start, end, typeExpression);
    }

    @Override
    protected TypeExpression keywordToInvalid(Keyword keyword)
    {
        return new InvalidOpTypeExpression(ImmutableList.of(Either.left(keyword.getContent())));
    }

    @Override
    protected TypeExpression makeExpression(ConsecutiveChild<TypeExpression, TypeSaver> start, ConsecutiveChild<TypeExpression, TypeSaver> end, List<Either<@Recorded TypeExpression, OpAndNode>> content, BracketAndNodes<TypeExpression, TypeSaver> brackets)
    {
        if (content.isEmpty())
            return new InvalidOpTypeExpression(ImmutableList.of());

        CollectedItems collectedItems = processItems(content);

        if (collectedItems.isValid())
        {
            ArrayList<TypeExpression> validOperands = collectedItems.getValidOperands();
            ArrayList<Operator> validOperators = collectedItems.getValidOperators();

            // Single expression?
            if (validOperands.size() == 1 && validOperators.size() == 0)
                return validOperands.get(0);
            
            // Now we need to check the operators can work together as one group:
            @Nullable TypeExpression e = makeExpressionWithOperators(ImmutableList.of(OPERATORS), errorDisplayerRecord, arg ->
                    makeInvalidOp(brackets.start, brackets.end, arg)
                , ImmutableList.copyOf(validOperands), ImmutableList.copyOf(validOperators), brackets, arg -> arg);
            if (e != null)
            {
                return e;
            }

        }

        return new InvalidOpTypeExpression(Utility.mapListI(collectedItems.getInvalid(), e -> e.mapBoth(o -> o.getContent(), x -> x)));
    }
    
}