package test.gen;

import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.generator.java.time.LocalDateGenerator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.jetbrains.annotations.NotNull;
import records.data.Column;
import records.data.ColumnId;
import records.data.KnownLengthRecordSet;
import records.data.MemoryBooleanColumn;
import records.data.MemoryNumericColumn;
import records.data.MemoryStringColumn;
import records.data.MemoryTaggedColumn;
import records.data.MemoryTemporalColumn;
import records.data.RecordSet;
import records.data.columntype.NumericColumnType;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DataTypeVisitor;
import records.data.datatype.DataType.NumberInfo;
import records.data.datatype.DataType.SpecificDataTypeVisitor;
import records.data.datatype.DataType.TagType;
import records.data.unit.Unit;
import records.data.unit.UnitManager;
import records.error.FunctionInt;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.expression.AddSubtractExpression;
import records.transformations.expression.AddSubtractExpression.Op;
import records.transformations.expression.AndExpression;
import records.transformations.expression.BooleanLiteral;
import records.transformations.expression.ColumnReference;
import records.transformations.expression.DivideExpression;
import records.transformations.expression.EqualExpression;
import records.transformations.expression.Expression;
import records.transformations.expression.MatchExpression;
import records.transformations.expression.MatchExpression.MatchClause;
import records.transformations.expression.MatchExpression.Pattern;
import records.transformations.expression.MatchExpression.PatternMatch;
import records.transformations.expression.MatchExpression.PatternMatchExpression;
import records.transformations.expression.NotEqualExpression;
import records.transformations.expression.NumericLiteral;
import records.transformations.expression.OrExpression;
import records.transformations.expression.RaiseExpression;
import records.transformations.expression.StringLiteral;
import records.transformations.expression.TagExpression;
import records.transformations.expression.TimesExpression;
import records.transformations.expression.VarExpression;
import test.DummyManager;
import test.TestUtil;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.ExSupplier;
import utility.Pair;
import utility.Utility;

import java.math.BigDecimal;
import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static test.TestUtil.distinctTypes;

/**
 * Generates expressions and resulting values by working forwards.
 * At each step, we generate an expression and then work out what its
 * value will be.  This allows us to test numeric expressions because
 * we can track its exact expected value, accounting for lost precision.
 */
public class GenExpressionValueForwards extends Generator<ExpressionValue>
{
    @SuppressWarnings("initialization")
    public GenExpressionValueForwards()
    {
        super(ExpressionValue.class);
    }

    // Easier than passing parameters around:
    private SourceOfRandomness r;
    private GenerationStatus gs;
    private List<FunctionInt<RecordSet, Column>> columns;
    private int nextVar = 0;

    @Override
    @OnThread(value = Tag.Simulation, ignoreParent = true)
    public ExpressionValue generate(SourceOfRandomness r, GenerationStatus generationStatus)
    {
        this.r = r;
        this.gs = generationStatus;
        this.columns = new ArrayList<>();
        try
        {
            DataType type = makeType(r);
            Pair<List<Object>, Expression> p = makeOfType(type);
            return new ExpressionValue(type, p.getFirst(), getRecordSet(), p.getSecond());
        }
        catch (InternalException | UserException e)
        {
            throw new RuntimeException(e);
        }
    }

    @NotNull
    @OnThread(Tag.Simulation)
    public KnownLengthRecordSet getRecordSet() throws InternalException, UserException
    {
        return new KnownLengthRecordSet("", this.columns, 1);
    }

    // Only valid after calling generate
    @NotNull
    @OnThread(value = Tag.Simulation, ignoreParent = true)
    public Pair<List<Object>, Expression> makeOfType(DataType type) throws UserException, InternalException
    {
        return make(type, 4);
    }

    @SuppressWarnings("intern")
    public static DataType makeType(SourceOfRandomness r)
    {
        // Leave out dates until we can actually make date values:
        return r.choose(distinctTypes.stream().filter(t -> t != DataType.DATE).collect(Collectors.<DataType>toList()));
    }

    private Pair<List<Object>, Expression> make(DataType type, int maxLevels) throws UserException, InternalException
    {
        return type.apply(new DataTypeVisitor<Pair<List<Object>, Expression>>()
        {
            @Override
            public Pair<List<Object>, Expression> number(NumberInfo displayInfo) throws InternalException, UserException
            {
                return termDeep(maxLevels, type, l(
                    () -> columnRef(type),
                    () ->
                    {
                        Number number = TestUtil.generateNumber(r, gs);
                        return new Pair<>(Collections.singletonList(number), new NumericLiteral(number, displayInfo.getUnit()));
                    }
                ), l(() -> {
                    int numArgs = r.nextInt(2, 6);
                    List<Expression> expressions = new ArrayList<>();
                    List<Op> ops = new ArrayList<>();
                    Number curTotal = 0;
                    for (int i = 0; i < numArgs; i++)
                    {
                        Pair<List<Object>, Expression> pair = make(type, maxLevels - 1);
                        expressions.add(pair.getSecond());
                        // First one we count as add, because we're adding to the zero running total:
                        boolean opIsAdd = i == 0 || r.nextBoolean();
                        curTotal = Utility.addSubtractNumbers((Number) pair.getFirst().get(0), curTotal, opIsAdd);
                        if (i > 0)
                            ops.add(opIsAdd ? Op.ADD : Op.SUBTRACT);
                    }
                    return new Pair<List<Object>, Expression>(Collections.singletonList(curTotal), new AddSubtractExpression(expressions, ops));
                }, () -> {
                    // Either just use numerator, or make up crazy one
                    Unit numUnit = r.nextBoolean() ? displayInfo.getUnit() : makeUnit();
                    Unit denomUnit = calculateRequiredMultiplyUnit(numUnit, displayInfo.getUnit()).reciprocal();
                    Pair<List<Object>, Expression> numerator = make(DataType.number(new NumberInfo(numUnit, 0)), maxLevels - 1);
                    Pair<List<Object>, Expression> denominator;
                    do
                    {
                        denominator = make(DataType.number(new NumberInfo(denomUnit, 0)), maxLevels - 1);
                    }
                    while (Utility.compareNumbers(denominator.getFirst().get(0), 0) == 0);
                    return new Pair<List<Object>, Expression>(Collections.singletonList(Utility.divideNumbers((Number)numerator.getFirst().get(0), (Number)denominator.getFirst().get(0))),new DivideExpression(numerator.getSecond(), denominator.getSecond()));
                }, () ->
                {
                    if (displayInfo.getUnit().equals(Unit.SCALAR))
                    {
                        // Can have any power if it's scalar:
                        Pair<List<Object>, Expression> lhs = make(type, maxLevels - 1);
                        Pair<List<Object>, Expression> rhs = make(type, maxLevels - 1);
                        Number value = Utility.raiseNumber((Number) lhs.getFirst().get(0), (Number) rhs.getFirst().get(0));
                        return new Pair<>(Collections.singletonList(value), new RaiseExpression(lhs.getSecond(), rhs.getSecond()));
                    }
                    else
                    {
                        // A unit is involved so we need to do things differently.
                        // Essentially there's three options:
                        //  - The unit can be reached by positive integer power (rare!)
                        //  - We just use 1 as the power
                        //  - We use the current unit to a power, and root it
                        List<Integer> powers = displayInfo.getUnit().getDetails().values().stream().map(Math::abs).distinct().collect(Collectors.<Integer>toList());
                        if (powers.size() == 1 && powers.get(0) != 1 && r.nextInt(0, 2) != 0)
                        {
                            // Positive integer power possible; go go go!
                            Unit lhsUnit = displayInfo.getUnit().rootedBy(powers.get(0));
                            Pair<List<Object>, Expression> lhs = make(DataType.number(new NumberInfo(lhsUnit, 0)), maxLevels - 1);
                            return new Pair<>(Collections.singletonList(Utility.raiseNumber((Number) lhs.getFirst().get(0), powers.get(0))), new RaiseExpression(lhs.getSecond(), new NumericLiteral(powers.get(0), null)));
                        }
                        else if (r.nextBoolean())
                        {
                            // Just use 1 as power:
                            Pair<List<Object>, Expression> lhs = make(type, maxLevels - 1);
                            return lhs.replaceSecond(new RaiseExpression(lhs.getSecond(), new NumericLiteral(1, null)));
                        }
                        else
                        {
                            // Make up a power, then root it:
                            int raiseTo = r.nextInt(2, 5);
                            Unit lhsUnit = displayInfo.getUnit().raisedTo(raiseTo);
                            Pair<List<Object>, Expression> lhs = make(DataType.number(new NumberInfo(lhsUnit, 0)), maxLevels - 1);
                            return new Pair<>(Collections.singletonList(Utility.raiseNumber((Number) lhs.getFirst().get(0), 1.0/powers.get(0))), new RaiseExpression(lhs.getSecond(), new DivideExpression(new NumericLiteral(1, null), new NumericLiteral(raiseTo, null))));
                        }
                    }
                },
                () -> {
                    Number runningTotal = 1;
                    Unit runningUnit = Unit.SCALAR;
                    int numArgs = r.nextInt(2, 6);
                    List<Expression> expressions = new ArrayList<>();
                    for (int i = 0; i < numArgs; i++)
                    {
                        Unit unit = i == numArgs - 1 ? calculateRequiredMultiplyUnit(runningUnit, displayInfo.getUnit()) : makeUnit();
                        runningUnit = runningUnit.times(unit);
                        Pair<List<Object>, Expression> pair = make(DataType.number(new NumberInfo(unit, 0)), maxLevels - 1);
                        runningTotal = Utility.multiplyNumbers(runningTotal, (Number)pair.getFirst().get(0));
                        expressions.add(pair.getSecond());
                    }
                    return new Pair<>(Collections.singletonList(runningTotal), new TimesExpression(expressions));
                }));
            }

            @Override
            public Pair<List<Object>, Expression> text() throws InternalException, UserException
            {
                return termDeep(maxLevels, type, l(
                    () -> columnRef(type),
                    () ->
                    {
                        String value = TestUtil.makeString(r, gs);
                        return new Pair<>(Collections.singletonList(value), new StringLiteral(value));
                    }
                ), l());
            }

            @Override
            public Pair<List<Object>, Expression> date() throws InternalException, UserException
            {
                return termDeep(maxLevels, type, l(), l());
            }

            @Override
            public Pair<List<Object>, Expression> bool() throws InternalException, UserException
            {
                return termDeep(maxLevels, type, l(() -> columnRef(type), () ->
                {
                    boolean value = r.nextBoolean();
                    return new Pair<List<Object>, Expression>(Collections.singletonList(value), new BooleanLiteral(value));
                }), l(
                    () -> {
                        DataType t = makeType(r);

                        Pair<List<Object>, Expression> lhs = make(t, maxLevels - 1);
                        Pair<List<Object>, Expression> rhs = make(t, maxLevels - 1);
                        if (r.nextBoolean())
                            return new Pair<List<Object>, Expression>(Collections.singletonList(Utility.compareLists(lhs.getFirst(), rhs.getFirst()) == 0), new EqualExpression(lhs.getSecond(), rhs.getSecond()));
                        else
                            return new Pair<List<Object>, Expression>(Collections.singletonList(Utility.compareLists(lhs.getFirst(), rhs.getFirst()) != 0), new NotEqualExpression(lhs.getSecond(), rhs.getSecond()));
                    },
                    () -> {
                        int size = r.nextInt(2, 5);
                        boolean and = r.nextBoolean();
                        boolean value = and ? true : false;
                        ArrayList<Expression> exps = new ArrayList<Expression>();
                        for (int i = 0; i < size; i++)
                        {
                            Pair<List<Object>, Expression> pair = make(DataType.BOOLEAN, maxLevels - 1);
                            if (and)
                                value &= (Boolean)pair.getFirst().get(0);
                            else
                                value |= (Boolean)pair.getFirst().get(0);
                            exps.add(pair.getSecond());
                        }
                        return new Pair<List<Object>, Expression>(Collections.singletonList(value), and ? new AndExpression(exps) : new OrExpression(exps));
                    }
                ));
            }

            @Override
            public Pair<List<Object>, Expression> tagged(String typeName, List<TagType<DataType>> tags) throws InternalException, UserException
            {
                List<ExpressionMaker> terminals = new ArrayList<>();
                List<ExpressionMaker> nonTerm = new ArrayList<>();
                int tagIndex = r.nextInt(0, tags.size() - 1);
                TagType<DataType> tag = tags.get(tagIndex);
                Pair<@Nullable String, String> name = new Pair<>(typeName, tag.getName());
                final @Nullable DataType inner = tag.getInner();
                if (inner == null)
                {
                    terminals.add(() -> new Pair<>(Collections.singletonList(tagIndex), new TagExpression(name, null)));
                }
                else
                {
                    final @NonNull DataType nonNullInner = inner;
                    nonTerm.add(() ->
                    {
                        Pair<List<Object>, Expression> innerVal = make(nonNullInner, maxLevels - 1);
                        return new Pair<>(Utility.consList(tagIndex, innerVal.getFirst()), new TagExpression(name, innerVal.getSecond()));
                    });
                }
                return termDeep(maxLevels, type, terminals, nonTerm);
            }
        });
    }

    // What unit do you have to multiply src by to get dest?
    private Unit calculateRequiredMultiplyUnit(Unit src, Unit dest)
    {
        // So we have src * x = dest
        // This can be rearranged to x = dest/src
        return dest.divide(src);
    }

    private Unit makeUnit() throws InternalException, UserException
    {
        UnitManager m = DummyManager.INSTANCE.getUnitManager();
        return r.choose(Arrays.asList(
            m.loadUse("m"),
            m.loadUse("cm"),
            m.loadUse("inch"),
            m.loadUse("g"),
            m.loadUse("kg"),
            m.loadUse("deg"),
            m.loadUse("s"),
            m.loadUse("hour"),
            m.loadUse("$")
        ));
    }

    private BigDecimal genBD()
    {
        return new BigDecimal(new GenNumber().generate(r, gs));
    }

    private Pair<List<Object>, Expression> columnRef(DataType type) throws UserException, InternalException
    {
        ColumnId name = new ColumnId("GEV Col " + columns.size());
        Pair<List<Object>, FunctionInt<RecordSet, Column>> pair = type.apply(new DataTypeVisitor<Pair<List<Object>, FunctionInt<RecordSet, Column>>>()
        {
            @Override
            public Pair<List<Object>, FunctionInt<RecordSet, Column>> number(NumberInfo displayInfo) throws InternalException, UserException
            {
                Number value = TestUtil.generateNumber(r, gs);
                return new Pair<>(Collections.singletonList(value), rs -> new MemoryNumericColumn(rs, name, new NumericColumnType(displayInfo.getUnit(), displayInfo.getMinimumDP(), false), Collections.singletonList(Utility.toBigDecimal(value).toPlainString())));
            }

            @Override
            public Pair<List<Object>, FunctionInt<RecordSet, Column>> text() throws InternalException, UserException
            {
                String value = TestUtil.makeString(r, gs);
                return new Pair<>(Collections.singletonList(value), rs -> new MemoryStringColumn(rs, name, Collections.singletonList(value)));
            }

            @Override
            public Pair<List<Object>, FunctionInt<RecordSet, Column>> date() throws InternalException, UserException
            {
                Temporal value = new LocalDateGenerator().generate(r, gs);
                return new Pair<>(Collections.singletonList(value), rs -> new MemoryTemporalColumn(rs, name, Collections.singletonList((Temporal) value)));
            }

            @Override
            public Pair<List<Object>, FunctionInt<RecordSet, Column>> bool() throws InternalException, UserException
            {
                boolean value = r.nextBoolean();
                return new Pair<>(Collections.singletonList(value), rs -> new MemoryBooleanColumn(rs, name, Collections.singletonList((Boolean) value)));
            }

            @Override
            public Pair<List<Object>, FunctionInt<RecordSet, Column>> tagged(String typeName, List<TagType<DataType>> tags) throws InternalException, UserException
            {
                int tagIndex = r.nextInt(0, tags.size() - 1);
                @Nullable DataType inner = tags.get(tagIndex).getInner();
                List<Object> rest = inner == null ? Collections.emptyList() : make(inner, 1).getFirst();
                List<Object> value = Utility.consList(1, rest);
                return new Pair<>(value, rs -> new MemoryTaggedColumn(rs, name, typeName, tags, Collections.singletonList(value)));
            }
        });
        columns.add(pair.getSecond());
        return pair.replaceSecond(new ColumnReference(name));
    }

    @FunctionalInterface
    public static interface ExpressionMaker
    {
        public Pair<List<Object>, Expression> make() throws InternalException, UserException;
    }

    private List<ExpressionMaker> l(ExpressionMaker... suppliers)
    {
        return Arrays.asList(suppliers);
    }

    private Pair<List<Object>, Expression> termDeep(int maxLevels, DataType type, List<ExpressionMaker> terminals, List<ExpressionMaker> deeper) throws UserException, InternalException
    {
        /*
        if (maxLevels > 1 && r.nextInt(0, 5) == 0)
        {
            return makeMatch(maxLevels - 1, () -> termDeep(maxLevels - 1, type, terminals, deeper), () -> make(type, maxLevels - 1));
        }
        */

        //TODO generate match expressions here (valid for all types)
        if (!terminals.isEmpty() && (maxLevels <= 1 || deeper.isEmpty() || r.nextInt(0, 2) == 0))
            return r.choose(terminals).make();
        else
            return r.choose(deeper).make();
    }
/*
    private Expression makeMatch(int maxLevels, ExSupplier<Expression> makeCorrectOutcome, ExSupplier<Expression> makeOtherOutcome) throws InternalException, UserException
    {
        DataType t = makeTypeWithoutNumbers(r);
        List<Object> actual = makeValue(t);
        // Make a bunch of guards which won't fire:
        List<Function<MatchExpression, MatchClause>> clauses = new ArrayList<>(TestUtil.makeList(r, 0, 5, (ExSupplier<Optional<Function<MatchExpression, MatchClause>>>)() -> {
            // Generate a bunch which can't match the item:
            List<Function<MatchExpression, Pattern>> patterns = makeNonMatchingPatterns(maxLevels, t, actual);
            Expression outcome = makeOtherOutcome.get();
            if (patterns.isEmpty())
                return Optional.<Function<MatchExpression, MatchClause>>empty();
            return Optional.<Function<MatchExpression, MatchClause>>of((MatchExpression me) -> me.new MatchClause(Utility.<Function<MatchExpression, Pattern>, Pattern>mapList(patterns, p -> p.apply(me)), outcome));
        }).stream().<Function<MatchExpression, MatchClause>>flatMap(o -> o.isPresent() ? Stream.<Function<MatchExpression, MatchClause>>of(o.get()) : Stream.<Function<MatchExpression, MatchClause>>empty()).collect(Collectors.<Function<MatchExpression, MatchClause>>toList()));
        Expression correctOutcome = makeCorrectOutcome.get();
        List<Function<MatchExpression, Pattern>> patterns = new ArrayList<>(makeNonMatchingPatterns(maxLevels, t, actual));
        Pair<Function<MatchExpression, PatternMatch>, @Nullable Expression> match = makePatternMatch(maxLevels - 1, t, actual);
        patterns.add(r.nextInt(0, patterns.size()), me -> {
            List<Expression> guards = new ArrayList<>(TestUtil.makeList(r, 0, 3, () -> make(DataType.BOOLEAN, Collections.singletonList(true), maxLevels - 1)));
            Expression extraGuard = match.getSecond();
            if (extraGuard != null)
                guards.add(r.nextInt(0, guards.size()), extraGuard);
            return new Pattern(match.getFirst().apply(me), guards);
        });
        clauses.add(r.nextInt(0, clauses.size()), me -> me.new MatchClause(Utility.<Function<MatchExpression, Pattern>, Pattern>mapList(patterns, p -> p.apply(me)), correctOutcome));
        return new MatchExpression(make(t, actual, maxLevels), clauses);
    }

    // Pattern and an optional guard
    @NotNull
    private Pair<Function<MatchExpression, PatternMatch>, @Nullable Expression> makePatternMatch(int maxLevels, DataType t, List<Object> actual)
    {
        try
        {
            if (t.isTagged() && r.nextBoolean())
            {
                return t.apply(new SpecificDataTypeVisitor<Pair<Function<MatchExpression, PatternMatch>, @Nullable Expression>>()
                {
                    @Override
                    public Pair<Function<MatchExpression, PatternMatch>, @Nullable Expression> tagged(String typeName, List<TagType<DataType>> tagTypes) throws InternalException, UserException
                    {
                        TagType<DataType> tagType = tagTypes.get((Integer) actual.get(0));
                        @Nullable DataType inner = tagType.getInner();
                        if (inner == null)
                            return new Pair<>(me -> me.new PatternMatchConstructor(tagType.getName(), null), null);
                        Pair<Function<MatchExpression, PatternMatch>, @Nullable Expression> subPattern = makePatternMatch(maxLevels, inner, actual.subList(1, actual.size()));
                        return new Pair<>(me -> me.new PatternMatchConstructor(tagType.getName(), subPattern.getFirst().apply(me)), subPattern.getSecond());
                    }
                });

            }
            else if (r.nextBoolean()) // Do equals but using variable + guard
            {
                String varName = "var" + nextVar++;
                return new Pair<>(me -> me.new PatternMatchVariable(varName), new EqualExpression(new VarExpression(varName), make(t, actual, maxLevels)));
            }
            Expression expression = make(t, actual, maxLevels);
            return new Pair<Function<MatchExpression, PatternMatch>, @Nullable Expression>(me -> new PatternMatchExpression(expression), null);
        }
        catch (InternalException | UserException e)
        {
            throw new RuntimeException(e);
        }
    }

    private List<Function<MatchExpression, Pattern>> makeNonMatchingPatterns(final int maxLevels, final DataType t, List<Object> actual)
    {
        class CantMakeNonMatching extends RuntimeException {}
        try
        {
            return TestUtil.<Function<MatchExpression, Pattern>>makeList(r, 1, 4, () ->
            {
                Pair<Function<MatchExpression, PatternMatch>, @Nullable Expression> match = r.choose(Arrays.<ExSupplier<Pair<Function<MatchExpression, PatternMatch>, @Nullable Expression>>>asList(
                    () ->
                    {
                        List<Object> nonMatchingValue;
                        int attempts = 0;
                        do
                        {
                            nonMatchingValue = makeValue(t);
                            if (attempts++ >= 30)
                                throw new CantMakeNonMatching();
                        }
                        while (Utility.compareLists(nonMatchingValue, actual) == 0);
                        List<Object> nonMatchingValueFinal = nonMatchingValue;
                        return makePatternMatch(maxLevels - 1, t, nonMatchingValueFinal);
                    }
                )).get();
                List<Expression> guards = TestUtil.makeList(r, 0, 3, () -> make(DataType.BOOLEAN, Collections.singletonList(r.nextBoolean()), maxLevels - 1));
                Expression extraGuard = match.getSecond();
                if (extraGuard != null)
                    guards.add(r.nextInt(0, guards.size()), extraGuard);
                return (Function<MatchExpression, Pattern>)((MatchExpression me) -> new Pattern(match.getFirst().apply(me), guards));
            });
        }
        catch (CantMakeNonMatching e)
        {
            return Collections.emptyList();
        }
    }
    */
}
