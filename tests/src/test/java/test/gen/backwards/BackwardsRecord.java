package test.gen.backwards;

import annotation.identifier.qual.ExpressionIdentifier;
import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import xyz.columnal.data.DataTestUtil;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import records.transformations.expression.FieldAccessExpression;
import test.TestUtil;
import xyz.columnal.utility.Pair;
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
            @ExpressionIdentifier String ourField = DataTestUtil.generateExpressionIdentifier(r);
            fields.add(new Pair<>(ourField, targetType));
            // Add a few more:
            fields.addAll(DataTestUtil.<Pair<@ExpressionIdentifier String, DataType>>makeList(r, 1, 3, () -> new Pair<>(DataTestUtil.generateExpressionIdentifier(r), parent.makeType())));

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
