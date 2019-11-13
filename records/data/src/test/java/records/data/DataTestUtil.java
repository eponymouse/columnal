package records.data;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.Assert;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeUtility;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.ExSupplier;
import utility.Pair;
import utility.Utility;
import utility.Workers;
import utility.Workers.Priority;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

public class DataTestUtil
{
    @OnThread(Tag.Any)
    public static void assertValueEitherEqual(String prefix, @Nullable Either<String, @Value Object> a, @Nullable Either<String, @Value Object> b) throws UserException, InternalException
    {
        if (a == null && b == null)
            return;
        
        if (a == null || b == null)
        {
            Assert.fail(prefix + " Expected " + a + " but found " + b);
            return;
        }
        @NonNull Either<String, @Value Object> aNN = a;
        @NonNull Either<String, @Value Object> bNN = b;
        
        aNN.either_(aErr -> bNN.either_(
            bErr -> {
                Assert.assertEquals(aErr, bErr);},
            bVal -> {
                Assert.fail(prefix + " Expected Left:" + aErr + " but found Right:" + bVal);}),
            aVal -> bNN.either_(
                bErr -> {
                    Assert.fail(prefix + " Expected Right:" + aVal + " but found Left:" + bErr);},
                bVal -> {assertValueEqual(prefix, aVal, bVal);}
        ));
    }

    @OnThread(Tag.Any)
    public static void assertValueEqual(String prefix, @Nullable @Value Object a, @Nullable @Value Object b)
    {
        try
        {
            if ((a == null) != (b == null))
                Assert.fail(prefix + " differing nullness: " + (a == null) + " vs " + (b == null));
            
            if (a != null && b != null)
            {
                @NonNull @Value Object aNN = a;
                @NonNull @Value Object bNN = b;
                CompletableFuture<Either<Throwable,  Integer>> f = new CompletableFuture<>();
                Workers.onWorkerThread("Comparison", Priority.FETCH, () -> f.complete(exceptionToEither(() -> Utility.compareValues(aNN, bNN))));
                int compare = f.get().either(e -> {throw new RuntimeException(e);}, x -> x);
                if (compare != 0)
                {
                    Assert.fail(prefix + " comparing " + DataTypeUtility._test_valueToString(a) + " against " + DataTypeUtility._test_valueToString(b) + " result: " + compare);
                }
            }
        }
        catch (ExecutionException | InterruptedException e)
        {
            throw new RuntimeException(e);
        }
    }

    @OnThread(Tag.Any)
    public static void assertValueListEqual(String prefix, List<@Value Object> a, @Nullable List<@Value Object> b) throws UserException, InternalException
    {
        Assert.assertNotNull(prefix + " not null", b);
        if (b != null)
        {
            Assert.assertEquals(prefix + " list size", a.size(), b.size());
            for (int i = 0; i < a.size(); i++)
            {
                assertValueEqual(prefix, a.get(i), b.get(i));
            }
        }
    }

    @OnThread(Tag.Any)
    public static void assertValueListEitherEqual(String prefix, List<Either<String, @Value Object>> a, @Nullable List<Either<String, @Value Object>> b) throws UserException, InternalException
    {
        Assert.assertNotNull(prefix + " not null", b);
        if (b != null)
        {
            Assert.assertEquals(prefix + " list size", a.size(), b.size());
            for (int i = 0; i < a.size(); i++)
            {
                assertValueEitherEqual(prefix + " row " + i, a.get(i), b.get(i));
            }
        }
    }

    public static <T> Either<Throwable, T> exceptionToEither(ExSupplier<T> supplier)
    {
        try
        {
            return Either.right(supplier.get());
        }
        catch (Throwable e)
        {
            return Either.left(e);
        }
    }

    @OnThread(Tag.Simulation)
    public static ImmutableList<@Value Object> getRowVals(RecordSet recordSet, int targetRow)
    {
        return recordSet.getColumns().stream().<@Value Object>map(c -> {
            try
            {
                return c.getType().getCollapsed(targetRow);
            }
            catch (InternalException | UserException e)
            {
                throw new RuntimeException(e);
            }
        }).collect(ImmutableList.<@Value Object>toImmutableList());
    }
}
