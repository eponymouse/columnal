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

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import org.checkerframework.checker.nullness.qual.Nullable;
import test.functions.TFunctionUtil;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.DataTypeUtility;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.transformations.expression.Expression;
import xyz.columnal.transformations.function.FunctionList;
import test.DummyManager;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Pair;

import java.math.BigDecimal;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.stream.Stream;

public abstract class GenExpressionValueBase extends GenValueBaseE<ExpressionValue>
{
    private final IdentityHashMap<Expression, Pair<DataType, @Value Object>> expressionValues = new IdentityHashMap<>();
    protected DummyManager dummyManager;
    private List<DataType> distinctTypes;

    @SuppressWarnings("initialization")
    protected GenExpressionValueBase()
    {
        super(ExpressionValue.class);
    }

    @Override
    @OnThread(value = Tag.Simulation, ignoreParent = true)
    public final ExpressionValue generate(SourceOfRandomness r, GenerationStatus generationStatus)
    {
        this.r = r;
        this.gs = generationStatus;
        Pair<DummyManager, List<DataType>> p = TFunctionUtil.managerWithTestTypes();
        dummyManager = p.getFirst();
        distinctTypes = p.getSecond();
        expressionValues.clear();
        return generate();
    }

    public final DataType makeType(SourceOfRandomness r)
    {
        return r.choose(distinctTypes);
    }

    @OnThread(value = Tag.Simulation, ignoreParent = true)
    protected abstract ExpressionValue generate();
    
    public final Expression register(Expression expression, DataType dataType, @Value Object value)
    {
        expressionValues.put(expression, new Pair<DataType, @Value Object>(dataType, value));
        return expression;
    }

    @Override
    public boolean canShrink(Object larger)
    {
        return larger instanceof ExpressionValue && ((ExpressionValue)larger).generator == this;
    }

    @Override
    @OnThread(value = Tag.Simulation, ignoreParent = true)
    public List<ExpressionValue> doShrink(SourceOfRandomness random, ExpressionValue larger)
    {
        return expressionValues.entrySet().stream().flatMap((Entry<Expression, Pair<DataType, @Value Object>> e) -> {
            Expression replacement = makeLiteral(e.getValue().getFirst(), e.getValue().getSecond());
            if (replacement == null)
                return Stream.of();
            Expression after = larger.expression.replaceSubExpression(e.getKey(), replacement);
            if (after.equals(larger.expression))
                return Stream.of(); // No longer within
            return Stream.of(larger.withExpression(after));
        }).collect(ImmutableList.toImmutableList());
    }

    @Override
    public BigDecimal magnitude(Object value)
    {
        if (value instanceof ExpressionValue)
        {
            return BigDecimal.valueOf(((ExpressionValue)value).expression.toString().length());
        }
        return super.magnitude(value);
    }

    @OnThread(Tag.Simulation)
    private @Nullable Expression makeLiteral(DataType dataType, @Value Object object)
    {
        try
        {
            return TFunctionUtil.parseExpression(DataTypeUtility.valueToString(object, dataType, false, null), dummyManager.getTypeManager(), FunctionList.getFunctionLookup(dummyManager.getUnitManager()));
        }
        catch (InternalException | UserException e)
        {
            return null;
        }
    }
}
