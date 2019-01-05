package test.gen.backwards;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import records.data.datatype.DataType;
import records.data.datatype.TypeManager;
import records.error.InternalException;
import records.error.UserException;
import records.jellytype.JellyType;
import records.transformations.expression.TypeLiteralExpression;
import test.DummyManager;

import java.util.List;

@SuppressWarnings("recorded")
public class BackwardsFixType extends BackwardsProvider
{
    public BackwardsFixType(SourceOfRandomness r, RequestBackwardsExpression parent)
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
            TypeManager m = parent.getTypeManager();
            return TypeLiteralExpression.fixType(m, JellyType.fromConcrete(targetType), parent.make(targetType, targetValue, maxLevels - 1));
        });
    }
}
