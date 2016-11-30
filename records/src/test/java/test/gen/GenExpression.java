package test.gen;

import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import records.transformations.expression.BinaryOpExpression;
import records.transformations.expression.ColumnReference;
import records.transformations.expression.Expression;
import records.transformations.expression.NotEqualExpression;
import records.transformations.expression.NumericLiteral;
import test.TestUtil;

/**
 * Created by neil on 27/11/2016.
 */
public class GenExpression extends Generator<Expression>
{
    public GenExpression()
    {
        super(Expression.class);
    }


    @Override
    public Expression generate(SourceOfRandomness sourceOfRandomness, GenerationStatus generationStatus)
    {
        return genDepth(sourceOfRandomness, 0);
    }

    private Expression genDepth(SourceOfRandomness r, int depth)
    {
        // Make terminals more likely as we get further down:
        if (r.nextInt(0, 3 - depth) == 0)
        {
            // Terminal:
            if (r.nextBoolean())
                return new NumericLiteral(r.nextBigInteger(160));
            else
                return new ColumnReference(TestUtil.generateColumnId(r));
        }
        else
        {
            // Non-terminal:
            return new NotEqualExpression(genDepth(r, depth + 1), genDepth(r, depth + 1));
        }
    }
}
