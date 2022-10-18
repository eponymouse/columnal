/*
 * Columnal: Safer, smoother data table processing.
 * Copyright (c) Neil Brown, 2016-2020, 2022.
 *
 * This file is part of Columnal.
 *
 * Columnal is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 *
 * Columnal is distributed in the hope that it will be useful, but WITHOUT 
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or 
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for 
 * more details.
 *
 * You should have received a copy of the GNU General Public License along 
 * with Columnal. If not, see <https://www.gnu.org/licenses/>.
 */

package test.gen;

import annotation.identifier.qual.ExpressionIdentifier;
import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import test.functions.TFunctionUtil;
import xyz.columnal.data.Column;
import xyz.columnal.data.ColumnUtility;
import xyz.columnal.id.ColumnId;
import xyz.columnal.data.TBasicUtil;
import xyz.columnal.data.KnownLengthRecordSet;
import xyz.columnal.data.MemoryBooleanColumn;
import xyz.columnal.data.RecordSet;
import xyz.columnal.id.TableId;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.DataType.DataTypeVisitor;
import xyz.columnal.data.datatype.DataType.DateTimeInfo;
import xyz.columnal.data.datatype.DataType.DateTimeInfo.DateTimeType;
import xyz.columnal.data.datatype.DataType.FlatDataTypeVisitor;
import xyz.columnal.data.datatype.DataType.TagType;
import xyz.columnal.data.datatype.DataTypeUtility;
import xyz.columnal.data.datatype.NumberInfo;
import xyz.columnal.data.datatype.TaggedTypeDefinition;
import xyz.columnal.data.datatype.TypeId;
import xyz.columnal.data.datatype.TypeManager;
import xyz.columnal.data.datatype.TypeManager.TagInfo;
import xyz.columnal.data.unit.Unit;
import xyz.columnal.data.unit.UnitManager;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.jellytype.JellyType;
import xyz.columnal.transformations.expression.AddSubtractExpression;
import xyz.columnal.transformations.expression.AddSubtractExpression.AddSubtractOp;
import xyz.columnal.transformations.expression.AndExpression;
import xyz.columnal.transformations.expression.ArrayExpression;
import xyz.columnal.transformations.expression.BooleanLiteral;
import xyz.columnal.transformations.expression.CallExpression;
import xyz.columnal.transformations.expression.ComparisonExpression;
import xyz.columnal.transformations.expression.ComparisonExpression.ComparisonOperator;
import xyz.columnal.transformations.expression.DivideExpression;
import xyz.columnal.transformations.expression.EqualExpression;
import xyz.columnal.transformations.expression.Expression;
import xyz.columnal.transformations.expression.FieldAccessExpression;
import xyz.columnal.transformations.expression.IdentExpression;
import xyz.columnal.transformations.expression.IfThenElseExpression;
import xyz.columnal.transformations.expression.NotEqualExpression;
import xyz.columnal.transformations.expression.NumericLiteral;
import xyz.columnal.transformations.expression.OrExpression;
import xyz.columnal.transformations.expression.RecordExpression;
import xyz.columnal.transformations.expression.StringConcatExpression;
import xyz.columnal.transformations.expression.StringLiteral;
import xyz.columnal.transformations.expression.TemporalLiteral;
import xyz.columnal.transformations.expression.TimesExpression;
import xyz.columnal.transformations.expression.TypeLiteralExpression;
import xyz.columnal.transformations.expression.function.FunctionLookup;
import xyz.columnal.transformations.expression.type.TypePrimitiveLiteral;
import xyz.columnal.transformations.function.FunctionList;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.function.ExFunction;
import xyz.columnal.utility.function.ExSupplier;
import xyz.columnal.utility.IdentifierUtility;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.function.simulation.SimulationFunction;
import xyz.columnal.utility.TaggedValue;
import xyz.columnal.utility.Utility;
import xyz.columnal.utility.Utility.ListExList;
import xyz.columnal.utility.Utility.RecordMap;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Random;
import java.util.TreeSet;

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
@SuppressWarnings("recorded")
@OnThread(Tag.Simulation)
public class GenExpressionValueForwards extends GenExpressionValueBase
{
    @SuppressWarnings("initialization")
    public GenExpressionValueForwards()
    {
    }

    private TableId tableId = new TableId("Gen Forwards Table");
    // Easier than passing parameters around:
    private List<SimulationFunction<RecordSet, Column>> columns;
    // The length of the column we are generating:
    private int targetSize;

    @Override
    @OnThread(value = Tag.Simulation, ignoreParent = true)
    public ExpressionValue generate()
    {
        this.columns = new ArrayList<>();
        this.targetSize = r.nextInt(1, 100);
        try
        {
            DataType type = makeType(r);
            Pair<List<@Value Object>, Expression> p = makeOfType(type);
            return new ExpressionValue(type, p.getFirst(), dummyManager.getTypeManager(), tableId, getRecordSet(), p.getSecond(), this);
        }
        catch (InternalException | UserException e)
        {
            throw new RuntimeException(e);
        }
    }

    @OnThread(Tag.Simulation)
    public KnownLengthRecordSet getRecordSet() throws InternalException, UserException
    {
        List<SimulationFunction<RecordSet, Column>> columns = this.columns;
        if (columns.isEmpty())
            columns = ImmutableList.of(rs -> new MemoryBooleanColumn(rs, new ColumnId("Column to avoid being empty"), Utility.replicate(targetSize, Either.right(true)), true));
        return new KnownLengthRecordSet(columns, targetSize);
    }

    // Only valid after calling generate
    @OnThread(value = Tag.Simulation, ignoreParent = true)
    public Pair<List<@Value Object>, Expression> makeOfType(DataType type) throws UserException, InternalException
    {
        return make(type, 4);
    }

    private Pair<List<@Value Object>, Expression> make(DataType type, int maxLevels) throws UserException, InternalException
    {
        return type.apply(new DataTypeVisitor<Pair<List<@Value Object>, Expression>>()
        {
            @Override
            @OnThread(value = Tag.Simulation,ignoreParent = true)
            public Pair<List<@Value Object>, Expression> number(NumberInfo displayInfo) throws InternalException, UserException
            {
                return termDeep(maxLevels, type, l(
                    columnRef(type),
                    () ->
                    {
                        @Value Number number = TBasicUtil.generateNumberV(r, gs);
                        return literal(number, new NumericLiteral(number, makeUnitExpression(displayInfo.getUnit())));
                    }
                ), l(fix(maxLevels - 1, type), () -> {
                    int numArgs = r.nextInt(2, 6);
                    List<Expression> expressions = new ArrayList<>();
                    List<AddSubtractOp> addSubtractOps = new ArrayList<>();
                    List<@Value Number> curTotals = replicate(DataTypeUtility.value(0));
                    for (int i = 0; i < numArgs; i++)
                    {
                        Pair<List<@Value Object>, Expression> pair = make(type, maxLevels - 1);
                        expressions.add(pair.getSecond());
                        // First one we count as add, because we're adding to the zero running total:
                        boolean opIsAdd = i == 0 || r.nextBoolean();
                        eachI(curTotals, (row, curTotal) -> Utility.addSubtractNumbers(curTotal, Utility.cast(pair.getFirst().get(row), Number.class), opIsAdd));
                        if (i > 0)
                            addSubtractOps.add(opIsAdd ? AddSubtractOp.ADD : AddSubtractOp.SUBTRACT);
                    }
                    return num(curTotals, new AddSubtractExpression(expressions, addSubtractOps));
                }, () -> {
                    // Either just use target unit, or make up crazy one
                    Unit numUnit = r.nextBoolean() ? displayInfo.getUnit() : makeUnit();
                    Unit denomUnit = calculateRequiredMultiplyUnit(numUnit, displayInfo.getUnit()).reciprocal();
                    Pair<List<@Value Object>, Expression> numerator = make(DataType.number(new NumberInfo(numUnit)), maxLevels - 1);
                    Pair<List<@Value Object>, Expression> denominator = make(DataType.number(new NumberInfo(denomUnit)), maxLevels - 1);
                    // We avoid divide by zeros and return -7 in that case (arbitrary pick unlikely to come up by accident/bug):
                    return map2(numerator, denominator, (top, bottom) -> Utility.compareValues(bottom, DataTypeUtility.value(0)) == 0 ? DataTypeUtility.value(-7) : Utility.divideNumbers((Number) top, (Number) bottom), (topE, bottomE) -> IfThenElseExpression.unrecorded(new EqualExpression(ImmutableList.of(bottomE, new NumericLiteral(0, makeUnitExpression(denomUnit))), false), new NumericLiteral(-7, makeUnitExpression(displayInfo.getUnit())), new DivideExpression(topE, bottomE)));
                }, /* TODO put RaiseExpression back again
                () ->
                {
                    if (displayInfo.getUnit().equals(Unit.SCALAR))
                    {
                        // Can have any power if it's scalar:
                        Pair<List<@Value Object>, Expression> lhs = make(type, maxLevels - 1);
                        Pair<List<@Value Object>, Expression> rhs = make(type, maxLevels - 1);

                        // Except you can't raise a negative to a non-integer power.  So check for that:

                        if (Utility.compareNumbers(lhs.getFirst(), 0) < 0 && !Utility.isIntegral(rhs.getFirst()))
                        {
                            // Two fixes: apply abs to LHS, or round to RHS.  Latter only suitable if power low
                            //if (r.nextBoolean() || Utility.compareNumbers(rhs.getFirst().get(0), 10) > 0)
                            //{
                                // Apply abs to LHS:
                                lhs = num(Utility.withNumber(lhs.getFirst(), l -> safeAbs(l), BigDecimal::abs), call("abs", lhs.getSecond()));
                            //}
                            //else
                            //{
                                // Apply round to RHS:
                                //rhs = new Pair<>(Collections.singletonList(Utility.withNumber(rhs.getFirst().get(0), x -> x, x -> x, d -> d.setScale(0, BigDecimal.ROUND_UP))), call("round", rhs.getSecond()));
                            //}
                        }

                        for (int attempts = 0; attempts < 50; attempts++)
                        {
                            try
                            {
                                Number value = Utility.raiseNumber((Number) lhs.getFirst(), (Number) rhs.getFirst());
                                return num(value, new RaiseExpression(lhs.getSecond(), rhs.getSecond()));
                            }
                            catch (UserException e)
                            {
                                // Probably trying raising too high, cut it down and go again:
                                rhs = num(Utility.toBigDecimal((Number) rhs.getFirst()).divide(BigDecimal.valueOf(20)), new DivideExpression(rhs.getSecond(), new NumericLiteral(20, null)));
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
                                Pair<List<@Value Object>, Expression> lhs = make(DataType.number(new NumberInfo(lhsUnit, 0)), maxLevels - 1);
                                return num(Utility.raiseNumber((Number) lhs.getFirst(), powers.get(0)), new RaiseExpression(lhs.getSecond(), new NumericLiteral(powers.get(0), null)));
                            }
                            else if (r.nextBoolean())
                            {
                                // Just use 1 as power:
                                Pair<List<@Value Object>, Expression> lhs = make(type, maxLevels - 1);
                                return lhs.replaceSecond(new RaiseExpression(lhs.getSecond(), new NumericLiteral(1, null)));
                            }
                            else
                            {
                                // Make up a power, then root it:
                                int raiseTo = r.nextInt(2, 5);
                                Unit lhsUnit = displayInfo.getUnit().raisedTo(raiseTo);
                                Pair<List<@Value Object>, Expression> lhs = make(DataType.number(new NumberInfo(lhsUnit, 0)), maxLevels - 1);
                                // You can't raise a negative to a non-integer power.  So check for that:
                                if (Utility.compareNumbers(lhs.getFirst(), 0) < 0)
                                {
                                    // Apply abs to LHS:
                                    lhs = num(Utility.withNumber(lhs.getFirst(), l -> safeAbs(l), BigDecimal::abs), call("abs", lhs.getSecond()));
                                }
                                return num(Utility.raiseNumber((Number) lhs.getFirst(), BigDecimal.valueOf(1.0 / raiseTo)), new RaiseExpression(lhs.getSecond(), new DivideExpression(new NumericLiteral(1, null), new NumericLiteral(raiseTo, null))));
                            }
                        }
                        catch (UserException e)
                        {
                            // Might have tried to raise a big decimal too large, in which case forget it:
                            return make(DataType.number(displayInfo), maxLevels - 1);
                        }
                    }
                }, */
                () -> {
                    List<@Value Number> runningTotals = replicate(DataTypeUtility.value(1));
                    Unit runningUnit = Unit.SCALAR;
                    int numArgs = r.nextInt(2, 6);
                    List<Expression> expressions = new ArrayList<>();
                    for (int i = 0; i < numArgs; i++)
                    {
                        Unit unit = i == numArgs - 1 ? calculateRequiredMultiplyUnit(runningUnit, displayInfo.getUnit()) : makeUnit();
                        runningUnit = runningUnit.times(unit);
                        Pair<List<@Value Object>, Expression> pair = make(DataType.number(new NumberInfo(unit)), maxLevels - 1);
                        eachI(runningTotals, (row, runningTotal) -> Utility.multiplyNumbers(runningTotal, Utility.cast(pair.getFirst().get(row), Number.class)));
                        expressions.add(pair.getSecond());
                    }
                    return num(runningTotals, new TimesExpression(expressions));
                }));
            }

            @Override
            @OnThread(value = Tag.Simulation,ignoreParent = true)
            public Pair<List<@Value Object>, Expression> text() throws InternalException, UserException
            {
                return termDeep(maxLevels, type, l(
                    columnRef(type),
                    () ->
                    {
                        @Value String value = TBasicUtil.makeStringV(r, gs);
                        return literal(value, TFunctionUtil.makeStringLiteral(value, r));
                    }
                ), l(fix(maxLevels - 1, type), () -> {
                    int numOperands = r.nextInt(2, 5);
                    List<Expression> operands = new ArrayList<>();
                    List<@Value Object> results = GenExpressionValueForwards.this.<@Value Object>replicate(DataTypeUtility.value(""));
                    for (int i = 0; i < numOperands; i++)
                    {
                        Pair<List<@Value Object>, Expression> item = make(DataType.TEXT, maxLevels - 1);
                        operands.add(item.getSecond());
                        for (int row = 0; row < targetSize; row++)
                        {
                            results.set(row, ((String)results.get(row)) + item.getFirst().get(row));
                        }
                    }
                    return new Pair<>(results, new StringConcatExpression(operands));
                }));
            }

            @Override
            @OnThread(value = Tag.Simulation,ignoreParent = true)
            public Pair<List<@Value Object>, Expression> date(DateTimeInfo dateTimeInfo) throws InternalException, UserException
            {
                List<ExpressionMaker> deep = new ArrayList<>();
                // We don't use the from-integer versions here with deeper expressions because we can't
                // guarantee the values are in range, so we'd likely get an error.
                // Instead we use the narrowing versions or the ones that compose valid values:
                switch(dateTimeInfo.getType())
                {
                    case YEARMONTHDAY:
                        deep.add(() -> {
                            Pair<String, DateTimeType> convertAndType = r.choose(Arrays.asList(
                                new Pair<>("date from datetime", DateTimeType.DATETIME),
                                new Pair<>("date from datetimezoned", DateTimeType.DATETIMEZONED)));
                            Pair<List<@Value Object>, Expression> dateTimes = make(DataType.date(new DateTimeInfo(convertAndType.getSecond())), maxLevels - 1);
                            return map(dateTimes, v -> LocalDate.from((TemporalAccessor) v), e -> call(convertAndType.getFirst(), e));
                        });
                        /*
                        deep.add(() -> {
                            Pair<List<@Value Object>, Expression> dateTime = make(DataType.date(new DateTimeInfo(DateTimeType.YEARMONTH)), maxLevels - 1);
                            int day = r.nextInt(1, 28);
                            return map(dateTime, v ->
                            {
                                YearMonth yearMonth = YearMonth.from((TemporalAccessor) v);
                                return LocalDate.of(yearMonth.getYear(), yearMonth.getMonth(), day);
                            }, e -> call("date.from.ym.day", e, new NumericLiteral(day, makeUnitExpression(getUnit("day")))));
                        });
                        */
                        break;
                    case YEARMONTH:
                        deep.add(() -> {
                            Pair<String, DateTimeType> convertAndType = r.choose(Arrays.asList(
                                new Pair<>("dateym from date", DateTimeType.YEARMONTHDAY)));
                            Pair<List<@Value Object>, Expression> dateTimes = make(DataType.date(new DateTimeInfo(convertAndType.getSecond())), maxLevels - 1);
                            return map(dateTimes, v -> YearMonth.from((TemporalAccessor) v), e -> call(convertAndType.getFirst(), e));
                        });
                        break;
                    case TIMEOFDAY:
                        deep.add(() -> {
                            Pair<String, DateTimeType> convertAndType = r.choose(Arrays.asList(
                                //new Pair<>("time.from.timezoned", DateTimeType.TIMEOFDAYZONED),
                                new Pair<>("time from datetime", DateTimeType.DATETIME), 
                                new Pair<>("time from datetimezoned", DateTimeType.DATETIMEZONED)));
                            Pair<List<@Value Object>, Expression> dateTimes = make(DataType.date(new DateTimeInfo(convertAndType.getSecond())), maxLevels - 1);
                            return map(dateTimes, v -> LocalTime.from((TemporalAccessor) v), e -> call(convertAndType.getFirst(), e));
                        });
                        break;
                    /*
                    case TIMEOFDAYZONED:
                        deep.add(() -> {
                            Pair<List<@Value Object>, Expression> dateTimes = make(DataType.date(new DateTimeInfo(DateTimeType.DATETIMEZONED)), maxLevels - 1);
                            return map(dateTimes, v -> OffsetTime.from((TemporalAccessor) v), e -> call("timezoned.from.datetimezoned", e));
                        });
                        deep.add(() -> {
                            Pair<List<@Value Object>, Expression> dateTimes = make(DataType.date(new DateTimeInfo(DateTimeType.TIMEOFDAY)), maxLevels - 1);
                            ZoneOffset zone = TestUtil.generateZoneOffset(r, gs);
                            return map(dateTimes, v -> OffsetTime.of((LocalTime)v, zone), e -> call("timezoned", e, new StringLiteral(zone.toString())));
                        });
                        break;
                    */
                    case DATETIME:
                        // down cast:
                        deep.add(() -> {
                            Pair<List<@Value Object>, Expression> dateTimes = make(DataType.date(new DateTimeInfo(r.<@NonNull DateTimeType>choose(Arrays.asList(DateTimeType.DATETIMEZONED)))), maxLevels - 1);
                            return map(dateTimes, v -> LocalDateTime.from((TemporalAccessor) v), e -> call("datetime from datetimezoned", e));
                        });
                        // date + time:
                        deep.add(() -> {
                            Pair<List<@Value Object>, Expression> dates = make(DataType.date(new DateTimeInfo(r.<@NonNull DateTimeType>choose(Arrays.asList(DateTimeType.YEARMONTHDAY)))), maxLevels - 1);
                            Pair<List<@Value Object>, Expression> times = make(DataType.date(new DateTimeInfo(r.<@NonNull DateTimeType>choose(Arrays.asList(DateTimeType.TIMEOFDAY)))), maxLevels - 1);
                            return map2(dates, times, (date, time) -> LocalDateTime.of((LocalDate) date, (LocalTime) time), (dateE, timeE) -> call("datetime from dt", dateE, timeE));
                        });
                        break;
                    case DATETIMEZONED:
                        // datetime + zone:
                        /*
                        deep.add(() -> {
                            Pair<List<@Value Object>, Expression> dateTimes = make(DataType.date(new DateTimeInfo(r.<@NonNull DateTimeType>choose(Arrays.asList(DateTimeType.DATETIME)))), maxLevels - 1);
                            ZoneOffset zone = TestUtil.generateZoneOffset(r, gs);
                            return map(dateTimes, v -> ZonedDateTime.of((LocalDateTime)v, zone), e -> call("datetimezoned.from.datetime.zone", e, new StringLiteral(zone.toString())));
                        });
                        */
                        // date+time+zone:
                        deep.add(() -> {
                            Pair<List<@Value Object>, Expression> dates = make(DataType.date(new DateTimeInfo(r.<@NonNull DateTimeType>choose(Arrays.asList(DateTimeType.YEARMONTHDAY)))), maxLevels - 1);
                            Pair<List<@Value Object>, Expression> times = make(DataType.date(new DateTimeInfo(r.<@NonNull DateTimeType>choose(Arrays.asList(DateTimeType.TIMEOFDAY)))), maxLevels - 1);
                            ZoneOffset zone = TBasicUtil.generateZoneOffset(r, gs);
                            return map2(dates, times, (date, time) -> ZonedDateTime.of((LocalDate)date, (LocalTime) time, zone), (dateE, timeE) -> call("datetimezoned from dtz", dateE, timeE, TFunctionUtil.makeStringLiteral(zone.toString(), r)));
                        });
                        break;
                }
                List<ExpressionMaker> shallow = new ArrayList<>();
                shallow.add((ExpressionMaker)() ->
                {
                    @Value TemporalAccessor value = makeTemporalValue(dateTimeInfo);
                    return literal(value, call("from text to", new TypeLiteralExpression(new TypePrimitiveLiteral(DataType.date(dateTimeInfo))), new StringLiteral(value.toString())));
                });
                shallow.add((ExpressionMaker)() ->
                {
                    @Value TemporalAccessor value = makeTemporalValue(dateTimeInfo);
                    return literal(value, new TemporalLiteral(dateTimeInfo.getType(), value.toString()));
                });

                switch (dateTimeInfo.getType())
                {
                    case YEARMONTHDAY:
                        shallow.add(() -> {
                            int year = r.nextInt(1, 9999);
                            int month = r.nextInt(1, 12);
                            int day = r.nextInt(1, 28);
                            return literal(LocalDate.of(year, month, day), call("date from ymd",
                                new NumericLiteral(year, makeUnitExpression(getUnit("year"))),
                                new NumericLiteral(month, makeUnitExpression(getUnit("month"))),
                                new NumericLiteral(day, makeUnitExpression(getUnit("day")))
                            ));
                        });
                        break;
                    case YEARMONTH:
                        shallow.add(() -> {
                            int year = r.nextInt(1, 9999);
                            int month = r.nextInt(1, 12);
                            return literal(YearMonth.of(year, month), call("dateym from ym",
                                new NumericLiteral(year, makeUnitExpression(getUnit("year"))),
                                new NumericLiteral(month, makeUnitExpression(getUnit("month")))
                            ));
                        });
                        break;
                    case TIMEOFDAY:
                        shallow.add(() -> {
                            int hour = r.nextInt(0, 23);
                            int minute = r.nextInt(0, 59);
                            int second = r.nextInt(0, 59);
                            int nano = r.nextInt(0, 999999999);
                            return literal(LocalTime.of(hour, minute, second, nano), call("time from hms",
                                new NumericLiteral(hour, makeUnitExpression(getUnit("hour"))),
                                new NumericLiteral(minute, makeUnitExpression(getUnit("minute"))),
                                new NumericLiteral(BigDecimal.valueOf(nano).divide(BigDecimal.valueOf(1_000_000_000)).add(BigDecimal.valueOf(second)), makeUnitExpression(getUnit("s")))
                            ));
                        });
                }
                shallow.add(columnRef(type));

                return termDeep(maxLevels, type, shallow, deep);
            }

            @Override
            @OnThread(value = Tag.Simulation,ignoreParent = true)
            public Pair<List<@Value Object>, Expression> bool() throws InternalException, UserException
            {
                return termDeep(maxLevels, type, l(columnRef(type), () ->
                {
                    boolean value = r.nextBoolean();
                    return literal(DataTypeUtility.value(value), new BooleanLiteral(value));
                }), l(fix(maxLevels - 1, type),
                    () -> {
                        DataType t = makeType(r);
                        int size = r.nextInt(2, 5);
                        ArrayList<Pair<List<@Value Object>, Expression>> items = new ArrayList<>();
                        List<Boolean> result = replicate(true);
                        for (int i = 0; i < size; i++)
                        {
                            Pair<List<@Value Object>, Expression> item = make(t, maxLevels - 1);
                            items.add(item);
                            eachI(result, (j, v) -> v & Utility.compareValues(items.get(0).getFirst().get(j), item.getFirst().get(j)) == 0);
                        }
                        return new Pair<>(Utility.<Boolean, @Value Object>mapList(result, b -> DataTypeUtility.value(b)), new EqualExpression(Utility.mapList(items, (Pair<List<@Value Object>, Expression> p) -> p.getSecond()), false));
                    },
                    () -> {
                        DataType t = makeType(r);

                        Pair<List<@Value Object>, Expression> lhs = make(t, maxLevels - 1);
                        Pair<List<@Value Object>, Expression> rhs = make(t, maxLevels - 1);
                        return map2(lhs, rhs, (l, r) -> DataTypeUtility.value(Utility.compareValues(l, r) != 0), NotEqualExpression::new);
                    },
                    () ->
                    {
                        DataType t = makeType(r);

                        List<Pair<List<@Value Object>, Expression>> args = TBasicUtil.<Pair<List<@Value Object>, Expression>>makeList(r, 2, 4, () -> make(t, maxLevels - 1));
                        boolean lt = r.nextBoolean();
                        List<ComparisonOperator> ops = new ArrayList<>();
                        List<Boolean> result = replicate(true);
                        for (int argIndex = 0; argIndex < args.size() - 1; argIndex++)
                        {
                            ops.add(r.nextBoolean() ?
                                (lt ? ComparisonOperator.LESS_THAN : ComparisonOperator.GREATER_THAN)
                                : (lt ? ComparisonOperator.LESS_THAN_OR_EQUAL_TO : ComparisonOperator.GREATER_THAN_OR_EQUAL_TO)
                            );
                            List<@Value Object> lhs = args.get(argIndex).getFirst();
                            List<@Value Object> rhs = args.get(argIndex + 1).getFirst();
                            switch (ops.get(ops.size() - 1))
                            {
                                case LESS_THAN:
                                    eachI(result, (i, v) -> v & Utility.compareValues(lhs.get(i), rhs.get(i)) < 0);
                                    break;
                                case LESS_THAN_OR_EQUAL_TO:
                                    eachI(result, (i, v) -> v & Utility.compareValues(lhs.get(i), rhs.get(i)) <= 0);
                                    break;
                                case GREATER_THAN:
                                    eachI(result, (i, v) -> v & Utility.compareValues(lhs.get(i), rhs.get(i)) > 0);
                                    break;
                                case GREATER_THAN_OR_EQUAL_TO:
                                    eachI(result, (i, v) -> v & Utility.compareValues(lhs.get(i), rhs.get(i)) >= 0);
                                    break;
                            }
                        }
                        return new Pair<>(Utility.<Boolean, @Value Object>mapList(result, b -> DataTypeUtility.value(b)), new ComparisonExpression(Utility.mapList(args, (Pair<List<@Value Object>, Expression> p) -> p.getSecond()), ImmutableList.copyOf(ops)));
                    },
                    () -> {
                        int size = r.nextInt(2, 5);
                        boolean and = r.nextBoolean();
                        List<Boolean> values = replicate(and ? true : false);
                        ArrayList<Expression> exps = new ArrayList<Expression>();
                        for (int i = 0; i < size; i++)
                        {
                            Pair<List<@Value Object>, Expression> pair = make(DataType.BOOLEAN, maxLevels - 1);
                            if (and)
                                eachI(values, (vi, v) -> v & (Boolean)pair.getFirst().get(vi));
                            else
                                eachI(values, (vi, v) -> v | (Boolean)pair.getFirst().get(vi));
                            exps.add(pair.getSecond());
                        }
                        return new Pair<>(Utility.<Boolean, @Value Object>mapList(values, b -> DataTypeUtility.value(b)), and ? new AndExpression(exps) : new OrExpression(exps));
                    }
                ));
            }

            @Override
            @OnThread(value = Tag.Simulation,ignoreParent = true)
            public Pair<List<@Value Object>, Expression> tagged(TypeId typeName, ImmutableList<Either<Unit, DataType>> typeVars, ImmutableList<TagType<DataType>> tags) throws InternalException, UserException
            {
                List<ExpressionMaker> terminals = new ArrayList<>();
                List<ExpressionMaker> nonTerm = new ArrayList<>();
                terminals.add(columnRef(type));
                nonTerm.add(fix(maxLevels - 1, type));
                int tagIndex = r.nextInt(0, tags.size() - 1);
                TagType<DataType> tag = tags.get(tagIndex);
                @Nullable TaggedTypeDefinition typeDefinition = dummyManager.getTypeManager().lookupDefinition(typeName);
                if (typeDefinition == null)
                    throw new InternalException("Looked up type but null definition: " + typeName);
                TagInfo tagInfo = new TagInfo(typeDefinition, tagIndex);
                final @Nullable DataType inner = tag.getInner();
                if (inner == null)
                {
                    terminals.add(() -> literal(new TaggedValue(tagIndex, null, DataTypeUtility.fromTags(tags)), TFunctionUtil.tagged(dummyManager.getUnitManager(), tagInfo, null, type, true)));
                }
                else
                {
                    final @NonNull DataType nonNullInner = inner;
                    nonTerm.add(() ->
                    {
                        Pair<List<@Value Object>, Expression> innerVal = make(nonNullInner, maxLevels - 1);
                        return map(innerVal, v -> new TaggedValue(tagIndex, v, DataTypeUtility.fromTags(tags)), e -> TFunctionUtil.tagged(dummyManager.getUnitManager(), tagInfo, e, type, true));
                    });
                }
                return termDeep(maxLevels, type, terminals, nonTerm);
            }

            @Override
            @OnThread(Tag.Simulation)
            public Pair<List<@Value Object>, Expression> record(ImmutableMap<@ExpressionIdentifier String, DataType> fields) throws InternalException, UserException
            {
                if (fields.size() < 1)
                    throw new InternalException("Invalid record type of size " + fields.size() + " during generation");

                return termDeep(maxLevels, type, l(columnRef(type)), l(fix(maxLevels - 1, type), () ->
                {
                    List<HashMap<@ExpressionIdentifier String, @Value Object>> records = GenExpressionValueForwards.this.<HashMap<@ExpressionIdentifier String, @Value Object>>replicateM(() -> new HashMap<>());
                    List<Pair<@ExpressionIdentifier String, Expression>> expressions = new ArrayList<>();
                    for (Entry<@ExpressionIdentifier String, DataType> entry : fields.entrySet())
                    {
                        // We don't reduce max levels as it may make nested tuples
                        // not feature complex expressions, and the finiteness of the type
                        // prevents infinite expansion:
                        Pair<List<@Value Object>, Expression> item = make(entry.getValue(), maxLevels);
                        for (int row = 0; row < records.size(); row++)
                            records.get(row).put(entry.getKey(), item.getFirst().get(row));
                        expressions.add(new Pair<>(entry.getKey(), item.getSecond()));
                    }
                    Collections.shuffle(expressions, new Random(r.nextLong()));
                    
                    return new Pair<>(Utility.<HashMap<@ExpressionIdentifier String, @Value Object>, @Value Object>mapList(records, r -> DataTypeUtility.value(new RecordMap(r))), new RecordExpression(ImmutableList.copyOf(expressions)));
                }));
            }

            @Override
            @OnThread(value = Tag.Simulation,ignoreParent = true)
            public Pair<List<@Value Object>, Expression> array(@Nullable DataType inner) throws InternalException, UserException
            {
                if (inner == null)
                    return literal(DataTypeUtility.value(Collections.emptyList()), new ArrayExpression(ImmutableList.of()));
                @Nullable DataType innerFinal = inner;
                return termDeep(maxLevels, type, l(columnRef(type)), l(fix(maxLevels - 1, type), () ->
                {
                    int length = r.nextInt(1, 12);
                    // Each outer list item is one row in the final set of expression values
                    // Each inner list is an entire array.
                    List<List<@Value Object>> values = GenExpressionValueForwards.this.<List<@Value Object>>replicateM(ArrayList<@Value Object>::new);
                    List<Expression> expressions = new ArrayList<>();
                    for (int i = 0; i < length; i++)
                    {
                        Pair<List<@Value Object>, Expression> item = make(innerFinal, maxLevels - 1);
                        // This is the set of items for the i-th item in each array, so add accordingly:
                        for (int j = 0; j < item.getFirst().size() /* aka targetSize */; j++)
                            values.get(j).add(item.getFirst().get(j));
                        expressions.add(item.getSecond());
                    }
                    return new Pair<>(Utility.<List<@Value Object>, @Value Object>mapList(values, DataTypeUtility::value), new ArrayExpression(ImmutableList.copyOf(expressions)));
                }));
            }
        });
    }

    protected @Value TemporalAccessor makeTemporalValue(DateTimeInfo dateTimeInfo)
    {
        @Value TemporalAccessor value;
        switch (dateTimeInfo.getType())
        {
            case YEARMONTHDAY:
                value = TBasicUtil.generateDate(r, gs);
                break;
            case YEARMONTH:
                value = YearMonth.from(TBasicUtil.generateDate(r, gs));
                break;
            case TIMEOFDAY:
                value = TBasicUtil.generateTime(r, gs);
                break;
            //case TIMEOFDAYZONED:
                //@Value OffsetTime timez = OffsetTime.from(TestUtil.generateDateTimeZoned(r, gs));
                //return literal(timez, call("timezoned.from.string", new StringLiteral(timez.toString())));
            case DATETIME:
                value = TBasicUtil.generateDateTime(r, gs);
                break;
            case DATETIMEZONED:
                value = TBasicUtil.generateDateTimeZoned(r, gs);
                break;
            default:
                throw new RuntimeException("No date generator for " + dateTimeInfo.getType());
        }
        return value;
    }

    private Pair<List<@Value Object>, Expression> num(List<@Value Number> values, Expression expression)
    {
        return new Pair<>(Utility.<@Value Number, @Value Object>mapList(values, n -> n), expression);
    }

    private Number safeAbs(Long l)
    {
        return l.longValue() == Long.MIN_VALUE ? BigDecimal.valueOf(l).abs() : Math.abs(l);
    }

    // What unit do you have to multiply src by to get dest?
    private Unit calculateRequiredMultiplyUnit(Unit src, Unit dest)
    {
        // So we have src * x = dest
        // This can be rearranged to x = dest/src
        return dest.divideBy(src);
    }

    private Unit getUnit(String name) throws InternalException, UserException
    {
        UnitManager m = dummyManager.getUnitManager();
        return m.loadUse(name);
    }

    private Unit makeUnit() throws InternalException, UserException
    {
        UnitManager m = dummyManager.getUnitManager();
        return r.<@NonNull Unit>choose(Arrays.asList(
            m.loadUse("m"),
            m.loadUse("cm"),
            m.loadUse("inch"),
            m.loadUse("g"),
            m.loadUse("kg"),
            m.loadUse("deg"),
            m.loadUse("s"),
            m.loadUse("hour"),
            m.loadUse("USD")
        ));
    }

    private BigDecimal genBD()
    {
        return new BigDecimal(new GenNumberAsString().generate(r, gs));
    }

    private ExpressionMaker columnRef(DataType type) throws UserException, InternalException
    {
        ColumnId name = new ColumnId(IdentifierUtility.identNum("GEV Col", columns.size()));
        return () -> {
            @Nullable DataType listInnerType = type.apply(new FlatDataTypeVisitor<@Nullable DataType>(null)
            {
                @Override
                public @Nullable DataType array(DataType inner) throws InternalException, InternalException
                {
                    return inner;
                }
            });
            if (listInnerType != null)
            {
                // Return whole column:
                List<@Value Object> value = GenExpressionValueForwards.this.<@Value Object>replicateM(() -> makeValue(listInnerType));
                columns.add(rs -> ColumnUtility.makeCalculatedColumn(listInnerType, rs, name, i -> value.get(i), t -> t));
                // Each row gets the same full list:
                return new Pair<List<@Value Object>, Expression>(GenExpressionValueForwards.this.<@Value Object>replicateM(() -> new ListExList(value)), IdentExpression.makeEntireColumnReference(tableId, name));
            }
            else
            {
                List<@Value Object> value = GenExpressionValueForwards.this.<@Value Object>replicateM(() -> makeValue(type));
                columns.add(rs -> ColumnUtility.makeCalculatedColumn(type, rs, name, i -> value.get(i), t -> t));

                FunctionLookup functionLookup = FunctionList.getFunctionLookup(dummyManager.getUnitManager());
                // Index into a whole column:
                if (r.nextBoolean())
                {
                    // Use #column name
                    return new Pair<List<@Value Object>, Expression>(value, new CallExpression(functionLookup, "element", IdentExpression.makeEntireColumnReference(tableId, name), IdentExpression.load("row")));
                }
                else
                {
                    // Use #rows
                    return new Pair<List<@Value Object>, Expression>(value, new FieldAccessExpression(new CallExpression(functionLookup, "element", new FieldAccessExpression(IdentExpression.table(tableId.getRaw()), "rows"), IdentExpression.load("row")), name.getRaw()));
                }
            }
        };
    }
    
    private ExpressionMaker fix(int maxLevels, DataType type)
    {
        TypeManager m = dummyManager.getTypeManager();
        return () -> {
            Pair<List<@Value Object>, Expression> inner = make(type, maxLevels);
            return new Pair<>(inner.getFirst(), TypeLiteralExpression.fixType(m, FunctionList.getFunctionLookup(m.getUnitManager()), JellyType.fromConcrete(type), inner.getSecond()));
        };
    }

    @FunctionalInterface
    public interface ExpressionMaker
    {
        @OnThread(Tag.Simulation)
        public Pair<List<@Value Object>, Expression> make() throws InternalException, UserException;
    }

    // Replicates the first argument.  Useful when you have a constant expression
    // which will just be repeated:
    private Pair<List<@Value Object>, Expression> literal(@Value Object o, Expression e)
    {
        return new Pair<>(this.<@Value Object>replicate(o), e);
    }

    // Replicates an item targetSize times into a list:
    private <T> ArrayList<T> replicate(T single)
    {
        ArrayList<T> repeated = new ArrayList<>();
        for (int i = 0; i < targetSize; i++)
        {
            repeated.add(single);
        }
        return repeated;
    }

    // Replicates a generator targetSize times into a list:
    private <T> List<T> replicateM(ExSupplier<T> single) throws InternalException, UserException
    {
        List<T> repeated = new ArrayList<>();
        for (int i = 0; i < targetSize; i++)
        {
            repeated.add(single.get());
        }
        return repeated;
    }

    private static interface ExBiFunction<S, T, R>
    {
        @OnThread(Tag.Simulation)
        public R apply(S s, T t) throws UserException, InternalException;
    }

    // Modifies a list in-place by applying the given function
    private <T> void each(List<T> list, ExFunction<T, T> modify) throws InternalException, UserException
    {
        for (int i = 0; i < list.size(); i++)
        {
            list.set(i, modify.apply(list.get(i)));
        }
    }

    // Like each but supplies index too
    @OnThread(Tag.Simulation)
    private <T> void eachI(List<T> list, ExBiFunction<Integer, T, T> modify) throws InternalException, UserException
    {
        for (int i = 0; i < list.size(); i++)
        {
            list.set(i, modify.apply(i, list.get(i)));
        }
    }

    @OnThread(Tag.Simulation)
    private <T> Pair<List<@Value Object>, Expression> map(Pair<List<@Value Object>, Expression> src, ExFunction<@Value Object, @Value Object> eachValue, ExFunction<Expression, Expression> withExpression) throws InternalException, UserException
    {
        List<@Value Object> list = new ArrayList<>(src.getFirst());
        each(list, eachValue);
        return new Pair<>(list, withExpression.apply(src.getSecond()));
    }

    // zipWith, by another name
    @OnThread(Tag.Simulation)
    private <T> Pair<List<@Value Object>, Expression> map2(Pair<List<@Value Object>, Expression> srcA, Pair<List<@Value Object>, Expression> srcB, ExBiFunction<@Value Object, @Value Object, @Value Object> eachValue, ExBiFunction<Expression, Expression, Expression> withExpression) throws InternalException, UserException
    {
        List<@Value Object> list = new ArrayList<>(srcA.getFirst());
        List<@Value Object> listB = new ArrayList<>(srcB.getFirst());
        for (int i = 0; i < list.size(); i++)
        {
            list.set(i, eachValue.apply(list.get(i), listB.get(i)));
        }
        return new Pair<>(list, withExpression.apply(srcA.getSecond(), srcB.getSecond()));
    }

    private List<ExpressionMaker> l(ExpressionMaker... suppliers)
    {
        return Arrays.asList(suppliers);
    }

    @OnThread(Tag.Simulation)
    private Pair<List<@Value Object>, Expression> termDeep(int maxLevels, DataType type, List<ExpressionMaker> terminals, List<ExpressionMaker> deeper) throws UserException, InternalException
    {
        /*
        if (maxLevels > 1 && r.nextInt(0, 5) == 0)
        {
            return makeMatch(maxLevels - 1, () -> termDeep(maxLevels - 1, type, terminals, deeper), () -> make(type, maxLevels - 1));
        }
        */

        //TODO generate match expressions here (valid for all types)
        final Pair<List<@Value Object>, Expression> valueExp;
        if (deeper.isEmpty() || (!terminals.isEmpty() && (maxLevels <= 1 || deeper.isEmpty() || r.nextInt(0, 2) == 0)))
            valueExp = r.choose(terminals).make();
        else
            valueExp = r.choose(deeper).make();
        // Can only substitute with literal if it has constant value:
        TreeSet<@Value Object> allValues = new TreeSet<@Value Object>((@Value Object a, @Value Object b) -> {
            try
            {
                return Utility.compareValues(a, b);
            }
            catch (InternalException | UserException e)
            {
                throw new RuntimeException(e);
            }
        });
        allValues.addAll(valueExp.getFirst());
        if (allValues.size() == 1)
            register(valueExp.getSecond(), type, allValues.iterator().next());
        return valueExp;
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
    @NonNull
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
                return new Pair<>(me -> me.new PatternMatchVariable(varName), new EqualExpression(new VarUseExpression(varName), make(t, actual, maxLevels)));
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
