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

package xyz.columnal.transformations.function.math;

import annotation.qual.Value;
import ch.obermuhlner.math.big.BigDecimalMath;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.DataTypeUtility;
import xyz.columnal.data.datatype.TypeManager;
import xyz.columnal.data.unit.Unit;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.transformations.function.FunctionDefinition;
import xyz.columnal.transformations.function.ValueFunction2;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.function.simulation.SimulationFunction;
import xyz.columnal.utility.Utility;
import xyz.columnal.transformations.expression.function.ValueFunction;

import java.math.BigDecimal;
import java.math.MathContext;

public class Logarithm extends FunctionDefinition
{
    public Logarithm() throws InternalException
    {
        super("math:log");
    }

    @Override
    public @OnThread(Tag.Simulation) ValueFunction getInstance(TypeManager typeManager, SimulationFunction<String, Either<Unit, DataType>> paramTypes) throws InternalException, UserException
    {
        return new ValueFunction2<Number, Number>(Number.class, Number.class) {

            @Override
            public @OnThread(Tag.Simulation) @Value Object call2(@Value Number x, @Value Number base) throws InternalException, UserException
            {
                try
                {
                    @Value BigDecimal bd = Utility.toBigDecimal(x);
                    final BigDecimal r;
                    if (Utility.compareNumbers(base, 10) == 0)
                        r = BigDecimalMath.log10(bd, MathContext.DECIMAL128);
                    else if (Utility.compareNumbers(base, 2) == 0)
                        r = BigDecimalMath.log2(bd, MathContext.DECIMAL128);
                    else
                        r = BigDecimalMath.log(bd, MathContext.DECIMAL128).divide(BigDecimalMath.log(Utility.toBigDecimal(base), MathContext.DECIMAL128), MathContext.DECIMAL128);
                    return DataTypeUtility.value(r);
                }
                catch (ArithmeticException e)
                {
                    throw new UserException("Error while taking logarithm: " + e.getLocalizedMessage(), e);
                }
            }
        };
    }
}
