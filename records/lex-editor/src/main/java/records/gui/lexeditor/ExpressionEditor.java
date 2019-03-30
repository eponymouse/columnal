package records.gui.lexeditor;

import annotation.recorded.qual.Recorded;
import javafx.beans.binding.ObjectExpression;
import javafx.beans.value.ObservableObjectValue;
import javafx.scene.input.DataFormat;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.Table;
import records.data.datatype.DataType;
import records.data.datatype.TypeManager;
import records.gui.expressioneditor.ExpressionSaver;
import records.transformations.expression.Expression;
import records.transformations.expression.Expression.ColumnLookup;
import records.transformations.expression.InvalidIdentExpression;
import records.transformations.expression.function.FunctionLookup;
import styled.StyledShowable;
import utility.FXPlatformConsumer;
import utility.gui.FXUtility;

public class ExpressionEditor extends TopLevelEditor<Expression, ExpressionLexer>
{
    public static final DataFormat EXPRESSION_CLIPBOARD_TYPE = FXUtility.getDataFormat("application/records-expression");
    
    public ExpressionEditor(@Nullable Expression startingValue, ObjectExpression<@Nullable Table> srcTable, ObservableObjectValue<ColumnLookup> columnLookup, ObservableObjectValue<@Nullable DataType> expectedType, TypeManager typeManager, FunctionLookup functionLookup, FXPlatformConsumer<@NonNull @Recorded Expression> onChangeHandler)
    {
        super(startingValue == null ? "" : startingValue.toString(), new ExpressionLexer(columnLookup, typeManager), onChangeHandler);
    }
}
