package test;

import annotation.qual.Value;
import com.google.common.collect.ImmutableMap;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.When;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import javafx.embed.swing.JFXPanel;
import org.apache.commons.io.FileUtils;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import org.testfx.framework.junit.ApplicationTest;
import records.data.DataSource;
import records.data.RecordSet;
import records.error.InternalException;
import records.error.UserException;
import records.importers.GuessFormat;
import records.importers.GuessFormat.FinalTextFormat;
import records.importers.GuessFormat.Import;
import records.importers.GuessFormat.InitialTextFormat;
import records.importers.GuessFormat.TrimChoice;
import records.importers.TextImporter;
import test.gen.GenFormattedData;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Utility;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

/**
 * Created by neil on 29/10/2016.
 */
@RunWith(JUnitQuickcheck.class)
public class PropFormat extends ApplicationTest
{
    @Property(trials = 25)
    @OnThread(Tag.Simulation)
    public void testGuessFormat(@When(seed=1L) @From(GenFormattedData.class) GenFormattedData.FormatAndData formatAndData) throws IOException, UserException, InternalException, InterruptedException, ExecutionException, TimeoutException
    {
        String content = formatAndData.content.stream().collect(Collectors.joining("\n"));
        Import<InitialTextFormat, FinalTextFormat> format = GuessFormat.guessTextFormat(DummyManager.INSTANCE.getTypeManager(), DummyManager.INSTANCE.getUnitManager(), variousCharsets(formatAndData.content, formatAndData.format.initialTextFormat.charset), formatAndData.format.initialTextFormat, formatAndData.format.trimChoice);
        @OnThread(Tag.Simulation) FinalTextFormat ftf = format._test_getResultNoGUI();
        assertEquals("Failure with content: " + content, formatAndData.format, ftf);
        checkDataValues(formatAndData, TextImporter.makeRecordSet(DummyManager.INSTANCE.getTypeManager(), writeDataToFile(formatAndData), ftf));
    }

    @Property(trials=10)
    @OnThread(Tag.Simulation)
    public void testGuessFormatGUI(@When(seed=1L) @From(GenFormattedData.class) GenFormattedData.FormatAndData formatAndData) throws IOException, UserException, InternalException, InterruptedException, ExecutionException, TimeoutException
    {
        File tempFile = writeDataToFile(formatAndData);
        
        CompletableFuture<RecordSet> rsFuture = TextImporter._test_importTextFile(new DummyManager(), tempFile); //, link);
        // GUI should show (after a slight delay), so we should provide inputs:
        TestUtil.sleep(3000);
        // Dummy for now:
        clickOn(".ok-button");
        
        
        
        RecordSet rs = rsFuture.get(5000, TimeUnit.MILLISECONDS);
        checkDataValues(formatAndData, rs);
    }

    private File writeDataToFile(@From(GenFormattedData.class) @When(seed = 1L) GenFormattedData.FormatAndData formatAndData) throws IOException
    {
        String content = formatAndData.content.stream().collect(Collectors.joining("\n"));
        File tempFile = File.createTempFile("test", "txt");
        tempFile.deleteOnExit();
        FileUtils.writeStringToFile(tempFile, content, formatAndData.format.initialTextFormat.charset);
        return tempFile;
    }

    @OnThread(Tag.Simulation)
    private void checkDataValues(@From(GenFormattedData.class) @When(seed = 1L) GenFormattedData.FormatAndData formatAndData, RecordSet rs) throws UserException, InternalException
    {
        assertEquals("Right column length", formatAndData.loadedContent.size(), rs.getLength());
        for (int i = 0; i < formatAndData.loadedContent.size(); i++)
        {
            assertEquals("Right row length for row " + i + " (+" + formatAndData.format.trimChoice.trimFromTop + "):\n" + formatAndData.content.get(i + formatAndData.format.trimChoice.trimFromTop) + "\n" + Utility.listToString(formatAndData.loadedContent.get(i)) + " guessed: " + "", 
                rs.getColumns().size(), formatAndData.loadedContent.get(i).size());
            for (int c = 0; c < rs.getColumns().size(); c++)
            {
                @Value Object expected = formatAndData.loadedContent.get(i).get(c);
                @Value Object loaded = rs.getColumns().get(c).getType().getCollapsed(i);
                assertEquals("Column " + c + " expected: " + expected + " was " + loaded + " from row " + formatAndData.content.get(i + 1), 0, Utility.compareValues(expected, loaded));
            }
        }
    }

    private Map<Charset, List<String>> variousCharsets(List<String> content, Charset actual)
    {
        return ImmutableMap.of(actual, content);
        /*
        Map<Charset, List<String>> m = new HashMap<>();
        String joined = content.stream().collect(Collectors.joining("\n"));
        for (Charset c : Arrays.asList(Charset.forName("UTF-8"), Charset.forName("ISO-8859-1"), Charset.forName("UTF-16")))
        {
            m.put(c, Arrays.asList(new String(joined.getBytes(actual), c).split("\n")));
        }

        return m;
        */
    }

}
