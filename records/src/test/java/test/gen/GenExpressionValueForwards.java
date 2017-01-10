package test.gen;

import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
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
import records.data.datatype.DataType;
import records.data.datatype.DataType.DataTypeVisitor;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataType.DateTimeInfo.DateTimeType;
import records.data.datatype.DataType.NumberInfo;
import records.data.datatype.DataType.TagType;
import records.data.datatype.TypeId;
import records.data.unit.Unit;
import records.data.unit.UnitManager;
import records.error.FunctionInt;
import records.error.InternalException;
import records.error.UnimplementedException;
import records.error.UserException;
import records.transformations.expression.AddSubtractExpression;
import records.transformations.expression.AddSubtractExpression.Op;
import records.transformations.expression.AndExpression;
import records.transformations.expression.BooleanLiteral;
import records.transformations.expression.CallExpression;
import records.transformations.expression.ColumnReference;
import records.transformations.expression.DivideExpression;
import records.transformations.expression.EqualExpression;
import records.transformations.expression.Expression;
import records.transformations.expression.NotEqualExpression;
import records.transformations.expression.NumericLiteral;
import records.transformations.expression.OrExpression;
import records.transformations.expression.RaiseExpression;
import records.transformations.expression.StringLiteral;
import records.transformations.expression.TagExpression;
import records.transformations.expression.TimesExpression;
import test.DummyManager;
import test.TestUtil;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
import utility.Utility;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static test.TestUtil.distinctTypes;

/**
 * Generates expressions and resulting values by working the value forwards.
 * At each step, we generate an expression and then work out what its
 * value will be.  This allows us to test numeric expressions because
 * we can track its exact expected value, accounting for lost precision.
 * Types still go backwards.  i.e. we first decide we want say a year-month.
 * Then we decide to make one using the year-month function from two integers.
 * Then we generate the integers, say two literals, then feed those values back
 * down the tree to see what year-month value we end up with.
 * In contrast, going backwards we would start with the year-month value we want,
 * then generate the integer values to match.
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
            Pair<Object, Expression> p = makeOfType(type);
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
    public Pair<Object, Expression> makeOfType(DataType type) throws UserException, InternalException
    {
        return make(type, 4);
    }

    public static DataType makeType(SourceOfRandomness r)
    {
        return r.choose(distinctTypes);
    }

    private Pair<Object, Expression> make(DataType type, int maxLevels) throws UserException, InternalException
    {
        return type.apply(new DataTypeVisitor<Pair<Object, Expression>>()
        {
            @Override
            public Pair<Object, Expression> number(NumberInfo displayInfo) throws InternalException, UserException
            {
                return termDeep(maxLevels, type, l(
                    () -> columnRef(type),
                    () ->
                    {
                        Number number = TestUtil.generateNumber(r, gs);
                        return new Pair<>(number, new NumericLiteral(number, displayInfo.getUnit()));
                    }
                ), l(() -> {
                    int numArgs = r.nextInt(2, 6);
                    List<Expression> expressions = new ArrayList<>();
                    List<Op> ops = new ArrayList<>();
                    Number curTotal = 0;
                    for (int i = 0; i < numArgs; i++)
                    {
                        Pair<Object, Expression> pair = make(type, maxLevels - 1);
                        expressions.add(pair.getSecond());
                        // First one we count as add, because we're adding to the zero running total:
                        boolean opIsAdd = i == 0 || r.nextBoolean();
                        curTotal = Utility.addSubtractNumbers(curTotal, (Number) pair.getFirst(), opIsAdd);
                        if (i > 0)
                            ops.add(opIsAdd ? Op.ADD : Op.SUBTRACT);
                    }
                    return new Pair<>(curTotal, new AddSubtractExpression(expressions, ops));
                }, () -> {
                    // Either just use numerator, or make up crazy one
                    Unit numUnit = r.nextBoolean() ? displayInfo.getUnit() : makeUnit();
                    Unit denomUnit = calculateRequiredMultiplyUnit(numUnit, displayInfo.getUnit()).reciprocal();
                    Pair<Object, Expression> numerator = make(DataType.number(new NumberInfo(numUnit, 0)), maxLevels - 1);
                    Pair<Object, Expression> denominator;
                    do
                    {
                        denominator = make(DataType.number(new NumberInfo(denomUnit, 0)), maxLevels - 1);
                    }
                    while (Utility.compareNumbers(denominator.getFirst(), 0) == 0);
                    return new Pair<>(Utility.divideNumbers((Number)numerator.getFirst(), (Number)denominator.getFirst()),new DivideExpression(numerator.getSecond(), denominator.getSecond()));
                }, () ->
                {
                    if (displayInfo.getUnit().equals(Unit.SCALAR))
                    {
                        // Can have any power if it's scalar:
                        Pair<Object, Expression> lhs = make(type, maxLevels - 1);
                        Pair<Object, Expression> rhs = make(type, maxLevels - 1);

                        // Except you can't raise a negative to a non-integer power.  So check for that:
                        if (Utility.compareNumbers(lhs.getFirst(), 0) < 0 && !Utility.isIntegral(rhs.getFirst()))
                        {
                            // Two fixes: apply abs to LHS, or round to RHS.  Latter only suitable if power low
                            //if (r.nextBoolean() || Utility.compareNumbers(rhs.getFirst().get(0), 10) > 0)
                            //{
                                // Apply abs to LHS:
                                lhs = new Pair<>(Utility.withNumber(lhs.getFirst(), l -> safeAbs(l), BigInteger::abs, BigDecimal::abs), new CallExpression("abs", lhs.getSecond()));
                            //}
                            //else
                            //{
                                // Apply round to RHS:
                                //rhs = new Pair<>(Collections.singletonList(Utility.withNumber(rhs.getFirst().get(0), x -> x, x -> x, d -> d.setScale(0, BigDecimal.ROUND_UP))), new CallExpression("round", rhs.getSecond()));
                            //}
                        }

                        for (int attempts = 0; attempts < 50; attempts++)
                        {
                            try
                            {
                                Number value = Utility.raiseNumber((Number) lhs.getFirst(), (Number) rhs.getFirst());
                                return new Pair<>(value, new RaiseExpression(lhs.getSecond(), rhs.getSecond()));
                            }
                            catch (UserException e)
                            {
                                // Probably trying raising too high, cut it down and go again:
                                rhs = new Pair<>(Utility.toBigDecimal((Number) rhs.getFirst()).divide(BigDecimal.valueOf(20)), new DivideExpression(rhs.getSecond(), new NumericLiteral(20, null)));
                            }
                        }
                        // Give up trying to raise, just return LHS:
                        return lhs;
                    }
                    else
                    {
                        try
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
                                Pair<Object, Expression> lhs = make(DataType.number(new NumberInfo(lhsUnit, 0)), maxLevels - 1);
                                return new Pair<>(Utility.raiseNumber((Number) lhs.getFirst(), powers.get(0)), new RaiseExpression(lhs.getSecond(), new NumericLiteral(powers.get(0), null)));
                            }
                            else if (r.nextBoolean())
                            {
                                // Just use 1 as power:
                                Pair<Object, Expression> lhs = make(type, maxLevels - 1);
                                return lhs.replaceSecond(new RaiseExpression(lhs.getSecond(), new NumericLiteral(1, null)));
                            }
                            else
                            {
                                // Make up a power, then root it:
                                int raiseTo = r.nextInt(2, 5);
                                Unit lhsUnit = displayInfo.getUnit().raisedTo(raiseTo);
                                Pair<Object, Expression> lhs = make(DataType.number(new NumberInfo(lhsUnit, 0)), maxLevels - 1);
                                // You can't raise a negative to a non-integer power.  So check for that:
                                if (Utility.compareNumbers(lhs.getFirst(), 0) < 0)
                                {
                                    // Apply abs to LHS:
                                    lhs = new Pair<>(Utility.withNumber(lhs.getFirst(), l -> safeAbs(l), BigInteger::abs, BigDecimal::abs), new CallExpression("abs", lhs.getSecond()));
                                }
                                return new Pair<>(Utility.raiseNumber((Number) lhs.getFirst(), BigDecimal.valueOf(1.0 / raiseTo)), new RaiseExpression(lhs.getSecond(), new DivideExpression(new NumericLiteral(1, null), new NumericLiteral(raiseTo, null))));
                            }
                        }
                        catch (UserException e)
                        {
                            // Might have tried to raise a big decimal too large, in which case forget it:
                            return make(DataType.number(displayInfo), maxLevels - 1);
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
                        Pair<Object, Expression> pair = make(DataType.number(new NumberInfo(unit, 0)), maxLevels - 1);
                        runningTotal = Utility.multiplyNumbers(runningTotal, (Number)pair.getFirst());
                        expressions.add(pair.getSecond());
                    }
                    return new Pair<>(runningTotal, new TimesExpression(expressions));
                }));
            }

            @Override
            public Pair<Object, Expression> text() throws InternalException, UserException
            {
                return termDeep(maxLevels, type, l(
                    () -> columnRef(type),
                    () ->
                    {
                        String value = TestUtil.makeString(r, gs);
                        return new Pair<>(value, new StringLiteral(value));
                    }
                ), l());
            }

            @Override
            public Pair<Object, Expression> date(DateTimeInfo dateTimeInfo) throws InternalException, UserException
            {
                List<ExpressionMaker> deep = new ArrayList<>();
                // We don't use the from-integer versions here with deeper expressions because we can't
                // guarantee the values are in range, so we'd likely get an error.
                // Instead we use the narrowing versions or the ones that compose valid values:
                switch(dateTimeInfo.getType())
                {
                    case YEARMONTHDAY:
                        deep.add(() -> {
                            Pair<Object, Expression> dateTime = make(DataType.date(new DateTimeInfo(r.choose(Arrays.asList(DateTimeType.DATETIME, DateTimeType.DATETIMEZONED)))), maxLevels - 1);
                            return new Pair<>(LocalDate.from((TemporalAccessor) dateTime.getFirst()), new CallExpression("date", dateTime.getSecond()));
                        });
                        deep.add(() -> {
                            Pair<Object, Expression> dateTime = make(DataType.date(new DateTimeInfo(DateTimeType.YEARMONTH)), maxLevels - 1);
                            YearMonth yearMonth = YearMonth.from((TemporalAccessor) dateTime.getFirst());
                            int day = r.nextInt(1, 28);
                            return new Pair<>(LocalDate.of(yearMonth.getYear(), yearMonth.getMonth(), day), new CallExpression("date", dateTime.getSecond(), new NumericLiteral(day, getUnit("day"))));
                        });
                        break;
                    case YEARMONTH:
                        deep.add(() -> {
                            Pair<Object, Expression> dateTime = make(DataType.date(new DateTimeInfo(r.choose(Arrays.asList(DateTimeType.YEARMONTHDAY, DateTimeType.DATETIME, DateTimeType.DATETIMEZONED)))), maxLevels - 1);
                            return new Pair<>(YearMonth.from((TemporalAccessor) dateTime.getFirst()), new CallExpression("dateym", dateTime.getSecond()));
                        });
                        break;
                    case TIMEOFDAY:
                        deep.add(() -> {
                            Pair<Object, Expression> dateTime = make(DataType.date(new DateTimeInfo(r.choose(Arrays.asList(DateTimeType.TIMEOFDAYZONED, DateTimeType.DATETIME, DateTimeType.DATETIMEZONED)))), maxLevels - 1);
                            return new Pair<>(LocalTime.from((TemporalAccessor) dateTime.getFirst()), new CallExpression("time", dateTime.getSecond()));
                        });
                        break;
                    case TIMEOFDAYZONED:
                        deep.add(() -> {
                            Pair<Object, Expression> dateTime = make(DataType.date(new DateTimeInfo(r.choose(Arrays.asList(DateTimeType.DATETIMEZONED)))), maxLevels - 1);
                            return new Pair<>(OffsetTime.from((TemporalAccessor) dateTime.getFirst()), new CallExpression("timez", dateTime.getSecond()));
                        });
                        deep.add(() -> {
                            Pair<Object, Expression> dateTime = make(DataType.date(new DateTimeInfo(r.choose(Arrays.asList(DateTimeType.TIMEOFDAY)))), maxLevels - 1);
                            ZoneOffset zone = TestUtil.generateZoneOffset(r, gs);
                            return new Pair<>(OffsetTime.of((LocalTime)dateTime.getFirst(), zone), new CallExpression("timez", dateTime.getSecond(), new StringLiteral(zone.toString())));
                        });
                        break;
                    case DATETIME:
                        // down cast:
                        deep.add(() -> {
                            Pair<Object, Expression> dateTime = make(DataType.date(new DateTimeInfo(r.choose(Arrays.asList(DateTimeType.DATETIMEZONED)))), maxLevels - 1);
                            return new Pair<>(LocalDateTime.from((TemporalAccessor) dateTime.getFirst()), new CallExpression("datetime", dateTime.getSecond()));
                        });
                        // date + time:
                        deep.add(() -> {
                            Pair<Object, Expression> date = make(DataType.date(new DateTimeInfo(r.choose(Arrays.asList(DateTimeType.YEARMONTHDAY)))), maxLevels - 1);
                            Pair<Object, Expression> time = make(DataType.date(new DateTimeInfo(r.choose(Arrays.asList(DateTimeType.TIMEOFDAY)))), maxLevels - 1);
                            return new Pair<>(LocalDateTime.of((LocalDate) date.getFirst(), (LocalTime) time.getFirst()), new CallExpression("datetime", date.getSecond(), time.getSecond()));
                        });
                        break;
                    case DATETIMEZONED:
                        // datetime + zone:
                        deep.add(() -> {
                            Pair<Object, Expression> dateTime = make(DataType.date(new DateTimeInfo(r.choose(Arrays.asList(DateTimeType.DATETIME)))), maxLevels - 1);
                            ZoneOffset zone = TestUtil.generateZoneOffset(r, gs);
                            return new Pair<>(ZonedDateTime.of((LocalDateTime)dateTime.getFirst(), zone).withFixedOffsetZone(), new CallExpression("datetimez", dateTime.getSecond(), new StringLiteral(zone.toString())));
                        });
                        // date+time+zone:
                        deep.add(() -> {
                            Pair<Object, Expression> date = make(DataType.date(new DateTimeInfo(r.choose(Arrays.asList(DateTimeType.YEARMONTHDAY)))), maxLevels - 1);
                            Pair<Object, Expression> time = make(DataType.date(new DateTimeInfo(r.choose(Arrays.asList(DateTimeType.TIMEOFDAY)))), maxLevels - 1);
                            ZoneOffset zone = TestUtil.generateZoneOffset(r, gs);
                            return new Pair<>(ZonedDateTime.of((LocalDate)date.getFirst(), (LocalTime) time.getFirst(), zone).withFixedOffsetZone(), new CallExpression("datetimez", date.getSecond(), time.getSecond(), new StringLiteral(zone.toString())));
                        });
                        break;
                }
                List<ExpressionMaker> shallow = new ArrayList<>();
                shallow.add((ExpressionMaker)() ->
                {
                    switch (dateTimeInfo.getType())
                    {
                        case YEARMONTHDAY:
                            LocalDate date = TestUtil.generateDate(r, gs);
                            return new Pair<>(date, new CallExpression("date", new StringLiteral(date.toString())));
                        case YEARMONTH:
                            YearMonth ym = YearMonth.from(TestUtil.generateDate(r, gs));
                            return new Pair<>(ym, new CallExpression("dateym", new StringLiteral(ym.toString())));
                        case TIMEOFDAY:
                            LocalTime time = TestUtil.generateTime(r, gs);
                            return new Pair<>(time, new CallExpression("time", new StringLiteral(time.toString())));
                        case TIMEOFDAYZONED:
                            OffsetTime timez = OffsetTime.from(TestUtil.generateDateTimeZoned(r, gs));
                            return new Pair<>(timez, new CallExpression("timez", new StringLiteral(timez.toString())));
                        case DATETIME:
                            LocalDateTime dateTime = TestUtil.generateDateTime(r, gs);
                            return new Pair<>(dateTime, new CallExpression("datetime", new StringLiteral(dateTime.toString())));
                        case DATETIMEZONED:
                            ZonedDateTime zonedDateTime = TestUtil.generateDateTimeZoned(r, gs);
                            return new Pair<>(zonedDateTime.withFixedOffsetZone(), new CallExpression("datetimez", new StringLiteral(zonedDateTime.toString())));

                    }
                    throw new RuntimeException("No date generator for " + dateTimeInfo.getType());
                });

                switch (dateTimeInfo.getType())
                {
                    case YEARMONTHDAY:
                        shallow.add(() -> {
                            int year = r.nextInt(1, 9999);
                            int month = r.nextInt(1, 12);
                            int day = r.nextInt(1, 28);
                            return new Pair<>(LocalDate.of(year, month, day), new CallExpression("date",
                                new NumericLiteral(year, getUnit("year")),
                                new NumericLiteral(month, getUnit("month")),
                                new NumericLiteral(day, getUnit("day"))
                            ));
                        });
                        break;
                    case YEARMONTH:
                        shallow.add(() -> {
                            int year = r.nextInt(1, 9999);
                            int month = r.nextInt(1, 12);
                            return new Pair<>(YearMonth.of(year, month), new CallExpression("dateym",
                                new NumericLiteral(year, getUnit("year")),
                                new NumericLiteral(month, getUnit("month"))
                            ));
                        });
                        break;
                    case TIMEOFDAY:
                        shallow.add(() -> {
                            int hour = r.nextInt(0, 23);
                            int minute = r.nextInt(0, 59);
                            int second = r.nextInt(0, 59);
                            int nano = r.nextInt(0, 999999999);
                            return new Pair<>(LocalTime.of(hour, minute, second, nano), new CallExpression("time",
                                new NumericLiteral(hour, getUnit("hour")),
                                new NumericLiteral(minute, getUnit("min")),
                                new NumericLiteral(BigDecimal.valueOf(nano).divide(BigDecimal.valueOf(1_000_000_000)).add(BigDecimal.valueOf(second)), getUnit("s"))
                            ));
                        });
                }

                return termDeep(maxLevels, type, shallow, deep);
            }

            @Override
            public Pair<Object, Expression> bool() throws InternalException, UserException
            {
                return termDeep(maxLevels, type, l(() -> columnRef(type), () ->
                {
                    boolean value = r.nextBoolean();
                    return new Pair<>(value, new BooleanLiteral(value));
                }), l(
                    () -> {
                        DataType t = makeType(r);

                        Pair<Object, Expression> lhs = make(t, maxLevels - 1);
                        Pair<Object, Expression> rhs = make(t, maxLevels - 1);
                        if (r.nextBoolean())
                            return new Pair<>(Utility.compareValues(lhs.getFirst(), rhs.getFirst()) == 0, new EqualExpression(lhs.getSecond(), rhs.getSecond()));
                        else
                            return new Pair<>(Utility.compareValues(lhs.getFirst(), rhs.getFirst()) != 0, new NotEqualExpression(lhs.getSecond(), rhs.getSecond()));
                    },
                    () -> {
                        int size = r.nextInt(2, 5);
                        boolean and = r.nextBoolean();
                        boolean value = and ? true : false;
                        ArrayList<Expression> exps = new ArrayList<Expression>();
                        for (int i = 0; i < size; i++)
                        {
                            Pair<Object, Expression> pair = make(DataType.BOOLEAN, maxLevels - 1);
                            if (and)
                                value &= (Boolean)pair.getFirst();
                            else
                                value |= (Boolean)pair.getFirst();
                            exps.add(pair.getSecond());
                        }
                        return new Pair<>(value, and ? new AndExpression(exps) : new OrExpression(exps));
                    }
                ));
            }

            @Override
            public Pair<Object, Expression> tagged(TypeId typeName, List<TagType<DataType>> tags) throws InternalException, UserException
            {
                List<ExpressionMaker> terminals = new ArrayList<>();
                List<ExpressionMaker> nonTerm = new ArrayList<>();
                int tagIndex = r.nextInt(0, tags.size() - 1);
                TagType<DataType> tag = tags.get(tagIndex);
                Pair<String, String> name = new Pair<>(typeName.getRaw(), tag.getName());
                final @Nullable DataType inner = tag.getInner();
                if (inner == null)
                {
                    terminals.add(() -> new Pair<>(new Pair<Integer, @Nullable Object>(tagIndex, null), new TagExpression(name, null)));
                }
                else
                {
                    final @NonNull DataType nonNullInner = inner;
                    nonTerm.add(() ->
                    {
                        Pair<Object, Expression> innerVal = make(nonNullInner, maxLevels - 1);
                        return new Pair<>(new Pair<>(tagIndex, innerVal.getFirst()), new TagExpression(name, innerVal.getSecond()));
                    });
                }
                return termDeep(maxLevels, type, terminals, nonTerm);
            }

            @Override
            public Pair<Object, Expression> tuple(List<DataType> inner) throws InternalException, UserException
            {
                throw new UnimplementedException();
            }

            @Override
            public Pair<Object, Expression> array(DataType inner) throws InternalException, UserException
            {
                throw new UnimplementedException();
            }
        });
    }

    private Number safeAbs(Long l)
    {
        return l.longValue() == Long.MIN_VALUE ? BigInteger.valueOf(l).abs() : Math.abs(l);
    }

    // What unit do you have to multiply src by to get dest?
    private Unit calculateRequiredMultiplyUnit(Unit src, Unit dest)
    {
        // So we have src * x = dest
        // This can be rearranged to x = dest/src
        return dest.divide(src);
    }

    private Unit getUnit(String name) throws InternalException, UserException
    {
        UnitManager m = DummyManager.INSTANCE.getUnitManager();
        return m.loadUse(name);
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
        return new BigDecimal(new GenNumberAsString().generate(r, gs));
    }

    private Pair<Object, Expression> columnRef(DataType type) throws UserException, InternalException
    {
        ColumnId name = new ColumnId("GEV Col " + columns.size());
        Pair<Object, FunctionInt<RecordSet, Column>> pair = type.apply(new DataTypeVisitor<Pair<Object, FunctionInt<RecordSet, Column>>>()
        {
            @Override
            public Pair<Object, FunctionInt<RecordSet, Column>> number(NumberInfo displayInfo) throws InternalException, UserException
            {
                Number value = TestUtil.generateNumber(r, gs);
                return new Pair<>(value, rs -> new MemoryNumericColumn(rs, name, new NumberInfo(displayInfo.getUnit(), displayInfo.getMinimumDP()), Stream.of(Utility.toBigDecimal(value).toPlainString())));
            }

            @Override
            public Pair<Object, FunctionInt<RecordSet, Column>> text() throws InternalException, UserException
            {
                String value = TestUtil.makeString(r, gs);
                return new Pair<>(value, rs -> new MemoryStringColumn(rs, name, Collections.singletonList(value)));
            }

            @Override
            public Pair<Object, FunctionInt<RecordSet, Column>> date(DateTimeInfo dateTimeInfo) throws InternalException, UserException
            {
                Temporal value = TestUtil.generateDate(r, gs);
                return new Pair<>(value, rs -> new MemoryTemporalColumn(rs, name, new DateTimeInfo(DateTimeType.YEARMONTHDAY), Collections.singletonList((Temporal) value)));
            }

            @Override
            public Pair<Object, FunctionInt<RecordSet, Column>> bool() throws InternalException, UserException
            {
                boolean value = r.nextBoolean();
                return new Pair<>(value, rs -> new MemoryBooleanColumn(rs, name, Collections.singletonList((Boolean) value)));
            }

            @Override
            public Pair<Object, FunctionInt<RecordSet, Column>> tagged(TypeId typeName, List<TagType<DataType>> tags) throws InternalException, UserException
            {
                int tagIndex = r.nextInt(0, tags.size() - 1);
                @Nullable DataType inner = tags.get(tagIndex).getInner();
                Object rest = inner == null ? null : make(inner, 1).getFirst();
                Pair<Integer, @Nullable Object> value = new Pair<>(1, rest);
                return new Pair<>(value, rs -> new MemoryTaggedColumn(rs, name, typeName, tags, Collections.singletonList(value)));
            }

            @Override
            public Pair<Object, FunctionInt<RecordSet, Column>> tuple(List<DataType> inner) throws InternalException, UserException
            {
                throw new UnimplementedException();
            }

            @Override
            public Pair<Object, FunctionInt<RecordSet, Column>> array(DataType inner) throws InternalException, UserException
            {
                throw new UnimplementedException();
            }
        });
        columns.add(pair.getSecond());
        return pair.replaceSecond(new ColumnReference(name));
    }

    @FunctionalInterface
    public static interface ExpressionMaker
    {
        public Pair<Object, Expression> make() throws InternalException, UserException;
    }

    private List<ExpressionMaker> l(ExpressionMaker... suppliers)
    {
        return Arrays.asList(suppliers);
    }

    private Pair<Object, Expression> termDeep(int maxLevels, DataType type, List<ExpressionMaker> terminals, List<ExpressionMaker> deeper) throws UserException, InternalException
    {
        /*
        if (maxLevels > 1 && r.nextInt(0, 5) == 0)
        {
            return makeMatch(maxLevels - 1, () -> termDeep(maxLevels - 1, type, terminals, deeper), () -> make(type, maxLevels - 1));
        }
        */

        //TODO generate match expressions here (valid for all types)
        if (deeper.isEmpty() || (!terminals.isEmpty() && (maxLevels <= 1 || deeper.isEmpty() || r.nextInt(0, 2) == 0)))
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
