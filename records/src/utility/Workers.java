package utility;

import org.checkerframework.checker.guieffect.qual.SafeEffect;
import org.checkerframework.checker.lock.qual.GuardedBy;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.Stack;
import java.util.concurrent.locks.Lock;

/**
 * Created by neil on 23/10/2016.
 */
@OnThread(Tag.Simulation)
public class Workers
{
    @FunctionalInterface
    public static interface Worker
    {
        @OnThread(Tag.Simulation)
        public void run();
    }

    @GuardedBy("<self>")
    private static Stack<WorkChunk> currentlyRunning = new Stack<>();

    private static class WorkChunk
    {
        private final Worker work;
        private final String title;
        private final long timeReady;

        public WorkChunk(String title, Worker work, long timeReady)
        {
            this.title = title;
            this.work = work;
            this.timeReady = timeReady;
        }
    }

    @OnThread(Tag.Any)
    private static final Object workQueueLock = new Object();
    @OnThread(Tag.Any)
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
        // TODO will want better shutdown than this;
        thread.setDaemon(true);
        thread.start();
    }

    @OnThread(Tag.Any)
    public static void onWorkerThread(String title, Worker runnable)
    {
        onWorkerThread(title, runnable, 0);
    }

    @OnThread(Tag.Any)
    public static void onWorkerThread(String title, Worker runnable, long delay)
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
