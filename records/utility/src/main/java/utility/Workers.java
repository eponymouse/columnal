package utility;

import com.google.common.collect.ImmutableList;
import javafx.scene.layout.Priority;
import org.checkerframework.checker.interning.qual.UsesObjectEquals;
import org.checkerframework.checker.lock.qual.GuardedBy;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.OptionalLong;
import java.util.PriorityQueue;
import java.util.Stack;
import java.util.stream.Stream;

/**
 * Created by neil on 23/10/2016.
 */
@OnThread(Tag.Simulation)
public class Workers
{
    public static enum Priority
    {
        // Highest to lowest:
        SAVE_TO_DISK, SAVE_ENTRY, LOAD_FROM_DISK, FETCH;
    }

    @FunctionalInterface
    @UsesObjectEquals
    public static interface Worker
    {
        @OnThread(Tag.Simulation)
        public void run();

        // Optional methods to get updates on your place in the queue:
        // addedToQueue is guaranteed to be called and return
        // before queueMoved is called.
        @OnThread(Tag.Simulation)
        public default void queueMoved(long finished, long lastQueued) {};
        // Don't do too much here; the lock is held!
        @OnThread(Tag.Any)
        public default void addedToQueue(long finished, long us) {};
    }

    @GuardedBy("<self>")
    @OnThread(value = Tag.Any)
    private static final Stack<WorkChunk> currentlyRunning = new Stack<>();

    private static class WorkChunk
    {
        private final Worker work;
        private final String title;
        private final long timeReady;
        private final Priority priority;
        private final StackTraceElement[] caller;

        public WorkChunk(String title, Priority priority, Worker work, long timeReady, StackTraceElement[] caller)
        {
            this.title = title;
            this.priority = priority;
            this.work = work;
            this.timeReady = timeReady;
            this.caller = caller;
        }
    }

    @OnThread(value = Tag.Any, requireSynchronized = true)
    private static final PriorityQueue<@NonNull WorkChunk> workQueue = new PriorityQueue<>(
        Comparator.<WorkChunk, Priority>comparing(a -> a.priority).thenComparing(Comparator.comparingLong(a -> a.timeReady))
    );

    @OnThread(value = Tag.Any, requireSynchronized = true)
    private static long finished = 0;

    private static final Thread thread = new Thread(() -> {
        long lastQueueUpdate = System.currentTimeMillis();
        while (true)
        {
            try
            {
                @Nullable WorkChunk next = null;
                synchronized (Workers.class)
                {
                    finished += 1; // Not strictly right on startup but doesn't matter
                    do
                    {
                        @Nullable WorkChunk work = workQueue.peek();
                        if (work == null)
                        {
                            Workers.class.wait();
                        }
                        else
                        {
                            long now = System.currentTimeMillis();
                            if (now > lastQueueUpdate + 1000)
                            {
                                for (WorkChunk w : workQueue)
                                {
                                    w.work.queueMoved(finished, finished + workQueue.size());
                                }
                                lastQueueUpdate = now;
                            }

                            if (now < work.timeReady)
                            {
                                Workers.class.wait(work.timeReady - now);
                            }
                            else
                            {
                                workQueue.poll();
                                next = work;
                            }
                        }
                    }
                    while (next == null);
                }
                run(next);
            }
            catch (InterruptedException e)
            {

            }

        }
    }, "Workers Thread");
    static {
        // TODO will want better shutdown than this;
        thread.setDaemon(true);
        thread.start();
    }

    @OnThread(Tag.Any)
    public static void onWorkerThread(String title, Priority priority, Worker runnable)
    {
        onWorkerThread(title, priority, runnable, 0);
    }

    @OnThread(Tag.Any)
    public static void onWorkerThread(String title, Priority priority, Worker runnable, long delay)
    {
        int numAhead;
        synchronized (Workers.class)
        {
            // We ask for current time.  If we just used 0, then all immediates
            // would queue-jump all timed ones.  Giving new immediates a larger
            // ready time than old delayed ones makes sure the delayed ones aren't starved.
            workQueue.add(new WorkChunk(title, priority, runnable, System.currentTimeMillis() + delay, Thread.currentThread().getStackTrace()));

            // TODO this isn't right if we actually use the delay feature:
            runnable.addedToQueue(finished, finished + workQueue.size());

            //synchronized (currentlyRunning)
            //{
                //System.out.println("Work queue size: " + workQueue.size() + " cur running: " + (currentlyRunning.isEmpty() ? "none" : currentlyRunning.peek().title));
            //}
            Workers.class.notify();
        }
    }

    @OnThread(Tag.FXPlatform)
    public static void cancel(Worker worker)
    {
        synchronized (Workers.class)
        {
            for (Iterator<WorkChunk> iterator = workQueue.iterator(); iterator.hasNext(); )
            {
                WorkChunk c = iterator.next();
                if (c.work == worker)
                {
                    iterator.remove();
                    return;
                }
            }
        }

    }

    /*
    public static void maybeYield()
    {
        synchronized (currentlyRunning)
        {
            // If we are too deep in yields, just plough on:
            if (currentlyRunning.size() > 100)
                return;
        }


        while (true)
        {
            @Nullable WorkChunk work;
            synchronized (workQueueLock)
            {
                // No more work to yield to:
                if (workQueue.isEmpty())
                    return;
                long now = System.currentTimeMillis();
                // Clear work queue up to the point we arrived:
                // Makes sure we can't be here forever because all new arrivals will
                // have later ready time.
                if ((work = workQueue.peek()) != null && work.timeReady < now)
                {
                    // Ready before we came; we'll take it and run it:
                    workQueue.poll();
                }
                else // Not ready before we came; leave it alone:
                    return;
            }
            run(work);
        }
    }*/

    private static void run(WorkChunk work)
    {
        synchronized (currentlyRunning)
        {
            currentlyRunning.push(work);
        }
        try
        {
            Utility.storeThreadedCaller(work.caller);
            work.work.run();
        }
        catch (Throwable t)
        {
            t.printStackTrace();
        }
        finally
        {
            synchronized (currentlyRunning)
            {
                currentlyRunning.pop();
            }
        }
    }

    // The publicly viewable details by the user
    public static class WorkInfo
    {
        public final String taskName;
        public final Priority priority;
        // Both a point in time using System.currentTimeMillis:
        public final OptionalLong startedTime;
        public final long queuedTime;

        private WorkInfo(WorkChunk chunk)
        {
            this.taskName = chunk.title;
            this.priority = chunk.priority;
            this.queuedTime = chunk.timeReady;
            this.startedTime = OptionalLong.empty();
        }
    }

    @OnThread(Tag.FXPlatform)
    public static synchronized ImmutableList<WorkInfo> getTaskList()
    {
        return Stream.concat(currentlyRunning.stream(), workQueue.stream()).map(WorkInfo::new).collect(ImmutableList.toImmutableList());
    }

    @OnThread(Tag.Any)
    public static String _test_getCurrentTaskName()
    {
        synchronized (currentlyRunning)
        {
            if (!currentlyRunning.isEmpty())
                return currentlyRunning.peek().title;
        }
        return "<NONE>";
    }

    public static boolean _test_isOnWorkerThread()
    {
        return Thread.currentThread() == thread;
    }

    // Runs work until the work queue is empty
    public static void _test_yield()
    {
        while (true)
        {
            @Nullable WorkChunk next = null;
            synchronized (Workers.class)
            {
                do
                {
                    @Nullable WorkChunk work = workQueue.peek();
                    if (work == null)
                    {
                        return; // Nothing to run
                    }
                    else
                    {
                        long now = System.currentTimeMillis();
                        if (now < work.timeReady)
                        {
                            try
                            {
                                Workers.class.wait(work.timeReady - now);
                            }
                            catch (InterruptedException e)
                            {
                            }
                        }
                        else
                        {
                            workQueue.poll();
                            next = work;
                        }
                    }
                }
                while (next == null);
            }
            run(next);
        }
    }
}
