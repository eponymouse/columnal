package test.gen;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.jetbrains.annotations.NotNull;
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
import records.data.datatype.DataTypeUtility;
import records.data.datatype.TypeManager.TagInfo;
import utility.Either;
import utility.TaggedValue;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DataTypeVisitor;
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
import records.transformations.expression.CallExpression;
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
import records.transformations.expression.TagExpression;
import records.transformations.expression.TupleExpression;
import records.transformations.expression.VarDeclExpression;
import records.transformations.expression.VarUseExpression;
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
import java.time.OffsetTime;
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
public class GenExpressionValueBackwards extends GenValueBase<ExpressionValue>
{

    @SuppressWarnings("initialization")
    public GenExpressionValueBackwards()
    {
        super(ExpressionValue.class);
    }

    private List<ExFunction<RecordSet, Column>> columns;
    private int nextVar = 0;

    @Override
    @OnThread(value = Tag.Simulation, ignoreParent = true)
    public ExpressionValue generate(SourceOfRandomness r, GenerationStatus generationStatus)
    {
        this.r = r;
        this.gs = generationStatus;
        this.numberOnlyInt = true;
        this.columns = new ArrayList<>();
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

    @NotNull
    @OnThread(Tag.Simulation)
    public KnownLengthRecordSet getRecordSet() throws InternalException, UserException
    {
        return new KnownLengthRecordSet(this.columns, 1);
    }

    // Only valid after calling generate
    @NotNull
    @OnThread(value = Tag.Simulation, ignoreParent = true)
    public Pair<@Value Object, Expression> makeOfType(DataType type) throws UserException, InternalException
    {
        @Value Object value = makeValue(type);
        Expression expression = make(type, value, 4);
        return new Pair<>(value, expression);
    }

    @SuppressWarnings("valuetype")
    private Expression make(DataType type, Object targetValue, int maxLevels) throws UserException, InternalException
    {
        return type.apply(new DataTypeVisitor<Expression>()
        {
            @Override
            @OnThread(Tag.Simulation)
            public Expression number(NumberInfo displayInfo) throws InternalException, UserException
            {
                // We only do integers because beyond that the result isn't guaranteed, which
                // could cause failures in things like equal expressions.
                return termDeep(maxLevels, type, l(
                    () -> columnRef(type, targetValue),
                    () -> new NumericLiteral((Number)targetValue, makeUnitExpression(displayInfo.getUnit()))
                ), l(() -> {
                    // We just make up a bunch of numbers, and at the very end we add one more to correct the difference
                    int numMiddle = r.nextInt(0, 6);
                    List<Expression> expressions = new ArrayList<>();
                    List<Op> ops = new ArrayList<>();
                    BigDecimal curTotal = BigDecimal.valueOf(genInt());
                    expressions.add(make(type, curTotal, maxLevels - 1));
                    for (int i = 0; i < numMiddle; i++)
                    {
                        //System.err.println("Exp Cur: " + curTotal.toPlainString() + " after " + expressions.get(i));
                        long next = genInt();
                        expressions.add(make(type, next, maxLevels - 1));
                        BigDecimal prevTotal = curTotal;
                        if (r.nextBoolean())
                        {
                            curTotal = curTotal.add(BigDecimal.valueOf(next), MathContext.DECIMAL128);
                            if (prevTotal.add(BigDecimal.valueOf(next)).compareTo(curTotal) != 0)
                                throw new RuntimeException("Error building expression +");
                            ops.add(Op.ADD);
                        }
                        else
                        {
                            curTotal = curTotal.subtract(BigDecimal.valueOf(next), MathContext.DECIMAL128);
                            if (prevTotal.subtract(BigDecimal.valueOf(next)).compareTo(curTotal) != 0)
                                throw new RuntimeException("Error building expression +");
                            ops.add(Op.SUBTRACT);
                        }
                    }
                    //System.err.println("Exp Cur: " + curTotal.toPlainString() + " after " + expressions.get(expressions.size() - 1));
                    // Now add one more to make the difference:
                    BigDecimal diff = (targetValue instanceof BigDecimal ? (BigDecimal)targetValue : BigDecimal.valueOf(((Number)targetValue).longValue())).subtract(curTotal, MathContext.DECIMAL128);
                    boolean add = r.nextBoolean();
                    expressions.add(make(type, DataTypeUtility.value(add ? diff : diff.negate()), maxLevels - 1));
                    ops.add(add ? Op.ADD : Op.SUBTRACT);
                    //System.err.println("Exp Result: " + Utility.toBigDecimal((Number)targetValue).toPlainString() + " after " + expressions.get(expressions.size() - 1) + " diff was: " + diff.toPlainString());
                    return new AddSubtractExpression(expressions, ops);
                }, () -> {
                    // A few options; keep units and value in numerator and divide by 1
                    // Or make random denom, times that by target to get num, and make up crazy units which work
                    if (r.nextInt(0, 4) == 0)
                        return new DivideExpression(make(type, targetValue, maxLevels - 1), new NumericLiteral(1, makeUnitExpression(Unit.SCALAR)));
                    else
                    {
                        long denominator;
                        do
                        {
                            denominator = genInt();
                        }
                        while (Utility.compareNumbers(denominator, 0) == 0);
                        Number numerator = Utility.multiplyNumbers((Number) targetValue, denominator);
                        if (Utility.compareNumbers(Utility.divideNumbers(numerator, denominator), targetValue) != 0)
                        {
                            // Divide won't come out right: just divide by 1:
                            return new DivideExpression(make(type, targetValue, maxLevels - 1), new NumericLiteral(1, makeUnitExpression(Unit.SCALAR)));
                        }

                        // Either just use numerator, or make up crazy one
                        Unit numUnit = r.nextBoolean() ? displayInfo.getUnit() : makeUnit();
                        Unit denomUnit = calculateRequiredMultiplyUnit(numUnit, displayInfo.getUnit()).reciprocal();
                        // TODO test division by zero behaviour (test errors generally)
                        return new DivideExpression(make(DataType.number(new NumberInfo(numUnit, null)), numerator, maxLevels - 1), make(DataType.number(new NumberInfo(denomUnit, null)), denominator, maxLevels - 1));
                    }
                }));
            }

            @Override
            @OnThread(Tag.Simulation)
            public Expression text() throws InternalException, UserException
            {
                return termDeep(maxLevels, type, l(
                    () -> columnRef(type, targetValue),
                    () -> new StringLiteral((String)targetValue)
                ), l());
            }

            @Override
            @OnThread(Tag.Simulation)
            public Expression date(DateTimeInfo dateTimeInfo) throws InternalException, UserException
            {
                List<ExpressionMaker> deep = new ArrayList<ExpressionMaker>();
                deep.add(() -> new CallExpression(getCreator(dateTimeInfo.getType()), make(DataType.TEXT, targetValue.toString(), maxLevels - 1)));

                switch (dateTimeInfo.getType())
                {
                    case YEARMONTHDAY:
                    {
                        @NonNull DateTimeType dateTimeType = r.<@NonNull DateTimeType>choose(Arrays.asList(DateTimeType.DATETIME, DateTimeType.DATETIMEZONED));
                        DataType t = DataType.date(new DateTimeInfo(dateTimeType));
                        deep.add(() -> new CallExpression("date", make(t, makeTemporalToMatch(dateTimeType, (TemporalAccessor) targetValue), maxLevels - 1)));
                        LocalDate target = (LocalDate) targetValue;
                        deep.add(() -> new CallExpression("date",
                            make(DataType.number(new NumberInfo(getUnit("year"), null)), target.getYear(), maxLevels - 1),
                            make(DataType.number(new NumberInfo(getUnit("month"), null)), target.getMonthValue(), maxLevels - 1),
                            make(DataType.number(new NumberInfo(getUnit("day"), null)), target.getDayOfMonth(), maxLevels - 1)
                        ));
                        deep.add(() -> new CallExpression("date",
                            make(DataType.date(new DateTimeInfo(DateTimeType.YEARMONTH)), YearMonth.of(target.getYear(), target.getMonth()), maxLevels - 1),
                            make(DataType.number(new NumberInfo(getUnit("day"), null)), target.getDayOfMonth(), maxLevels - 1)
                        ));
                    }
                        break;
                    case YEARMONTH:
                    {
                        DateTimeType dateTimeType = r.<@NonNull DateTimeType>choose(Arrays.asList(DateTimeType.YEARMONTHDAY, DateTimeType.DATETIME, DateTimeType.DATETIMEZONED));
                        DataType t = DataType.date(new DateTimeInfo(dateTimeType));
                        deep.add(() -> new CallExpression("dateym", make(t, makeTemporalToMatch(dateTimeType, (TemporalAccessor) targetValue), maxLevels - 1)));
                        YearMonth target = (YearMonth) targetValue;
                        deep.add(() -> new CallExpression("dateym",
                            make(DataType.number(new NumberInfo(getUnit("year"), null)), target.getYear(), maxLevels - 1),
                            make(DataType.number(new NumberInfo(getUnit("month"), null)), target.getMonthValue(), maxLevels - 1)
                        ));
                    }
                        break;
                    case TIMEOFDAY:
                    {
                        DateTimeType dateTimeType = r.<@NonNull DateTimeType>choose(Arrays.asList(DateTimeType.TIMEOFDAYZONED, DateTimeType.DATETIME, DateTimeType.DATETIMEZONED));
                        DataType t = DataType.date(new DateTimeInfo(dateTimeType));
                        deep.add(() -> new CallExpression("time", make(t, makeTemporalToMatch(dateTimeType, (TemporalAccessor) targetValue), maxLevels - 1)));
                        LocalTime target = (LocalTime) targetValue;
                        deep.add(() -> new CallExpression("time",
                            make(DataType.number(new NumberInfo(getUnit("hour"), null)), target.getHour(), maxLevels - 1),
                            make(DataType.number(new NumberInfo(getUnit("min"), null)), target.getMinute(), maxLevels - 1),
                            // We only generate integers in this class, so generate nanos then divide:
                            new DivideExpression(make(DataType.number(new NumberInfo(getUnit("s"), null)), (long)target.getSecond() * 1_000_000_000L + target.getNano(), maxLevels - 1), new NumericLiteral(1_000_000_000L, null))
                        ));
                    }
                        break;
                    case TIMEOFDAYZONED:
                    {
                        DateTimeType dateTimeType = r.<@NonNull DateTimeType>choose(Arrays.asList(DateTimeType.DATETIMEZONED));
                        DataType t = DataType.date(new DateTimeInfo(dateTimeType));
                        deep.add(() -> new CallExpression("timez", make(t, makeTemporalToMatch(dateTimeType, (TemporalAccessor) targetValue), maxLevels - 1)));
                        OffsetTime target = (OffsetTime) targetValue;
                        deep.add(() -> new CallExpression("timez",
                            make(DataType.date(new DateTimeInfo(DateTimeType.TIMEOFDAY)), target.toLocalTime(), maxLevels - 1),
                            make(DataType.TEXT, target.getOffset().toString(), maxLevels - 1)
                        ));
                    }
                        break;
                    case DATETIME:
                    {
                        LocalDateTime target = (LocalDateTime) targetValue;
                        //date+time+zone:
                        deep.add(() -> new CallExpression("datetime",
                            make(DataType.date(new DateTimeInfo(DateTimeType.YEARMONTHDAY)), target.toLocalDate(), maxLevels - 1),
                            make(DataType.date(new DateTimeInfo(DateTimeType.TIMEOFDAY)), target.toLocalTime(), maxLevels - 1)
                        ));
                    }
                        break;
                    case DATETIMEZONED:
                    {
                        ZonedDateTime target = (ZonedDateTime) targetValue;
                        //datetime+zone
                        deep.add(() -> new CallExpression("datetimez",
                            make(DataType.date(new DateTimeInfo(DateTimeType.DATETIME)), target.toLocalDateTime(), maxLevels - 1),
                            make(DataType.TEXT, target.getZone().toString(), maxLevels - 1)
                        ));
                        //date + time&zone
                        // only if using offset, not a zone:
                        if (target.getZone().equals(target.getOffset()))
                        {
                            deep.add(() -> new CallExpression("datetimez",
                                make(DataType.date(new DateTimeInfo(DateTimeType.YEARMONTHDAY)), target.toLocalDate(), maxLevels - 1),
                                make(DataType.date(new DateTimeInfo(DateTimeType.TIMEOFDAYZONED)), target.toOffsetDateTime().toOffsetTime(), maxLevels - 1)
                            ));
                        }
                        //date+time+zone:
                        deep.add(() -> new CallExpression("datetimez",
                            make(DataType.date(new DateTimeInfo(DateTimeType.YEARMONTHDAY)), target.toLocalDate(), maxLevels - 1),
                            make(DataType.date(new DateTimeInfo(DateTimeType.TIMEOFDAY)), target.toLocalTime(), maxLevels - 1),
                            make(DataType.TEXT, target.getZone().toString(), maxLevels - 1)
                        ));
                    }
                        break;
                }

                return termDeep(maxLevels, type, l((ExpressionMaker)() -> {
                    return new CallExpression(getCreator(dateTimeInfo.getType()), new StringLiteral(targetValue.toString()));
                }, () -> columnRef(type, targetValue)), deep);
            }

            @Override
            @OnThread(Tag.Simulation)
            public Expression bool() throws InternalException, UserException
            {
                boolean target = (Boolean)targetValue;
                return termDeep(maxLevels, type, l(() -> columnRef(type, targetValue), () -> new BooleanLiteral(target)), l(
                    () -> {
                        DataType t = makeType(r);
                        @Value Object valA = makeValue(t);
                        @Value Object valB;
                        int attempts = 0;
                        do
                        {
                            valB = makeValue(t);
                            if (attempts++ >= 100)
                                return new BooleanLiteral(target);
                        }
                        while (Utility.compareValues(valA, valB) == 0);
                        return new EqualExpression(make(t, valA, maxLevels - 1), make(t, target == true ? valA : valB, maxLevels - 1));
                    },
                    () -> {
                        DataType t = makeType(r);
                        @Value Object valA = makeValue(t);
                        @Value Object valB;
                        int attempts = 0;
                        do
                        {
                            valB = makeValue(t);
                            if (attempts++ >= 100)
                                return new BooleanLiteral(target);
                        }
                        while (Utility.compareValues(valA, valB) == 0);
                        return new NotEqualExpression(make(t, valA, maxLevels - 1), make(t, target == true ? valB : valA, maxLevels - 1));
                    },
                    () -> {
                        // If target is true, all must be true:
                        if ((Boolean)targetValue)
                            return new AndExpression(TestUtil.makeList(r, 2, 5, () -> make(DataType.BOOLEAN, true, maxLevels - 1)));
                        // Otherwise they can take on random values, but one must be false:
                        else
                        {
                            int size = r.nextInt(2, 5);
                            int mustBeFalse = r.nextInt(0, size - 1);
                            ArrayList<Expression> exps = new ArrayList<Expression>();
                            for (int i = 0; i < size; i++)
                                exps.add(make(DataType.BOOLEAN, mustBeFalse == i ? false : r.nextBoolean(), maxLevels - 1));
                            return new AndExpression(exps);
                        }
                    },
                    () -> {
                        // If target is false, all must be false:
                        if ((Boolean)targetValue == false)
                            return new OrExpression(TestUtil.makeList(r, 2, 5, () -> make(DataType.BOOLEAN, false, maxLevels - 1)));
                            // Otherwise they can take on random values, but one must be false:
                        else
                        {
                            int size = r.nextInt(2, 5);
                            int mustBeTrue = r.nextInt(0, size - 1);
                            ArrayList<Expression> exps = new ArrayList<Expression>();
                            for (int i = 0; i < size; i++)
                                exps.add(make(DataType.BOOLEAN, mustBeTrue == i ? true : r.nextBoolean(), maxLevels - 1));
                            return new OrExpression(exps);
                        }
                    },
                    () -> {
                        // First form a valid set of values and sort them into order
                        boolean ascending = r.nextBoolean();
                        DataType dataType = r.choose(TestUtil.distinctTypes);
                        List<Pair<@Value Object, Expression>> operands = TestUtil.makeList(r, 2, 5, () -> makeOfType(dataType));
                        Collections.sort(operands, (a, b) -> { try
                        {
                            return Utility.compareValues(a.getFirst(), b.getFirst()) * (ascending ? 1 : -1);
                        } catch (UserException | InternalException e) { throw new RuntimeException(e); }});
                        List<ComparisonOperator> ops = new ArrayList<>();
                        for (int i = 0; i < operands.size() - 1; i++)
                        {
                            // We may have randomly generated equal values, so check for that and adjust
                            // operator to the or-equals variant if necessary:
                            ops.add(
                                Utility.compareValues(operands.get(i).getFirst(), operands.get(i+1).getFirst()) == 0 ?
                                  (ascending ? ComparisonOperator.LESS_THAN_OR_EQUAL_TO : ComparisonOperator.GREATER_THAN_OR_EQUAL_TO)
                                : (ascending ? ComparisonOperator.LESS_THAN : ComparisonOperator.GREATER_THAN));
                        }
                        // Randomly duplicate a value and change to <=/>=:
                        int swap = r.nextInt(0, operands.size() - 2); // Picking operator really, not operand
                        // Copy from left to right or right to left:
                        if (r.nextBoolean())
                        {
                            // Copy from right to left:
                            Object newTarget = operands.get(swap + 1).getFirst();
                            operands.set(swap, new Pair<>(newTarget, make(dataType, newTarget, maxLevels - 1)));
                            ops.set(swap, ascending ? ComparisonOperator.LESS_THAN_OR_EQUAL_TO : ComparisonOperator.GREATER_THAN_OR_EQUAL_TO);
                        }
                        else
                        {
                            // Copy from left to right:
                            Object newTarget = operands.get(swap).getFirst();
                            operands.set(swap + 1, new Pair<>(newTarget, make(dataType, newTarget, maxLevels - 1)));
                            ops.set(swap, ascending ? ComparisonOperator.LESS_THAN_OR_EQUAL_TO : ComparisonOperator.GREATER_THAN_OR_EQUAL_TO);
                        }
                        // If we are aiming for true, job done.
                        if ((Boolean)targetValue == false)
                        {
                            // Need to make it false by swapping two adjacent values (and getting rid of <=/>=)
                            int falsifyOp = r.nextInt(0, operands.size() - 2); // Picking operator really, not operand
                            Pair<Object, Expression> temp = operands.get(falsifyOp);
                            operands.set(falsifyOp, operands.get(falsifyOp + 1));
                            operands.set(falsifyOp + 1, temp);
                            ops.set(falsifyOp, ascending ? ComparisonOperator.LESS_THAN : ComparisonOperator.GREATER_THAN);
                        }
                        return new ComparisonExpression(Utility.mapList(operands, p -> p.getSecond()), ImmutableList.copyOf(ops));
                    }
                ));
            }

            @Override
            @OnThread(Tag.Simulation)
            public Expression tagged(TypeId typeName, ImmutableList<TagType<DataType>> tags) throws InternalException, UserException
            {
                List<ExpressionMaker> terminals = new ArrayList<>();
                List<ExpressionMaker> nonTerm = new ArrayList<>();
                int tagIndex = ((TaggedValue) targetValue).getTagIndex();
                TagType<DataType> tag = tags.get(tagIndex);
                TagInfo tagInfo = new TagInfo(type, tagIndex, tag);
                final @Nullable DataType inner = tag.getInner();
                if (inner == null)
                {
                    terminals.add(() -> new TagExpression(Either.right(tagInfo), null));
                }
                else
                {
                    final @NonNull DataType nonNullInner = inner;
                    nonTerm.add(() ->
                    {
                        @Nullable Object innerValue = ((TaggedValue) targetValue).getInner();
                        if (innerValue == null)
                            throw new InternalException("Type says inner value but is null");
                        return new TagExpression(Either.right(tagInfo), make(nonNullInner, innerValue, maxLevels - 1));
                    });
                }
                terminals.add(() -> columnRef(type, targetValue));
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
                terminals.add(() -> columnRef(type, targetValue));
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
                terminals.add(() -> columnRef(type, targetValue));
                return termDeep(maxLevels, type, terminals, nonTerm);
            }
        });
    }
    
    private String getCreator(DateTimeType t)
    {
        switch (t)
        {
            case YEARMONTHDAY:
                return "date";
            case YEARMONTH:
                return "dateym";
            case TIMEOFDAY:
                return "time";
            case TIMEOFDAYZONED:
                return "timez";
            case DATETIME:
                return "datetime";
            case DATETIMEZONED:
                return "datetimez";
        }
        throw new RuntimeException("Unknown date type: " + t);
    }

    // Makes a value which, when the right fields are extracted, will give the value target
    // That is, you pass a type which is "bigger" than the intended (e.g. datetimezone)
    // and a target value (e.g. a timezoned), and it will make a datetimezone
    // value which you can downcast to your target value.
    private Temporal makeTemporalToMatch(DateTimeType type, TemporalAccessor target)
    {
        Function<TemporalField, Integer> tf = field -> {
            if (target.isSupported(field))
                return target.get(field);
            if (field.equals(ChronoField.YEAR))
                return r.nextInt(1, 9999);
            if (field.equals(ChronoField.MONTH_OF_YEAR))
                return r.nextInt(1, 12);
            if (field.equals(ChronoField.DAY_OF_MONTH))
                return r.nextInt(1, 28);
            if (field.equals(ChronoField.HOUR_OF_DAY))
                return r.nextInt(0, 23);
            if (field.equals(ChronoField.MINUTE_OF_HOUR))
                return r.nextInt(0, 59);
            if (field.equals(ChronoField.SECOND_OF_MINUTE))
                return r.nextInt(0, 59);
            if (field.equals(ChronoField.NANO_OF_SECOND))
                return r.nextInt(0, 999999999);
            if (field.equals(ChronoField.OFFSET_SECONDS))
                return r.nextInt(-12*60, 12*60) * 60;

            throw new RuntimeException("Unknown temporal field: " + field + " on type " + target);
        };

        switch (type)
        {
            case YEARMONTHDAY:
                return LocalDate.of(tf.apply(ChronoField.YEAR), tf.apply(ChronoField.MONTH_OF_YEAR), tf.apply(ChronoField.DAY_OF_MONTH));
            case YEARMONTH:
                return YearMonth.of(tf.apply(ChronoField.YEAR), tf.apply(ChronoField.MONTH_OF_YEAR));
            case TIMEOFDAY:
                return LocalTime.of(tf.apply(ChronoField.HOUR_OF_DAY), tf.apply(ChronoField.MINUTE_OF_HOUR), tf.apply(ChronoField.SECOND_OF_MINUTE), tf.apply(ChronoField.NANO_OF_SECOND));
            case TIMEOFDAYZONED:
                return OffsetTime.of(tf.apply(ChronoField.HOUR_OF_DAY), tf.apply(ChronoField.MINUTE_OF_HOUR), tf.apply(ChronoField.SECOND_OF_MINUTE), tf.apply(ChronoField.NANO_OF_SECOND), ZoneOffset.ofTotalSeconds(tf.apply(ChronoField.OFFSET_SECONDS)));
            case DATETIME:
                return LocalDateTime.of(tf.apply(ChronoField.YEAR), tf.apply(ChronoField.MONTH_OF_YEAR), tf.apply(ChronoField.DAY_OF_MONTH), tf.apply(ChronoField.HOUR_OF_DAY), tf.apply(ChronoField.MINUTE_OF_HOUR), tf.apply(ChronoField.SECOND_OF_MINUTE), tf.apply(ChronoField.NANO_OF_SECOND));
            case DATETIMEZONED:
                if (target instanceof ZonedDateTime)
                    return (ZonedDateTime)target; //Preserves zone name properly; this is the only type that can have a named zone
                else
                    return ZonedDateTime.of(tf.apply(ChronoField.YEAR), tf.apply(ChronoField.MONTH_OF_YEAR), tf.apply(ChronoField.DAY_OF_MONTH), tf.apply(ChronoField.HOUR_OF_DAY), tf.apply(ChronoField.MINUTE_OF_HOUR), tf.apply(ChronoField.SECOND_OF_MINUTE), tf.apply(ChronoField.NANO_OF_SECOND), ZoneId.ofOffset("", ZoneOffset.ofTotalSeconds(tf.apply(ChronoField.OFFSET_SECONDS)))).withFixedOffsetZone();
        }
        throw new RuntimeException("Cannot match " + type);
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
        return r.<@NonNull Unit>choose(Arrays.asList(
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

    private Unit getUnit(String name) throws InternalException, UserException
    {
        UnitManager m = DummyManager.INSTANCE.getUnitManager();
        return m.loadUse(name);
    }

    private Expression columnRef(DataType type, Object value)
    {
        ColumnId name = new ColumnId("GEV Col " + columns.size());
        columns.add(rs -> type.apply(new DataTypeVisitor<Column>()
        {
            @Override
            @OnThread(Tag.Simulation)
            public Column number(NumberInfo numberInfo) throws InternalException, UserException
            {
                return new MemoryNumericColumn(rs, name, numberInfo, Stream.of(Utility.toBigDecimal((Number) value).toPlainString()));
            }

            @Override
            @OnThread(Tag.Simulation)
            public Column text() throws InternalException, UserException
            {
                return new MemoryStringColumn(rs, name, Collections.singletonList((String)value), "");
            }

            @Override
            @OnThread(Tag.Simulation)
            public Column date(DateTimeInfo dateTimeInfo) throws InternalException, UserException
            {
                return new MemoryTemporalColumn(rs, name, dateTimeInfo, Collections.singletonList((Temporal)value), DateTimeInfo.DEFAULT_VALUE);
            }

            @Override
            @OnThread(Tag.Simulation)
            public Column bool() throws InternalException, UserException
            {
                return new MemoryBooleanColumn(rs, name, Collections.singletonList((Boolean) value), false);
            }

            @Override
            @OnThread(Tag.Simulation)
            public Column tagged(TypeId typeName, ImmutableList<TagType<DataType>> tags) throws InternalException, UserException
            {
                return new MemoryTaggedColumn(rs, name, typeName, tags, Collections.singletonList((TaggedValue) value), (TaggedValue)makeValue(type));
            }

            @Override
            @OnThread(Tag.Simulation)
            public Column tuple(ImmutableList<DataType> inner) throws InternalException, UserException
            {
                return new MemoryTupleColumn(rs, name, inner, Collections.singletonList((Object[])value), (Object[])makeValue(type));
            }

            @Override
            @OnThread(Tag.Simulation)
            public Column array(@Nullable DataType inner) throws InternalException, UserException
            {
                return new MemoryArrayColumn(rs, name, inner, Collections.singletonList((ListEx)value), new ListExList(Collections.emptyList()));
            }
        }));
        return new ColumnReference(name, ColumnReferenceType.CORRESPONDING_ROW);
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

    @OnThread(Tag.Simulation)
    private Expression makeMatch(int maxLevels, ExSupplier<Expression> makeCorrectOutcome, ExSupplier<Expression> makeOtherOutcome) throws InternalException, UserException
    {
        DataType t = makeType(r);
        @Value Object actual = makeValue(t);
        // Make a bunch of guards which won't fire:
        List<Function<MatchExpression, MatchClause>> clauses = new ArrayList<>(TestUtil.makeList(r, 0, 5, (ExSupplier<Optional<Function<MatchExpression, MatchClause>>>)() -> {
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
    @NotNull
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
                    public Pair<Expression, @Nullable Expression> tagged(TypeId typeName, ImmutableList<TagType<DataType>> tagTypes) throws InternalException, UserException
                    {
                        TagType<DataType> tagType = tagTypes.get(p.getTagIndex());
                        @Nullable DataType inner = tagType.getInner();
                        if (inner == null)
                            return new Pair<>(new TagExpression(Either.right(new TagInfo(t, p.getTagIndex(), tagType)), null), null);
                        @Nullable Object innerValue = p.getInner();
                        if (innerValue == null)
                            throw new InternalException("Type says inner value but is null");
                        Pair<Expression, @Nullable Expression> subPattern = makePatternMatch(maxLevels, inner, innerValue);
                        return new Pair<>(new TagExpression(Either.right(new TagInfo(t, p.getTagIndex(), tagType)), subPattern.getFirst()), subPattern.getSecond());
                    }
                });

            }
            else if (r.nextBoolean()) // Do equals but using variable + guard
            {
                String varName = "var" + nextVar++;
                return new Pair<>(new VarDeclExpression(varName), new EqualExpression(new VarUseExpression(varName), make(t, actual, maxLevels)));
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
}
