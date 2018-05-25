package test.gen;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.Column;
import records.data.ColumnId;
import records.data.KnownLengthRecordSet;
import records.data.MemoryArrayColumn;
import records.data.MemoryBooleanColumn;
import records.data.MemoryNumericColumn;
import records.data.MemoryStringColumn;
import records.data.MemoryTaggedColumn;
import records.data.MemoryTemporalColumn;
import records.data.MemoryTupleColumn;
import records.data.RecordSet;
import records.data.datatype.DataType.ConcreteDataTypeVisitor;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.TaggedTypeDefinition;
import records.data.datatype.TypeManager;
import records.data.datatype.TypeManager.TagInfo;
import records.jellytype.JellyType;
import records.transformations.expression.IdentExpression;
import records.transformations.expression.TemporalLiteral;
import records.transformations.expression.TypeLiteralExpression;
import records.transformations.expression.StringConcatExpression;
import records.transformations.expression.type.TypeExpression;
import test.gen.backwards.*;
import utility.Either;
import utility.SimulationFunction;
import utility.TaggedValue;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataType.DateTimeInfo.DateTimeType;
import records.data.datatype.NumberInfo;
import records.data.datatype.DataType.SpecificDataTypeVisitor;
import records.data.datatype.DataType.TagType;
import records.data.datatype.TypeId;
import records.data.unit.Unit;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.expression.AddSubtractExpression;
import records.transformations.expression.AddSubtractExpression.Op;
import records.transformations.expression.AndExpression;
import records.transformations.expression.ArrayExpression;
import records.transformations.expression.BooleanLiteral;
import records.transformations.expression.ColumnReference;
import records.transformations.expression.ColumnReference.ColumnReferenceType;
import records.transformations.expression.ComparisonExpression;
import records.transformations.expression.ComparisonExpression.ComparisonOperator;
import records.transformations.expression.DivideExpression;
import records.transformations.expression.EqualExpression;
import records.transformations.expression.Expression;
import records.transformations.expression.MatchExpression;
import records.transformations.expression.MatchExpression.MatchClause;
import records.transformations.expression.MatchExpression.Pattern;
import records.transformations.expression.NotEqualExpression;
import records.transformations.expression.NumericLiteral;
import records.transformations.expression.OrExpression;
import records.transformations.expression.StringLiteral;
import records.transformations.expression.TupleExpression;
import records.transformations.expression.VarDeclExpression;
import test.DummyManager;
import test.TestUtil;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.ExFunction;
import utility.ExSupplier;
import utility.Pair;
import utility.Utility;
import utility.Utility.ListEx;
import utility.Utility.ListExList;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoField;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalField;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Generates expressions and resulting values by working backwards.
 * At each step, we generate a target value and then make an expression which will
 * produce that value.  This tends to create better tests for things like pattern
 * matches or equality expressions because we make sure to create non-matching and matching guards (or passing equalities).
 * But it prevents using numeric expressions because we cannot be sure of exact
 * results due to precision (e.g. make me a function which returns 0.3333333; 1/3 might
 * not quite crack it).
 */
@SuppressWarnings("recorded")
@OnThread(Tag.Simulation)
public class GenExpressionValueBackwards extends GenValueBase<ExpressionValue> implements RequestBackwardsExpression
{

    @SuppressWarnings("initialization")
    public GenExpressionValueBackwards()
    {
        super(ExpressionValue.class);
    }

    private int nextVar = 0;

    private BackwardsColumnRef columnProvider;
    private ImmutableList<BackwardsProvider> providers;

    @Override
    @OnThread(value = Tag.Simulation, ignoreParent = true)
    public ExpressionValue generate(SourceOfRandomness r, GenerationStatus generationStatus)
    {
        this.r = r;
        this.gs = generationStatus;
        this.numberOnlyInt = true;
        columnProvider = new BackwardsColumnRef(r, this);
        providers = ImmutableList.of(
            columnProvider,
            new BackwardsBooleans(r, this),
            new BackwardsFromText(r, this),
            new BackwardsLiteral(r, this),
            new BackwardsNumbers(r, this),
            new BackwardsTemporal(r, this),
            new BackwardsText(r, this)
        );
        try
        {
            DataType type = makeType(r);
            Pair<@Value Object, Expression> p = makeOfType(type);
            return new ExpressionValue(type, Collections.singletonList(p.getFirst()), DummyManager.INSTANCE.getTypeManager(), getRecordSet(), p.getSecond());
        }
        catch (InternalException | UserException e)
        {
            throw new RuntimeException(e);
        }
    }

    @Override
    public TypeManager getTypeManager()
    {
        return DummyManager.INSTANCE.getTypeManager();
    }

    @NonNull
    @OnThread(Tag.Simulation)
    public KnownLengthRecordSet getRecordSet() throws InternalException, UserException
    {
        return new KnownLengthRecordSet(columnProvider.getColumns(), 1);
    }

    // Only valid after calling generate
    @NonNull
    @OnThread(value = Tag.Simulation, ignoreParent = true)
    public Pair<@Value Object, Expression> makeOfType(DataType type) throws UserException, InternalException
    {
        @Value Object value = makeValue(type);
        Expression expression = make(type, value, 4);
        return new Pair<>(value, expression);
    }

    @SuppressWarnings("valuetype")
    public Expression make(DataType type, Object targetValue, int maxLevels) throws UserException, InternalException
    {
        if (r.nextBoolean())
        {
            ImmutableList.Builder<ExpressionMaker> terminals = ImmutableList.builder();
            ImmutableList.Builder<ExpressionMaker> deep = ImmutableList.builder();
            for (BackwardsProvider provider : providers)
            {
                terminals.addAll(provider.terminals(type, targetValue));
                deep.addAll(provider.terminals(type, targetValue));
            }
            
            return termDeep(maxLevels, type, terminals.build(), deep.build());
        }
        
        return type.apply(new ConcreteDataTypeVisitor<Expression>()
        {
            @Override
            @OnThread(Tag.Simulation)
            public Expression number(NumberInfo displayInfo) throws InternalException, UserException
            {
                // We only do integers because beyond that the result isn't guaranteed, which
                // could cause failures in things like equal expressions.
                return termDeep(maxLevels, type, l(
                    () -> new NumericLiteral((Number)targetValue, makeUnitExpression(displayInfo.getUnit()))
                ), l(fixType(maxLevels - 1, type, targetValue)));
            }

            @Override
            @OnThread(Tag.Simulation)
            public Expression text() throws InternalException, UserException
            {
                return termDeep(maxLevels, type, l(
                    () -> new StringLiteral((String)targetValue)
                ), l(fixType(maxLevels - 1, type, targetValue)));
            }

            @Override
            @OnThread(Tag.Simulation)
            public Expression date(DateTimeInfo dateTimeInfo) throws InternalException, UserException
            {
                List<ExpressionMaker> deep = new ArrayList<ExpressionMaker>();
                deep.add(() -> call("asType", new TypeLiteralExpression(TypeExpression.fromDataType(DataType.date(dateTimeInfo))), call("from text", make(DataType.TEXT, targetValue.toString(), maxLevels - 1))));

                deep.add(fixType(maxLevels - 1, type, targetValue));

                return termDeep(maxLevels, type, l((ExpressionMaker)() -> {
                    return call("asType", new TypeLiteralExpression(TypeExpression.fromDataType(DataType.date(dateTimeInfo))), call("from text", new StringLiteral(targetValue.toString())));
                }, (ExpressionMaker)() -> {
                    return new TemporalLiteral(dateTimeInfo.getType(), targetValue.toString());
                }), deep);
            }

            @Override
            @OnThread(Tag.Simulation)
            public Expression bool() throws InternalException, UserException
            {
                boolean target = (Boolean)targetValue;
                return termDeep(maxLevels, type, l(() -> new BooleanLiteral(target)), l(fixType(maxLevels - 1, type, targetValue)));
            }

            @Override
            @OnThread(Tag.Simulation)
            public Expression tagged(TypeId typeName, ImmutableList<Either<Unit, DataType>> typeVars, ImmutableList<TagType<DataType>> tags) throws InternalException, UserException
            {
                List<ExpressionMaker> terminals = new ArrayList<>();
                List<ExpressionMaker> nonTerm = new ArrayList<>();
                int tagIndex = ((TaggedValue) targetValue).getTagIndex();
                TagType<DataType> tag = tags.get(tagIndex);
                @Nullable TaggedTypeDefinition typeDefinition = DummyManager.INSTANCE.getTypeManager().lookupDefinition(typeName);
                if (typeDefinition == null)
                    throw new InternalException("Looked up type but null definition: " + typeName);
                TagInfo tagInfo = new TagInfo(typeDefinition, tagIndex);
                final @Nullable DataType inner = tag.getInner();
                if (inner == null)
                {
                    terminals.add(() -> TestUtil.tagged(Either.right(tagInfo), null));
                }
                else
                {
                    final @NonNull DataType nonNullInner = inner;
                    nonTerm.add(() ->
                    {
                        @Nullable Object innerValue = ((TaggedValue) targetValue).getInner();
                        if (innerValue == null)
                            throw new InternalException("Type says inner value but is null");
                        return TestUtil.tagged(Either.right(tagInfo), make(nonNullInner, innerValue, maxLevels - 1));
                    });
                }
                nonTerm.add(fixType(maxLevels - 1, type, targetValue));
                return termDeep(maxLevels, type, terminals, nonTerm);
            }

            @Override
            @OnThread(Tag.Simulation)
            public Expression tuple(ImmutableList<DataType> inner) throws InternalException, UserException
            {
                @Value Object[] target = (@Value Object[]) targetValue;
                List<ExpressionMaker> terminals = new ArrayList<>();
                terminals.add(() -> new TupleExpression(Utility.mapListExI_Index(inner, (i, t) -> make(t, target[i], 1))));
                List<ExpressionMaker> nonTerm = new ArrayList<>();
                terminals.add(() -> new TupleExpression(Utility.mapListExI_Index(inner, (i, t) -> make(t, target[i], maxLevels - 1))));
                nonTerm.add(fixType(maxLevels - 1, type, targetValue));
                return termDeep(maxLevels, type, terminals, nonTerm);
            }

            @Override
            @OnThread(Tag.Simulation)
            public Expression array(@Nullable DataType inner) throws InternalException, UserException
            {
                if (inner == null)
                    return new ArrayExpression(ImmutableList.of());
                @NonNull DataType innerFinal = inner;
                @Value ListEx target = Utility.valueList(targetValue);
                List<ExpressionMaker> terminals = new ArrayList<>();
                terminals.add(() -> new ArrayExpression(Utility.mapListExI(target, t -> make(innerFinal, t, 1))));
                List<ExpressionMaker> nonTerm = new ArrayList<>();
                terminals.add(() -> new ArrayExpression(Utility.mapListExI(target, t -> make(innerFinal, t, maxLevels - 1))));
                nonTerm.add(fixType(maxLevels - 1, type, targetValue));
                return termDeep(maxLevels, type, terminals, nonTerm);
            }
        });
    }
    
    private ExpressionMaker fixType(int maxLevels, DataType type, @Value Object value)
    {
        TypeManager m = DummyManager.INSTANCE.getTypeManager();
        return () -> TypeLiteralExpression.fixType(m, JellyType.fromConcrete(type), make(type, value, maxLevels - 1));
    }
    
    private List<ExpressionMaker> l(ExpressionMaker... suppliers)
    {
        return Arrays.asList(suppliers);
    }

    @OnThread(Tag.Simulation)
    private Expression termDeep(int maxLevels, DataType type, List<ExpressionMaker> terminals, List<ExpressionMaker> deeper) throws UserException, InternalException
    {
        if (maxLevels > 1 && r.nextInt(0, 5) == 0)
        {
            return makeMatch(maxLevels - 1, () -> termDeep(maxLevels - 1, type, terminals, deeper), () -> make(type, makeValue(type), maxLevels - 1));
        }

        //TODO generate match expressions here (valid for all types)
        if (!terminals.isEmpty() && (maxLevels <= 1 || deeper.isEmpty() || r.nextInt(0, 2) == 0))
            return r.choose(terminals).make();
        else
            return r.choose(deeper).make();
    }

    /**
     * Make a match expression or an if expression with a match.
     * @param maxLevels
     * @param makeCorrectOutcome Make an expression that has the desired outcome value
     * @param makeOtherOutcome Make an expression with the right type but arbitrary value.
     * @return A MatchExpression or IfThenElseExpression that evaluates to the correct outcome.
     * @throws InternalException
     * @throws UserException
     */
    @OnThread(Tag.Simulation)
    private Expression makeMatch(int maxLevels, ExSupplier<Expression> makeCorrectOutcome, ExSupplier<Expression> makeOtherOutcome) throws InternalException, UserException
    {
        if (r.nextBoolean())
        {
            // Use an if-then-else
            
        }
        
        DataType t = makeType(r);
        @Value Object actual = makeValue(t);
        // Make a bunch of guards which won't fire:
        List<Function<MatchExpression, MatchClause>> clauses = new ArrayList<>(TestUtil.makeList(r, 0, 4, (ExSupplier<Optional<Function<MatchExpression, MatchClause>>>)() -> {
            // Generate a bunch which can't match the item:
            List<ExFunction<MatchExpression, Pattern>> patterns = makeNonMatchingPatterns(maxLevels - 1, t, actual);
            Expression outcome = makeOtherOutcome.get();
            if (patterns.isEmpty())
                return Optional.<Function<MatchExpression, MatchClause>>empty();
            return Optional.<Function<MatchExpression, MatchClause>>of((MatchExpression me) -> {
                try
                {
                    return me.new MatchClause(Utility.<ExFunction<MatchExpression, Pattern>, Pattern>mapListEx(patterns, p -> p.apply(me)), outcome);
                }
                catch (UserException | InternalException e)
                {
                    throw new RuntimeException(e);
                }
            });
        }).stream().<Function<MatchExpression, MatchClause>>flatMap(o -> o.isPresent() ? Stream.<Function<MatchExpression, MatchClause>>of(o.get()) : Stream.<Function<MatchExpression, MatchClause>>empty()).collect(Collectors.<Function<MatchExpression, MatchClause>>toList()));
        Expression correctOutcome = makeCorrectOutcome.get();
        List<ExFunction<MatchExpression, Pattern>> patterns = new ArrayList<>(makeNonMatchingPatterns(maxLevels - 1, t, actual));
        Pair<Expression, @Nullable Expression> match = makePatternMatch(maxLevels - 1, t, actual);
        patterns.add(r.nextInt(0, patterns.size()), me -> {
            @Nullable Expression guard = r.nextBoolean() ? null : make(DataType.BOOLEAN, true, maxLevels - 1);
            @Nullable Expression extraGuard = match.getSecond();
            if (extraGuard != null)
                guard = (guard == null ? extraGuard : new AndExpression(Arrays.asList(guard, extraGuard)));
            return new Pattern(match.getFirst(), guard);
        });
        clauses.add(r.nextInt(0, clauses.size()), me -> {
            try
            {
                return me.new MatchClause(Utility.<ExFunction<MatchExpression, Pattern>, Pattern>mapListEx(patterns, p -> p.apply(me)), correctOutcome);
            }
            catch (InternalException | UserException e)
            {
                throw new RuntimeException(e);
            }
        });
        return new MatchExpression(make(t, actual, maxLevels - 1), clauses);
    }

    // Pattern and an optional guard
    @NonNull
    private Pair<Expression, @Nullable Expression> makePatternMatch(int maxLevels, DataType t, Object actual)
    {
        try
        {
            if (t.isTagged() && r.nextBoolean())
            {
                TaggedValue p = (TaggedValue) actual;
                return t.apply(new SpecificDataTypeVisitor<Pair<Expression, @Nullable Expression>>()
                {
                    @Override
                    @OnThread(value = Tag.Simulation, ignoreParent = true)
                    public Pair<Expression, @Nullable Expression> tagged(TypeId typeName, ImmutableList<Either<Unit, DataType>> typeVars, ImmutableList<TagType<DataType>> tagTypes) throws InternalException, UserException
                    {
                        TagType<DataType> tagType = tagTypes.get(p.getTagIndex());
                        @Nullable DataType inner = tagType.getInner();
                        @Nullable TaggedTypeDefinition typeDefinition = DummyManager.INSTANCE.getTypeManager().lookupDefinition(typeName);
                        if (typeDefinition == null)
                            throw new InternalException("Looked up type but null definition: " + typeName);
                        if (inner == null)
                            return new Pair<>(TestUtil.tagged(Either.right(new TagInfo(typeDefinition, p.getTagIndex())), null), null);
                        @Nullable Object innerValue = p.getInner();
                        if (innerValue == null)
                            throw new InternalException("Type says inner value but is null");
                        Pair<Expression, @Nullable Expression> subPattern = makePatternMatch(maxLevels, inner, innerValue);
                        return new Pair<>(TestUtil.tagged(Either.right(new TagInfo(typeDefinition, p.getTagIndex())), subPattern.getFirst()), subPattern.getSecond());
                    }
                });

            }
            else if (r.nextBoolean()) // Do equals but using variable + guard
            {
                String varName = "var" + nextVar++;
                return new Pair<>(new VarDeclExpression(varName), new EqualExpression(ImmutableList.of(new IdentExpression(varName), make(t, actual, maxLevels))));
            }
            Expression expression = make(t, actual, maxLevels);
            return new Pair<Expression, @Nullable Expression>(expression, null);
        }
        catch (InternalException | UserException e)
        {
            throw new RuntimeException(e);
        }
    }

    @OnThread(Tag.Simulation)
    private List<ExFunction<MatchExpression, Pattern>> makeNonMatchingPatterns(final int maxLevels, final DataType t, @Value Object actual)
    {
        class CantMakeNonMatching extends RuntimeException {}
        try
        {
            return TestUtil.<ExFunction<MatchExpression, Pattern>>makeList(r, 1, 4, () ->
            {
                Pair<Expression, @Nullable Expression> match = r.choose(Arrays.<ExSupplier<Pair<Expression, @Nullable Expression>>>asList(
                    () ->
                    {
                        @Value Object nonMatchingValue;
                        int attempts = 0;
                        do
                        {
                            nonMatchingValue = makeValue(t);
                            if (attempts++ >= 30)
                                throw new CantMakeNonMatching();
                        }
                        while (Utility.compareValues(nonMatchingValue, actual) == 0);
                        Object nonMatchingValueFinal = nonMatchingValue;
                        return makePatternMatch(maxLevels - 1, t, nonMatchingValueFinal);
                    }
                )).get();
                @Nullable Expression guard = r.nextBoolean() ? null : make(DataType.BOOLEAN, true, maxLevels - 1);
                @Nullable Expression extraGuard = match.getSecond();
                if (extraGuard != null)
                    guard = (guard == null ? extraGuard : new AndExpression(Arrays.asList(guard, extraGuard)));
                @Nullable Expression guardFinal = guard;
                return (ExFunction<MatchExpression, Pattern>)((MatchExpression me) -> new Pattern(match.getFirst(), guardFinal));
            });
        }
        catch (CantMakeNonMatching e)
        {
            return Collections.emptyList();
        }
    }

    // Turn makeValue public:
    @Override
    @OnThread(value = Tag.Simulation, ignoreParent = true)
    public @Value Object makeValue(DataType t) throws UserException, InternalException
    {
        return super.makeValue(t);
    }

    @Override
    public DataType makeType() throws InternalException, UserException
    {
        return makeType(r);
    }
    
    // Make public:

    @Override
    @OnThread(value = Tag.Simulation, ignoreParent = true)
    public @Value long genInt()
    {
        return super.genInt();
    }
}
