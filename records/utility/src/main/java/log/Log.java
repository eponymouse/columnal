package log;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.qual.Pure;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Utility;

import java.util.Map;
import java.util.WeakHashMap;

public class Log
{
    private static Logger logger = LogManager.getRootLogger();
    
    // Used to remember which thread set off which runnable:
    @OnThread(value = Tag.Any, requireSynchronized = true)
    private static final Map<Thread, StackTraceElement[]> threadedCallers = new WeakHashMap<>();

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
            log("Suppressed:", se);

        // Print cause, if any
        Throwable ourCause = e.getCause();
        if (ourCause != null)
            log("Caused by:", ourCause);

        synchronized (Log.class)
        {
            StackTraceElement[] el = threadedCallers.get(Thread.currentThread());
            if (el != null)
            {
                StringBuilder sb = new StringBuilder();
                sb.append("Original caller:\n");
                for (StackTraceElement traceElement : el)
                    sb.append("\tat " + traceElement + "\n");
                logger.log(Level.ERROR, sb);
            }
        }
    }

    /**
     * For the current thread, store the stack as extra info that will be printed
     * if an exception is logged in this thread.  Will be overwritten by another
     * call to this same method on the same thread.
     */
    public synchronized static void storeThreadedCaller(StackTraceElement @Nullable [] stack)
    {
        if (stack != null)
            threadedCallers.put(Thread.currentThread(), stack);
        else
            threadedCallers.remove(Thread.currentThread());
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

        synchronized (Log.class)
        {
            StackTraceElement[] el = threadedCallers.get(Thread.currentThread());
            if (el != null)
            {
                sb.append("Original caller:\n");
                for (StackTraceElement traceElement : el)
                    sb.append("\tat " + traceElement + "\n");
            }
        }
        return sb.toString();
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
}
