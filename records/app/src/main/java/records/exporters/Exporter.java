package records.exporters;

import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.i18n.qual.Localized;
import records.data.RecordSet;
import records.data.Table;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;

import java.io.File;

public interface Exporter
{
    /**
     * Do the actual export to the given file.
     */
    @OnThread(Tag.Simulation)
    public void exportData(File destination, Table data) throws UserException, InternalException;

    /**
     * The name of the exporter to display to the user when picking an exporter
     */
    @Localized String getName();

    /**
     * Get the list of supported file types.  Each pair is the localized label for the file type,
     * and a list of file extensions (like "*.txt").
     */
    @OnThread(Tag.Any)
    public ImmutableList<Pair<@Localized String, ImmutableList<String>>> getSupportedFileTypes();
}
