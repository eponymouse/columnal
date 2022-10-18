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

package test.gen.backwards;

import annotation.identifier.qual.ExpressionIdentifier;
import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import xyz.columnal.data.TBasicUtil;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.transformations.expression.FieldAccessExpression;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.Utility.RecordMap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

public class BackwardsRecord extends BackwardsProvider
{
    public BackwardsRecord(SourceOfRandomness r, RequestBackwardsExpression parent)
    {
        super(r, parent);
    }

    @Override
    public List<ExpressionMaker> terminals(DataType targetType, @Value Object targetValue) throws InternalException, UserException
    {
        return ImmutableList.of();
    }

    @Override
    public List<ExpressionMaker> deep(int maxLevels, DataType targetType, @Value Object targetValue) throws InternalException, UserException
    {
        return ImmutableList.of(() -> {
            // Make a record then access its field:
            ArrayList<Pair<@ExpressionIdentifier String, DataType>> fields = new ArrayList<>();
            @ExpressionIdentifier String ourField = TBasicUtil.generateExpressionIdentifier(r);
            fields.add(new Pair<>(ourField, targetType));
            // Add a few more:
            fields.addAll(TBasicUtil.<Pair<@ExpressionIdentifier String, DataType>>makeList(r, 1, 3, () -> new Pair<>(TBasicUtil.generateExpressionIdentifier(r), parent.makeType())));

            ImmutableMap<@ExpressionIdentifier String, DataType> fieldMap = fields.stream().collect(ImmutableMap.toImmutableMap(p -> p.getFirst(), p -> p.getSecond(), (a, b) -> a));
            DataType recordType = DataType.record(fieldMap);
            
            HashMap<@ExpressionIdentifier String, @Value Object> valueMap = new HashMap<>();
            valueMap.put(ourField, targetValue);

            for (Entry<@ExpressionIdentifier String, DataType> f : fieldMap.entrySet())
            {
                if (!f.getKey().equals(ourField))
                    valueMap.put(f.getKey(), parent.makeValue(f.getValue()));
            }
            
            return new FieldAccessExpression(parent.make(recordType, new RecordMap(valueMap), maxLevels - 1), ourField);
        });
    }
}
