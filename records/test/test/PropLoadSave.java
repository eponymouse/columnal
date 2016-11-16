package test;

import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import org.junit.runner.RunWith;
import records.data.Table;
import records.data.TableManager;
import records.data.Transformation;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.Sort;
import records.transformations.TransformationManager;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.ExFunction;

import javax.swing.SwingUtilities;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.Assert.assertEquals;

/**
 * Created by neil on 16/11/2016.
 */
@RunWith(JUnitQuickcheck.class)
public class PropLoadSave
{
    @Property
    @OnThread(value = Tag.Simulation,ignoreParent = true)
    public void testSort(@From(GenSort.class) GenSort.MakeSort sort) throws IOException, ExecutionException, InterruptedException, InternalException, UserException, InvocationTargetException
    {
        test(sort.apply(new DummyManager()));
    }

    @OnThread(value = Tag.Simulation,ignoreParent = true)
    private static void test(Transformation original) throws ExecutionException, InterruptedException, UserException, InternalException, InvocationTargetException
    {
        String saved = save(original);
        //Assume users destroy leading whitespace:
        String savedMangled = saved.replaceAll("\n +", "\n");
        Table loaded = TransformationManager.getInstance().loadOne(new DummyManager(), savedMangled);
        String savedAgain = save(loaded);
        Table loadedAgain = TransformationManager.getInstance().loadOne(new DummyManager(), savedAgain);



        assertEquals(saved, savedAgain);
        assertEquals(original, loaded);
        assertEquals(loaded, loadedAgain);
    }

    private static String save(Table original) throws ExecutionException, InterruptedException, InvocationTargetException
    {
        CompletableFuture<String> f = new CompletableFuture<>();
        SwingUtilities.invokeAndWait(() -> new JFXPanel());
        Platform.runLater(() -> {
            try
            {
                original.save(null, s -> f.complete(s));
            }
            catch (Throwable t)
            {
                f.complete("");
            }
        });
        return f.get();
    }

}
