package test.gen;

import records.data.RecordSet;
import records.data.datatype.DataType;
import records.transformations.expression.Expression;

import java.util.List;

/**
 * Created by neil on 12/12/2016.
 */
public class ExpressionValue
{
    public final DataType type;
    public final Object value;
    public final RecordSet recordSet;
    public final Expression expression;

    public ExpressionValue(DataType type, Object value, RecordSet recordSet, Expression expression)
    {
        this.type = type;
        this.value = value;
        this.recordSet = recordSet;
        this.expression = expression;
    }
}
