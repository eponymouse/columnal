package test.gen.backwards;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import records.data.datatype.DataType;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.expression.Expression;
import records.transformations.expression.StringConcatExpression;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BackwardsText extends BackwardsProvider
{
    public BackwardsText(SourceOfRandomness r, RequestBackwardsExpression parent)
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
            int numOperands = r.nextInt(2, 5);
            List<Integer> breakpoints = new ArrayList<>();
            int[] codepoints = ((String)targetValue).codePoints().toArray();
            for (int i = 0; i < numOperands - 1; i++)
            {
                breakpoints.add(r.nextInt(0, codepoints.length));
            }
            Collections.sort(breakpoints);
            //Simplify following code by adding start and end index to list:
            breakpoints.add(0, 0);
            breakpoints.add(codepoints.length);
            List<Expression> operands = new ArrayList<>();
            for (int i = 0; i < numOperands; i++)
            {
                Expression e = parent.make(DataType.TEXT, new String(codepoints, breakpoints.get(i), breakpoints.get(i + 1) - breakpoints.get(i)), maxLevels - 1);
                operands.add(e);
            }
            return new StringConcatExpression(operands);
        });
    }
}
