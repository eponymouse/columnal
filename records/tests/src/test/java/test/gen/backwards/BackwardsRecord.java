package test.gen.backwards;

import annotation.identifier.qual.ExpressionIdentifier;
import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import records.data.datatype.DataType;
import records.data.datatype.DataType.FlatDataTypeVisitor;
import records.data.datatype.DataTypeUtility;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.expression.CallExpression;
import records.transformations.expression.Expression;
import records.transformations.expression.RecordExpression;
import records.transformations.function.FunctionList;
import utility.Pair;
import utility.Utility;
import utility.Utility.Record;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;
import java.util.Random;

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
        return ImmutableList.of();
    }
}
