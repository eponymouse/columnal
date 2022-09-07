package test.gen.backwards;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.DataTypeUtility;
import xyz.columnal.data.datatype.TypeManager;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import records.transformations.expression.CallExpression;
import records.transformations.expression.StringLiteral;
import records.transformations.expression.TypeLiteralExpression;
import records.transformations.expression.type.TypeExpression;
import records.transformations.function.FunctionList;
import test.TestUtil;

import java.util.List;

@SuppressWarnings("recorded")
public class BackwardsFromText extends BackwardsProvider
{
    public BackwardsFromText(SourceOfRandomness r, RequestBackwardsExpression parent)
    {
        super(r, parent);
    }

    @Override
    public List<ExpressionMaker> terminals(DataType targetType, @Value Object targetValue) throws InternalException, UserException
    {
        String val = DataTypeUtility.valueToString(targetValue);
        return ImmutableList.of(
            () -> new CallExpression(FunctionList.getFunctionLookup(parent.getTypeManager().getUnitManager()), "from text to",
                new TypeLiteralExpression(TypeExpression.fromDataType(targetType)),
                TestUtil.makeStringLiteral(val, r)
            )
        );
    }

    @Override
    public List<ExpressionMaker> deep(int maxLevels, DataType targetType, @Value Object targetValue) throws InternalException, UserException
    {        
        String val = DataTypeUtility.valueToString(targetValue);
        return ImmutableList.of(
                () -> new CallExpression(FunctionList.getFunctionLookup(parent.getTypeManager().getUnitManager()), "from text to",
                        new TypeLiteralExpression(TypeExpression.fromDataType(targetType)),
                        parent.make(DataType.TEXT, val, maxLevels - 1)
                )
        );
    }
}
