package test.gen.backwards;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeUtility;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.expression.CallExpression;
import records.transformations.function.FunctionList;

import java.util.List;

public class BackwardsTuple extends BackwardsProvider
{
    public BackwardsTuple(SourceOfRandomness r, RequestBackwardsExpression parent)
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
            int tupleSize = r.nextInt(2, 9);
            int elementIndex = r.nextInt(0, tupleSize - 1);

            ImmutableList.Builder<DataType> elementTypes = ImmutableList.builderWithExpectedSize(tupleSize);
            ImmutableList.Builder<@Value Object> tupleValue = ImmutableList.builderWithExpectedSize(tupleSize);
            for (int i = 0; i < tupleSize; i++)
            {
                if (i == elementIndex)
                {
                    elementTypes.add(targetType);
                    tupleValue.add(targetValue);
                }
                else
                {
                    DataType type = parent.makeType();
                    elementTypes.add(type);
                    tupleValue.add(parent.makeValue(type));
                }
            }

            DataType tupleType = DataType.tuple(elementTypes.build());
            
            String accessor = ImmutableList.of(
                "first", "second", "third", "fourth", "fifth", "sixth", "seventh", "eighth", "ninth"
            ).get(elementIndex);

            return new CallExpression(FunctionList.getFunctionLookup(parent.getTypeManager().getUnitManager()), accessor, parent.make(tupleType, DataTypeUtility.value(tupleValue.build().toArray(new @Value Object[0])), maxLevels - 1));
        });
    }
}
