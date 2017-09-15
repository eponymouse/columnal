package records.importers.base;

import com.google.common.collect.ImmutableList;
import javafx.stage.Window;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.DataSource;
import records.data.TableManager;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformConsumer;
import utility.Pair;

import java.io.File;

public interface Importer
{
    /**
     * Get the list of supported file types.  Each pair is the localized label for the file type,
     * and a list of file extensions (like "*.txt").
     */
    @OnThread(Tag.Any)
    public ImmutableList<Pair<@Localized String, ImmutableList<String>>> getSupportedFileTypes();

    /**
     * Attempt to load the given file
     *
     * @param parent The window parent, in case you need to show any dialogs
     * @param tableManager The destination table manager
     * @param src The file to read from
     * @param onLoad The callback to call if the load is successful
     */
    @OnThread(Tag.FXPlatform)
    public void importFile(Window parent, TableManager tableManager, File src, FXPlatformConsumer<DataSource> onLoad);
}
