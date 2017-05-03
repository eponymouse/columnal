package records.gui;

import javafx.application.Application;
import javafx.stage.Stage;
import org.apache.commons.io.FileUtils;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.gui.MainWindow.MainWindowActions;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.io.File;
import java.nio.charset.Charset;

/**
 * Created by neil on 18/10/2016.
 */
public class Main extends Application
{
    @Override
    @OnThread(value = Tag.FXPlatform,ignoreParent = true)
    public void start(final Stage primaryStage) throws Exception
    {
        Parameters parameters = getParameters();
        if (parameters.getUnnamed().isEmpty())
        {
            InitialWindow.show(primaryStage);
        }
        else
        {
            for (String param : parameters.getUnnamed())
            {
                File paramFile = new File(param);
                if (param.endsWith(".rec"))
                {
                    MainWindow.show(paramFile, FileUtils.readFileToString(paramFile, Charset.forName("UTF-8")));
                }
                else
                {
                    @Nullable MainWindowActions mainWindowActions = InitialWindow.newProject(null);
                    if (mainWindowActions != null)
                    {
                        mainWindowActions.importFile(paramFile);
                    }
                }
            }
        }
    }


    // TODO pass -XX:AutoBoxCacheMax= parameter on execution
    public static void main(String[] args)
    {
        Application.launch(Main.class, args);
    }
}
