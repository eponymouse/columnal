package test;

import annotation.qual.Value;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import org.apache.commons.io.FileUtils;
import org.junit.runner.RunWith;
import records.data.DataSource;
import records.error.InternalException;
import records.error.UserException;
import records.importers.ChoicePoint;
import records.importers.ChoicePoint.Choice;
import records.importers.GuessFormat;
import records.importers.GuessFormat.ColumnCountChoice;
import records.importers.GuessFormat.HeaderRowChoice;
import records.importers.GuessFormat.SeparatorChoice;
import records.importers.TextFormat;
import records.importers.TextImport;
import test.TestUtil.ChoicePick;
import test.gen.GenFormattedData;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Utility;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;
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
        ChoicePoint<TextFormat> formatChoicePoint = GuessFormat.guessTextFormat(DummyManager.INSTANCE.getUnitManager(), formatAndData.content);
        ChoicePick[] picks = new ChoicePick[] {
            new ChoicePick<HeaderRowChoice>(HeaderRowChoice.class, new HeaderRowChoice(formatAndData.format.headerRows)),
            new ChoicePick<SeparatorChoice>(SeparatorChoice.class, new SeparatorChoice("" + formatAndData.format.separator)),
            new ChoicePick<ColumnCountChoice>(ColumnCountChoice.class, new ColumnCountChoice(formatAndData.format.columnTypes.size()))
        };
        assertEquals("Failure with content: " + content, formatAndData.format, TestUtil.pick(formatChoicePoint, picks));
        File tempFile = File.createTempFile("test", "txt");
        tempFile.deleteOnExit();
        FileUtils.writeStringToFile(tempFile, content, Charset.forName("UTF-8"));
        DataSource ds = TestUtil.pick(TextImport._test_importTextFile(new DummyManager(), tempFile), picks);
        assertEquals("Right column length", formatAndData.loadedContent.size(), ds.getData().getLength());
        for (int i = 0; i < formatAndData.loadedContent.size(); i++)
        {
            assertEquals("Right row length for row " + i + " (+" + formatAndData.format.headerRows + "):\n" + formatAndData.content.get(i + formatAndData.format.headerRows) + "\n" + format + "\n" + Utility.listToString(formatAndData.loadedContent.get(i)) + " guessed: " + "", ds.getData().getColumns().size(), formatAndData.loadedContent.get(i).size());
            for (int c = 0; c < ds.getData().getColumns().size(); c++)
            {
                @Value Object expected = formatAndData.loadedContent.get(i).get(c);
                @Value Object loaded = ds.getData().getColumns().get(c).getType().getCollapsed(i);
                assertEquals("Column " + c + " expected: " + expected + " was " + loaded + " from row " + formatAndData.content.get(i + 1), 0, Utility.compareValues(expected, loaded));
            }
        }
    }

}
