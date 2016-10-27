package utility;

import org.checkerframework.checker.interning.qual.UsesObjectEquals;
import org.checkerframework.checker.lock.qual.GuardedBy;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.util.Iterator;
import java.util.PriorityQueue;
import java.util.Stack;

/**
 * Created by neil on 23/10/2016.
 */
@OnThread(Tag.Simulation)
public class Workers
{
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
        @OnThread(Tag.FXPlatform)
        public default void addedToQueue(long finished, long us) {};
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

    @OnThread(value = Tag.Any, requireSynchronized = true)
    private static final PriorityQueue<@NonNull WorkChunk> workQueue = new PriorityQueue<>((a, b) -> Long.compare(a.timeReady, b.timeReady));

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
    });
    static {
        // TODO will want better shutdown than this;
        thread.setDaemon(true);
        thread.start();
    }

    @OnThread(Tag.FXPlatform)
    public static void onWorkerThread(String title, Worker runnable)
    {
        onWorkerThread(title, runnable, 0);
    }

    @OnThread(Tag.FXPlatform)
    public static void onWorkerThread(String title, Worker runnable, long delay)
    {
        int numAhead;
        synchronized (Workers.class)
        {
            // We ask for current time.  If we just used 0, then all immediates
            // would queue-jump all timed ones.  Giving new immediates a larger
            // ready time than old delayed ones makes sure the delayed ones aren't starved.
            workQueue.add(new WorkChunk(title, runnable, System.currentTimeMillis() + delay));

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
        work.work.run();
        synchronized (currentlyRunning)
        {
            currentlyRunning.pop();
        }
    }
}
