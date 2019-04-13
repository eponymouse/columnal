package records.gui.lexeditor;

import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import javafx.beans.binding.ObjectExpression;
import javafx.beans.value.ObservableObjectValue;
import javafx.geometry.Point2D;
import javafx.scene.input.DataFormat;
import javafx.util.Duration;
import log.Log;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.ColumnId;
import records.data.Table;
import records.data.TableAndColumnRenames;
import records.data.TableId;
import records.data.datatype.DataType;
import records.data.datatype.TypeManager;
import records.error.InternalException;
import records.transformations.expression.BracketedStatus;
import records.transformations.expression.ColumnReference.ColumnReferenceType;
import records.transformations.expression.Expression;
import records.transformations.expression.Expression.ColumnLookup;
import records.transformations.expression.function.FunctionLookup;
import records.transformations.expression.function.StandardFunctionDefinition;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformConsumer;
import utility.Pair;
import utility.gui.FXUtility;

import java.util.Objects;
import java.util.function.Predicate;

public class ExpressionEditor extends TopLevelEditor<Expression, ExpressionLexer, ExpressionCompletionContext>
{
    public static final DataFormat EXPRESSION_CLIPBOARD_TYPE = FXUtility.getDataFormat("application/records-expression");
    
    public ExpressionEditor(@Nullable Expression startingValue, ObjectExpression<@Nullable Table> srcTable, ObservableObjectValue<ColumnLookup> columnLookup, ObservableObjectValue<@Nullable DataType> expectedType, @Nullable ColumnPicker columnPicker, TypeManager typeManager, FunctionLookup functionLookup, FXPlatformConsumer<@NonNull @Recorded Expression> onChangeHandler)
    {
        super(startingValue == null ? "" : startingValue.save(false, BracketedStatus.TOP_LEVEL, new TableAndColumnRenames(ImmutableMap.of())), new ExpressionLexer(columnLookup, typeManager, getAllFunctions(functionLookup)), onChangeHandler, "expression-editor");
        
        FXUtility.onceNotNull(display.sceneProperty(), s -> {
            FXUtility.onceNotNull(s.windowProperty(), w -> {
                FXUtility.addChangeListenerPlatformNN(w.showingProperty(), showing -> {
                    if (columnPicker != null)
                    {
                        if (showing)
                        {
                            columnPicker.enableColumnPickingMode(null, c -> display.isFocused() && !calcAvailableColRefTypes(columnLookup.get(), srcTable.get(), c.getFirst().getId(), c.getSecond()).isEmpty(), c -> {
                                String ref = "";
                                if (!calcAvailableColRefTypes(columnLookup.get(), srcTable.get(), c.getFirst().getId(), c.getSecond()).contains(ColumnReferenceType.CORRESPONDING_ROW))
                                    ref += "@entire ";
                                if (c.getFirst() != srcTable.getValue())
                                    ref += c.getFirst().getId().getRaw() + ":";
                                ref += c.getSecond().getRaw();
                                content.replaceSelection(ref);
                                FXUtility.runAfterDelay(Duration.millis(50), () -> {
                                    w.requestFocus();
                                    display.requestFocus();
                                });
                            });
                        }
                        else
                        {
                            columnPicker.disablePickingMode();
                        }
                    }
                });
            });
        });
        FXUtility.addChangeListenerPlatformNN(display.focusedProperty(), focused -> {
            
        });
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
    
    // For a given table id and column id, what 
    private static ImmutableList<ColumnReferenceType> calcAvailableColRefTypes(ColumnLookup columnLookup, @Nullable Table srcTable, TableId colTableId, ColumnId columnId)
    {
        return columnLookup.getAvailableColumnReferences().filter(c -> {
            if (c.getTableId() == null)
            {
                if (srcTable != null)
                {
                    return Objects.equals(srcTable.getId(), colTableId) && Objects.equals(c.getColumnId(), columnId);
                }
                else
                    return false; // No source, and yet no table id in the reference?!
            }
            else
            {
                return Objects.equals(c.getTableId(), colTableId) && Objects.equals(c.getColumnId(), columnId);
            }
        }).map(c -> c.getReferenceType()).collect(ImmutableList.<ColumnReferenceType>toImmutableList());
    }
    
    @OnThread(Tag.FXPlatform)
    public static interface ColumnPicker
    {
        public void enableColumnPickingMode(@Nullable Point2D screenPos, Predicate<Pair<Table, ColumnId>> includeColumn, FXPlatformConsumer<Pair<Table, ColumnId>> onPick);
        
        public void disablePickingMode();
    }
}
