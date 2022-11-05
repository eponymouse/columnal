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

import com.google.common.collect.ImmutableList;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.MessageFactory;
import org.apache.logging.log4j.message.SimpleMessageFactory;
import org.apache.logging.log4j.simple.SimpleLogger;
import org.apache.logging.log4j.util.PropertiesUtil;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.qual.Pure;
import xyz.columnal.error.InternalException;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.Utility;

import java.util.Properties;
import java.util.function.Consumer;

public class Log
{
    private static @MonotonicNonNull Logger logger;
    
    // This is a bit hacky for this to live in the Log class,
    // but in practical terms it's the easiest thing to do:
    @OnThread(value = Tag.Any, requireSynchronized = true)
    private static Consumer<InternalException> internalExceptionHandler = e -> {};
    
    // Used to remember which thread set off which runnable.  Each thread can only manipulate
    // its own caller state.
    @OnThread(Tag.Any)
    private static final ThreadLocal<@Nullable ImmutableList<Throwable>> threadCaller = new ThreadLocal<>();

    public static void normal(String message)
    {
        getLogger().info(message);
    }

    private static synchronized Consumer<InternalException> getInternalExceptionHandler()
    {
        return internalExceptionHandler;
    }

    @SuppressWarnings("i18n")
    public static void log(String info, Throwable e)
    {
        getLogger().log(Level.ERROR, info, e);
        
        // Print suppressed exceptions, if any
        for (Throwable se : e.getSuppressed())
            getLogger().log(Level.ERROR, "Suppressed:", se);

        // Print cause, if any
        Throwable ourCause = e.getCause();
        if (ourCause != null)
            getLogger().log(Level.ERROR, "Caused by:", ourCause);

        StringBuilder sb = new StringBuilder();
        logCallers(sb);
        String s = sb.toString();
        if (!s.isEmpty())
            getLogger().log(Level.ERROR, sb);

        if (e instanceof InternalException)
        {
            getInternalExceptionHandler().accept((InternalException) e);
        }
    }

    /**
     * For the current thread, store the stack (which comes from another thread that
     * spawned us) as extra info that will be printed if an exception is logged in this thread.
     * Will be overwritten by another call to this same method on the same thread.
     * 
     * Stack is passed as a Throwable as this saves time if the full stack trace is never needed, see
     * https://ionutbalosin.com/2018/06/an-even-faster-way-than-stackwalker-api-for-asynchronously-processing-the-stack-frames/
     * https://ionutbalosin.com/2018/06/getting-the-stack-trace-versus-throwing-an-exception-what-is-common-and-what-is-different/
     */
    public static void storeThreadedCaller(@Nullable ImmutableList<Throwable> stack)
    {
        if (stack != null)
            threadCaller.set(stack);
        else
            threadCaller.remove();
    }
    
    // Gets the current stack trace, plus any stack trace of our caller, as recorded in storeThreadedCaller
    // for the current thread.
    public static ImmutableList<Throwable> getTotalStack()
    {
        Throwable ourStack = new Throwable();
        @Nullable ImmutableList<Throwable> prev = threadCaller.get();
        return prev == null ? ImmutableList.of(ourStack) : Utility.prependToList(ourStack, prev);
    }

    public static void log(Throwable e)
    {
        log("", e);
    }

    @Pure
    public static void logStackTrace(String s)
    {
        if (getLogger().isDebugEnabled())
        {
            String trace = getStackTrace(1, 1000);
            debug(s + "\n" + trace);
        }
    }

    @Pure
    public static void normalStackTrace(String message, int maxLevels)
    {
        String trace = getStackTrace(1, maxLevels);
        normal(message + "\n" + trace);
    }

    private static String getStackTrace(int excludeFromTop, int maxItems)
    {
        StringBuilder sb = new StringBuilder();
        StackTraceElement[] ourStack = Thread.currentThread().getStackTrace();
        // By default we exclude top item, which is us,
        // then we also exclude the number passed to us
        for (int i = 1 + excludeFromTop; i < Math.min(ourStack.length, 1 + excludeFromTop + maxItems); i++)
        {
            sb.append("\tat " + ourStack[i] + "\n");
        }

        logCallers(sb);
        return sb.toString();
    }

    private static void logCallers(StringBuilder sb)
    {
        @Nullable ImmutableList<Throwable> throwables = threadCaller.get();
        if (throwables != null)
        {
            for (Throwable throwable : throwables)
            {
                sb.append("Called from another thread by:\n");
                for (StackTraceElement traceElement : throwable.getStackTrace())
                    sb.append("\tat " + traceElement + "\n");
            }
        }
    }

    public static void error(String s)
    {
        getLogger().log(Level.ERROR, s);
    }

    // This should only be used temporarily while debugging, and should not be left in:
    @Pure
    public static void debug(String s)
    {
        getLogger().log(Level.DEBUG, s);
    }

    // This should only be used temporarily while debugging, and should not be left in:
    @Pure
    public static void debugTime(String s)
    {
        long millis = System.currentTimeMillis() % 100000L;
        getLogger().log(Level.DEBUG, String.format("#%03d.%03d: ", millis / 1000L, millis % 1000L) + s);
    }

    public static void debugDuration(String prefix, Runnable runnable)
    {
        long start = System.currentTimeMillis();
        runnable.run();
        long end = System.currentTimeMillis();
        debug(prefix + " took " + (end - start) + " milliseconds");
    }

    private static Logger getLogger()
    {
        if (logger == null)
        {
            try
            {
                logger = LogManager.getRootLogger();
            }
            catch (Throwable t)
            {
                // Uh-oh!  Make a backup logger:
                logger = new SimpleLogger("BackupLogger", Level.ERROR, false, true, true, false, "yyyy/MM/dd HH:mm:ss:SSS zzz", new SimpleMessageFactory(), new PropertiesUtil(new Properties()), System.out);
                logger.log(Level.ERROR, t);
            }
        }
        return logger;
    }

    /**
     * Sets a handler to be called when we log an InternalException.
     * The handler should not block the current thread.  Also, it's possible
     * this consumer could be called from multiple threads simultaneously.
     */
    public static synchronized void setInternalExceptionHandler(Consumer<InternalException> internalExceptionHandler)
    {
        Log.internalExceptionHandler = internalExceptionHandler;
    }
}
