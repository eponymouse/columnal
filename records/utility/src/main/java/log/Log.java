package log;

import org.checkerframework.checker.nullness.qual.Nullable;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Utility;

import java.util.Map;
import java.util.WeakHashMap;

public class Log
{
    // Used to remember which thread set off which runnable:
    @OnThread(value = Tag.Any, requireSynchronized = true)
    private static final Map<Thread, StackTraceElement[]> threadedCallers = new WeakHashMap<>();

    @SuppressWarnings("i18n")
    public static void log(String info, Throwable e)
    {
        System.err.println(info);
        // Print our stack trace
        System.err.println(e);
        StackTraceElement[] trace = e.getStackTrace();
        for (StackTraceElement traceElement : trace)
            System.err.println("\tat " + traceElement);

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
                System.err.println("Original caller:");
                for (StackTraceElement traceElement : el)
                    System.err.println("\tat " + traceElement);
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

    public static void logStackTrace(String s)
    {
        try
        {
            throw new Exception(s);
        }
        catch (Exception e)
        {
            log(e);
        }
    }
}
