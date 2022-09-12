package test.gui;

import javafx.scene.input.KeyCode;
import org.apache.commons.lang3.SystemUtils;
import org.testfx.util.WaitForAsyncUtils;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.data.CellPosition;
import xyz.columnal.data.TBasicUtil;
import xyz.columnal.data.Table;
import xyz.columnal.data.TableManager;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.id.TableId;
import xyz.columnal.utility.Either;
import xyz.columnal.utility.FXPlatformRunnable;
import xyz.columnal.utility.SimulationRunnable;
import xyz.columnal.utility.SimulationSupplier;
import xyz.columnal.utility.Workers;

import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class TFXUtil
{
    @OnThread(Tag.Any)
    public static <T> T fx(FXPlatformSupplierEx<T> action)
    {
        try
        {
            return WaitForAsyncUtils.asyncFx(action).get(60, TimeUnit.SECONDS);
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    // Note: also waits for the queue to be empty
    @OnThread(Tag.Any)
    public static void fx_(FXPlatformRunnable action)
    {
        try
        {
            WaitForAsyncUtils.asyncFx(action::run).get(60, TimeUnit.SECONDS);
            WaitForAsyncUtils.waitForFxEvents();
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    // Doesn't wait for action to complete
    @OnThread(Tag.Any)
    public static void asyncFx_(FXPlatformRunnable action)
    {
        WaitForAsyncUtils.asyncFx(action::run);
    }

    @OnThread(Tag.Any)
    public static void fxTest_(FXPlatformRunnable action)
    {
        try
        {
            WaitForAsyncUtils.<Optional<Throwable>>asyncFx(new Callable<Optional<Throwable>>()
            {
                @Override
                @OnThread(value = Tag.FXPlatform, ignoreParent = true)
                public Optional<Throwable> call() throws Exception
                {
                    try
                    {
                        action.run();
                        return Optional.empty();
                    }
                    catch (Throwable t)
                    {
                        return Optional.of(t);
                    }
                }
            }).get(5, TimeUnit.MINUTES).ifPresent(e -> {throw new RuntimeException(e);});
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    @OnThread(Tag.Any)
    public static <T> T sim(SimulationSupplier<T> action)
    {
        try
        {
            CompletableFuture<Either<Throwable, T>> f = new CompletableFuture<>();
            Workers.onWorkerThread("Test.sim " + Thread.currentThread().getStackTrace()[2].getClassName() + "." + Thread.currentThread().getStackTrace()[2].getMethodName() + ":" + Thread.currentThread().getStackTrace()[2].getLineNumber(), Workers.Priority.FETCH, () -> {
                try
                {
                    f.complete(Either.right(action.get()));
                }
                catch (Throwable e)
                {
                    f.complete(Either.left(e));
                }
            });
            return f.get(60, TimeUnit.SECONDS).either(e -> {throw new RuntimeException(e);}, x -> x);
        }
        catch (TimeoutException e)
        {
            throw new RuntimeException(Workers._test_getCurrentTaskName(), e);
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    @OnThread(Tag.Any)
    public static void sim_(SimulationRunnable action)
    {
        try
        {
            CompletableFuture<Either<Throwable, Object>> f = new CompletableFuture<>();
            Workers.onWorkerThread("Test.sim_ " + Thread.currentThread().getStackTrace()[2].getClassName() + "." + Thread.currentThread().getStackTrace()[2].getMethodName() + ":" + Thread.currentThread().getStackTrace()[2].getLineNumber(), Workers.Priority.FETCH, () -> {
                try
                {
                    action.run();
                    f.complete(Either.right(new Object()));
                }
                catch (Throwable e)
                {
                    f.complete(Either.left(e));
                }
            });
            f.get(60, TimeUnit.SECONDS).either_(e -> {throw new RuntimeException(e);}, x -> {});
        }
        catch (TimeoutException e)
        {
            throw new RuntimeException(Workers._test_getCurrentTaskName(), e);
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    public static void sleep(int millis)
    {
        try
        {
            Thread.sleep(millis);
        }
        catch (InterruptedException e)
        {

        }
    }

    public static KeyCode ctrlCmd()
    {
        return SystemUtils.IS_OS_MAC_OSX ? KeyCode.COMMAND : KeyCode.CONTROL;
    }

    public @OnThread(Tag.Any)
    static CellPosition tablePosition(TableManager tableManager, TableId srcId) throws UserException
    {
        Table table = tableManager.getSingleTableOrThrow(srcId);
        return TBasicUtil.checkNonNull(fx(() -> table.getDisplay())).getMostRecentPosition();
    }

    public static interface FXPlatformSupplierEx<T> extends Callable<T>
    {
        @OnThread(value = Tag.FXPlatform, ignoreParent = true)
        public T call() throws InternalException, UserException;
    }
}
