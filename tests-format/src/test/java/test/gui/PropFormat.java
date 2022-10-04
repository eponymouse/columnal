/*
 * Columnal: Safer, smoother data table processing.
 * Copyright (c) Neil Brown, 2016-2020, 2022.
 *
 * This file is part of Columnal.
 *
 * Columnal is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 *
 * Columnal is distributed in the hope that it will be useful, but WITHOUT 
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or 
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for 
 * more details.
 *
 * You should have received a copy of the GNU General Public License along 
 * with Columnal. If not, see <https://www.gnu.org/licenses/>.
 */

package test.gui;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import javafx.application.Platform;
import javafx.geometry.Point2D;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Region;
import javafx.scene.shape.Rectangle;
import org.junit.Assert;
import org.junit.Ignore;
import xyz.columnal.log.Log;
import org.apache.commons.io.FileUtils;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.Test;
import org.junit.runner.RunWith;
import xyz.columnal.data.CellPosition;
import xyz.columnal.id.ColumnId;
import xyz.columnal.data.DataSource;
import xyz.columnal.data.RecordSet;
import xyz.columnal.data.columntype.CleanDateColumnType;
import xyz.columnal.data.columntype.NumericColumnType;
import xyz.columnal.data.columntype.OrBlankColumnType;
import xyz.columnal.data.datatype.DataType.DateTimeInfo;
import xyz.columnal.data.datatype.DataType.DateTimeInfo.DateTimeType;
import xyz.columnal.data.datatype.DataTypeUtility;
import xyz.columnal.data.datatype.TypeManager;
import xyz.columnal.data.unit.Unit;
import xyz.columnal.data.unit.UnitManager;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.gui.grid.RectangleBounds;
import xyz.columnal.gui.grid.VirtualGrid;
import xyz.columnal.gui.grid.VirtualGridSupplier.VisibleBounds;
import xyz.columnal.importers.ColumnInfo;
import xyz.columnal.importers.GuessFormat;
import xyz.columnal.importers.GuessFormat.FinalTextFormat;
import xyz.columnal.importers.GuessFormat.Import;
import xyz.columnal.importers.GuessFormat.InitialTextFormat;
import xyz.columnal.importers.GuessFormat.TrimChoice;
import xyz.columnal.importers.HTMLImporter;
import xyz.columnal.importers.TextImporter;
import xyz.columnal.importers.gui.ImportChoicesDialog;
import xyz.columnal.importers.gui.ImportChoicesDialog.RecordSetDataDisplay;
import xyz.columnal.importers.gui.ImportChoicesDialog.SrcDataDisplay;
import xyz.columnal.importers.gui.ImporterGUI.PickOrOther;
import test.DummyManager;
import test.gen.GenFormattedData;
import test.gen.GenFormattedData.FormatAndData;
import test.gui.trait.ComboUtilTrait;
import test.gui.trait.ScreenshotTrait;
import test.gui.util.FXApplicationTest;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.FXPlatformConsumer;
import xyz.columnal.utility.SimulationConsumer;
import xyz.columnal.utility.TaggedValue;
import xyz.columnal.utility.Utility;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static xyz.columnal.data.datatype.DataType.DateTimeInfo.F.*;

/**
 * Created by neil on 29/10/2016.
 */
@Ignore // TODO restore
@RunWith(JUnitQuickcheck.class)
@OnThread(Tag.Simulation)
public class PropFormat extends FXApplicationTest implements ComboUtilTrait, ScreenshotTrait
{
    @Property(trials = 10)
    @OnThread(Tag.Simulation)
    public void testGuessFormat(@From(GenFormattedData.class) GenFormattedData.FormatAndData formatAndData) throws IOException, UserException, InternalException, InterruptedException, ExecutionException, TimeoutException
    {
        String content = formatAndData.textContent.stream().collect(Collectors.joining("\n"));
        Import<InitialTextFormat, FinalTextFormat> format = GuessFormat.guessTextFormat(DummyManager.make().getTypeManager(), DummyManager.make().getUnitManager(), variousCharsets(formatAndData.textContent, formatAndData.format.initialTextFormat.charset), formatAndData.format.initialTextFormat, formatAndData.format.trimChoice);
        FinalTextFormat ftf = format._test_getResultNoGUI();
        Assert.assertEquals("Failure with content: " + content + "\n\n", formatAndData.format, ftf);
        checkDataValues(formatAndData, TextImporter.makeRecordSet(DummyManager.make().getTypeManager(), writeDataToFile(formatAndData), ftf));
        
        List<DataSource> loaded = new ArrayList<>();
        File htmlFile = File.createTempFile("data", "html");
        FileUtils.writeStringToFile(htmlFile, formatAndData.htmlContent, StandardCharsets.UTF_8);
        SimulationConsumer<ImmutableList<DataSource>> addToLoaded = loaded::addAll;
        FXPlatformConsumer<Integer> loadTable = HTMLImporter.importHTMLFile(null, DummyManager.make(), htmlFile, htmlFile.toURI().toURL(), CellPosition.ORIGIN, addToLoaded).getSecond();
        CompletableFuture<Boolean> f = new CompletableFuture<>();
        Platform.runLater(() -> {
            loadTable.consume(0);
            f.complete(true);
        });
        TFXUtil.sleep(5000);

        @Nullable ImportChoicesDialog<?, ?> maybeICD = TFXUtil.<@Nullable ImportChoicesDialog<?, ?>>fx(() -> ImportChoicesDialog._test_getCurrentlyShowing());
        if (maybeICD == null)
        {
            assertNotNull(maybeICD);
            return;

        }
        // Turn off the whitespace-trimming:
        clickOn(".check-box");
        
        checkTrim(maybeICD);
        
        setTrim(new TrimChoice(formatAndData.format.trimChoice.trimFromTop - 1, formatAndData.format.trimChoice.trimFromBottom, formatAndData.format.trimChoice.trimFromLeft, formatAndData.format.trimChoice.trimFromRight), maybeICD);
        
        clickOn(".ok-button");
        f.get();
        checkDataValues(formatAndData, loaded.get(0).getData());
    }

    @Ignore // TODO restore
    @Property(trials=4)
    @OnThread(Tag.Simulation)
    public void testGuessFormatGUI(@From(GenFormattedData.class) GenFormattedData.FormatAndData formatAndData) throws IOException, UserException, InternalException, InterruptedException, ExecutionException, TimeoutException
    {
        File tempFile = writeDataToFile(formatAndData);
        
        CompletableFuture<RecordSet> rsFuture = TextImporter._test_importTextFile(new DummyManager(), tempFile); //, link);
        // GUI should show (after a slight delay), so we should provide inputs:
        TFXUtil.sleep(8000);
        @Nullable ImportChoicesDialog<?, ?> maybeICD = TFXUtil.<@Nullable ImportChoicesDialog<?, ?>>fx(() -> ImportChoicesDialog._test_getCurrentlyShowing());
        if (maybeICD == null)
        {
            assertNotNull(maybeICD);
            return;
            
        }
        @NonNull ImportChoicesDialog<?, ?> importChoicesDialog = maybeICD;
        checkTrim(importChoicesDialog);
        
        selectGivenComboBoxItem(lookup(".id-guess-charset").query(), new PickOrOther<>(formatAndData.format.initialTextFormat.charset));
        TFXUtil.sleep(2000);
        checkTrim(importChoicesDialog);
        selectGivenComboBoxItem(lookup(".id-guess-separator").query(), new PickOrOther<>(formatAndData.format.initialTextFormat.separator == null ? "" : formatAndData.format.initialTextFormat.separator));
        TFXUtil.sleep(2000);
        checkTrim(importChoicesDialog);
        selectGivenComboBoxItem(lookup(".id-guess-quote").query(), new PickOrOther<>(formatAndData.format.initialTextFormat.quote == null ? "" : formatAndData.format.initialTextFormat.quote));
        TFXUtil.sleep(2000);
        checkTrim(importChoicesDialog);

        setTrim(formatAndData.format.trimChoice, importChoicesDialog);
        
        @Nullable RecordSet destRS = TFXUtil.<@Nullable RecordSet>fx(() -> importChoicesDialog._test_getDestDataDisplay()._test_getRecordSet());
        assertNotNull(destRS);
        if (destRS != null)
            checkDataValues(formatAndData, destRS);

        TFXUtil.fx_(() -> dumpScreenshot());
        clickOn(".ok-button");
        TFXUtil.sleep(5000);
        TFXUtil.fx_(() -> dumpScreenshot());
        
        
        
        RecordSet rs = rsFuture.get(10_000, TimeUnit.MILLISECONDS);
        checkDataValues(formatAndData, rs);
    }

    public void setTrim(TrimChoice trimChoice, @NonNull ImportChoicesDialog<?, ?> importChoicesDialog) throws InternalException, UserException
    {
        Log.debug("Trying to set trim " + trimChoice);
        VirtualGrid srcGrid = importChoicesDialog._test_getSrcGrid();
        SrcDataDisplay srcDataDisplay = importChoicesDialog._test_getSrcDataDisplay();
        // Find the current top-left corner of the selection rectangle:
        RectangleBounds curBounds = TFXUtil.fx(() -> srcDataDisplay._test_getCurSelectionBounds());
        VisibleBounds visibleBounds = TFXUtil.fx(() -> srcGrid.getVisibleBounds());
        Region srcGridNode = TFXUtil.fx(() -> srcGrid.getNode());
        targetWindow(srcGridNode);
        Point2D startDrag = TFXUtil.fx(() -> visibleBounds._test_localToScreen(new Point2D(
                visibleBounds.getXCoord(curBounds.topLeftIncl.columnIndex) + 1.0,
                visibleBounds.getYCoord(curBounds.topLeftIncl.rowIndex) + 1.0)));
        moveTo(startDrag);
        drag(MouseButton.PRIMARY);
        TFXUtil.sleep(500);
        CellPosition newTopLeft = TFXUtil.fx(() -> srcDataDisplay.getPosition()).offsetByRowCols(1 + trimChoice.trimFromTop, trimChoice.trimFromLeft);
        Point2D endDrag = TFXUtil.fx(() -> visibleBounds._test_localToScreen(new Point2D(
                visibleBounds.getXCoord(newTopLeft.columnIndex),
                visibleBounds.getYCoord(newTopLeft.rowIndex))));
        dropTo(endDrag);
        TFXUtil.sleep(1000);
        checkTrim(importChoicesDialog);
        assertEquals("Dragged from " + startDrag + " to " + endDrag, trimChoice, TFXUtil.fx(() -> importChoicesDialog._test_getSrcDataDisplay().getTrim()));
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
        TrimChoice internal = TFXUtil.fx(() -> srcDataDisplay.getTrim());

        // Check against the visual bounds held internally:
        RectangleBounds expectedTrimBounds = TFXUtil.fx(() -> new RectangleBounds(srcDataDisplay.getPosition().offsetByRowCols(1 + internal.trimFromTop, internal.trimFromLeft),
            srcDataDisplay.getBottomRightIncl().offsetByRowCols(- internal.trimFromBottom, -internal.trimFromRight)));
        assertEquals("Internal trim against logical rectangle bounds", expectedTrimBounds, 
            TFXUtil.fx(() -> srcDataDisplay._test_getCurSelectionBounds()));
        
        // Check the visible bounds of the graphical rectangle:
        VisibleBounds srcVisibleBounds = TFXUtil.fx(() -> importChoicesDialog._test_getSrcGrid().getVisibleBounds());
        @SuppressWarnings("nullness")
        @NonNull Rectangle blackRect = lookup(".prospective-import-rectangle").query();
        assertEquals("Graphical left", TFXUtil.fx(() -> srcVisibleBounds.getXCoord(expectedTrimBounds.topLeftIncl.columnIndex)), TFXUtil.fx(() -> blackRect.getLayoutX()), 1.0);
        // Because of clamp visible, we can only do a very weak check on right and bottom:
        MatcherAssert.assertThat("Graphical right", TFXUtil.fx(() -> srcVisibleBounds.getXCoordAfter(expectedTrimBounds.bottomRightIncl.columnIndex)), Matchers.greaterThanOrEqualTo(TFXUtil.fx(() -> blackRect.getLayoutX() + blackRect.getWidth())));
        assertEquals("Graphical top", TFXUtil.fx(() -> srcVisibleBounds.getYCoord(expectedTrimBounds.topLeftIncl.rowIndex)), TFXUtil.fx(() -> blackRect.getLayoutY()), 1.0);
        MatcherAssert.assertThat("Graphical bottom", TFXUtil.fx(() -> srcVisibleBounds.getYCoordAfter(expectedTrimBounds.bottomRightIncl.rowIndex)), Matchers.greaterThanOrEqualTo(TFXUtil.fx(() -> blackRect.getLayoutY() + blackRect.getHeight())));
        
        // Check the size of the dest record set:
        RecordSetDataDisplay destDataDisplay = importChoicesDialog._test_getDestDataDisplay();
        RecordSet destRecordSet = TFXUtil.<@Nullable RecordSet>fx(() -> destDataDisplay._test_getRecordSet());
        RecordSet srcRecordSet = TFXUtil.<@Nullable RecordSet>fx(() -> srcDataDisplay._test_getRecordSet());
        assertNotNull(srcRecordSet);
        assertNotNull(destRecordSet);
        if (srcRecordSet != null && destRecordSet != null)
        {
            assertEquals(srcRecordSet.getColumns().size() - internal.trimFromLeft - internal.trimFromRight,
                destRecordSet.getColumns().size());
            assertEquals(srcRecordSet.getLength() - internal.trimFromTop - internal.trimFromBottom,
                destRecordSet.getLength());
            assertEquals(CellPosition.ORIGIN.offsetByRowCols(internal.trimFromTop, 1 + internal.trimFromLeft),
                TFXUtil.fx(() -> destDataDisplay.getPosition()));    
        }
    }

    private File writeDataToFile(GenFormattedData.FormatAndData formatAndData) throws IOException
    {
        String content = formatAndData.textContent.stream().collect(Collectors.joining("\n"));
        File tempFile = File.createTempFile("test", "txt");
        tempFile.deleteOnExit();
        FileUtils.writeStringToFile(tempFile, content, formatAndData.format.initialTextFormat.charset);
        return tempFile;
    }

    @OnThread(Tag.Simulation)
    private void checkDataValues(GenFormattedData.FormatAndData formatAndData, RecordSet rs) throws UserException, InternalException
    {
        Assert.assertEquals("Column length, given intended trim " + formatAndData.format.trimChoice + " and source length " + formatAndData.textContent.size(), formatAndData.loadedContent.size(), rs.getLength());
        for (int i = 0; i < formatAndData.loadedContent.size(); i++)
        {
            Assert.assertEquals("Right row length for row " + i + " (+" + formatAndData.format.trimChoice.trimFromTop + "):\n" + formatAndData.textContent.get(i + formatAndData.format.trimChoice.trimFromTop) + "\n" + Utility.listToString(formatAndData.loadedContent.get(i)) + " guessed: " + "", 
                formatAndData.loadedContent.get(i).size(), rs.getColumns().size());
            for (int c = 0; c < rs.getColumns().size(); c++)
            {
                @Value Object expected = formatAndData.loadedContent.get(i).get(c);
                @Value Object loaded = rs.getColumns().get(c).getType().getCollapsed(i);
                assertEquals("Column " + c + " expected: {{{" + expected + "}}} was {{{" + loaded + "}}} from row " + formatAndData.textContent.get(i + formatAndData.format.trimChoice.trimFromTop), 0, Utility.compareValues(expected, loaded));
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

    @Test
    public void testAwkwardDate() throws Exception
    {
        ImmutableList<String> textRows = ImmutableList.of("19/10/54", "30/12/34");
        ImmutableList<List<@Value Object>> target = ImmutableList.of(
            ImmutableList.of(DataTypeUtility.value(new DateTimeInfo(DateTimeType.YEARMONTHDAY), LocalDate.of(1954, 10, 19))),
            ImmutableList.of(DataTypeUtility.value(new DateTimeInfo(DateTimeType.YEARMONTHDAY), LocalDate.of(2034, 12, 30)))
        );
        testGuessFormatGUI(new FormatAndData(
            new FinalTextFormat(new InitialTextFormat(StandardCharsets.UTF_8, ",", null), new TrimChoice(0, 0, 0, 0), ImmutableList.of(new ColumnInfo(new CleanDateColumnType(DateTimeType.YEARMONTHDAY, true, DateTimeInfo.m(" ", DAY, MONTH_NUM, YEAR2), LocalDate::from), new ColumnId("A")))),
                textRows, "<html><body><table><tr><td>" + textRows.stream().collect(Collectors.joining("</td></tr><tr><td>")) + "</table>", target)
        );
    }

    @Test
    public void testNumOrMissing() throws Exception
    {
        TypeManager typeManager = new TypeManager(new UnitManager());
        ImmutableList<String> textRows = ImmutableList.of("1", "3", "NA", "-8910.444", "NA");
        ImmutableList<List<@Value Object>> target = Utility.mapListI(ImmutableList.of(
            new TaggedValue(1, DataTypeUtility.value(1), typeManager.getMaybeType()),
            new TaggedValue(1, DataTypeUtility.value(3), typeManager.getMaybeType()),
            new TaggedValue(0, null, typeManager.getMaybeType()),
            new TaggedValue(1, DataTypeUtility.value(new BigDecimal ("-8910.444")), typeManager.getMaybeType()),
            new TaggedValue(0, null, typeManager.getMaybeType())
                ), ImmutableList::of);
        
        testGuessFormatGUI(new FormatAndData(
                new FinalTextFormat(new InitialTextFormat(StandardCharsets.UTF_8, ",", null), new TrimChoice(0, 0, 0, 0), ImmutableList.of(new ColumnInfo(new OrBlankColumnType(new NumericColumnType(Unit.SCALAR, 0, null, null), "NA"), new ColumnId("A")))),
                textRows, "<html><body><table><tr><td>" + textRows.stream().collect(Collectors.joining("</td></tr><tr><td>")) + "</table>", target)
        );
    }
}
