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

package test.gen;

import annotation.qual.Value;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.RecordSet;
import xyz.columnal.id.TableId;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.TypeManager;
import xyz.columnal.transformations.expression.Expression;
import xyz.columnal.transformations.expression.Expression.ColumnLookup;
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
