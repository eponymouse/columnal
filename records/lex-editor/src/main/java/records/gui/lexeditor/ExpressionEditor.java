package records.gui.lexeditor;

import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import javafx.beans.binding.ObjectExpression;
import javafx.beans.value.ObservableObjectValue;
import javafx.geometry.Dimension2D;
import javafx.geometry.Point2D;
import javafx.scene.Scene;
import javafx.scene.input.DataFormat;
import javafx.util.Duration;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.PolyNull;
import records.data.ColumnId;
import records.data.Table;
import records.data.TableAndColumnRenames;
import records.data.datatype.DataType;
import records.data.datatype.TypeManager;
import records.transformations.expression.BracketedStatus;
import records.transformations.expression.Expression;
import records.transformations.expression.Expression.ColumnLookup;
import records.transformations.expression.Expression.ColumnLookup.ClickedReference;
import records.transformations.expression.Expression.SaveDestination;
import records.transformations.expression.TypeState;
import records.transformations.expression.function.FunctionLookup;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformConsumer;
import utility.FXPlatformSupplierInt;
import utility.Pair;
import utility.gui.FXUtility;

import java.util.Comparator;
import java.util.function.Predicate;

public class ExpressionEditor extends TopLevelEditor<Expression, ExpressionLexer, ExpressionCompletionContext>
{
    public static final DataFormat EXPRESSION_CLIPBOARD_TYPE = FXUtility.getDataFormat("application/records-expression");
    
    public ExpressionEditor(@Nullable Expression startingValue, ObjectExpression<@Nullable Table> srcTable, ObservableObjectValue<ColumnLookup> columnLookup, @Nullable DataType expectedType, @Nullable ColumnPicker columnPicker, TypeManager typeManager, FXPlatformSupplierInt<TypeState> makeTypeState, FunctionLookup functionLookup, FXPlatformConsumer<@NonNull @Recorded Expression> onChangeHandler)
    {
        super(startingValue == null ? null : startingValue.save(SaveDestination.TO_EDITOR, BracketedStatus.DONT_NEED_BRACKETS, typeManager, new TableAndColumnRenames(ImmutableMap.of())), new ExpressionLexer(columnLookup, typeManager, functionLookup, makeTypeState, expectedType), typeManager, onChangeHandler, "expression-editor");
        
        FXUtility.onceNotNull(display.sceneProperty(), s -> {
            FXUtility.onceNotNull(s.windowProperty(), w -> {
                FXUtility.addChangeListenerPlatformNN(w.showingProperty(), showing -> {
                    if (columnPicker != null)
                    {
                        if (showing)
                        {
                            columnPicker.enableColumnPickingMode(null, display.sceneProperty(), c -> display.isFocused() && columnLookup.get().getPossibleColumnReferences(c.getFirst().getId(), c.getSecond()).findFirst().isPresent(), c -> {
                                ImmutableList<ClickedReference> columnReferences = columnLookup.get().getPossibleColumnReferences(c.getFirst().getId(), c.getSecond()).sorted(Comparator.<ClickedReference,  Boolean>comparing(cr -> cr.getTableId() != null)).collect(ImmutableList.<ClickedReference>toImmutableList());
                                if (!columnReferences.isEmpty())
                                {
                                    content.replaceSelection(columnReferences.get(0).getExpression().save(SaveDestination.TO_EDITOR, BracketedStatus.DONT_NEED_BRACKETS, null, TableAndColumnRenames.EMPTY));
                                    FXUtility.runAfterDelay(Duration.millis(50), () -> {
                                        w.requestFocus();
                                        display.requestFocus();
                                    });
                                }
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

    @Override
    protected Dimension2D getEditorDimension(@UnknownInitialization(Object.class) ExpressionEditor this)
    {
        return new Dimension2D(450.0, 130.0);
    }

    @OnThread(Tag.FXPlatform)
    public static interface ColumnPicker
    {
        public void enableColumnPickingMode(@Nullable Point2D screenPos, ObjectExpression<@PolyNull Scene> sceneProperty, Predicate<Pair<Table, ColumnId>> includeColumn, FXPlatformConsumer<Pair<Table, ColumnId>> onPick);
        
        public void disablePickingMode();
    }
}
