package test.gen;

import annotation.qual.Value;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.RecordSet;
import records.data.TableId;
import records.data.datatype.DataType;
import records.data.datatype.TypeManager;
import records.transformations.expression.Expression;
import records.transformations.expression.Expression.ColumnLookup;
import test.TestUtil.SingleTableLookup;

import java.util.List;

/**
 * A tuple of: original data, new-expression, new-type and expected-new-value for a record set + calculate-new-column
 */
public class ExpressionValue extends SingleTableLookup implements ColumnLookup
{
    public final DataType type;
    // Number of values  in list will equal number of rows
    // in recordSet.  Will be 1 for GenBackwards and N for GenForwards.
    public final List<@Value Object> value;
    public final TableId tableId;
    public final RecordSet recordSet;
    public final Expression expression;
    public final TypeManager typeManager;
    public final @Nullable GenExpressionValueBase generator;

    public ExpressionValue(DataType type, List<@Value Object> value, TypeManager typeManager, TableId tableId, RecordSet recordSet, Expression expression, @Nullable GenExpressionValueBase generator)
    {
        super(tableId, recordSet);
        this.tableId = tableId;
        this.type = type;
        this.value = value;
        this.typeManager = typeManager;
        this.recordSet = recordSet;
        this.expression = expression;
        this.generator = generator;
    }
    
    @Override
    public String toString()
    {
        return "Type: " + type + " Expression: " + expression;
    }

    public ExpressionValue withExpression(Expression replacement)
    {
        return new ExpressionValue(type, value, typeManager, tableId, recordSet, replacement, generator);
    }
    
    public int getExpressionLength()
    {
        return expression.toString().length();
    }
}
