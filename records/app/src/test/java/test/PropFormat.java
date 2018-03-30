package test;

import annotation.qual.Value;
import com.google.common.collect.ImmutableMap;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.When;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import javafx.embed.swing.JFXPanel;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Region;
import log.Log;
import org.apache.commons.io.FileUtils;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import org.testfx.framework.junit.ApplicationTest;
import org.testfx.robot.Motion;
import records.data.CellPosition;
import records.data.DataSource;
import records.data.RecordSet;
import records.error.InternalException;
import records.error.UserException;
import records.gui.grid.RectangleBounds;
import records.gui.grid.VirtualGrid;
import records.gui.grid.VirtualGridSupplier.VisibleBounds;
import records.importers.GuessFormat;
import records.importers.GuessFormat.FinalTextFormat;
import records.importers.GuessFormat.Import;
import records.importers.GuessFormat.InitialTextFormat;
import records.importers.GuessFormat.TrimChoice;
import records.importers.TextImporter;
import records.importers.gui.ImportChoicesDialog;
import records.importers.gui.ImportChoicesDialog.SrcDataDisplay;
import records.importers.gui.ImporterGUI.PickOrOther;
import test.gen.GenFormattedData;
import test.gui.ComboUtilTrait;
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
import static org.junit.Assert.assertNotNull;

/**
 * Created by neil on 29/10/2016.
 */
@RunWith(JUnitQuickcheck.class)
public class PropFormat extends ApplicationTest implements ComboUtilTrait
{
    @Property(trials = 25)
    @OnThread(Tag.Simulation)
    public void testGuessFormat(@From(GenFormattedData.class) GenFormattedData.FormatAndData formatAndData) throws IOException, UserException, InternalException, InterruptedException, ExecutionException, TimeoutException
    {
        String content = formatAndData.content.stream().collect(Collectors.joining("\n"));
        Import<InitialTextFormat, FinalTextFormat> format = GuessFormat.guessTextFormat(DummyManager.INSTANCE.getTypeManager(), DummyManager.INSTANCE.getUnitManager(), variousCharsets(formatAndData.content, formatAndData.format.initialTextFormat.charset), formatAndData.format.initialTextFormat, formatAndData.format.trimChoice);
        @OnThread(Tag.Simulation) FinalTextFormat ftf = format._test_getResultNoGUI();
        assertEquals("Failure with content: " + content, formatAndData.format, ftf);
        checkDataValues(formatAndData, TextImporter.makeRecordSet(DummyManager.INSTANCE.getTypeManager(), writeDataToFile(formatAndData), ftf));
    }

    @Property(trials=10)
    @OnThread(Tag.Simulation)
    public void testGuessFormatGUI(@When(seed=5L) @From(GenFormattedData.class) GenFormattedData.FormatAndData formatAndData) throws IOException, UserException, InternalException, InterruptedException, ExecutionException, TimeoutException
    {
        File tempFile = writeDataToFile(formatAndData);
        
        CompletableFuture<RecordSet> rsFuture = TextImporter._test_importTextFile(new DummyManager(), tempFile); //, link);
        // GUI should show (after a slight delay), so we should provide inputs:
        TestUtil.sleep(3000);
        selectGivenComboBoxItem(lookup(".id-guess-charset").query(), new PickOrOther<>(formatAndData.format.initialTextFormat.charset));
        selectGivenComboBoxItem(lookup(".id-guess-separator").query(), new PickOrOther<>(formatAndData.format.initialTextFormat.separator));
        selectGivenComboBoxItem(lookup(".id-guess-quote").query(), new PickOrOther<>(formatAndData.format.initialTextFormat.quote));
        @Nullable ImportChoicesDialog<?, ?> importChoicesDialog = TestUtil.<@Nullable ImportChoicesDialog<?, ?>>fx(() -> ImportChoicesDialog._test_getCurrentlyShowing());
        if (importChoicesDialog != null)
        {
            Log.debug("Trying to set trim " + formatAndData.format.trimChoice);
            VirtualGrid srcGrid = importChoicesDialog._test_getSrcGrid();
            SrcDataDisplay srcDataDisplay = importChoicesDialog._test_getSrcDataDisplay();
            // Find the current top-left corner of the selection rectangle:
            RectangleBounds curBounds = TestUtil.fx(() -> srcDataDisplay._test_getCurSelectionBounds());
            VisibleBounds visibleBounds = TestUtil.fx(() -> srcGrid.getVisibleBounds());
            Region srcGridNode = TestUtil.fx(() -> srcGrid.getNode());
            targetWindow(srcGridNode);
            moveTo(TestUtil.fx(() -> srcGridNode.localToScreen(new Point2D(
                visibleBounds.getXCoord(curBounds.topLeftIncl.columnIndex),
                visibleBounds.getYCoord(curBounds.topLeftIncl.rowIndex)))));
            press(MouseButton.PRIMARY);
            CellPosition newTopLeft = TestUtil.fx(() -> srcDataDisplay.getPosition()).offsetByRowCols(1 + formatAndData.format.trimChoice.trimFromTop, formatAndData.format.trimChoice.trimFromLeft);
            moveTo(TestUtil.fx(() -> srcGridNode.localToScreen(new Point2D(
                visibleBounds.getXCoord(newTopLeft.columnIndex),
                visibleBounds.getYCoord(newTopLeft.rowIndex)))));
            release(MouseButton.PRIMARY);
            @NonNull ImportChoicesDialog<?, ?> icdFinal = importChoicesDialog;
            @Nullable RecordSet destRS = TestUtil.<@Nullable RecordSet>fx(() -> icdFinal._test_getDestRecordSet());
            assertNotNull(destRS);
            if (destRS != null)
                checkDataValues(formatAndData, destRS);
        }
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
        assertEquals("Column length, given intended trim " + formatAndData.format.trimChoice + " and source length " + formatAndData.content.size(), formatAndData.loadedContent.size(), rs.getLength());
        for (int i = 0; i < formatAndData.loadedContent.size(); i++)
        {
            assertEquals("Right row length for row " + i + " (+" + formatAndData.format.trimChoice.trimFromTop + "):\n" + formatAndData.content.get(i + formatAndData.format.trimChoice.trimFromTop) + "\n" + Utility.listToString(formatAndData.loadedContent.get(i)) + " guessed: " + "", 
                formatAndData.loadedContent.get(i).size(), rs.getColumns().size());
            for (int c = 0; c < rs.getColumns().size(); c++)
            {
                @Value Object expected = formatAndData.loadedContent.get(i).get(c);
                @Value Object loaded = rs.getColumns().get(c).getType().getCollapsed(i);
                assertEquals("Column " + c + " expected: {{{" + expected + "}}} was {{{" + loaded + "}}} from row " + formatAndData.content.get(i + 1), 0, Utility.compareValues(expected, loaded));
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
