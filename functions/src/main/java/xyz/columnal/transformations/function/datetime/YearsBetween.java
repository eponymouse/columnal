package xyz.columnal.transformations.function.datetime;

import annotation.qual.Value;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.DataTypeUtility;
import xyz.columnal.data.datatype.TypeManager;
import xyz.columnal.data.unit.Unit;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.transformations.expression.function.ValueFunction;
import xyz.columnal.transformations.function.FunctionDefinition;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.Either;
import xyz.columnal.utility.SimulationFunction;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalField;

public class YearsBetween extends FunctionDefinition
{
    public YearsBetween() throws InternalException
    {
        super("datetime:years between");
    }

    @Override
    public @OnThread(Tag.Simulation) ValueFunction getInstance(TypeManager typeManager, SimulationFunction<String, Either<Unit, DataType>> paramTypes) throws InternalException, UserException
    {
        return new ValueFunction()
        {
            @Override
            public @OnThread(Tag.Simulation) @Value Object _call() throws InternalException, UserException
            {
                @Value Temporal lhs = arg(0, Temporal.class);
                @Value Temporal rhs = arg(1, Temporal.class);
                long wholeYears = ChronoUnit.YEARS.between(lhs, rhs);
                LocalDate lhsMDInRhsYear = LocalDate.of(rhs.get(ChronoField.YEAR), lhs.get(ChronoField.MONTH_OF_YEAR), lhs.get(ChronoField.DAY_OF_MONTH));
                LocalDate lhsMDInRhsYearPlusOne = LocalDate.of(rhs.get(ChronoField.YEAR) + 1, lhs.get(ChronoField.MONTH_OF_YEAR), lhs.get(ChronoField.DAY_OF_MONTH));
                long daysBetween = ChronoUnit.DAYS.between(lhsMDInRhsYear, rhs);
                long daysThatYear = ChronoUnit.DAYS.between(lhsMDInRhsYear, lhsMDInRhsYearPlusOne);
                BigDecimal r = new BigDecimal(daysBetween).divide(new BigDecimal(daysThatYear), MathContext.DECIMAL128).add(new BigDecimal(wholeYears), MathContext.DECIMAL128);
                return DataTypeUtility.value(r);
            }
        };
    }
}
