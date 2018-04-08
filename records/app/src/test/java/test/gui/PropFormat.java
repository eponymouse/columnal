package test.gui;

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
import javafx.scene.shape.Rectangle;
import log.Log;
import org.apache.commons.io.FileUtils;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import org.testfx.framework.junit.ApplicationTest;
import org.testfx.robot.Motion;
import records.data.CellPosition;
import records.data.DataSource;
import records.data.RecordSet;
import records.error.InternalException;
import records.error.UserException;
import records.gui.DataDisplay;
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
import records.importers.gui.ImportChoicesDialog.RecordSetDataDisplay;
import records.importers.gui.ImportChoicesDialog.SrcDataDisplay;
import records.importers.gui.ImporterGUI.PickOrOther;
import test.DummyManager;
import test.TestUtil;
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
    public void testGuessFormatGUI(@From(GenFormattedData.class) GenFormattedData.FormatAndData formatAndData) throws IOException, UserException, InternalException, InterruptedException, ExecutionException, TimeoutException
    {
        File tempFile = writeDataToFile(formatAndData);
        
        CompletableFuture<RecordSet> rsFuture = TextImporter._test_importTextFile(new DummyManager(), tempFile); //, link);
        // GUI should show (after a slight delay), so we should provide inputs:
        TestUtil.sleep(3000);
        @Nullable ImportChoicesDialog<?, ?> maybeICD = TestUtil.<@Nullable ImportChoicesDialog<?, ?>>fx(() -> ImportChoicesDialog._test_getCurrentlyShowing());
        if (maybeICD == null)
        {
            assertNotNull(maybeICD);
            return;
            
        }
        @NonNull ImportChoicesDialog<?, ?> importChoicesDialog = maybeICD;
        checkTrim(importChoicesDialog);
        
        selectGivenComboBoxItem(lookup(".id-guess-charset").query(), new PickOrOther<>(formatAndData.format.initialTextFormat.charset));
        TestUtil.sleep(2000);
        checkTrim(importChoicesDialog);
        selectGivenComboBoxItem(lookup(".id-guess-separator").query(), new PickOrOther<>(formatAndData.format.initialTextFormat.separator));
        TestUtil.sleep(2000);
        checkTrim(importChoicesDialog);
        selectGivenComboBoxItem(lookup(".id-guess-quote").query(), new PickOrOther<>(formatAndData.format.initialTextFormat.quote));
        TestUtil.sleep(2000);
        checkTrim(importChoicesDialog);
        
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
        drag(MouseButton.PRIMARY);
        CellPosition newTopLeft = TestUtil.fx(() -> srcDataDisplay.getPosition()).offsetByRowCols(1 + formatAndData.format.trimChoice.trimFromTop, formatAndData.format.trimChoice.trimFromLeft);
        moveTo(TestUtil.fx(() -> srcGridNode.localToScreen(new Point2D(
            visibleBounds.getXCoord(newTopLeft.columnIndex),
            visibleBounds.getYCoord(newTopLeft.rowIndex)))));
        drop();
        TestUtil.sleep(1000);
        checkTrim(importChoicesDialog);
        @Nullable RecordSet destRS = TestUtil.<@Nullable RecordSet>fx(() -> importChoicesDialog._test_getDestDataDisplay()._test_getRecordSet());
        assertNotNull(destRS);
        if (destRS != null)
            checkDataValues(formatAndData, destRS);
        
        clickOn(".ok-button");
        
        
        
        RecordSet rs = rsFuture.get(5000, TimeUnit.MILLISECONDS);
        checkDataValues(formatAndData, rs);
    }
    
    @OnThread(Tag.Simulation)
    private void checkTrim(ImportChoicesDialog<?, ?> importChoicesDialog) throws InternalException, UserException
    {
        // We don't care what the trim actually is, we just want it to be consistent between:
        // - the trim value held internally
        // - the trim visual bounds held internally
        // - the graphical display of the black rectangle
        // - the size of the destination record set

        SrcDataDisplay srcDataDisplay = importChoicesDialog._test_getSrcDataDisplay();
        // Start with internal trim as the base value:
        TrimChoice internal = TestUtil.fx(() -> srcDataDisplay.getTrim());

        // Check against the visual bounds held internally:
        RectangleBounds expectedTrimBounds = TestUtil.fx(() -> new RectangleBounds(srcDataDisplay.getPosition().offsetByRowCols(1 + internal.trimFromTop, internal.trimFromLeft),
            srcDataDisplay.getBottomRightIncl().offsetByRowCols(- internal.trimFromBottom, -internal.trimFromRight)));
        assertEquals("Internal trim against logical rectangle bounds", expectedTrimBounds, 
            TestUtil.fx(() -> srcDataDisplay._test_getCurSelectionBounds()));
        
        // Check the visible bounds of the graphical rectangle:
        VisibleBounds srcVisibleBounds = TestUtil.fx(() -> importChoicesDialog._test_getSrcGrid().getVisibleBounds());
        @SuppressWarnings("nullness")
        @NonNull Rectangle blackRect = lookup(".prospective-import-rectangle").query();
        assertEquals("Graphical left", TestUtil.fx(() -> srcVisibleBounds.getXCoord(expectedTrimBounds.topLeftIncl.columnIndex)), TestUtil.fx(() -> blackRect.getLayoutX()), 1.0);
        // Because of clamp visible, we can only do a very weak check on right and bottom:
        MatcherAssert.assertThat("Graphical right", TestUtil.fx(() -> srcVisibleBounds.getXCoordAfter(expectedTrimBounds.bottomRightIncl.columnIndex)), Matchers.greaterThanOrEqualTo(TestUtil.fx(() -> blackRect.getLayoutX() + blackRect.getWidth())));
        assertEquals("Graphical top", TestUtil.fx(() -> srcVisibleBounds.getYCoord(expectedTrimBounds.topLeftIncl.rowIndex)), TestUtil.fx(() -> blackRect.getLayoutY()), 1.0);
        MatcherAssert.assertThat("Graphical bottom", TestUtil.fx(() -> srcVisibleBounds.getYCoordAfter(expectedTrimBounds.bottomRightIncl.rowIndex)), Matchers.greaterThanOrEqualTo(TestUtil.fx(() -> blackRect.getLayoutY() + blackRect.getHeight())));
        
        // Check the size of the dest record set:
        RecordSetDataDisplay destDataDisplay = importChoicesDialog._test_getDestDataDisplay();
        RecordSet destRecordSet = TestUtil.<@Nullable RecordSet>fx(() -> destDataDisplay._test_getRecordSet());
        RecordSet srcRecordSet = TestUtil.<@Nullable RecordSet>fx(() -> srcDataDisplay._test_getRecordSet());
        assertNotNull(srcRecordSet);
        assertNotNull(destRecordSet);
        if (srcRecordSet != null && destRecordSet != null)
        {
            assertEquals(srcRecordSet.getColumns().size() - internal.trimFromLeft - internal.trimFromRight,
                destRecordSet.getColumns().size());
            assertEquals(srcRecordSet.getLength() - internal.trimFromTop - internal.trimFromBottom,
                destRecordSet.getLength());
            assertEquals(CellPosition.ORIGIN.offsetByRowCols(internal.trimFromTop, internal.trimFromLeft),
                TestUtil.fx(() -> destDataDisplay.getPosition()));    
        }
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
