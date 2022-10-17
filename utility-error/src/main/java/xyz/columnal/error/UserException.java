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

package xyz.columnal.error;

import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.nullness.qual.NonNull;
import xyz.columnal.styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;

/**
 * Created by neil on 22/10/2016.
 */
@OnThread(Tag.Any)
public class UserException extends ExceptionWithStyle
{
    public UserException(String message)
    {
        this(StyledString.s(message));
    }

    public UserException(String message, Throwable cause)
    {
        super(StyledString.s(message), cause);
    }

    public UserException(StyledString styledString)
    {
        super(styledString);
    }

    @SuppressWarnings({"nullness", "i18n"}) // Given our constructors require non-null, this can't return null.
    // Also, putting @Localized on Exception.getLocalizedMessage doesn't seem to prevent error here, so suppress i18n warning.
    @Override
    public @NonNull @Localized String getLocalizedMessage()
    {
        return super.getLocalizedMessage();
    }
}
