package records.importers.manager;

import javafx.stage.FileChooser.ExtensionFilter;
import javafx.stage.Window;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.DataSource;
import records.data.TableManager;
import records.importers.base.Importer;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformConsumer;
import utility.gui.FXUtility;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

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
    public void chooseAndImportFile(Window parent, TableManager tableManager, FXPlatformConsumer<DataSource> onLoad)
    {
        @Nullable File chosen = FXUtility.chooseFileOpen("data.import.dialogTitle", "dataImport", parent,
                registeredImporters.stream().flatMap(imp -> imp.getSupportedFileTypes().stream())
                        .map(p -> new ExtensionFilter(p.getFirst(), p.getSecond()))
                        .toArray(ExtensionFilter[]::new)
        );
        if (chosen != null)
        {
            importFile(parent, tableManager, chosen, onLoad);
        }
    }

    @OnThread(Tag.FXPlatform)
    public void importFile(Window parent, TableManager tableManager, File file, FXPlatformConsumer<DataSource> onLoad)
    {
        // Work out which importer will handle it:
        for (Importer importer : registeredImporters)
        {
            if (importer.getSupportedFileTypes().stream().anyMatch(p -> p.getSecond().stream().anyMatch(ext -> matches(file, ext))))
            {
                importer.importFile(parent, tableManager, file, onLoad);
                break;
            }
        }

        // TODO if none match, give user free choice of importers
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
}
