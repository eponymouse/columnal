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

package xyz.columnal.log;

import javafx.application.Platform;
import org.checkerframework.checker.i18n.qual.Localized;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.util.function.Function;

public abstract class ErrorHandler
{
    private static ErrorHandler errorHandler = new ErrorHandler()
    {
        @Override
        public @OnThread(Tag.Simulation) void showError(@Localized String title, Function<@Localized String, @Localized String> errWrap, Exception e)
        {
            // Default if new handler not set is just to log
            @Localized String localMsg = e.getLocalizedMessage();
            Log.log(title + (localMsg == null ? "<null>" : errWrap.apply(localMsg)), e);
        }
    };

    public static ErrorHandler getErrorHandler()
    {
        return errorHandler;
    }

    public static void setErrorHandler(ErrorHandler errorHandler)
    {
        ErrorHandler.errorHandler = errorHandler;
    }

    @OnThread(Tag.Simulation)
    public final void alertOnError_(@Localized String title, RunOrError r)
    {
        alertOnError_(title, err -> err, r);
    }

    @OnThread(Tag.Simulation)
    public final void alertOnError_(@Localized String title, Function<@Localized String, @Localized String> errWrap, RunOrError r)
    {
        try
        {
            r.run();
        }
        catch (InternalException | UserException e)
        {
            showError(title, errWrap, e);
        }
    }

    @OnThread(Tag.Simulation)
    public final void showError(@Localized String title, Exception e)
    {
        showError(title, x -> x, e);
    }

    // Note -- should not block the simulation thread!
    @OnThread(Tag.Simulation)
    public abstract void showError(@Localized String title, Function<@Localized String, @Localized String> errWrap, Exception e);

    public static interface RunOrError
    {
        @OnThread(Tag.Simulation)
        void run() throws InternalException, UserException;
    }

}
