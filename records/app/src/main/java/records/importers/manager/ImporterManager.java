package records.importers.manager;

import com.google.common.collect.ImmutableList;
import javafx.collections.FXCollections;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser.ExtensionFilter;
import javafx.stage.Window;
import org.apache.commons.io.FileUtils;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.CellPosition;
import records.data.DataSource;
import records.data.TableManager;
import records.gui.ErrorableTextField;
import records.gui.ErrorableTextField.ConversionResult;
import records.importers.base.Importer;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.FXPlatformConsumer;
import utility.Pair;
import utility.gui.ErrorableDialog;
import utility.gui.FXUtility;
import utility.gui.GUI;
import utility.gui.TranslationUtility;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ImporterManager
{
    // Singleton:
    private static final ImporterManager SINGLETON = new ImporterManager();

    private final List<Importer> registeredImporters = new ArrayList<>();

    public void registerImporter(Importer importer)
    {
        registeredImporters.add(importer);
    }

    @OnThread(Tag.FXPlatform)
    public void chooseAndImportFile(Window parent, TableManager tableManager, CellPosition destination, FXPlatformConsumer<DataSource> onLoad)
    {
        ArrayList<ExtensionFilter> filters = new ArrayList<>();
        filters.add(new ExtensionFilter(TranslationUtility.getString("importer.all.known"), registeredImporters.stream().flatMap(imp -> imp.getSupportedFileTypes().stream().flatMap((Pair<@Localized String, ImmutableList<String>> p) -> p.getSecond().stream())).collect(Collectors.toList())));
        filters.addAll(registeredImporters.stream().flatMap(imp -> imp.getSupportedFileTypes().stream())
                .map(p -> new ExtensionFilter(p.getFirst(), p.getSecond())).collect(Collectors.toList()));
        filters.add(new ExtensionFilter(TranslationUtility.getString("importer.all.files"), "*.*"));

        @Nullable File chosen = FXUtility.chooseFileOpen("data.import.dialogTitle", "dataImport", parent,
                filters.toArray(new ExtensionFilter[0])
        );
        if (chosen != null)
        {
            try
            {
                importFile(parent, tableManager, destination, chosen, chosen.toURI().toURL(), onLoad);
            }
            catch (MalformedURLException e)
            {
                FXUtility.logAndShowError("error.filePath", e);
            }
        }
    }

    @OnThread(Tag.FXPlatform)
    public void chooseAndImportURL(Window parent, TableManager tableManager, CellPosition destination, FXPlatformConsumer<DataSource> onLoad)
    {
        @Nullable URLImportDetails choice = new ImportURLDialog().showAndWait().orElse(null);
        if (choice != null)
        {
            choice.chosenImporter.importFile(parent, tableManager, destination, choice.downloadedFile, choice.source, onLoad);
        }
    }

    private static File download(URL url) throws IOException
    {
        String suggested = url.getPath();
        int slash = suggested.indexOf('/');
        if (slash != -1)
            suggested = suggested.substring(slash + 1);
        // From https://stackoverflow.com/a/32628056/412908
        suggested = suggested.replaceAll("[^\\p{IsAlphabetic}^\\p{IsDigit}]", "");
        if (suggested.length() < 3)
            suggested += "abc";
        File f = File.createTempFile(suggested, null);
        f.deleteOnExit();
        FileUtils.copyURLToFile(url, f);
        return f;
    }


    @OnThread(Tag.FXPlatform)
    public void importFile(Window parent, TableManager tableManager, CellPosition destination, File file, URL source, FXPlatformConsumer<DataSource> onLoad)
    {
        // Work out which importer will handle it:
        for (Importer importer : registeredImporters)
        {
            if (importer.getSupportedFileTypes().stream().anyMatch((Pair<@Localized String, ImmutableList<String>> p) -> p.getSecond().stream().anyMatch(ext -> matches(file, ext))))
            {
                importer.importFile(parent, tableManager, destination, file, source, onLoad);
                return;
            }
        }

        // If none match, give user free choice of importers:
        new PickImporterDialog(file).showAndWait().ifPresent(importer -> importer.importFile(parent, tableManager, destination, file, source, onLoad));
    }

    private static boolean matches(File file, String wildcard)
    {
        if (wildcard.startsWith("*") && file.getName().endsWith(wildcard.substring(1)))
            return true;
        return false;
    }

    public static ImporterManager getInstance()
    {
        return SINGLETON;
    }

    private static class URLImportDetails
    {
        private final File downloadedFile;
        private final URL source;
        private final Importer chosenImporter;

        private URLImportDetails(File downloadedFile, URL source, Importer chosenImporter)
        {
            this.downloadedFile = downloadedFile;
            this.source = source;
            this.chosenImporter = chosenImporter;
        }
    }
    
    private class ImportURLDialog extends ErrorableDialog<URLImportDetails>
    {
        private final ErrorableTextField<URL> linkField;
        private final PickImporterPane pickImporterPane;

        @OnThread(Tag.FXPlatform)
        public ImportURLDialog()
        {
            linkField = new ErrorableTextField<>(ImporterManager::checkURL);
            Node linkPane = GUI.labelled("importer.link", linkField.getNode());
            pickImporterPane = new PickImporterPane();
            getDialogPane().setContent(new VBox(linkPane, pickImporterPane, getErrorLabel()));
            setResizable(true);
            getDialogPane().getStylesheets().addAll(FXUtility.getSceneStylesheets());
        }

        @Override
        @OnThread(Tag.FXPlatform)
        protected Either<@Localized String, URLImportDetails> calculateResult()
        {
            @Nullable URL url = linkField.valueProperty().get();
            if (url == null)
                return Either.left(TranslationUtility.getString("importer.link.invalid"));

            @Nullable Importer importer = pickImporterPane.get();
            if (importer == null)
                return Either.left(TranslationUtility.getString("importer.noimporter"));

            try
            {
                // TODO show progress while downloading
                return Either.right(new URLImportDetails(download(url), url, importer));
            }
            catch (Exception e)
            {
                return Either.left(TranslationUtility.getString("importer.download.error", e.getLocalizedMessage()));
            }


        }
    }


    private static ConversionResult<URL> checkURL(String src)
    {
        if (src.isEmpty())
            return ConversionResult.error(TranslationUtility.getString("importer.error.url.blank"));

        Exception originalException = null;
        try
        {
            return ConversionResult.success(new URL(src));
        }
        catch (Exception e)
        {
            originalException = e;
        }
        // It may be a file; we should try this before HTTP, but only accept it if it exists:
        try
        {
            URL file = new URL("file://" + src);
            if (new File(file.toURI()).exists())
            {
                return ConversionResult.success(file);
            }
        }
        catch (Exception e)
        {
        }

        // Most likely issue is that it needs http:// on the front:
        try
        {
            return ConversionResult.success(new URL("http://" + src));
        }
        catch (Exception e)
        {
        }

        // Return first exception:
        return ConversionResult.error(originalException.getLocalizedMessage());
    }

    private class PickImporterPane extends BorderPane
    {
        private final ListView<Importer> importerList;

        public PickImporterPane()
        {
            this.importerList = new ListView<>(FXCollections.observableArrayList(registeredImporters));
            importerList.setCellFactory(lv -> {
                return new ListCell<Importer>() {
                    @Override
                    protected void updateItem(@Nullable Importer item, boolean empty)
                    {
                        super.updateItem(item, empty);
                        if (item != null && !empty)
                        {
                            setText(item.getName() + "\n" + item.getSupportedFileTypes().stream().map((Pair<@Localized String, ImmutableList<String>> p) -> p.getFirst() + "(" + p.getSecond().stream().collect(Collectors.joining(", ")) + ")").collect(Collectors.joining("; ")));
                        }
                    }
                };
            });
            importerList.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
            importerList.getSelectionModel().selectFirst();
            setCenter(importerList);
        }

        public @Nullable Importer get()
        {
            return importerList.getSelectionModel().getSelectedItem();
        }
    }

    @OnThread(Tag.FXPlatform)
    private class PickImporterDialog extends ErrorableDialog<Importer>
    {
        private final PickImporterPane pickImporterPane;

        public PickImporterDialog(File src)
        {
            pickImporterPane = new PickImporterPane();
            getDialogPane().setContent(new VBox(new Label("Pick importer for " + src.getName()), pickImporterPane, getErrorLabel()));

        }

        @Override
        protected Either<@Localized String, Importer> calculateResult()
        {
            @Nullable Importer sel = pickImporterPane.get();
            if (sel != null)
                return Either.right(sel);
            else
                return Either.left(TranslationUtility.getString("importer.error.nopick"));
        }
    }
}
