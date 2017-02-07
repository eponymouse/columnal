package test;

import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.When;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import org.junit.runner.RunWith;
import records.data.DataSource;
import records.data.ImmediateDataSource;
import records.data.Table;
import records.data.Table.FullSaver;
import records.data.TableManager;
import records.data.Transformation;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.TransformationManager;
import test.gen.GenFilter;
import test.gen.GenImmediateData;
import test.gen.GenSort;
import test.gen.GenSummaryStats;
import test.gen.GenTableManager;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
import utility.Utility;

import javax.swing.SwingUtilities;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.Assert.assertEquals;

/**
 * Created by neil on 07/12/2016.
 */
@RunWith(JUnitQuickcheck.class)
public class PropLoadSaveData
{
    @Property(trials = 30)
    @OnThread(value = Tag.FXPlatform, ignoreParent = true)
    public void testImmediate(@From(GenTableManager.class) TableManager mgr1, @From(GenTableManager.class) TableManager mgr2, @From(GenImmediateData.class) GenImmediateData.ImmediateData_Mgr original) throws ExecutionException, InterruptedException, UserException, InternalException, InvocationTargetException
    {
        String saved = save(original.mgr);
        try
        {
            //Assume users destroy leading whitespace:
            String savedMangled = saved.replaceAll("\n +", "\n");
            Table loaded = mgr1.loadAll(savedMangled).get(0);
            String savedAgain = save(mgr1);
            Table loadedAgain = mgr2.loadAll(savedAgain).get(0);


            assertEquals(saved, savedAgain);
            assertEquals(original.data(), loaded);
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
    private static String save(TableManager tableManager) throws ExecutionException, InterruptedException, InvocationTargetException
    {
        // This thread is only pretend running on FXPlatform, but sets off some
        // code which actually runs on the fx platform thread:
        CompletableFuture<String> f = new CompletableFuture<>();
        SwingUtilities.invokeAndWait(() -> new JFXPanel());
        Utility.runAfter(() -> {
            try
            {
                tableManager.save(null, new FullSaver() {
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
                t.printStackTrace();
                f.complete("");
            }
        });
        return f.get();
    }
}
