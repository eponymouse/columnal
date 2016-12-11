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
import records.transformations.expression.StringLiteral;
import records.transformations.expression.TagExpression;
import records.transformations.expression.TimesExpression;
import records.transformations.expression.VarExpression;
import test.DummyManager;
import test.TestUtil;
import test.gen.GenExpressionValue.ExpressionValue;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.ExFunction;
import utility.ExSupplier;
import utility.Pair;
import utility.Utility;

import java.math.BigDecimal;
import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static test.TestUtil.distinctTypes;

/**
 * Created by neil on 10/12/2016.
 */
public class GenExpressionValue extends Generator<ExpressionValue>
{
    public static class ExpressionValue
    {
        public final DataType type;
        public final List<Object> value;
        public final RecordSet recordSet;
        public final Expression expression;

        public ExpressionValue(DataType type, List<Object> value, RecordSet recordSet, Expression expression)
        {
            this.type = type;
            this.value = value;
            this.recordSet = recordSet;
            this.expression = expression;
        }
    }

    @SuppressWarnings("initialization")
    public GenExpressionValue()
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
        List<Object> value = makeValue(type);
        Expression expression = make(type, value, 4);
        return new Pair<>(value, expression);
    }

    @SuppressWarnings("intern")
    public static DataType makeType(SourceOfRandomness r)
    {
        // Leave out dates until we can actually make date values:
        return r.choose(distinctTypes.stream().filter(t -> t != DataType.DATE).collect(Collectors.<DataType>toList()));
    }

    private Expression make(DataType type, List<Object> targetValue, int maxLevels) throws UserException, InternalException
    {
        return type.apply(new DataTypeVisitor<Expression>()
        {
            @Override
            public Expression number(NumberInfo displayInfo) throws InternalException, UserException
            {
                return termDeep(maxLevels, type, l(
                    () -> columnRef(type, targetValue),
                    () -> new NumericLiteral((Number)targetValue.get(0), displayInfo.getUnit())
                ), l(() -> {
                    // We just make up a bunch of numbers, and at the very end we add one more to correct the difference
                    int numMiddle = r.nextInt(0, 6);
                    List<Expression> expressions = new ArrayList<>();
                    List<Op> ops = new ArrayList<>();
                    BigDecimal curTotal = genBD();
                    expressions.add(make(type, Collections.singletonList(curTotal), maxLevels - 1));
                    for (int i = 0; i < numMiddle; i++)
                    {
                        BigDecimal next = genBD();
                        expressions.add(make(type, Collections.singletonList(next), maxLevels - 1));
                        if (r.nextBoolean())
                        {
                            curTotal = curTotal.add(next);
                            ops.add(Op.ADD);
                        }
                        else
                        {
                            curTotal = curTotal.subtract(next);
                            ops.add(Op.SUBTRACT);
                        }
                    }
                    // Now add one more to make the difference:
                    BigDecimal diff = Utility.toBigDecimal((Number)targetValue.get(0)).subtract(curTotal);
                    boolean add = r.nextBoolean();
                    expressions.add(make(type, Collections.singletonList(add ? diff : diff.negate()), maxLevels - 1));
                    ops.add(add ? Op.ADD : Op.SUBTRACT);
                    return new AddSubtractExpression(expressions, ops);
                }, () -> {
                    // A few options; keep units and value in numerator and divide by 1
                    // Or make random denom, times that by target to get num, and make up crazy units which work
                    if (r.nextInt(0, 4) == 0)
                        return new DivideExpression(make(type, targetValue, maxLevels - 1), new NumericLiteral(1, Unit.SCALAR));
                    else
                    {
                        Number denominator;
                        do
                        {
                            denominator = genBD();
                        } while (Utility.compareNumbers(denominator, 0) == 0);
                        Number numerator = Utility.multiplyNumbers((Number) targetValue.get(0), denominator);
                        // Either just use numerator, or make up crazy one
                        Unit numUnit = r.nextBoolean() ? displayInfo.getUnit() : makeUnit();
                        Unit denomUnit = calculateRequiredMultiplyUnit(numUnit, displayInfo.getUnit()).reciprocal();
                        // TODO test division by zero behaviour (test errors generally)
                        return new DivideExpression(make(DataType.number(new NumberInfo(numUnit, 0)), Collections.singletonList(numerator), maxLevels - 1), make(DataType.number(new NumberInfo(denomUnit, 0)), Collections.singletonList(denominator), maxLevels - 1));
                    }
                }, () -> {
                    Number runningTotal = 1;
                    Unit runningUnit = Unit.SCALAR;
                    int numExtra = r.nextInt(1, 4);
                    List<Expression> expressions = new ArrayList<>();
                    boolean targetZero = Utility.compareNumbers(targetValue.get(0), 0) == 0;
                    for (int i = 0; i < numExtra; i++)
                    {
                        //Only allow zeroes if the target is zero:
                        Number subVal;
                        do
                        {
                            subVal = genBD();
                        }
                        while (subVal.toString().equals("0") && !targetZero);
                        runningTotal = Utility.multiplyNumbers(runningTotal, subVal);
                        Unit unit = makeUnit();
                        runningUnit = runningUnit.times(unit);
                        expressions.add(make(DataType.number(new NumberInfo(unit, 0)),Collections.singletonList(subVal), maxLevels - 1));
                    }
                    Unit lastUnit = calculateRequiredMultiplyUnit(runningUnit, displayInfo.getUnit());
                    if (targetZero)
                    {
                        // If running total already zero, generate what we like, otherwise we must generate a zero:
                        if (Utility.compareNumbers(runningTotal, 0) == 0)
                        {
                            expressions.add(make(DataType.number(new NumberInfo(lastUnit, 0)), Collections.singletonList(genBD()), maxLevels - 1));
                        }
                        else
                        {
                            expressions.add(make(DataType.number(new NumberInfo(lastUnit, 0)), Collections.singletonList(0), maxLevels - 1));
                        }
                    }
                    else
                    {
                        expressions.add(make(DataType.number(new NumberInfo(lastUnit, 0)), Collections.singletonList(Utility.divideNumbers((Number) targetValue.get(0), runningTotal)), maxLevels - 1));
                    }
                    return new TimesExpression(expressions);
                }));
            }

            @Override
            public Expression text() throws InternalException, UserException
            {
                return termDeep(maxLevels, type, l(
                    () -> columnRef(type, targetValue),
                    () -> new StringLiteral((String)targetValue.get(0))
                ), l());
            }

            @Override
            public Expression date() throws InternalException, UserException
            {
                return termDeep(maxLevels, type, l(), l());
            }

            @Override
            public Expression bool() throws InternalException, UserException
            {
                boolean target = (Boolean)targetValue.get(0);
                return termDeep(maxLevels, type, l(() -> columnRef(type, targetValue), () -> new BooleanLiteral(target)), l(
                    () -> {
                        DataType t = makeType(r);
                        // Don't do numbers because result isn't exact:
                        while (t.hasNumber())
                            t = makeType(r);
                        List<Object> valA = makeValue(t);
                        List<Object> valB;
                        int attempts = 0;
                        do
                        {
                            valB = makeValue(t);
                            if (attempts++ >= 100)
                                return new BooleanLiteral(target);
                        }
                        while (Utility.compareLists(valA, valB) == 0);
                        return new EqualExpression(make(t, valA, maxLevels - 1), make(t, target == true ? valA : valB, maxLevels - 1));
                    },
                    () -> {
                        DataType t = makeType(r);
                        // Don't do numbers because result isn't exact:
                        while (t.hasNumber())
                            t = makeType(r);
                        List<Object> valA = makeValue(t);
                        List<Object> valB;
                        int attempts = 0;
                        do
                        {
                            valB = makeValue(t);
                            if (attempts++ >= 100)
                                return new BooleanLiteral(target);
                        }
                        while (Utility.compareLists(valA, valB) == 0);
                        return new NotEqualExpression(make(t, valA, maxLevels - 1), make(t, target == true ? valB : valA, maxLevels - 1));
                    },
                    () -> {
                        // If target is true, all must be true:
                        if ((Boolean)targetValue.get(0))
                            return new AndExpression(TestUtil.makeList(r, 2, 5, () -> make(DataType.BOOLEAN, Collections.singletonList(true), maxLevels - 1)));
                        // Otherwise they can take on random values, but one must be false:
                        else
                        {
                            int size = r.nextInt(2, 5);
                            int mustBeFalse = r.nextInt(0, size - 1);
                            ArrayList<Expression> exps = new ArrayList<Expression>();
                            for (int i = 0; i < size; i++)
                                exps.add(make(DataType.BOOLEAN, Collections.singletonList(mustBeFalse == i ? false : r.nextBoolean()), maxLevels - 1));
                            return new AndExpression(exps);
                        }
                    },
                    () -> {
                        // If target is false, all must be false:
                        if ((Boolean)targetValue.get(0) == false)
                            return new OrExpression(TestUtil.makeList(r, 2, 5, () -> make(DataType.BOOLEAN, Collections.singletonList(false), maxLevels - 1)));
                            // Otherwise they can take on random values, but one must be false:
                        else
                        {
                            int size = r.nextInt(2, 5);
                            int mustBeTrue = r.nextInt(0, size - 1);
                            ArrayList<Expression> exps = new ArrayList<Expression>();
                            for (int i = 0; i < size; i++)
                                exps.add(make(DataType.BOOLEAN, Collections.singletonList(mustBeTrue == i ? true : r.nextBoolean()), maxLevels - 1));
                            return new OrExpression(exps);
                        }
                    }
                ));
            }

            @Override
            public Expression tagged(String typeName, List<TagType<DataType>> tags) throws InternalException, UserException
            {
                List<ExpressionMaker> terminals = new ArrayList<>();
                List<ExpressionMaker> nonTerm = new ArrayList<>();
                TagType<DataType> tag = tags.get((Integer) targetValue.get(0));
                Pair<@Nullable String, String> name = new Pair<>(typeName, tag.getName());
                final @Nullable DataType inner = tag.getInner();
                if (inner == null)
                {
                    terminals.add(() -> new TagExpression(name, null));
                }
                else
                {
                    final @NonNull DataType nonNullInner = inner;
                    nonTerm.add(() -> new TagExpression(name, make(nonNullInner, targetValue.subList(1, targetValue.size()), maxLevels - 1)));
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

    private Expression columnRef(DataType type, List<Object> value)
    {
        ColumnId name = new ColumnId("GEV Col " + columns.size());
        columns.add(rs -> type.apply(new DataTypeVisitor<Column>()
        {
            @Override
            public Column number(NumberInfo displayInfo) throws InternalException, UserException
            {
                return new MemoryNumericColumn(rs, name, new NumericColumnType(displayInfo.getUnit(), displayInfo.getMinimumDP(), false), Collections.singletonList(Utility.toBigDecimal((Number) value.get(0)).toPlainString()));
            }

            @Override
            public Column text() throws InternalException, UserException
            {
                return new MemoryStringColumn(rs, name, Collections.singletonList((String)value.get(0)));
            }

            @Override
            public Column date() throws InternalException, UserException
            {
                return new MemoryTemporalColumn(rs, name, Collections.singletonList((Temporal)value.get(0)));
            }

            @Override
            public Column bool() throws InternalException, UserException
            {
                return new MemoryBooleanColumn(rs, name, Collections.singletonList((Boolean) value.get(0)));
            }

            @Override
            public Column tagged(String typeName, List<TagType<DataType>> tags) throws InternalException, UserException
            {
                return new MemoryTaggedColumn(rs, name, typeName, tags, Collections.singletonList(value));
            }
        }));
        return new ColumnReference(name);
    }

    @FunctionalInterface
    public static interface ExpressionMaker
    {
        public Expression make() throws InternalException, UserException;
    }

    private List<ExpressionMaker> l(ExpressionMaker... suppliers)
    {
        return Arrays.asList(suppliers);
    }

    private List<Object> makeValue(DataType t) throws UserException, InternalException
    {
        return t.apply(new DataTypeVisitor<List<Object>>()
        {
            @Override
            public List<Object> number(NumberInfo displayInfo) throws InternalException, UserException
            {
                return Collections.singletonList(Utility.parseNumber(new GenNumber().generate(r, gs)));
            }

            @Override
            public List<Object> text() throws InternalException, UserException
            {
                return Collections.singletonList(TestUtil.makeString(r, gs));
            }

            @Override
            public List<Object> date() throws InternalException, UserException
            {
                return Collections.singletonList(new LocalDateGenerator().generate(r, gs));
            }

            @Override
            public List<Object> bool() throws InternalException, UserException
            {
                return Collections.singletonList(r.nextBoolean());
            }

            @Override
            public List<Object> tagged(String typeName, List<TagType<DataType>> tags) throws InternalException, UserException
            {
                int tagIndex = r.nextInt(0, tags.size() - 1);
                ArrayList<Object> o;
                @Nullable DataType inner = tags.get(tagIndex).getInner();
                if (inner != null)
                    o = new ArrayList<>(makeValue(inner));
                else
                    o = new ArrayList<>();
                o.add(0, tagIndex);
                return o;
            }
        });
    }

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

    private DataType makeTypeWithoutNumbers(SourceOfRandomness r)
    {
        DataType t;
        do
        {
            t = makeType(r);
        }
        while (t.hasNumber());
        return t;
    }
}
