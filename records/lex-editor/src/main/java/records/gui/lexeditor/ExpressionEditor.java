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
import records.transformations.expression.ColumnReference;
import records.transformations.expression.ColumnReference.ColumnReferenceType;
import records.transformations.expression.Expression;
import records.transformations.expression.Expression.ColumnLookup;
import records.transformations.expression.TypeState;
import records.transformations.expression.function.FunctionLookup;
import records.transformations.expression.function.StandardFunctionDefinition;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformConsumer;
import utility.FXPlatformSupplier;
import utility.FXPlatformSupplierInt;
import utility.Pair;
import utility.gui.FXUtility;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class ExpressionEditor extends TopLevelEditor<Expression, ExpressionLexer, ExpressionCompletionContext>
{
    public static final DataFormat EXPRESSION_CLIPBOARD_TYPE = FXUtility.getDataFormat("application/records-expression");
    
    public ExpressionEditor(@Nullable Expression startingValue, ObjectExpression<@Nullable Table> srcTable, ObservableObjectValue<ColumnLookup> columnLookup, ObservableObjectValue<@Nullable DataType> expectedType, @Nullable ColumnPicker columnPicker, TypeManager typeManager, FXPlatformSupplierInt<TypeState> makeTypeState, FunctionLookup functionLookup, FXPlatformConsumer<@NonNull @Recorded Expression> onChangeHandler)
    {
        super(startingValue == null ? null : startingValue.save(false, BracketedStatus.TOP_LEVEL, new TableAndColumnRenames(ImmutableMap.of())), new ExpressionLexer(columnLookup, typeManager, getAllFunctions(functionLookup), makeTypeState), onChangeHandler, "expression-editor");
        
        FXUtility.onceNotNull(display.sceneProperty(), s -> {
            FXUtility.onceNotNull(s.windowProperty(), w -> {
                FXUtility.addChangeListenerPlatformNN(w.showingProperty(), showing -> {
                    if (columnPicker != null)
                    {
                        if (showing)
                        {
                            columnPicker.enableColumnPickingMode(null, c -> display.isFocused() && columnLookup.get().getPossibleColumnReferences(c.getFirst().getId(), c.getSecond()).findFirst().isPresent(), c -> {
                                String ref = "";
                                ImmutableList<ColumnReference> columnReferences = columnLookup.get().getPossibleColumnReferences(c.getFirst().getId(), c.getSecond()).sorted(Comparator.comparing(cr -> cr.getReferenceType())).collect(ImmutableList.<ColumnReference>toImmutableList());
                                if (!columnReferences.get(0).getReferenceType().equals(ColumnReferenceType.CORRESPONDING_ROW))
                                    ref += "@entire ";
                                if (columnReferences.get(0).getTableId() != null)
                                    ref += columnReferences.get(0).getTableId().getRaw() + ":";
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
    
    @OnThread(Tag.FXPlatform)
    public static interface ColumnPicker
    {
        public void enableColumnPickingMode(@Nullable Point2D screenPos, Predicate<Pair<Table, ColumnId>> includeColumn, FXPlatformConsumer<Pair<Table, ColumnId>> onPick);
        
        public void disablePickingMode();
    }
}
