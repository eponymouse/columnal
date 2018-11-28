package records.transformations.expression.type;

import annotation.identifier.qual.ExpressionIdentifier;
import annotation.recorded.qual.Recorded;
import annotation.recorded.qual.UnknownIfRecorded;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.collect.ImmutableMap;
import javafx.scene.input.DataFormat;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.TableAndColumnRenames;
import records.gui.expressioneditor.ConsecutiveBase;
import records.gui.expressioneditor.ConsecutiveChild;
import records.gui.expressioneditor.SaverBase;
import records.gui.expressioneditor.TypeEditor;
import records.gui.expressioneditor.TypeEntry.Keyword;
import records.gui.expressioneditor.TypeEntry.Operator;
import records.transformations.expression.BracketedStatus;
import records.transformations.expression.Expression;
import records.transformations.expression.UnitExpression;
import records.transformations.expression.type.TypeSaver.Context;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.FXPlatformConsumer;
import utility.Pair;
import utility.Utility;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@OnThread(Tag.FXPlatform)
public class TypeSaver extends SaverBase<TypeExpression, TypeSaver, Operator, Keyword, Context>
{
    final ImmutableList<OperatorExpressionInfo> OPERATORS = ImmutableList.of(
        new OperatorExpressionInfo(ImmutableList.of(new Pair<Operator, @Localized String>(Operator.COMMA, Utility.universal(""))), TypeSaver::makeNary)
    );

    private static TypeExpression makeNary(ImmutableList<@Recorded TypeExpression> typeExpressions, List<Operator> operators, BracketAndNodes<TypeExpression, TypeSaver> brackets)
    {
        if (brackets.bracketedStatus == BracketedStatus.DIRECT_ROUND_BRACKETED)
        {
            return new TupleTypeExpression(typeExpressions);
        }
        else if (brackets.bracketedStatus == BracketedStatus.DIRECT_SQUARE_BRACKETED && typeExpressions.size() == 1 && operators.isEmpty())
        {
            return new ListTypeExpression(typeExpressions.get(0));
        }
        else
        {
            Builder<TypeExpression> items = ImmutableList.builderWithExpectedSize(typeExpressions.size() + operators.size());
            for (int i = 0; i < typeExpressions.size(); i++)
            {
                items.add(typeExpressions.get(i));
                if (i < operators.size())
                    items.add(InvalidIdentTypeExpression.identOrUnfinished(operators.get(i).getContent()));
            }
            if (brackets.bracketedStatus == BracketedStatus.DIRECT_SQUARE_BRACKETED)
                return new ListTypeExpression(new InvalidOpTypeExpression(items.build()));
            else
                return new InvalidOpTypeExpression(items.build());
        }
    }
    
    public TypeSaver(ConsecutiveBase<TypeExpression, TypeSaver> parent, boolean showFoundErrors)
    {
        super(parent, showFoundErrors);
    }

    public class Context {}

    public void saveKeyword(Keyword keyword, ConsecutiveChild<TypeExpression, TypeSaver> errorDisplayer, FXPlatformConsumer<Context> withContext)
    {
        Supplier<ImmutableList<@Recorded TypeExpression>> prefixKeyword = () -> ImmutableList.of(record(errorDisplayer, errorDisplayer, new InvalidIdentTypeExpression(keyword.getContent())));
        
        if (keyword == Keyword.OPEN_ROUND)
        {
            currentScopes.push(new Scope(errorDisplayer, expect(Keyword.CLOSE_ROUND, close -> new BracketAndNodes<TypeExpression, TypeSaver>(BracketedStatus.DIRECT_ROUND_BRACKETED, errorDisplayer, close),
                    (bracketed, bracketEnd) -> {
                        ArrayList<Either<@Recorded TypeExpression, OpAndNode>> precedingItems = currentScopes.peek().items;
                        // Type applications are a special case:
                        if (precedingItems.size() >= 1 && precedingItems.get(precedingItems.size() - 1).either(e -> e instanceof IdentTypeExpression || e instanceof TypeApplyExpression, op -> false))
                        {
                            @Nullable @Recorded TypeExpression callTarget = precedingItems.remove(precedingItems.size() - 1).<@Nullable @Recorded TypeExpression>either(e -> e, op -> null);
                            // Shouldn't ever be null:
                            if (callTarget != null)
                            {
                                Either<UnitExpression, TypeExpression> newArg = bracketed instanceof  UnitLiteralTypeExpression ? Either.left(((UnitLiteralTypeExpression)bracketed).getUnitExpression()) : Either.right(bracketed);
                                
                                TypeApplyExpression typeExpression;
                                if (callTarget instanceof TypeApplyExpression)
                                {
                                    TypeApplyExpression applyExpression = (TypeApplyExpression) callTarget;
                                    
                                    typeExpression = new TypeApplyExpression(applyExpression.getTypeName(), Utility.concatI(applyExpression.getArgumentsOnly(), ImmutableList.of(newArg)));
                                }
                                else
                                {
                                    IdentTypeExpression identTypeExpression = (IdentTypeExpression) callTarget; 
                                    typeExpression = new TypeApplyExpression(identTypeExpression.getIdent(), ImmutableList.of(newArg));
                                }
                                return Either.left(errorDisplayerRecord.recordType(errorDisplayerRecord.recorderFor(callTarget).start, bracketEnd, typeExpression));
                            }
                        }
                        return Either.left(errorDisplayerRecord.recordType(errorDisplayer, bracketEnd, bracketed));
            }, prefixKeyword)));
        }
        else if (keyword == Keyword.OPEN_SQUARE)
        {
            currentScopes.push(new Scope(errorDisplayer, expect(Keyword.CLOSE_SQUARE, close -> new BracketAndNodes<>(BracketedStatus.DIRECT_SQUARE_BRACKETED, errorDisplayer, close), (e, c) -> Either.left(e), prefixKeyword)));
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
    public void saveOperand(TypeExpression singleItem, ConsecutiveChild<TypeExpression, TypeSaver> start, ConsecutiveChild<TypeExpression, TypeSaver> end, FXPlatformConsumer<Context> withContext)
    {
        if (singleItem instanceof UnitLiteralTypeExpression)
        {
            ArrayList<Either<@Recorded TypeExpression, OpAndNode>> curItems = currentScopes.peek().items;
            if (curItems.size() > 0)
            {
                Either<@Recorded TypeExpression, OpAndNode> last = curItems.get(curItems.size() - 1);
                if (last.either(t -> {
                    if (t instanceof NumberTypeExpression && !((NumberTypeExpression) t).hasUnit())
                    {
                        curItems.remove(curItems.size() - 1);
                        super.saveOperand(new NumberTypeExpression(((UnitLiteralTypeExpression) singleItem).getUnitExpression()), errorDisplayerRecord.recorderFor(t).start, end, withContext);
                        return true;
                    }
                    return false;
                }, o -> false))
                {
                    return;
                }
            }
        }
        super.saveOperand(singleItem, start, end, withContext);
    }

    @Override
    protected TypeExpression makeSingleInvalid(Keyword terminator)
    {
        return new InvalidIdentTypeExpression(terminator.getContent());
    }

    @Override
    protected @Recorded TypeExpression makeInvalidOp(ConsecutiveChild<TypeExpression, TypeSaver> start, ConsecutiveChild<TypeExpression, TypeSaver> end, ImmutableList<Either<OpAndNode, @Recorded TypeExpression>> items)
    {
        return errorDisplayerRecord.recordType(start, end, new InvalidOpTypeExpression(Utility.mapListI(items, x -> x.either(u -> new InvalidIdentTypeExpression(","), y -> y))));
    }

    @Override
    protected @Recorded TypeExpression record(ConsecutiveChild<TypeExpression, TypeSaver> start, ConsecutiveChild<TypeExpression, TypeSaver> end, TypeExpression typeExpression)
    {
        return errorDisplayerRecord.recordType(start, end, typeExpression);
    }

    @Override
    protected TypeExpression keywordToInvalid(Keyword keyword)
    {
        return new InvalidIdentTypeExpression(keyword.getContent());
    }

    @Override
    protected @Recorded TypeExpression makeExpression(ConsecutiveChild<TypeExpression, TypeSaver> start, ConsecutiveChild<TypeExpression, TypeSaver> end, List<Either<@Recorded TypeExpression, OpAndNode>> content, BracketAndNodes<TypeExpression, TypeSaver> brackets)
    {
        if (content.isEmpty())
            return record(start, end, new InvalidOpTypeExpression(ImmutableList.of()));

        CollectedItems collectedItems = processItems(content);

        if (collectedItems.isValid())
        {
            ArrayList<@Recorded TypeExpression> validOperands = collectedItems.getValidOperands();
            ArrayList<OpAndNode> validOperators = collectedItems.getValidOperators();

            // Single expression?
            if (validOperands.size() == 1 && validOperators.size() == 0)
            {
                if (brackets.bracketedStatus == BracketedStatus.DIRECT_SQUARE_BRACKETED)
                    return record(brackets.start, brackets.end, new ListTypeExpression(validOperands.get(0)));
                else
                    return validOperands.get(0);
            }
            
            // Now we need to check the operators can work together as one group:
            @Nullable TypeExpression e = makeExpressionWithOperators(ImmutableList.of(OPERATORS), errorDisplayerRecord, (ImmutableList<Either<OpAndNode, @Recorded TypeExpression>> arg) ->
                    makeInvalidOp(brackets.start, brackets.end, arg)
                , ImmutableList.copyOf(validOperands), ImmutableList.copyOf(validOperators), brackets, arg -> arg);
            if (e != null)
            {
                return record(start, end, e);
            }

        }

        return record(start, end, new InvalidOpTypeExpression(Utility.mapListI(collectedItems.getInvalid(), e -> e.either(o -> InvalidIdentTypeExpression.identOrUnfinished(o.op.getContent()), x -> x))));
    }

    @Override
    protected Map<DataFormat, Object> toClipboard(@UnknownIfRecorded TypeExpression expression)
    {
        return ImmutableMap.of(
                TypeEditor.TYPE_CLIPBOARD_TYPE, expression.save(true, TableAndColumnRenames.EMPTY),
                DataFormat.PLAIN_TEXT, expression.save(false, TableAndColumnRenames.EMPTY)
        );
    }
}
