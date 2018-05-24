package test.gen.backwards;

import records.error.InternalException;
import records.error.UserException;
import records.transformations.expression.Expression;

@FunctionalInterface
public interface ExpressionMaker
{
    public Expression make() throws InternalException, UserException;
}
