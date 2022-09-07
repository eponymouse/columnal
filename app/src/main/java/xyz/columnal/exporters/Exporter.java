package records.exporters;

import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.i18n.qual.Localized;
import xyz.columnal.data.RecordSet;
import xyz.columnal.data.Table;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.Pair;

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
     * Get the list of supported file types.  Each item is a file extension (like "*.txt").
     */
    @OnThread(Tag.Any)
    public ImmutableList<String> getSupportedFileTypes();
}
