package records.exporters;

import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.i18n.qual.Localized;
import records.data.Table;
import records.error.InternalException;
import records.error.UserException;
import records.rinterop.RData;
import utility.Pair;
import utility.TranslationUtility;

import java.io.File;
import java.io.IOException;

public class RExporter implements Exporter
{
    @Override
    public void exportData(File destination, Table data) throws UserException, InternalException
    {
        try
        {
            RData.writeRData(destination, RData.convertTableToR(data.getData()));
        }
        catch (IOException e)
        {
            throw new UserException("Problem writing to file", e);
        }
    }

    @Override
    public @Localized String getName()
    {
        return TranslationUtility.getString("importer.r.files");
    }

    @Override
    public ImmutableList<Pair<@Localized String, ImmutableList<String>>> getSupportedFileTypes()
    {
        return ImmutableList.of(new Pair<@Localized String, ImmutableList<String>>(TranslationUtility.getString("importer.r.files"), ImmutableList.of("*.rds", "*.Rdata")));
    }
}
