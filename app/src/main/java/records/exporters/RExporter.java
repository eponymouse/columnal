package records.exporters;

import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.i18n.qual.Localized;
import records.data.Table;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import records.rinterop.ConvertToR;
import records.rinterop.ConvertFromR.TableType;
import records.rinterop.RWrite;
import xyz.columnal.utility.TranslationUtility;

import java.io.File;
import java.io.IOException;

public class RExporter implements Exporter
{
    @Override
    public void exportData(File destination, Table data) throws UserException, InternalException
    {
        try
        {
            RWrite.writeRData(destination, ConvertToR.convertTableToR(data.getData(), TableType.TIBBLE));
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
    public ImmutableList<String> getSupportedFileTypes()
    {
        return ImmutableList.of("*.rds", "*.Rdata");
    }
}
