package test;

import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import org.apache.commons.io.FileUtils;
import org.junit.runner.RunWith;
import records.data.DataSource;
import records.data.columntype.CleanDateColumnType;
import records.data.columntype.NumericColumnType;
import records.error.InternalException;
import records.error.UserException;
import records.importers.GuessFormat;
import records.importers.ColumnInfo;
import records.importers.TextFormat;
import records.importers.TextImport;
import test.gen.GenFormat;
import test.gen.GenFormattedData;
import test.gen.GenRandom;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Utility;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

/**
 * Created by neil on 29/10/2016.
 */
@RunWith(JUnitQuickcheck.class)
public class PropFormat
{
    @Property
    @OnThread(Tag.Simulation)
    public void testGuessFormat(@From(GenFormattedData.class) GenFormattedData.FormatAndData formatAndData) throws IOException, UserException, InternalException
    {
        String content = formatAndData.content.stream().collect(Collectors.joining("\n"));
        String format = formatAndData.format.toString();
        assertEquals("Failure with content: " + content, formatAndData.format, GuessFormat.guessTextFormat(DummyManager.INSTANCE.getUnitManager(), formatAndData.content));
        File tempFile = File.createTempFile("test", "txt");
        tempFile.deleteOnExit();
        FileUtils.writeStringToFile(tempFile, content, Charset.forName("UTF-8"));
        DataSource ds = TextImport.importTextFile(new DummyManager(), tempFile);
        assertEquals("Right column length", formatAndData.loadedContent.size(), ds.getData().getLength());
        for (int i = 0; i < formatAndData.loadedContent.size(); i++)
        {
            assertEquals("Right row length " + content + " " + format, ds.getData().getColumns().size(), formatAndData.loadedContent.get(i).size());
            for (int c = 0; c < ds.getData().getColumns().size(); c++)
            {
                List<Object> expected = formatAndData.loadedContent.get(i).get(c);
                List<Object> loaded = ds.getData().getColumns().get(c).getType().getCollapsed(i);
                assertEquals("Expected: " + expected + " was " + loaded, 0, Utility.compareLists(expected, loaded));
            }
        }
    }
}
