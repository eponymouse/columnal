package utility;

import org.checkerframework.checker.lock.qual.GuardedBy;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.Stack;
import java.util.concurrent.locks.Lock;

/**
 * Created by neil on 23/10/2016.
 */
public class Workers
{
    @GuardedBy("<self>")
    private static Stack<WorkChunk> currentlyRunning = new Stack<>();

    private static class WorkChunk
    {
        private final Runnable work;
        private final String title;
        private final long timeReady;

        public WorkChunk(String title, Runnable work, long timeReady)
        {
            this.title = title;
            this.work = work;
            this.timeReady = timeReady;
        }
    }

    private static final Object workQueueLock = new Object();
    @GuardedBy("workQueueLock")
    private static final PriorityQueue<@NonNull WorkChunk> workQueue = new PriorityQueue<>((a, b) -> Long.compare(a.timeReady, b.timeReady));

    private static final Thread thread = new Thread(() -> {
        while (true)
        {
            try
            {
                @Nullable WorkChunk next = null;
                synchronized (workQueueLock)
                {
                    do
                    {
                        @Nullable WorkChunk work = workQueue.peek();
                        if (work == null)
                        {
                            workQueueLock.wait();
                        }
                        else
                        {
                            long now = System.currentTimeMillis();
                            if (now < work.timeReady)
                            {
                                workQueueLock.wait(work.timeReady - now);
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
    });
    static {
        thread.start();
    }

    public static void onWorkerThread(String title, Runnable runnable)
    {
        onWorkerThread(title, runnable, 0);
    }

    public static void onWorkerThread(String title, Runnable runnable, long delay)
    {
        synchronized (workQueueLock)
        {
            // We ask for current time.  If we just used 0, then all immediates
            // would queue-jump all timed ones.  Giving new immediates a larger
            // ready time than old delayed ones makes sure the delayed ones aren't starved.
            workQueue.add(new WorkChunk(title, runnable, System.currentTimeMillis() + delay));
            //synchronized (currentlyRunning)
            //{
                //System.out.println("Work queue size: " + workQueue.size() + " cur running: " + (currentlyRunning.isEmpty() ? "none" : currentlyRunning.peek().title));
            //}
            workQueueLock.notify();
        }
    }

    public static void maybeYield()
    {
        synchronized (currentlyRunning)
        {
            // If we are too deep in yields, just plough on:
            if (currentlyRunning.size() > 100)
                return;
        }

        synchronized (workQueueLock)
        {
            if (workQueue.isEmpty())
                return;
            long now = System.currentTimeMillis();
            // Clear work queue up to the point we arrived:
            // Makes sure we can't be here forever because all new arrivals will
            // have later ready time.
            @Nullable WorkChunk work;
            while ((work = workQueue.peek()) != null && work.timeReady < now)
            {
                workQueue.poll();
                run(work);
            }
        }
    }

    private static void run(WorkChunk work)
    {
        synchronized (currentlyRunning)
        {
            currentlyRunning.push(work);
        }
        work.work.run();
        synchronized (currentlyRunning)
        {
            currentlyRunning.pop();
        }
    }
}
