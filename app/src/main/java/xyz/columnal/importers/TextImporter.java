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

package xyz.columnal.importers;

import annotation.qual.ImmediateValue;
import annotation.units.TableDataRowIndex;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;
import javafx.stage.Window;
import xyz.columnal.data.CellPosition;
import xyz.columnal.data.Column;
import xyz.columnal.data.DataSource;
import xyz.columnal.data.EditableRecordSet;
import xyz.columnal.data.ImmediateDataSource;
import xyz.columnal.data.KnownLengthRecordSet;
import xyz.columnal.data.RecordSet;
import xyz.columnal.data.TableManager;
import xyz.columnal.data.TextFileColumn;
import xyz.columnal.id.ColumnId;
import xyz.columnal.log.Log;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.Table.InitialLoadDetails;
import xyz.columnal.data.columntype.BlankColumnType;
import xyz.columnal.data.columntype.CleanDateColumnType;
import xyz.columnal.data.columntype.NumericColumnType;
import xyz.columnal.data.columntype.OrBlankColumnType;
import xyz.columnal.data.columntype.TextColumnType;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.NumberInfo;
import xyz.columnal.data.datatype.DataTypeUtility;
import xyz.columnal.data.datatype.TypeManager;
import xyz.columnal.data.unit.Unit;
import xyz.columnal.error.FetchException;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.importers.GuessFormat.FinalTextFormat;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.IdentifierUtility;
import xyz.columnal.utility.function.simulation.SimulationConsumerNoError;
import xyz.columnal.utility.function.simulation.SimulationFunction;
import xyz.columnal.utility.TaggedValue;
import xyz.columnal.utility.Utility;
import xyz.columnal.utility.Utility.ReadState;
import xyz.columnal.utility.Workers;
import xyz.columnal.utility.Workers.Priority;
import xyz.columnal.utility.gui.FXUtility;
import xyz.columnal.utility.TranslationUtility;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

/**
 * Created by neil on 31/10/2016.
 */
public class TextImporter implements Importer
{
    @OnThread(Tag.Any)
    @Override
    public ImmutableList<String> getSupportedFileTypes()
    {
        return ImmutableList.of("*.txt", "*.csv");
    }

    @Override
    public @OnThread(Tag.FXPlatform) void importFile(Window parent, TableManager tableManager, CellPosition destination, File src, URL origin, SimulationConsumerNoError<DataSource> recordLoadedTable)
    {
        Workers.onWorkerThread("GuessFormat data", Priority.LOAD_FROM_DISK, () ->
        {
            try
            {
                importTextFile(parent, tableManager, src, destination, rs -> recordLoadedTable.consume(rs));
            }
            catch (IOException ex)
            {
                FXUtility.logAndShowError("import.text.error", ex);
            }
        });
    }

    @Override
    public @Localized String getName()
    {
        return TranslationUtility.getString("importer.files.text");
    }

    @OnThread(Tag.Simulation)
    public static void importTextFile(@Nullable Window parentWindow, TableManager mgr, File textFile, CellPosition destination, SimulationConsumerNoError<DataSource> then) throws IOException
    {
        Map<Charset, List<String>> initial = getInitial(textFile);
        GuessFormat.guessTextFormatGUI_Then(parentWindow, mgr, textFile, Files.getNameWithoutExtension(textFile.getName()), initial, impInfo ->
        {
            try
            {
                Log.debug("Importing format " + impInfo.getFormat());
                DataSource ds = makeDataSource(mgr, textFile, impInfo.getInitialLoadDetails(destination), impInfo.getFormat());
                then.consume(ds);
            }
            catch (InternalException | UserException | IOException e)
            {
                // TODO display
                Log.log(e);
            }
        });
    }

    @OnThread(Tag.Simulation)
    public static CompletableFuture<RecordSet> _test_importTextFile(TableManager mgr, File textFile) throws IOException, InternalException, UserException, InterruptedException, ExecutionException, TimeoutException
    {
        CompletableFuture<RecordSet> f = new CompletableFuture<>();
        // TODO need some test code to operate the GUI
        importTextFile(null, mgr, textFile, CellPosition.ORIGIN, data -> {
            try
            {
                f.complete(data.getData());
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        });
        return f;
    }

    @OnThread(Tag.Simulation)
    private static DataSource makeDataSource(TableManager mgr, final File textFile, final InitialLoadDetails initialLoadDetails, final FinalTextFormat format) throws IOException, InternalException, UserException
    {
        RecordSet rs = makeRecordSet(mgr.getTypeManager(), textFile, format);
        //if (importInfo.linkFile)
            //return new LinkedDataSource(mgr, importInfo.tableName, rs, MainLexer.TEXTFILE, textFile);
        //else
            return new ImmediateDataSource(mgr, initialLoadDetails, new EditableRecordSet(rs));
    }

    @OnThread(Tag.Simulation)
    public static RecordSet makeSrcRecordSet(File textFile, Charset charset, @Nullable String separator, @Nullable String quote, int totalColumns) throws IOException, InternalException, UserException
    {
        List<SimulationFunction<RecordSet, Column>> columns = new ArrayList<>();
        for (int i = 0; i < totalColumns; i++)
        {
            // Must be one per column:
            ReadState reader = Utility.skipFirstNRows(textFile, charset, 0);
            int iFinal = i;
            columns.add(rs -> {
                ColumnId columnName = new ColumnId(IdentifierUtility.identNum("Column", (iFinal + 1)));
                return TextFileColumn.stringColumn(rs, reader, separator, quote, columnName, iFinal, totalColumns);
            });
        }

        return new KnownLengthRecordSet(columns, Utility.countLines(textFile, charset));
    }

    @OnThread(Tag.Simulation)
    public static RecordSet makeRecordSet(TypeManager typeManager, File textFile, FinalTextFormat format) throws IOException, InternalException, UserException
    {
        List<SimulationFunction<RecordSet, Column>> columns = new ArrayList<>();
        int totalColumns = format.columnTypes.size();
        for (int i = 0; i < totalColumns; i++)
        {
            // Must be one per column:
            ReadState reader = Utility.skipFirstNRows(textFile, format.initialTextFormat.charset, format.trimChoice.trimFromTop);

            ColumnInfo columnInfo = format.columnTypes.get(i);
            int columnIndexInSrc = i + format.trimChoice.trimFromLeft;
            if (columnInfo.type instanceof NumericColumnType)
            {
                columns.add(rs ->
                {
                    NumericColumnType numericColumnType = (NumericColumnType) columnInfo.type;
                    return TextFileColumn.numericColumn(rs, reader, format.initialTextFormat.separator, format.initialTextFormat.quote, columnInfo.title, columnIndexInSrc, totalColumns, new NumberInfo(numericColumnType.unit), numericColumnType::removePrefixAndSuffix);
                });
            }
            else if (columnInfo.type instanceof OrBlankColumnType)
            {
                OrBlankColumnType orBlankColumnType = (OrBlankColumnType)columnInfo.type;
                if (orBlankColumnType.getInner() instanceof NumericColumnType)
                {
                    NumericColumnType numericColumnType = (NumericColumnType) orBlankColumnType.getInner();
                    DataType numberType = DataType.number(new NumberInfo(numericColumnType.unit));
                    DataType numberOrBlank = typeManager.getMaybeType().instantiate(ImmutableList.of(Either.<Unit, DataType>right(numberType)), typeManager);
                    columns.add(rs -> {
                        return TextFileColumn.<DataType>taggedColumn(rs, reader, format.initialTextFormat.separator, format.initialTextFormat.quote, columnInfo.title, columnIndexInSrc, totalColumns, DataTypeUtility.getTaggedTypeName(numberOrBlank), ImmutableList.of(Either.<Unit, DataType>right(numberType)), DataTypeUtility.getTagTypes(numberOrBlank), str -> {
                            if (str.equals(orBlankColumnType.getBlankString()))
                            {
                                return Either.<String, TaggedValue>right(new TaggedValue(0, null, typeManager.getMaybeType()));
                            }
                            else
                            {
                                return Utility.parseNumberOpt(str).map((@ImmediateValue Number n) -> {
                                    return Either.<String, TaggedValue>right(new TaggedValue(1, n, typeManager.getMaybeType()));
                                }).orElse(Either.<String, TaggedValue>left(str));
                            }
                        });
                    });
                }
                else
                    throw new InternalException("Unhandled or-blank column type: " + orBlankColumnType.getInner().getClass());
            }
            else if (columnInfo.type instanceof TextColumnType || columnInfo.type instanceof BlankColumnType)
            {
                columns.add(rs -> TextFileColumn.stringColumn(rs, reader, format.initialTextFormat.separator, format.initialTextFormat.quote, columnInfo.title, columnIndexInSrc, totalColumns));
            }
            else if (columnInfo.type instanceof CleanDateColumnType)
            {
                columns.add(rs ->
                {
                    CleanDateColumnType dateColumnType = (CleanDateColumnType) columnInfo.type;
                    return TextFileColumn.dateColumn(rs, reader, format.initialTextFormat.separator, format.initialTextFormat.quote, columnInfo.title, columnIndexInSrc, totalColumns, dateColumnType.getDateTimeInfo(), dateColumnType::parse);
                });
            }
            else
                throw new InternalException("Unhandled column type: " + columnInfo.type.getClass());
        }


        return new RecordSet(columns)
        {
            protected int rowCount = -1;

            @Override
            public final boolean indexValid(int index) throws UserException
            {
                return index < getLength();
            }

            @Override
            @SuppressWarnings("units")
            public @TableDataRowIndex int getLength() throws UserException
            {
                if (rowCount == -1)
                {
                    try
                    {
                        rowCount = Utility.countLines(textFile, format.initialTextFormat.charset) - format.trimChoice.trimFromTop - format.trimChoice.trimFromBottom;
                    }
                    catch (IOException e)
                    {
                        throw new FetchException("Error counting rows", e);
                    }
                }
                return rowCount;
            }
        };
    }

    private static Map<Charset, List<String>> getInitial(File textFile) throws IOException
    {
        Map<Charset, List<String>> initial = new LinkedHashMap<>();
        Set<Charset> charsets = new LinkedHashSet<>();
        charsets.addAll(Arrays.asList(StandardCharsets.UTF_8, StandardCharsets.ISO_8859_1, StandardCharsets.UTF_16));
        charsets.add(Charset.defaultCharset());
        for (Charset charset : charsets)
        {
            ArrayList<String> initialLines = new ArrayList<>();
            // Read the first few lines:
            try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(textFile), charset)))
            {
                String line;
                while ((line = br.readLine()) != null/* && initialLines.size() < GuessFormat.INITIAL_ROWS_TEXT_FILE*/)
                {
                    initialLines.add(line);
                }
            }
            initial.put(charset, initialLines);
        }
        return initial;
    }
}
