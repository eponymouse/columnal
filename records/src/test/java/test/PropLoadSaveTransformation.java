package test;

import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import org.junit.runner.RunWith;
import records.data.Table;
import records.data.Table.FullSaver;
import records.data.TableManager;
import records.data.Transformation;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.TransformationManager;
import test.gen.GenFilter;
import test.gen.GenSort;
import test.gen.GenSummaryStats;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.SimulationSupplier;
import utility.Utility;

import javax.swing.SwingUtilities;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.Assert.assertEquals;

/**
 * Created by neil on 16/11/2016.
 */
@RunWith(JUnitQuickcheck.class)
public class PropLoadSaveTransformation
{
    @Property(trials = 1000)
    @OnThread(value = Tag.FXPlatform,ignoreParent = true)
    public void testTransformation(@From(GenSort.class) @From(GenSummaryStats.class) @From(GenFilter.class) Transformation original) throws ExecutionException, InterruptedException, UserException, InternalException, InvocationTargetException
    {
        String saved = save(original);
        try
        {
            //Assume users destroy leading whitespace:
            String savedMangled = saved.replaceAll("\n +", "\n");
            Table loaded = DummyManager.INSTANCE.loadAll(savedMangled).get(0);
            String savedAgain = save(loaded);
            Table loadedAgain = DummyManager.INSTANCE.loadAll(savedAgain).get(0);


            assertEquals(saved, savedAgain);
            assertEquals(original, loaded);
            assertEquals(loaded, loadedAgain);
        }
        catch (Throwable t)
        {
            System.err.println("Original:\n" + saved);
            System.err.flush();
            throw t;
        }
    }

    @OnThread(Tag.FXPlatform)
    private static String save(Table original) throws ExecutionException, InterruptedException, InvocationTargetException
    {
        // This thread is only pretend running on FXPlatform, but sets off some
        // code which actually runs on the fx platform thread:
        CompletableFuture<String> f = new CompletableFuture<>();
        SwingUtilities.invokeAndWait(() -> new JFXPanel());
        Utility.runAfter(() -> {
            try
            {
                original.save(null, new FullSaver() {
                    @Override
                    public @OnThread(Tag.FXPlatform) void saveTable(String tableSrc)
                    {
                        super.saveTable(tableSrc);
                        f.complete(getCompleteFile());
                    }
                });
            }
            catch (Throwable t)
            {
                f.complete("");
            }
        });
        return f.get();
    }

    @Property
    @OnThread(value = Tag.Simulation,ignoreParent = true)
    public void testNoOpEdit(@From(GenSort.class) @From(GenSummaryStats.class) @From(GenFilter.class) Transformation original) throws ExecutionException, InterruptedException, UserException, InternalException, InvocationTargetException
    {
        TableManager mgr = new TableManager();
        SwingUtilities.invokeAndWait(() -> new JFXPanel());
        CompletableFuture<SimulationSupplier<Transformation>> f = new CompletableFuture<>();
        Platform.runLater(() -> {
            f.complete(original.edit().getTransformation(mgr));
        });
        assertEquals(f.get().get(), original);
    }
}
