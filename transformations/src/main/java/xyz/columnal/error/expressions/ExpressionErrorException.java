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

package xyz.columnal.error.expressions;

import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.Table;
import xyz.columnal.id.TableId;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.transformations.expression.Expression;
import xyz.columnal.transformations.expression.Expression.ColumnLookup;
import xyz.columnal.transformations.expression.TypeState;
import xyz.columnal.styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.function.fx.FXPlatformSupplierInt;

public class ExpressionErrorException extends UserException
{
    public final EditableExpression editableExpression;

    public ExpressionErrorException(StyledString styledString, EditableExpression editableExpression)
    {
        super(styledString);
        this.editableExpression = editableExpression;
    }
    
    public static abstract class EditableExpression
    {
        public final Expression current;
        public final @Nullable TableId srcTableId;
        public final ColumnLookup columnLookup;
        public final FXPlatformSupplierInt<TypeState> makeTypeState;
        public final @Nullable DataType expectedType;

        protected EditableExpression(Expression current, @Nullable TableId srcTableId, ColumnLookup columnLookup, FXPlatformSupplierInt<TypeState> makeTypeState, @Nullable DataType expectedType)
        {
            this.current = current;
            this.srcTableId = srcTableId;
            this.columnLookup = columnLookup;
            this.makeTypeState = makeTypeState;
            this.expectedType = expectedType;
        }

        @OnThread(Tag.Simulation)
        public abstract Table replaceExpression(Expression changed) throws InternalException;
    }
}
