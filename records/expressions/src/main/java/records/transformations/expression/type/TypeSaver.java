package records.transformations.expression.type;

import annotation.recorded.qual.Recorded;
import annotation.recorded.qual.UnknownIfRecorded;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.collect.ImmutableMap;
import javafx.scene.input.DataFormat;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.PolyNull;
import records.data.TableAndColumnRenames;
import records.data.unit.Unit;
import records.gui.expressioneditor.ConsecutiveBase;
import records.gui.expressioneditor.ConsecutiveChild;
import records.gui.expressioneditor.ErrorDisplayerRecord;
import records.gui.expressioneditor.ErrorDisplayerRecord.Span;
import records.gui.expressioneditor.SaverBase;
import records.gui.expressioneditor.TypeEditor;
import records.gui.expressioneditor.TypeEntry.Keyword;
import records.gui.expressioneditor.TypeEntry.Operator;
import records.transformations.expression.BracketedStatus;
import records.transformations.expression.UnitExpression;
import records.transformations.expression.type.TypeSaver.BracketContent;
import records.transformations.expression.type.TypeSaver.Context;
import records.typeExp.units.UnitExp;
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
public class TypeSaver extends SaverBase<TypeExpression, TypeSaver, Operator, Keyword, Context, BracketContent>
{
    final ImmutableList<OperatorExpressionInfo> OPERATORS = ImmutableList.of(
        new OperatorExpressionInfo(ImmutableList.of(new Pair<Operator, @Localized String>(Operator.COMMA, Utility.universal(""))), new MakeNary<TypeExpression, TypeSaver, Operator, BracketContent>()
        {
            @Nullable
            @Override
            public @Recorded TypeExpression makeNary(ImmutableList<@Recorded TypeExpression> typeExpressions, List<Pair<Operator, ConsecutiveChild<TypeExpression, TypeSaver>>> operators, BracketAndNodes<TypeExpression, TypeSaver, BracketContent> bracketedStatus, ErrorDisplayerRecord errorDisplayerRecord)
            {
                return bracketedStatus.applyBrackets.apply(new BracketContent(typeExpressions));
            }
        })
    );
    
    public TypeSaver(ConsecutiveBase<TypeExpression, TypeSaver> parent, boolean showFoundErrors)
    {
        super(parent, showFoundErrors);
    }

    public class Context {}
    
    public class BracketContent
    {
        private final ImmutableList<@Recorded TypeExpression> typeExpressions;

        public BracketContent(ImmutableList<@Recorded TypeExpression> typeExpressions)
        {
            this.typeExpressions = typeExpressions;
        }
    }

    public void saveKeyword(Keyword keyword, ConsecutiveChild<TypeExpression, TypeSaver> errorDisplayer, FXPlatformConsumer<Context> withContext)
    {
        Supplier<ImmutableList<@Recorded TypeExpression>> prefixKeyword = () -> ImmutableList.of(record(errorDisplayer, errorDisplayer, new InvalidIdentTypeExpression(keyword.getContent())));
        
        if (keyword == Keyword.OPEN_ROUND)
        {
            currentScopes.push(new Scope(errorDisplayer, expect(Keyword.CLOSE_ROUND, close -> new BracketAndNodes<TypeExpression, TypeSaver, BracketContent>(tupleOrSingle(errorDisplayerRecord, errorDisplayer, close), errorDisplayer, close, ImmutableList.of()),
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
                                    
                                    typeExpression = new TypeApplyExpression(applyExpression.getTypeName(), Utility.<Either<UnitExpression, TypeExpression>>concatI(applyExpression.getArgumentsOnly(), ImmutableList.<Either<UnitExpression, TypeExpression>>of(newArg)));
                                }
                                else
                                {
                                    IdentTypeExpression identTypeExpression = (IdentTypeExpression) callTarget; 
                                    typeExpression = new TypeApplyExpression(identTypeExpression.getIdent(), ImmutableList.of(newArg));
                                }
                                return Either.<@Recorded TypeExpression, Terminator>left(errorDisplayerRecord.<TypeExpression>recordType(errorDisplayerRecord.recorderFor(callTarget).start, bracketEnd, typeExpression));
                            }
                        }
                        return Either.<@Recorded TypeExpression, Terminator>left(errorDisplayerRecord.<TypeExpression>recordType(errorDisplayer, bracketEnd, bracketed));
            }, prefixKeyword)));
        }
        else if (keyword == Keyword.OPEN_SQUARE)
        {
            currentScopes.push(new Scope(errorDisplayer, expect(Keyword.CLOSE_SQUARE, close -> new BracketAndNodes<>(makeList(errorDisplayerRecord, errorDisplayer, close), errorDisplayer, close, ImmutableList.of()), (e, c) -> Either.<@Recorded TypeExpression, Terminator>left(e), prefixKeyword)));
        }
        else
        {
            // Should be a terminator:
            Scope cur = currentScopes.pop();
            if (currentScopes.size() == 0)
            {
                addTopLevelScope(errorDisplayer.getParent());
            }
            cur.terminator.terminate(new FetchMake(cur), keyword, errorDisplayer, withContext);
        }
    }

    private ApplyBrackets<BracketContent, TypeExpression> tupleOrSingle(ErrorDisplayerRecord errorDisplayerRecord, ConsecutiveChild<TypeExpression, TypeSaver> start, ConsecutiveChild<TypeExpression, TypeSaver> end)
    {
        return new ApplyBrackets<BracketContent, TypeExpression>()
        {
            @Override
            public @PolyNull @Recorded TypeExpression apply(@PolyNull BracketContent items)
            {
                if (items == null)
                    return null;
                else if (items.typeExpressions.size() == 1)
                    return items.typeExpressions.get(0);
                else
                    return errorDisplayerRecord.recordType(start, end, new TupleTypeExpression(items.typeExpressions));
            }

            @Override
            public @PolyNull @Recorded TypeExpression applySingle(@PolyNull @Recorded TypeExpression singleItem)
            {
                return singleItem;
            }
        };
    }

    private ApplyBrackets<BracketContent, TypeExpression> makeList(ErrorDisplayerRecord errorDisplayerRecord, ConsecutiveChild<TypeExpression, TypeSaver> start, ConsecutiveChild<TypeExpression, TypeSaver> end)
    {
        return new ApplyBrackets<BracketContent, TypeExpression>()
        {
            @Override
            public @Recorded @PolyNull TypeExpression apply(@PolyNull BracketContent items)
            {
                if (items == null)
                    return null;
                else if (items.typeExpressions.size() == 1)
                    return errorDisplayerRecord.recordType(start, end, new ListTypeExpression(items.typeExpressions.get(0)));
                else
                    return errorDisplayerRecord.recordType(start, end, new ListTypeExpression(new InvalidOpTypeExpression(items.typeExpressions)));
            }

            @Override
            public @Recorded @PolyNull TypeExpression applySingle(@PolyNull TypeExpression singleItem)
            {
                if (singleItem == null)
                    return null;
                else
                    return errorDisplayerRecord.recordType(start, end, new ListTypeExpression(singleItem));
            }
        };
    }

    @Override
    protected BracketAndNodes<TypeExpression, TypeSaver, BracketContent> expectSingle(@UnknownInitialization(Object.class) TypeSaver this, ErrorDisplayerRecord errorDisplayerRecord, ConsecutiveChild<TypeExpression, TypeSaver> start, ConsecutiveChild<TypeExpression, TypeSaver> end)
    {
        return new BracketAndNodes<>(new ApplyBrackets<BracketContent, TypeExpression>()
        {
            @Override
            public @PolyNull @Recorded TypeExpression apply(@PolyNull BracketContent items)
            {
                if (items == null)
                    return null;
                else if (items.typeExpressions.size() == 1)
                    return items.typeExpressions.get(0);
                else
                    return errorDisplayerRecord.recordType(start, end, new InvalidOpTypeExpression(items.typeExpressions));
            }

            @Override
            public @PolyNull @Recorded TypeExpression applySingle(@PolyNull @Recorded TypeExpression singleItem)
            {
                return singleItem;
            }
        }, start, end, ImmutableList.of());
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
    protected @Nullable Supplier<@Recorded TypeExpression> canBeUnary(OpAndNode operator, TypeExpression followingOperand)
    {
        return null;
    }

    @Override
    protected @Recorded TypeExpression makeInvalidOp(ConsecutiveChild<TypeExpression, TypeSaver> start, ConsecutiveChild<TypeExpression, TypeSaver> end, ImmutableList<Either<OpAndNode, @Recorded TypeExpression>> items)
    {
        return errorDisplayerRecord.recordType(start, end, new InvalidOpTypeExpression(Utility.<Either<OpAndNode, @Recorded TypeExpression>, @Recorded TypeExpression>mapListI(items, x -> x.<@Recorded TypeExpression>either(u -> record(u.sourceNode, u.sourceNode, new InvalidIdentTypeExpression(",")), y -> y))));
    }

    @Override
    protected Span<TypeExpression, TypeSaver> recorderFor(@Recorded TypeExpression typeExpression)
    {
        return errorDisplayerRecord.recorderFor(typeExpression);
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
    protected @Recorded TypeExpression makeExpression(ConsecutiveChild<TypeExpression, TypeSaver> start, ConsecutiveChild<TypeExpression, TypeSaver> end, List<Either<@Recorded TypeExpression, OpAndNode>> content, BracketAndNodes<TypeExpression, TypeSaver, BracketContent> brackets)
    {
        if (content.isEmpty())
            return record(start, end, new InvalidOpTypeExpression(ImmutableList.of()));
        
        content = new ArrayList<>(content);

        // Don't examine last item, can't be followed by unit:
        for (int i = 0; i < content.size() - 1; i++)
        {
            @Recorded NumberTypeExpression numberType = content.get(i).<@Nullable @Recorded NumberTypeExpression>either(e -> e instanceof NumberTypeExpression && !((NumberTypeExpression) e).hasUnit() ? (NumberTypeExpression)e : null, op -> null);
            if (numberType != null)
            {
                @Recorded UnitLiteralTypeExpression unitLiteral = content.get(i + 1).<@Nullable @Recorded UnitLiteralTypeExpression>either(e -> e instanceof UnitLiteralTypeExpression ? (UnitLiteralTypeExpression)e : null, op -> null);
                if (unitLiteral != null)
                {
                    content.set(i, Either.left(
                        record(errorDisplayerRecord.recorderFor(numberType).start,
                            errorDisplayerRecord.recorderFor(unitLiteral).end,
                            new NumberTypeExpression(unitLiteral.getUnitExpression()))));
                    i -= 1;
                }
            }
        }

        CollectedItems collectedItems = processItems(content);

        if (collectedItems.isValid())
        {
            ArrayList<@Recorded TypeExpression> validOperands = collectedItems.getValidOperands();
            ArrayList<OpAndNode> validOperators = collectedItems.getValidOperators();

            // Single expression?
            if (validOperands.size() == 1 && validOperators.size() == 0)
            {
                return brackets.applyBrackets.apply(new BracketContent(ImmutableList.copyOf(validOperands)));
            }
            
            // Now we need to check the operators can work together as one group:
            @Nullable TypeExpression e = makeExpressionWithOperators(ImmutableList.of(OPERATORS), errorDisplayerRecord, (ImmutableList<Either<OpAndNode, @Recorded TypeExpression>> arg) ->
                    makeInvalidOp(brackets.start, brackets.end, arg)
                , ImmutableList.copyOf(validOperands), ImmutableList.copyOf(validOperators), brackets);
            if (e != null)
            {
                return record(start, end, e);
            }

        }

        return collectedItems.makeInvalid(start, end, InvalidOpTypeExpression::new);
    }

    @Override
    protected TypeExpression opToInvalid(Operator operator)
    {
        return InvalidIdentTypeExpression.identOrUnfinished(operator.getContent());
    }

    @Override
    protected Map<DataFormat, Object> toClipboard(@UnknownIfRecorded TypeExpression expression)
    {
        return ImmutableMap.of(
                TypeEditor.TYPE_CLIPBOARD_TYPE, expression.save(true, TableAndColumnRenames.EMPTY),
                DataFormat.PLAIN_TEXT, expression.save(false, TableAndColumnRenames.EMPTY)
        );
    }

    private class FetchMake implements FetchContent<TypeExpression, TypeSaver, BracketContent>
    {
        private final Scope cur;

        public FetchMake(Scope cur)
        {
            this.cur = cur;
        }

        @Override
        public @Recorded TypeExpression fetchContent(BracketAndNodes<TypeExpression, TypeSaver, BracketContent> brackets)
        {
            return TypeSaver.this.makeExpression(cur.openingNode, brackets.end, cur.items, brackets);
        }
    }
}
