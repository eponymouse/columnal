package records.gui.lexeditor;

import annotation.identifier.qual.ExpressionIdentifier;
import annotation.recorded.qual.Recorded;
import annotation.recorded.qual.UnknownIfRecorded;
import annotation.units.CanonicalLocation;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import javafx.scene.input.DataFormat;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.TableAndColumnRenames;
import records.data.datatype.DataType;
import records.data.datatype.TypeManager;
import records.error.InternalException;
import records.gui.lexeditor.EditorLocationAndErrorRecorder.CanonicalSpan;
import records.gui.lexeditor.TypeLexer.Keyword;
import records.gui.lexeditor.TypeLexer.Operator;
import records.gui.lexeditor.TypeSaver.BracketContent;
import records.jellytype.JellyType;
import records.transformations.expression.UnitExpression;
import records.transformations.expression.type.IdentTypeExpression;
import records.transformations.expression.type.InvalidIdentTypeExpression;
import records.transformations.expression.type.InvalidOpTypeExpression;
import records.transformations.expression.type.ListTypeExpression;
import records.transformations.expression.type.NumberTypeExpression;
import records.transformations.expression.type.RecordTypeExpression;
import records.transformations.expression.type.TypeApplyExpression;
import records.transformations.expression.type.TypeExpression;
import records.transformations.expression.type.UnitLiteralTypeExpression;
import styled.StyledShowable;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.Pair;
import utility.Utility;
import utility.gui.FXUtility;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

@OnThread(Tag.FXPlatform)
public class TypeSaver extends SaverBase<TypeExpression, TypeSaver, Operator, Keyword, BracketContent>
{
    public static final DataFormat TYPE_CLIPBOARD_TYPE = FXUtility.getDataFormat("application/records-type");
    
    final ImmutableList<OperatorExpressionInfo> OPERATORS = ImmutableList.of(
        new OperatorExpressionInfo(ImmutableList.of(Operator.COMMA, Operator.COLON), new MakeNary<TypeExpression, TypeSaver, Operator, BracketContent>()
        {
            @Nullable
            @Override
            public <R extends StyledShowable> @Recorded R makeNary(ImmutableList<@Recorded TypeExpression> typeExpressions, List<Pair<Operator, CanonicalSpan>> operators, BracketAndNodes<TypeExpression, TypeSaver, BracketContent, R> bracketedStatus, EditorLocationAndErrorRecorder errorDisplayerRecord)
            {
                return bracketedStatus.applyBrackets.apply(new BracketContent(typeExpressions, ImmutableList.copyOf(operators)));
            }
        })
    );

    public class BracketContent
    {
        private final ImmutableList<@Recorded TypeExpression> typeExpressions;
        private final ImmutableList<Pair<Operator, CanonicalSpan>> operators;

        public BracketContent(ImmutableList<@Recorded TypeExpression> typeExpressions, ImmutableList<Pair<Operator, CanonicalSpan>> operators)
        {
            this.typeExpressions = typeExpressions;
            this.operators = operators;
        }
    }

    public void saveKeyword(Keyword keyword, CanonicalSpan errorDisplayer)
    {
        Supplier<ImmutableList<@Recorded TypeExpression>> prefixKeyword = () -> ImmutableList.of(this.<TypeExpression>record(errorDisplayer, new InvalidIdentTypeExpression(keyword.getContent())));
        
        if (keyword == Keyword.OPEN_ROUND)
        {
            currentScopes.push(new Scope(errorDisplayer, expect(ImmutableList.of(Keyword.CLOSE_ROUND), close -> new BracketAndNodes<TypeExpression, TypeSaver, BracketContent, TypeExpression>(tupleOrSingle(locationRecorder, CanonicalSpan.fromTo(errorDisplayer, close)), CanonicalSpan.fromTo(errorDisplayer, close), ImmutableList.of()),
                    (bracketed, bracketEnd) -> {
                        ArrayList<Either<@Recorded TypeExpression, OpAndNode>> precedingItems = currentScopes.peek().items;
                        // Type applications are a special case:
                        if (precedingItems.size() >= 1 && precedingItems.get(precedingItems.size() - 1).either(e -> e instanceof IdentTypeExpression || e instanceof TypeApplyExpression, op -> false))
                        {
                            @Nullable @Recorded TypeExpression callTarget = precedingItems.remove(precedingItems.size() - 1).<@Nullable @Recorded TypeExpression>either(e -> e, op -> null);
                            // Shouldn't ever be null:
                            if (callTarget != null)
                            {
                                Either<@Recorded UnitExpression, @Recorded TypeExpression> newArg = bracketed instanceof  UnitLiteralTypeExpression ? Either.left(((UnitLiteralTypeExpression)bracketed).getUnitExpression()) : Either.right(bracketed);
                                
                                TypeApplyExpression typeExpression;
                                if (callTarget instanceof TypeApplyExpression)
                                {
                                    TypeApplyExpression applyExpression = (TypeApplyExpression) callTarget;
                                    
                                    typeExpression = new TypeApplyExpression(applyExpression.getTypeName(), Utility.<Either<@Recorded UnitExpression, @Recorded TypeExpression>>concatI(applyExpression.getArgumentsOnly(), ImmutableList.<Either<@Recorded UnitExpression, @Recorded TypeExpression>>of(newArg)));
                                }
                                else
                                {
                                    IdentTypeExpression identTypeExpression = (IdentTypeExpression) callTarget; 
                                    typeExpression = new TypeApplyExpression(identTypeExpression.getIdent(), ImmutableList.<Either<@Recorded UnitExpression, @Recorded TypeExpression>>of(newArg));
                                }
                                return Either.<@Recorded TypeExpression, Terminator>left(locationRecorder.<TypeExpression>record(CanonicalSpan.fromTo(recorderFor(callTarget), bracketEnd), typeExpression));
                            }
                        }
                        return Either.<@Recorded TypeExpression, Terminator>left(bracketed);
            }, prefixKeyword, null, true)));
        }
        else if (keyword == Keyword.OPEN_SQUARE)
        {
            currentScopes.push(new Scope(errorDisplayer, expect(ImmutableList.of(Keyword.CLOSE_SQUARE), close -> new BracketAndNodes<>(makeList(locationRecorder, CanonicalSpan.fromTo(errorDisplayer, close), CanonicalSpan.fromTo(errorDisplayer.rhs(), close.lhs())), CanonicalSpan.fromTo(errorDisplayer, close), ImmutableList.of()), (e, c) -> Either.<@Recorded TypeExpression, Terminator>left(e), prefixKeyword, null, true)));
        }
        else
        {
            // Should be a terminator:
            Scope cur = currentScopes.pop();
            if (currentScopes.size() == 0)
            {
                addTopLevelScope();
            }
            cur.terminator.terminate(new FetchMake(cur), keyword, errorDisplayer);
        }
    }

    private ApplyBrackets<BracketContent, TypeExpression, TypeExpression> tupleOrSingle(@UnknownInitialization(Object.class) TypeSaver this, EditorLocationAndErrorRecorder errorDisplayerRecord, CanonicalSpan location)
    {
        return new ApplyBrackets<BracketContent, TypeExpression, TypeExpression>()
        {
            @Override
            public @Nullable @Recorded TypeExpression apply(@NonNull BracketContent items)
            {
                if (items.typeExpressions.size() == 1)
                    return items.typeExpressions.get(0);
                
                // Need to have pattern: IDENT : EXPRESSION (, IDENT : EXPRESSION)*
                boolean allOk = true;
                @Nullable @ExpressionIdentifier String recentIdent = null;
                ArrayList<Pair<@ExpressionIdentifier String, @Recorded TypeExpression>> pairs = new ArrayList<>();
                for (int i = 0; i < items.typeExpressions.size(); i++)
                {
                    @Recorded TypeExpression expression = items.typeExpressions.get(i);
                    if (recentIdent == null)
                    {
                        // Looking for ident:
                        if (expression instanceof IdentTypeExpression)
                        {
                            if (i != 0 && items.operators.get(i - 1).getFirst() != Operator.COMMA)
                            {
                                allOk = false;
                                break;
                            }

                            recentIdent = ((IdentTypeExpression) expression).getIdent();
                            continue;
                        }
                        allOk = false;
                    }
                    else
                    {
                        // Looking for expression:
                        if (i != 0 && items.operators.get(i - 1).getFirst() != Operator.COLON)
                        {
                            allOk = false;
                            break;
                        }
                        pairs.add(new Pair<>(recentIdent, expression));
                        recentIdent = null;
                    }
                }
                if (allOk)
                    return errorDisplayerRecord.record(location, new RecordTypeExpression(ImmutableList.copyOf(pairs)));
                
                ImmutableList.Builder<@Recorded TypeExpression> invalidOps = ImmutableList.builder();
                for (int i = 0; i < items.typeExpressions.size(); i++)
                {
                    @Recorded TypeExpression expression = items.typeExpressions.get(i);
                    invalidOps.add(expression);
                    if (i < items.operators.size())
                        invalidOps.add(errorDisplayerRecord.record(items.operators.get(i).getSecond(), new InvalidIdentTypeExpression(items.operators.get(i).getFirst().getContent())));
                }
                return errorDisplayerRecord.record(location, new InvalidOpTypeExpression(invalidOps.build()));
            }

            @Override
            public @NonNull @Recorded TypeExpression applySingle(@NonNull @Recorded TypeExpression singleItem)
            {
                return singleItem;
            }
        };
    }

    private ApplyBrackets<BracketContent, TypeExpression, TypeExpression> makeList(EditorLocationAndErrorRecorder errorDisplayerRecord, CanonicalSpan locationInclBrackets, CanonicalSpan locationInsideBrackets)
    {
        return new ApplyBrackets<BracketContent, TypeExpression, TypeExpression>()
        {
            @Override
            public @Recorded @Nullable TypeExpression apply(@NonNull BracketContent items)
            {
                if (items.typeExpressions.size() == 1)
                    return errorDisplayerRecord.record(locationInclBrackets, new ListTypeExpression(items.typeExpressions.get(0)));
                else
                {
                    @Recorded InvalidOpTypeExpression content = errorDisplayerRecord.record(locationInsideBrackets, new InvalidOpTypeExpression(items.typeExpressions));
                    errorDisplayerRecord.addErrorAndFixes(locationInsideBrackets, StyledString.s("Invalid expression"), ImmutableList.of());
                    return errorDisplayerRecord.record(locationInclBrackets, new ListTypeExpression(content));
                }
            }

            @Override
            public @Recorded @NonNull TypeExpression applySingle(@NonNull @Recorded TypeExpression singleItem)
            {
                return errorDisplayerRecord.record(locationInclBrackets, new ListTypeExpression(singleItem));
            }
        };
    }

    @Override
    protected BracketAndNodes<TypeExpression, TypeSaver, BracketContent, TypeExpression> expectSingle(@UnknownInitialization(Object.class)TypeSaver this, EditorLocationAndErrorRecorder errorDisplayerRecord, CanonicalSpan location)
    {
        return new BracketAndNodes<>(new ApplyBrackets<BracketContent, TypeExpression, TypeExpression>()
        {
            @Override
            public @Nullable @Recorded TypeExpression apply(@NonNull BracketContent items)
            {
                if (items.typeExpressions.size() == 1)
                    return items.typeExpressions.get(0);
                else
                    return null;
            }

            @Override
            public @NonNull @Recorded TypeExpression applySingle(@NonNull @Recorded TypeExpression singleItem)
            {
                return singleItem;
            }
        }, location, ImmutableList.of(tupleOrSingle(errorDisplayerRecord, location)));
    }
    

    @Override
    public void saveOperand(TypeExpression singleItem, CanonicalSpan location)
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
                        super.saveOperand(new NumberTypeExpression(((UnitLiteralTypeExpression) singleItem).getUnitExpression()), CanonicalSpan.fromTo(locationRecorder.recorderFor(t), location));
                        return true;
                    }
                    return false;
                }, o -> false))
                {
                    return;
                }
            }
        }
        super.saveOperand(singleItem, location);
    }

    @Override
    protected @Nullable Supplier<@Recorded TypeExpression> canBeUnary(OpAndNode operator, TypeExpression followingOperand)
    {
        return null;
    }

    @Override
    protected @Recorded TypeExpression makeInvalidOp(CanonicalSpan location, ImmutableList<Either<OpAndNode, @Recorded TypeExpression>> items)
    {
        return locationRecorder.record(location, new InvalidOpTypeExpression(Utility.<Either<OpAndNode, @Recorded TypeExpression>, @Recorded TypeExpression>mapListI(items, x -> x.<@Recorded TypeExpression>either(u -> record(u.sourceNode, new InvalidIdentTypeExpression(",")), y -> y))));
    }

    @Override
    protected CanonicalSpan recorderFor(@Recorded TypeExpression typeExpression)
    {
        return locationRecorder.recorderFor(typeExpression);
    }

    @Override
    protected TypeExpression keywordToInvalid(Keyword keyword)
    {
        return new InvalidIdentTypeExpression(keyword.getContent());
    }

    @Override
    protected <R extends StyledShowable> @Recorded R makeExpression(List<Either<@Recorded TypeExpression, OpAndNode>> content, BracketAndNodes<TypeExpression, TypeSaver, BracketContent, R> brackets, @CanonicalLocation int innerContentLocation, @Nullable String terminatorDescription)
    {
        if (content.isEmpty())
        {
            // If terminator is null, error will be elsewhere
            if (terminatorDescription != null)
                locationRecorder.addErrorAndFixes(new CanonicalSpan(innerContentLocation, innerContentLocation), StyledString.s("Empty brackets"), ImmutableList.of());
            return brackets.applyBrackets.applySingle(record(brackets.location, new InvalidOpTypeExpression(ImmutableList.of())));
        }

        CanonicalSpan location = CanonicalSpan.fromTo(getLocationForEither(content.get(0)), getLocationForEither(content.get(content.size() - 1)));
        
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
                        this.<TypeExpression>record(CanonicalSpan.fromTo(locationRecorder.recorderFor(numberType),
                            locationRecorder.recorderFor(unitLiteral)),
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
                @Recorded R bracketedValid = brackets.applyBrackets.applySingle(validOperands.get(0));
                return bracketedValid;
            }
            
            // Now we need to check the operators can work together as one group:
            @Nullable @Recorded R e = makeExpressionWithOperators(ImmutableList.of(OPERATORS), locationRecorder, (ImmutableList<Either<OpAndNode, @Recorded TypeExpression>> arg) ->
                    brackets.applyBrackets.applySingle(makeInvalidOp(brackets.location, arg))
                , ImmutableList.copyOf(validOperands), ImmutableList.copyOf(validOperators), brackets);
            if (e != null)
            {
                return e;
            }

        }

        return brackets.applyBrackets.applySingle(collectedItems.makeInvalid(location, InvalidOpTypeExpression::new));
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
                TYPE_CLIPBOARD_TYPE, expression.save(true, TableAndColumnRenames.EMPTY),
                DataFormat.PLAIN_TEXT, expression.save(false, TableAndColumnRenames.EMPTY)
        );
    }

    @Override
    protected BracketAndNodes<TypeExpression, TypeSaver, BracketContent, TypeExpression> unclosedBrackets(BracketAndNodes<TypeExpression, TypeSaver, BracketContent, TypeExpression> closed)
    {
        return new BracketAndNodes<>(new ApplyBrackets<BracketContent, TypeExpression, TypeExpression>()
        {
            @Nullable
            @Override
            public @Recorded TypeExpression apply(@NonNull BracketContent items)
            {
                return record(closed.location, new InvalidOpTypeExpression(items.typeExpressions));
            }

            @NonNull
            @Override
            public @Recorded TypeExpression applySingle(@NonNull @Recorded TypeExpression singleItem)
            {
                return singleItem;
            }
        }, closed.location, ImmutableList.of(closed.applyBrackets));
    }

    private class FetchMake implements FetchContent<TypeExpression, TypeSaver, BracketContent>
    {
        private final Scope cur;

        public FetchMake(Scope cur)
        {
            this.cur = cur;
        }

        @Override
        public <R extends StyledShowable> @Recorded R fetchContent(BracketAndNodes<TypeExpression, TypeSaver, BracketContent, R> brackets)
        {
            return TypeSaver.this.makeExpression(cur.items, brackets, cur.openingNode.end, cur.terminator.terminatorDescription);
        }
    }
}
