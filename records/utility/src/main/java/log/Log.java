package log;

import com.google.common.collect.ImmutableList;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.qual.Pure;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Utility;

public class Log
{
    private static Logger logger = LogManager.getRootLogger();
    
    // Used to remember which thread set off which runnable.  Each thread can only manipulate
    // its own caller state.
    @OnThread(Tag.Any)
    private static final ThreadLocal<@Nullable ImmutableList<StackTraceElement[]>> threadCaller = new ThreadLocal<>();

    public static void normal(String message)
    {
        logger.info(message);
    }
    
    @SuppressWarnings("i18n")
    public static void log(String info, Throwable e)
    {
        logger.log(Level.ERROR, info, e);
        
        // Print suppressed exceptions, if any
        for (Throwable se : e.getSuppressed())
            logger.log(Level.ERROR, "Suppressed:", se);

        // Print cause, if any
        Throwable ourCause = e.getCause();
        if (ourCause != null)
            logger.log(Level.ERROR, "Caused by:", ourCause);

        StringBuilder sb = new StringBuilder();
        logCallers(sb);
        String s = sb.toString();
        if (!s.isEmpty())
            logger.log(Level.ERROR, sb);        
    }

    /**
     * For the current thread, store the stack (which comes from another thread that
     * spawned us) as extra info that will be printed if an exception is logged in this thread.
     * Will be overwritten by another call to this same method on the same thread.
     */
    public static void storeThreadedCaller(@Nullable ImmutableList<StackTraceElement[]> stack)
    {
        if (stack != null)
            threadCaller.set(stack);
        else
            threadCaller.remove();
    }
    
    // Gets the current stack trace, plus any stack trace of our caller, as recorded in storeThreadedCaller
    // for the current thread.
    public static ImmutableList<StackTraceElement[]> getTotalStack()
    {
        StackTraceElement[] ourStack = Thread.currentThread().getStackTrace();
        @Nullable ImmutableList<StackTraceElement[]> prev = threadCaller.get();
        return prev == null ? ImmutableList.of(ourStack) : Utility.prependToList(ourStack, prev);
    }

    public static void log(Exception e)
    {
        log("", e);
    }

    @Pure
    public static void logStackTrace(String s)
    {
        String trace = getStackTrace(1, 1000);
        debug(s + "\n" + trace);
    }

    @Pure
    public static void normalStackTrace(String message, int maxLevels)
    {
        String trace = getStackTrace(1, maxLevels);
        debug(message + "\n" + trace);
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
        @Nullable ImmutableList<StackTraceElement[]> traces = threadCaller.get();
        if (traces != null)
        {
            for (StackTraceElement[] el : traces)
            {
                sb.append("Called from another thread by:\n");
                for (StackTraceElement traceElement : el)
                    sb.append("\tat " + traceElement + "\n");
            }
        }
    }

    public static void error(String s)
    {
        logger.log(Level.ERROR, s);
    }

    // This should only be used temporarily while debugging, and should not be left in:
    @Pure
    public static void debug(String s)
    {
        logger.log(Level.DEBUG, s);
    }

    // This should only be used temporarily while debugging, and should not be left in:
    @Pure
    public static void debugTime(String s)
    {
        long millis = System.currentTimeMillis() % 100000L;
        logger.log(Level.DEBUG, String.format("#%03d.%03d: ", millis / 1000L, millis % 1000L) + s);
    }
}
