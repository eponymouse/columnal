package records.transformations;

import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.TableId;
import records.transformations.expression.Expression;
import utility.Utility;

import java.util.stream.Stream;

// package-visible
class TransformationUtil
{
    static Stream<TableId> tablesFromExpression(Expression expression)
    {
        return tablesFromExpressions(Stream.of(expression));
    }

    static Stream<TableId> tablesFromExpressions(Stream<Expression> expressions)
    {
        return Utility.filterOutNulls(expressions.flatMap(e -> e.allColumnReferences()).<@Nullable TableId>map(r -> r.getTableId()));
    }
}
