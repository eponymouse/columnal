package test.gen;

import annotation.qual.Value;
import records.data.RecordSet;
import records.data.datatype.DataType;
import records.data.datatype.TypeManager;
import records.transformations.expression.Expression;

import java.util.List;

/**
 * Created by neil on 12/12/2016.
 */
public class ExpressionValue
{
    public final DataType type;
    // Should be match between number of values and number of column entries
    // in recordSet.  Will be 1 for GenBackwards and N for GenForwards.
    public final List<@Value Object> value;
    public final RecordSet recordSet;
    public final Expression expression;
    public final TypeManager typeManager;

    public ExpressionValue(DataType type, List<@Value Object> value, TypeManager typeManager, RecordSet recordSet, Expression expression)
    {
        this.type = type;
        this.value = value;
        this.typeManager = typeManager;
        this.recordSet = recordSet;
        this.expression = expression;
    }

    @Override
    public String toString()
    {
        return "Type: " + type + " Expression: " + expression;
    }
}
