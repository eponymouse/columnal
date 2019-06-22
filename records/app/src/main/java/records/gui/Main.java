package records.gui;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Stage;
import log.ErrorHandler;
import log.Log;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.poi.ss.formula.functions.T;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.gui.MainWindow.MainWindowActions;
import records.gui.table.Clickable;
import records.importers.ExcelImporter;
import records.importers.HTMLImporter;
import records.importers.TextImporter;
import records.importers.manager.ImporterManager;
import styled.StyledCSS;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
import utility.ResourceUtility;
import utility.Utility;
import utility.gui.FXUtility;
import utility.gui.SmallDeleteButton;

import javax.swing.SwingUtilities;
import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Created by neil on 18/10/2016.
 */
public class Main extends Application
{
    public static final String DOMAIN = "www.columnal.xyz";
    public static final String EXTENSION_INCL_DOT = ".clml";

    @OnThread(Tag.Any)
    public static class UpgradeInfo
    {
        private final String version;
        private final String description;
        private final URI downloadLink;

        public UpgradeInfo(String version, String description, URI downloadLink)
        {
            this.version = version;
            this.description = description;
            this.downloadLink = downloadLink;
        }

        @OnThread(Tag.FXPlatform)
        public void showAtTopOf(Pane pane)
        {
            BorderPane borderPane = new BorderPane();
            StyledString s = StyledString.concat(StyledString.s("Version " + version + " available.  "),
                StyledString.s("Download now").withStyle(new Clickable(null) {
                    @Override
                    protected @OnThread(Tag.FXPlatform) void onClick(MouseButton mouseButton, Point2D screenPoint)
                    {
                        pane.getChildren().remove(borderPane);
                        SwingUtilities.invokeLater(() -> {
                            try
                            {
                                Desktop.getDesktop().browse(downloadLink);
                            }
                            catch (IOException e)
                            {
                                Log.log(e);
                                Platform.runLater(() -> new Alert(AlertType.ERROR, "Could not open browser.  Go to " + DOMAIN + " to download the latest version."));
                            }
                        });
                    }
                }).withStyle(new StyledCSS("download-link")),
                StyledString.s("  (" + description + ")"));
            TextFlow textFlow = new TextFlow();
            textFlow.getChildren().setAll(s.toGUI());
            borderPane.setManaged(false);
            borderPane.resizeRelocate(0, 0, pane.getWidth(), 30);
            borderPane.setLeft(textFlow);
            BorderPane.setAlignment(textFlow, Pos.CENTER_LEFT);
            BorderPane.setMargin(textFlow, new Insets(4));
            SmallDeleteButton button = new SmallDeleteButton();
            button.setOnAction(() -> pane.getChildren().remove(borderPane));
            borderPane.setRight(button);
            BorderPane.setAlignment(button, Pos.CENTER_RIGHT);
            BorderPane.setMargin(button, new Insets(4, 8, 4, 4));
            borderPane.getStyleClass().add("upgrade-banner");
            pane.getChildren().add(borderPane);
        }
    }
    
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
        ClassLoader classLoader = getClass().getClassLoader();
        if (classLoader != null)
            ResourceUtility.setClassLoader(classLoader);

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

        ImporterManager.getInstance().registerImporter(new TextImporter());
        // TODO move this to a plugin:
        ImporterManager.getInstance().registerImporter(new HTMLImporter());
        ImporterManager.getInstance().registerImporter(new ExcelImporter());
        Log.normal("Registered importers");

        CompletableFuture<Optional<UpgradeInfo>> upgradeInfo = new CompletableFuture<>();
        Thread thread = new Thread()
        {
            @OnThread(Tag.Unique)
            public void run()
            {
                upgradeInfo.complete(fetchUpgradeInfo(System.getProperty("columnal.version")));
            }
        };
        thread.setDaemon(true);
        thread.start();

        Parameters parameters = getParameters();
        if (parameters.getUnnamed().isEmpty())
        {
            Log.normal("Showing initial window (no params)");
            InitialWindow.show(primaryStage, upgradeInfo);
        }
        else
        {
            for (String param : parameters.getUnnamed())
            {
                File paramFile = new File(param);
                if (param.endsWith(EXTENSION_INCL_DOT))
                {
                    Log.normal("Showing main window, to load file: \"" + paramFile.getAbsolutePath() + "\"");
                    MainWindow.show(new Stage(), paramFile, new Pair<>(paramFile, FileUtils.readFileToString(paramFile, StandardCharsets.UTF_8)), upgradeInfo);
                }
                else
                {
                    Log.normal("Showing main window, to import file: \"" + paramFile.getAbsolutePath() + "\"");
                    @Nullable MainWindowActions mainWindowActions = InitialWindow.newProject(null, upgradeInfo);
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

    @OnThread(Tag.Unique)
    private Optional<UpgradeInfo> fetchUpgradeInfo(@Nullable String currentVersion)
    {
        if (currentVersion == null)
            return Optional.empty();
        
        final String os;
        if (SystemUtils.IS_OS_WINDOWS)
            os = "windows";
        else if (SystemUtils.IS_OS_MAC)
            os = "macx86";
        else
            return Optional.empty();
        try
        {
            String[] lines = IOUtils.toString(new URL("https", DOMAIN, "/version/" + os + "/" + currentVersion + "/check"), StandardCharsets.UTF_8)
                .split("\\r?\\n");
            HashMap<String, String> props = toLowerCaseTrimmedProperties(lines);
            String latestVersion = props.get("version");
            String description = props.get("description");
            if (latestVersion != null && description != null)
            {
                if (!sameVersion(currentVersion, latestVersion))
                    return Optional.of(new UpgradeInfo(latestVersion, description, new URL("https", DOMAIN, "/version/" + os + "/" + latestVersion + "/download").toURI()));
            }
        }
        catch (IOException | URISyntaxException e)
        {
            Log.log(e);
        }
        return Optional.empty();
    }

    private HashMap<String, String> toLowerCaseTrimmedProperties(String[] lines)
    {
        HashMap<String, String> r = new HashMap<>();
        for (String line : lines)
        {
            int colon = line.indexOf(':');
            if (colon > 0)
            {
                // Lower-case the key for ease of use:
                String key = line.substring(0, colon).toLowerCase().trim();
                // Don't lowercase the value
                String value = line.substring(colon + 1).trim();
                r.put(key, value);
            }
        }
        return r;
    }

    @OnThread(Tag.Any)
    private boolean sameVersion(String currentVersion, String latestVersion)
    {
        // We allow for things like leading zeroes
        int[] cur = extractVersion(currentVersion);
        int[] latest = extractVersion(latestVersion);
        return cur != null && latest != null && Arrays.equals(cur, latest);
    }

    @OnThread(Tag.Any)
    private int @Nullable[] extractVersion(String currentVersion)
    {
        try
        {
            return Arrays.stream(currentVersion.split("\\.")).mapToInt(n -> Integer.parseInt(n)).toArray();
        }
        catch (NumberFormatException e)
        {
            return null;
        }
    }


    // TODO pass -XX:AutoBoxCacheMax= parameter on execution
    public static void main(String[] args)
    {
        Application.launch(Main.class, args);
    }
}
