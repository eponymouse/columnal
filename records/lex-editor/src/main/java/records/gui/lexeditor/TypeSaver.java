package records.gui.lexeditor;

import annotation.recorded.qual.Recorded;
import annotation.recorded.qual.UnknownIfRecorded;
import annotation.units.CanonicalLocation;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import javafx.scene.input.DataFormat;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.TableAndColumnRenames;
import records.gui.lexeditor.EditorLocationAndErrorRecorder.CanonicalSpan;
import records.gui.lexeditor.TypeLexer.Keyword;
import records.gui.lexeditor.TypeLexer.Operator;
import records.gui.lexeditor.TypeSaver.BracketContent;
import records.transformations.expression.UnitExpression;
import records.transformations.expression.type.IdentTypeExpression;
import records.transformations.expression.type.InvalidIdentTypeExpression;
import records.transformations.expression.type.InvalidOpTypeExpression;
import records.transformations.expression.type.ListTypeExpression;
import records.transformations.expression.type.NumberTypeExpression;
import records.transformations.expression.type.TupleTypeExpression;
import records.transformations.expression.type.TypeApplyExpression;
import records.transformations.expression.type.TypeExpression;
import records.transformations.expression.type.UnitLiteralTypeExpression;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.FXPlatformConsumer;
import utility.Pair;
import utility.Utility;
import utility.gui.FXUtility;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@OnThread(Tag.FXPlatform)
public class TypeSaver extends SaverBase<TypeExpression, TypeSaver, Operator, Keyword, BracketContent>
{
    public static final DataFormat TYPE_CLIPBOARD_TYPE = FXUtility.getDataFormat("application/records-type");
    
    final ImmutableList<OperatorExpressionInfo> OPERATORS = ImmutableList.of(
        new OperatorExpressionInfo(ImmutableList.of(Operator.COMMA), new MakeNary<TypeExpression, TypeSaver, Operator, BracketContent>()
        {
            @Nullable
            @Override
            public @Recorded TypeExpression makeNary(ImmutableList<@Recorded TypeExpression> typeExpressions, List<Pair<Operator, CanonicalSpan>> operators, BracketAndNodes<TypeExpression, TypeSaver, BracketContent> bracketedStatus, EditorLocationAndErrorRecorder errorDisplayerRecord)
            {
                return bracketedStatus.applyBrackets.apply(new BracketContent(typeExpressions));
            }
        })
    );

    public class BracketContent
    {
        private final ImmutableList<@Recorded TypeExpression> typeExpressions;

        public BracketContent(ImmutableList<@Recorded TypeExpression> typeExpressions)
        {
            this.typeExpressions = typeExpressions;
        }
    }

    public void saveKeyword(Keyword keyword, CanonicalSpan errorDisplayer)
    {
        Supplier<ImmutableList<@Recorded TypeExpression>> prefixKeyword = () -> ImmutableList.of(record(errorDisplayer, new InvalidIdentTypeExpression(keyword.getContent())));
        
        if (keyword == Keyword.OPEN_ROUND)
        {
            currentScopes.push(new Scope(errorDisplayer, expect(ImmutableList.of(Keyword.CLOSE_ROUND), close -> new BracketAndNodes<TypeExpression, TypeSaver, BracketContent>(tupleOrSingle(locationRecorder, CanonicalSpan.fromTo(errorDisplayer, close)), CanonicalSpan.fromTo(errorDisplayer, close), ImmutableList.of()),
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
                                return Either.<@Recorded TypeExpression, Terminator>left(locationRecorder.<TypeExpression>recordType(CanonicalSpan.fromTo(recorderFor(callTarget), bracketEnd), typeExpression));
                            }
                        }
                        return Either.<@Recorded TypeExpression, Terminator>left(bracketed);
            }, prefixKeyword, true)));
        }
        else if (keyword == Keyword.OPEN_SQUARE)
        {
            currentScopes.push(new Scope(errorDisplayer, expect(ImmutableList.of(Keyword.CLOSE_SQUARE), close -> new BracketAndNodes<>(makeList(locationRecorder, CanonicalSpan.fromTo(errorDisplayer, close), CanonicalSpan.fromTo(errorDisplayer.rhs(), close.lhs())), CanonicalSpan.fromTo(errorDisplayer, close), ImmutableList.of()), (e, c) -> Either.<@Recorded TypeExpression, Terminator>left(e), prefixKeyword, true)));
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

    private ApplyBrackets<BracketContent, TypeExpression> tupleOrSingle(@UnknownInitialization(Object.class)TypeSaver this, EditorLocationAndErrorRecorder errorDisplayerRecord, CanonicalSpan location)
    {
        return new ApplyBrackets<BracketContent, TypeExpression>()
        {
            @Override
            public @Nullable @Recorded TypeExpression apply(@NonNull BracketContent items)
            {
                if (items.typeExpressions.size() == 1)
                    return items.typeExpressions.get(0);
                else
                    return errorDisplayerRecord.recordType(location, new TupleTypeExpression(items.typeExpressions));
            }

            @Override
            public @NonNull @Recorded TypeExpression applySingle(@NonNull @Recorded TypeExpression singleItem)
            {
                return singleItem;
            }
        };
    }

    private ApplyBrackets<BracketContent, TypeExpression> makeList(EditorLocationAndErrorRecorder errorDisplayerRecord, CanonicalSpan locationInclBrackets, CanonicalSpan locationInsideBrackets)
    {
        return new ApplyBrackets<BracketContent, TypeExpression>()
        {
            @Override
            public @Recorded @Nullable TypeExpression apply(@NonNull BracketContent items)
            {
                if (items.typeExpressions.size() == 1)
                    return errorDisplayerRecord.recordType(locationInclBrackets, new ListTypeExpression(items.typeExpressions.get(0)));
                else
                {
                    @Recorded InvalidOpTypeExpression content = errorDisplayerRecord.recordType(locationInsideBrackets, new InvalidOpTypeExpression(items.typeExpressions));
                    errorDisplayerRecord.addErrorAndFixes(locationInsideBrackets, StyledString.s("Invalid expression"), ImmutableList.of());
                    return errorDisplayerRecord.recordType(locationInclBrackets, new ListTypeExpression(content));
                }
            }

            @Override
            public @Recorded @NonNull TypeExpression applySingle(@NonNull @Recorded TypeExpression singleItem)
            {
                return errorDisplayerRecord.recordType(locationInclBrackets, new ListTypeExpression(singleItem));
            }
        };
    }

    @Override
    protected BracketAndNodes<TypeExpression, TypeSaver, BracketContent> expectSingle(@UnknownInitialization(Object.class)TypeSaver this, EditorLocationAndErrorRecorder errorDisplayerRecord, CanonicalSpan location)
    {
        return new BracketAndNodes<>(new ApplyBrackets<BracketContent, TypeExpression>()
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
        return locationRecorder.recordType(location, new InvalidOpTypeExpression(Utility.<Either<OpAndNode, @Recorded TypeExpression>, @Recorded TypeExpression>mapListI(items, x -> x.<@Recorded TypeExpression>either(u -> record(u.sourceNode, new InvalidIdentTypeExpression(",")), y -> y))));
    }

    @Override
    protected CanonicalSpan recorderFor(@Recorded TypeExpression typeExpression)
    {
        return locationRecorder.recorderFor(typeExpression);
    }

    @Override
    protected @Recorded TypeExpression record(CanonicalSpan location, TypeExpression typeExpression)
    {
        return locationRecorder.recordType(location, typeExpression);
    }

    @Override
    protected TypeExpression keywordToInvalid(Keyword keyword)
    {
        return new InvalidIdentTypeExpression(keyword.getContent());
    }

    @Override
    protected @Recorded TypeExpression makeExpression(List<Either<@Recorded TypeExpression, OpAndNode>> content, BracketAndNodes<TypeExpression, TypeSaver, BracketContent> brackets, @CanonicalLocation int innerContentLocation, @Nullable String terminatorDescription)
    {
        if (content.isEmpty())
        {
            // If terminator is null, error will be elsewhere
            if (terminatorDescription != null)
                locationRecorder.addErrorAndFixes(new CanonicalSpan(innerContentLocation, innerContentLocation), StyledString.s("Empty brackets"), ImmutableList.of());
            return record(brackets.location, new InvalidOpTypeExpression(ImmutableList.of()));
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
                        record(CanonicalSpan.fromTo(locationRecorder.recorderFor(numberType),
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
                @Recorded TypeExpression bracketedValid = brackets.applyBrackets.applySingle(validOperands.get(0));
                return bracketedValid;
            }
            
            // Now we need to check the operators can work together as one group:
            @Nullable @Recorded TypeExpression e = makeExpressionWithOperators(ImmutableList.of(OPERATORS), locationRecorder, (ImmutableList<Either<OpAndNode, @Recorded TypeExpression>> arg) ->
                    makeInvalidOp(brackets.location, arg)
                , ImmutableList.copyOf(validOperands), ImmutableList.copyOf(validOperators), brackets);
            if (e != null)
            {
                return e;
            }

        }

        return collectedItems.makeInvalid(location, InvalidOpTypeExpression::new);
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
    protected BracketAndNodes<TypeExpression, TypeSaver, BracketContent> unclosedBrackets(BracketAndNodes<TypeExpression, TypeSaver, BracketContent> closed)
    {
        return new BracketAndNodes<>(new ApplyBrackets<BracketContent, TypeExpression>()
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
        public @Recorded TypeExpression fetchContent(BracketAndNodes<TypeExpression, TypeSaver, BracketContent> brackets)
        {
            return TypeSaver.this.makeExpression(cur.items, brackets, cur.openingNode.end, cur.terminator.terminatorDescription);
        }
    }
}
