/*
 * Columnal: Safer, smoother data table processing.
 * Copyright (c) Neil Brown, 2016-2020, 2022.
 *
 * This file is part of Columnal.
 *
 * Columnal is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 *
 * Columnal is distributed in the hope that it will be useful, but WITHOUT 
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or 
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for 
 * more details.
 *
 * You should have received a copy of the GNU General Public License along 
 * with Columnal. If not, see <https://www.gnu.org/licenses/>.
 */

package xyz.columnal.gui.lexeditor;

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
import xyz.columnal.id.ColumnId;
import xyz.columnal.data.Table;
import xyz.columnal.id.TableAndColumnRenames;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.TypeManager;
import xyz.columnal.transformations.expression.BracketedStatus;
import xyz.columnal.transformations.expression.Expression;
import xyz.columnal.transformations.expression.Expression.ColumnLookup;
import xyz.columnal.transformations.expression.Expression.ColumnLookup.ClickedReference;
import xyz.columnal.transformations.expression.Expression.SaveDestination;
import xyz.columnal.transformations.expression.TypeState;
import xyz.columnal.transformations.expression.function.FunctionLookup;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.function.fx.FXPlatformConsumer;
import xyz.columnal.utility.function.fx.FXPlatformSupplierInt;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.gui.FXUtility;

import java.util.Comparator;
import java.util.function.Predicate;

public class ExpressionEditor extends TopLevelEditor<Expression, ExpressionLexer, ExpressionCompletionContext>
{
    public static final DataFormat EXPRESSION_CLIPBOARD_TYPE = FXUtility.getDataFormat("application/records-expression");
    
    public ExpressionEditor(@Nullable Expression startingValue, ObjectExpression<@Nullable Table> srcTable, ObservableObjectValue<ColumnLookup> columnLookup, @Nullable DataType expectedType, @Nullable ColumnPicker columnPicker, TypeManager typeManager, FXPlatformSupplierInt<TypeState> makeTypeState, FunctionLookup functionLookup, FXPlatformConsumer<@NonNull @Recorded Expression> onChangeHandler)
    {
        super(startingValue == null ? null : startingValue.save(SaveDestination.toExpressionEditor(typeManager, columnLookup.get(), functionLookup), BracketedStatus.DONT_NEED_BRACKETS, new TableAndColumnRenames(ImmutableMap.of())), new ExpressionLexer(columnLookup, typeManager, functionLookup, makeTypeState, expectedType), typeManager, onChangeHandler, "expression-editor");
        
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
                                    content.replaceSelection(columnReferences.get(0).getExpression().save(SaveDestination.toExpressionEditor(typeManager, columnLookup.get(), functionLookup), BracketedStatus.DONT_NEED_BRACKETS, TableAndColumnRenames.EMPTY));
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
