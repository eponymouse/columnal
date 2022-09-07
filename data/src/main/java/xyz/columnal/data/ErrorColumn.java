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

package xyz.columnal.data;

import com.google.common.collect.ImmutableList;
import xyz.columnal.data.datatype.DataTypeValue;
import xyz.columnal.data.datatype.TypeManager;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;

/**
 * A column which shows only error values.
 */
public class ErrorColumn extends Column
{
    @OnThread(Tag.Any)
    private final DataTypeValue dataTypeValue;
    
    public ErrorColumn(RecordSet recordSet, TypeManager typeManager, ColumnId columnId, StyledString errorText) throws InternalException
    {
        super(recordSet, columnId);
        try
        {
            dataTypeValue = typeManager.getVoidType().instantiate(ImmutableList.of(), typeManager).fromCollapsed((i, prog) -> {
                throw new UserException(errorText);
            });
        }
        catch (UserException e)
        {
            throw new InternalException("Error accessing void type", e);
        }
    }
    
    @Override
    public @OnThread(Tag.Any) DataTypeValue getType() throws InternalException, UserException
    {
        return dataTypeValue;
    }

    @Override
    public @OnThread(Tag.Any) AlteredState getAlteredState()
    {
        // We are an error column because we failed to be a calculation, so count as overwriting:
        return AlteredState.OVERWRITTEN;
    }

    @OnThread(Tag.Any)
    public EditableStatus getEditableStatus()
    {
        return new EditableStatus(false, null);
    }
}
