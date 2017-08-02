package test;

import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.control.ScrollPane;
import org.junit.runner.RunWith;
import records.data.Table;
import records.data.Table.FullSaver;
import records.data.TableManager;
import records.data.Transformation;
import records.error.InternalException;
import records.error.UserException;
import records.gui.View;
import records.transformations.TransformationEditable;
import test.gen.GenTableManager;
import test.gen.GenNonsenseTransformation;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.SimulationSupplier;

import javax.swing.SwingUtilities;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.Assert.assertEquals;

/**
 * Created by neil on 16/11/2016.
 */
@RunWith(JUnitQuickcheck.class)
public class PropLoadSaveTransformation
{
    @Property(trials = 1000)
    @OnThread(value = Tag.Simulation, ignoreParent = true)
    public void testLoadSaveTransformation(@From(GenTableManager.class) TableManager mgr1, @From(GenTableManager.class) TableManager mgr2, @From(GenNonsenseTransformation.class) TestUtil.Transformation_Mgr original) throws ExecutionException, InterruptedException, UserException, InternalException, InvocationTargetException
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
            assertEquals(original.transformation, loaded);
            assertEquals(loaded, loadedAgain);
        }
        catch (Throwable t)
        {
            System.err.println("Original:\n" + saved);
            System.err.flush();
            throw t;
        }
    }

    @OnThread(Tag.Simulation)
    private static String save(TableManager original) throws ExecutionException, InterruptedException, InvocationTargetException
    {
        // This thread is only pretend running on FXPlatform, but sets off some
        // code which actually runs on the fx platform thread:
        CompletableFuture<String> f = new CompletableFuture<>();
        try
        {
            original.save(null, new FullSaver() {
                @Override
                public @OnThread(Tag.Simulation) void saveTable(String tableSrc)
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
        return f.get();
    }

    @Property
    @OnThread(value = Tag.Simulation,ignoreParent = true)
    public void testNoOpEdit(@From(GenNonsenseTransformation.class) TestUtil.Transformation_Mgr original) throws ExecutionException, InterruptedException, UserException, InternalException, InvocationTargetException, TimeoutException
    {
        // Initialise JavaFX:
        SwingUtilities.invokeAndWait(() -> new JFXPanel());
        CompletableFuture<SimulationSupplier<Transformation>> f = new CompletableFuture<>();
        Platform.runLater(() -> {
            View view;
            try
            {
                File tempFile = File.createTempFile("rec", "tmp");
                tempFile.deleteOnExit();
                view = new View(() -> {}, tempFile);
            }
            catch (InternalException | UserException | IOException e)
            {
                throw new RuntimeException(e);
            }
            f.complete(((TransformationEditable)original.transformation).edit(view).getTransformation(original.mgr));
        });
        assertEquals(original.transformation, f.get(10, TimeUnit.SECONDS).get());
    }
}
