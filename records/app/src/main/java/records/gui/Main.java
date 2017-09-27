package records.gui;

import javafx.application.Application;
import javafx.stage.Stage;
import org.apache.commons.io.FileUtils;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.gui.MainWindow.MainWindowActions;
import records.importers.ExcelImporter;
import records.importers.HTMLImporter;
import records.importers.TextImporter;
import records.importers.manager.ImporterManager;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
import utility.gui.FXUtility;

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
        FXUtility.ensureFontLoaded("DroidSansMono-Regular.ttf");
        FXUtility.ensureFontLoaded("NotoSans-Regular.ttf");
        FXUtility.ensureFontLoaded("SourceCodePro-Regular.ttf");
        FXUtility.ensureFontLoaded("SourceCodePro-Semibold.ttf");

        ImporterManager.getInstance().registerImporter(new TextImporter());
        // TODO move this to a plugin:
        ImporterManager.getInstance().registerImporter(new HTMLImporter());
        ImporterManager.getInstance().registerImporter(new ExcelImporter());

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
                    MainWindow.show(new Stage(), paramFile, new Pair<>(paramFile, FileUtils.readFileToString(paramFile, Charset.forName("UTF-8"))));
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
