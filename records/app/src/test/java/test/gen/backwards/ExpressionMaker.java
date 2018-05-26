package test.gen.backwards;

import records.error.InternalException;
import records.error.UserException;
import records.transformations.expression.Expression;

@FunctionalInterface
public interface ExpressionMaker
{
    public Expression make() throws InternalException, UserException;
    
    public default int getBias()
    {
        return 1;
    }
    
    public default ExpressionMaker withBias(int bias)
    {
        ExpressionMaker orig = this;
        return new ExpressionMaker()
        {
            @Override
            public Expression make() throws InternalException, UserException
            {
                return orig.make();
            }

            @Override
            public int getBias()
            {
                return bias;
            }
        };
    }
}
