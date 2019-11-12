package test.importExport;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.junit.Test;
import records.rinterop.RData;

import java.io.File;
import java.net.URL;

public class TestRLoadSave
{
    @Test
    public void testImportRData() throws Exception
    {
        @SuppressWarnings("nullness")
        @NonNull URL resource = getClass().getClassLoader().getResource("iris.rds");
        Object loaded = RData.readRData(new File(resource.toURI()));
        
    }
}
