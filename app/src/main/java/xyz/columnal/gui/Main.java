/*
 * Columnal: Safer, smoother data table processing.
 * Copyright (c) Neil Brown, 2016-2020, 2022.
 *
 * This file is part of Columnal.
 *
 * Columnal is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 *
 * Columnal is distributed in the hope that it will be useful, but WITHOUT 
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or 
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for 
 * more details.
 *
 * You should have received a copy of the GNU General Public License along 
 * with Columnal. If not, see <https://www.gnu.org/licenses/>.
 */

package xyz.columnal.gui;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.scene.text.TextFlow;
import javafx.stage.Stage;
import xyz.columnal.log.ErrorHandler;
import xyz.columnal.log.Log;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.SystemUtils;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.exporters.CSVExporter;
import xyz.columnal.exporters.ExcelExporter;
import xyz.columnal.exporters.HTMLExporter;
import xyz.columnal.exporters.RExporter;
import xyz.columnal.exporters.manager.ExporterManager;
import xyz.columnal.gui.MainWindow.MainWindowActions;
import xyz.columnal.importers.RImporter;
import xyz.columnal.utility.gui.Clickable;
import xyz.columnal.importers.ExcelImporter;
import xyz.columnal.importers.HTMLImporter;
import xyz.columnal.importers.TextImporter;
import xyz.columnal.importers.manager.ImporterManager;
import xyz.columnal.styled.StyledCSS;
import xyz.columnal.styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.ResourceUtility;
import xyz.columnal.utility.Utility;
import xyz.columnal.utility.gui.FXUtility;
import xyz.columnal.utility.gui.SmallDeleteButton;

import javax.swing.SwingUtilities;
import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Optional;
import java.util.UUID;
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
        public void showIn(Pane pane, int childPosition)
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
            pane.getChildren().add(childPosition, borderPane);
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
            public @OnThread(Tag.Simulation) void showError(@Localized String title, Function<@Localized String, @Localized String> errWrap, Exception e)
            {
                Platform.runLater(() -> FXUtility.showError(title, errWrap, e));
            }
        });
        ClassLoader classLoader = getClass().getClassLoader();
        if (classLoader != null)
            ResourceUtility.setClassLoader(classLoader);

        initialise();

        CompletableFuture<Optional<UpgradeInfo>> upgradeInfo = new CompletableFuture<>();
        Thread thread = new Thread()
        {
            @OnThread(Tag.Worker)
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
                else if (!param.startsWith("-") && !param.equals(getClass().getName()))
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
                else
                {
                    Log.normal("Showing initial window (no file params)");
                    InitialWindow.show(primaryStage, upgradeInfo);
                }
            }
        }
    }

    @OnThread(Tag.FXPlatform)
    public static void initialise()
    {
        FXUtility.ensureFontLoaded("Kalam-Regular.ttf");
        FXUtility.ensureFontLoaded("NotoMono-Regular.ttf");
        FXUtility.ensureFontLoaded("NotoSans-Regular.ttf");
        FXUtility.ensureFontLoaded("NotoSansSymbols-Regular.ttf");
        FXUtility.ensureFontLoaded("NotoSansSymbols2-Regular.ttf");
        FXUtility.ensureFontLoaded("NotoSans-Italic.ttf");
        FXUtility.ensureFontLoaded("NotoSans-Bold.ttf");
        FXUtility.ensureFontLoaded("NotoSans-BoldItalic.ttf");
        FXUtility.ensureFontLoaded("SourceCodePro-Regular.ttf");
        FXUtility.ensureFontLoaded("SourceCodePro-Semibold.ttf");
        Log.normal("Loaded fonts");

        ImporterManager.getInstance().registerImporter(new TextImporter());
        ImporterManager.getInstance().registerImporter(new HTMLImporter());
        ImporterManager.getInstance().registerImporter(new ExcelImporter());
        ImporterManager.getInstance().registerImporter(new RImporter());
        Log.normal("Registered importers");
        ExporterManager.getInstance().registerExporter(new CSVExporter());
        ExporterManager.getInstance().registerExporter(new HTMLExporter());
        ExporterManager.getInstance().registerExporter(new ExcelExporter());
        ExporterManager.getInstance().registerExporter(new RExporter());
        Log.normal("Registered exporters");
    }

    @OnThread(Tag.Worker)
    private Optional<UpgradeInfo> fetchUpgradeInfo(@Nullable String currentVersion)
    {
        // If in development or version totally broken, don't worry about checking:
        if (currentVersion == null)
            return Optional.empty();
        
        final String os;
        if (SystemUtils.IS_OS_WINDOWS)
            os = "windows";
        else if (SystemUtils.IS_OS_MAC)
            os = "mac-x86";
        else if (SystemUtils.IS_OS_LINUX)
            os = "linux-x86";
        else
            return Optional.empty();
        
        final String uuid;
        @Nullable String readUuid = Utility.getProperty("usage.stats", "uuid");
        if (readUuid == null || readUuid.length() < 10 || readUuid.length() > 50)
        {
            uuid = UUID.randomUUID().toString();
            Utility.setProperty("usage.stats", "uuid", uuid);
        }
        else
            uuid = readUuid;
        
        
        try
        {
            String[] lines = Utility.splitLines(IOUtils.toString(new URL("https", DOMAIN, "/version/" + os + "/" + currentVersion + "/check/" + uuid), StandardCharsets.UTF_8));
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

    @OnThread(Tag.Any)
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
