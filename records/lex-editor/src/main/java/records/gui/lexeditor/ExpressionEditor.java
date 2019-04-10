package records.gui.lexeditor;

import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import javafx.beans.binding.ObjectExpression;
import javafx.beans.value.ObservableObjectValue;
import javafx.scene.input.DataFormat;
import log.Log;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.Table;
import records.data.TableAndColumnRenames;
import records.data.datatype.DataType;
import records.data.datatype.TypeManager;
import records.error.InternalException;
import records.transformations.expression.BracketedStatus;
import records.transformations.expression.Expression;
import records.transformations.expression.Expression.ColumnLookup;
import records.transformations.expression.InvalidIdentExpression;
import records.transformations.expression.function.FunctionLookup;
import records.transformations.expression.function.StandardFunctionDefinition;
import styled.StyledShowable;
import utility.FXPlatformConsumer;
import utility.gui.FXUtility;

public class ExpressionEditor extends TopLevelEditor<Expression, ExpressionLexer, ExpressionCompletionContext>
{
    public static final DataFormat EXPRESSION_CLIPBOARD_TYPE = FXUtility.getDataFormat("application/records-expression");
    
    public ExpressionEditor(@Nullable Expression startingValue, ObjectExpression<@Nullable Table> srcTable, ObservableObjectValue<ColumnLookup> columnLookup, ObservableObjectValue<@Nullable DataType> expectedType, TypeManager typeManager, FunctionLookup functionLookup, FXPlatformConsumer<@NonNull @Recorded Expression> onChangeHandler)
    {
        super(startingValue == null ? "" : startingValue.save(false, BracketedStatus.TOP_LEVEL, new TableAndColumnRenames(ImmutableMap.of())), new ExpressionLexer(columnLookup, typeManager, getAllFunctions(functionLookup)), onChangeHandler, "expression-editor");
    }

    private static ImmutableList<StandardFunctionDefinition> getAllFunctions(FunctionLookup functionLookup)
    {
        try
        {
            return functionLookup.getAllFunctions();
        }
        catch (InternalException e)
        {
            Log.log(e);
            return ImmutableList.of();
        }
    }
}
