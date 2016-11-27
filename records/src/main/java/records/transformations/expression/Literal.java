package records.transformations.expression;

import records.data.ColumnId;

import java.util.Collections;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Created by neil on 27/11/2016.
 */
public abstract class Literal extends Expression
{
    @Override
    public Stream<ColumnId> allColumnNames()
    {
        return Stream.empty();
    }
}
