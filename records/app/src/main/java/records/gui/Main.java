package records.gui;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import log.ErrorHandler;
import log.Log;
import org.apache.commons.io.FileUtils;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.gui.MainWindow.MainWindowActions;
import records.importers.ExcelImporter;
import records.importers.HTMLImporter;
import records.importers.TextImporter;
import records.importers.manager.ImporterManager;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
import utility.Utility;
import utility.gui.FXUtility;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.function.Function;

/**
 * Created by neil on 18/10/2016.
 */
public class Main extends Application
{
    public static final String EXTENSION_INCL_DOT = ".clml";

    @Override
    @OnThread(value = Tag.FXPlatform,ignoreParent = true)
    public void start(final Stage primaryStage) throws Exception
    {
        System.setProperty("storageDirectory", Utility.getStorageDirectory().getAbsolutePath());
        Log.normal("Started application");
        ErrorHandler.setErrorHandler(new ErrorHandler()
        {
            @Override
            public @OnThread(Tag.Simulation) void showError(String title, Function<@Localized String, @Localized String> errWrap, Exception e)
            {
                Platform.runLater(() -> FXUtility.showError(title, errWrap, e));
            }
        });

        FXUtility.ensureFontLoaded("Kalam-Regular.ttf");
        FXUtility.ensureFontLoaded("NotoMono-Regular.ttf");
        FXUtility.ensureFontLoaded("NotoSans-Regular.ttf");
        FXUtility.ensureFontLoaded("NotoSansSymbols-Regular.ttf");
        FXUtility.ensureFontLoaded("NotoSans-Italic.ttf");
        FXUtility.ensureFontLoaded("NotoSans-Bold.ttf");
        FXUtility.ensureFontLoaded("NotoSans-BoldItalic.ttf");
        FXUtility.ensureFontLoaded("SourceCodePro-Regular.ttf");
        FXUtility.ensureFontLoaded("SourceCodePro-Semibold.ttf");
        Log.normal("Loaded fonts");
        
        DataCellSupplier.startPreload();

        ImporterManager.getInstance().registerImporter(new TextImporter());
        // TODO move this to a plugin:
        ImporterManager.getInstance().registerImporter(new HTMLImporter());
        ImporterManager.getInstance().registerImporter(new ExcelImporter());
        Log.normal("Registered importers");

        Parameters parameters = getParameters();
        if (parameters.getUnnamed().isEmpty())
        {
            Log.normal("Showing initial window (no params)");
            InitialWindow.show(primaryStage);
        }
        else
        {
            for (String param : parameters.getUnnamed())
            {
                File paramFile = new File(param);
                if (param.endsWith(EXTENSION_INCL_DOT))
                {
                    Log.normal("Showing main window, to load file: \"" + paramFile.getAbsolutePath() + "\"");
                    MainWindow.show(new Stage(), paramFile, new Pair<>(paramFile, FileUtils.readFileToString(paramFile, StandardCharsets.UTF_8)));
                }
                else
                {
                    Log.normal("Showing main window, to import file: \"" + paramFile.getAbsolutePath() + "\"");
                    @Nullable MainWindowActions mainWindowActions = InitialWindow.newProject(null);
                    if (mainWindowActions != null)
                    {
                        mainWindowActions.importFile(paramFile);
                    }
                    else
                    {
                        Log.error("No window actions found for blank new project");
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
