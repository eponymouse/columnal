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
import xyz.columnal.data.Column;
import xyz.columnal.id.ColumnId;
import xyz.columnal.data.KnownLengthRecordSet;
import xyz.columnal.data.MemoryBooleanColumn;
import xyz.columnal.data.RecordSet;
import xyz.columnal.id.TableId;
import xyz.columnal.data.datatype.TypeManager;
import test.gen.backwards.*;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.transformations.expression.Expression;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.function.simulation.SimulationFunction;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

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
public class GenExpressionValueBackwards extends GenExpressionValueBase implements RequestBackwardsExpression
{

    @SuppressWarnings("initialization")
    public GenExpressionValueBackwards()
    {
    }

    private BackwardsColumnRef columnProvider;
    private ImmutableList<BackwardsProvider> providers;

    @Override
    @OnThread(value = Tag.Simulation, ignoreParent = true)
    public ExpressionValue generate()
    {
        this.numberOnlyInt = true;
        columnProvider = new BackwardsColumnRef(r, this);
        providers = ImmutableList.of(
            columnProvider,
            new BackwardsBooleans(r, this),
            new BackwardsFixType(r, this),
            new BackwardsFunction(r, this),
            new BackwardsFromText(r, this),
            new BackwardsLiteral(r, this),
            new BackwardsMatch(r, this),
            new BackwardsNumbers(r, this),
            new BackwardsTemporal(r, this),
            new BackwardsText(r, this),
            new BackwardsRecord(r, this)
        );
        try
        {
            DataType type = makeType(r);
            Pair<@Value Object, Expression> p = makeOfType(type);
            return new ExpressionValue(type, Collections.singletonList(p.getFirst()), getTypeManager(), new TableId("Backwards"), getRecordSet(), p.getSecond(), this);
        }
        catch (InternalException | UserException e)
        {
            throw new RuntimeException(e);
        }
    }

    @Override
    public TypeManager getTypeManager()
    {
        return dummyManager.getTypeManager();
    }

    @OnThread(Tag.Simulation)
    public KnownLengthRecordSet getRecordSet() throws InternalException, UserException
    {
        List<SimulationFunction<RecordSet, Column>> columns = columnProvider.getColumns();
        if (columns.isEmpty())
            columns = ImmutableList.of(rs -> new MemoryBooleanColumn(rs, new ColumnId("Column to avoid being empty"), ImmutableList.of(Either.right(true)), true));
        return new KnownLengthRecordSet(columns, 1);
    }

    // Only valid after calling generate
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
        ImmutableList.Builder<ExpressionMaker> terminals = ImmutableList.builder();
        ImmutableList.Builder<ExpressionMaker> deep = ImmutableList.builder();
        for (BackwardsProvider provider : providers)
        {
            terminals.addAll(provider.terminals(type, targetValue));
            deep.addAll(provider.deep(maxLevels, type, targetValue));
        }

        return register(termDeep(maxLevels, type, terminals.build(), deep.build()), type, targetValue);
    }
    
    private List<ExpressionMaker> l(ExpressionMaker... suppliers)
    {
        return Arrays.asList(suppliers);
    }

    @OnThread(Tag.Simulation)
    private Expression termDeep(int maxLevels, DataType type, List<ExpressionMaker> terminals, List<ExpressionMaker> deeper) throws UserException, InternalException
    {
        if (!terminals.isEmpty() && (maxLevels <= 1 || deeper.isEmpty() || r.nextInt(0, 2) == 0))
            return r.choose(terminals).make();
        else
            return r.choose(deeper).make();
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
