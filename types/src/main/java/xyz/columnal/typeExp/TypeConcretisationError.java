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

package xyz.columnal.typeExp;

import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.styled.StyledString;

/**
 * Basically a wrapper for a String error, with a flag recording whether fixing the type
 * manually is a possible solution, and a suggested type in this case.
 */
public class TypeConcretisationError
{
    private final StyledString errorText;
    private final boolean couldBeFixedByManuallySpecifyingType;
    // If non-null, a suggested type for the fix:
    private final @Nullable DataType suggestedTypeFix;

    public TypeConcretisationError(StyledString errorText)
    {
        this.errorText = errorText;
        this.couldBeFixedByManuallySpecifyingType = false;
        this.suggestedTypeFix = null;
    }
    
    public TypeConcretisationError(StyledString errorText, @Nullable DataType suggestedTypeFix)
    {
        this.errorText = errorText;
        this.couldBeFixedByManuallySpecifyingType = true;
        this.suggestedTypeFix = suggestedTypeFix;
    }

    public StyledString getErrorText()
    {
        return errorText;
    }

    public @Nullable DataType getSuggestedTypeFix()
    {
        return suggestedTypeFix;
    }

    // For debugging:
    @Override
    public String toString()
    {
        return errorText.toPlain();
    }
}
