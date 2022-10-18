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

import com.google.common.collect.ImmutableList;
import javafx.stage.Window;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellAddress;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.CellPosition;
import xyz.columnal.id.ColumnId;
import xyz.columnal.data.DataSource;
import xyz.columnal.data.ImmediateDataSource;
import xyz.columnal.data.TableManager;
import xyz.columnal.importers.GuessFormat.Import;
import xyz.columnal.importers.GuessFormat.ImportInfo;
import xyz.columnal.importers.GuessFormat.TrimChoice;
import xyz.columnal.importers.ImportPlainTable.PlainImportInfo;
import xyz.columnal.importers.gui.ImportChoicesDialog;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.IdentifierUtility;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.function.simulation.SimulationConsumerNoError;
import xyz.columnal.utility.function.simulation.SimulationSupplier;
import xyz.columnal.utility.UnitType;
import xyz.columnal.utility.Workers;
import xyz.columnal.utility.Workers.Priority;
import xyz.columnal.utility.gui.FXUtility;
import xyz.columnal.utility.TranslationUtility;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class ExcelImporter implements Importer
{
    @Override
    public @Localized String getName()
    {
        return TranslationUtility.getString("importer.excel.files");
    }

    @Override
    public @OnThread(Tag.Any) ImmutableList<String> getSupportedFileTypes()
    {
        return ImmutableList.of("*.xls", "*.xlsx", "*.xlsm");
    }

    @SuppressWarnings("deprecation")
    @Override
    public @OnThread(Tag.FXPlatform) void importFile(Window parent, TableManager mgr, CellPosition destination, File src, URL origin, SimulationConsumerNoError<DataSource> recordLoadedTable)
    {
        try
        {
            Workbook workbook = new XSSFWorkbook(src);
            // TODO offer sheet list as import choice
            Sheet datatypeSheet = workbook.getSheetAt(0);
            
            List<ArrayList<String>> vals = new ArrayList<>();
            List<ColumnInfo> columnInfos = new ArrayList<>();

            Map<CellAddress, CellRangeAddress> mergedRegions = new HashMap<>();
            for (CellRangeAddress mergedRegion : datatypeSheet.getMergedRegions())
            {
                for (int row = mergedRegion.getFirstRow(); row <= mergedRegion.getLastRow(); row++)
                {
                    for (int col = mergedRegion.getFirstColumn(); col <= mergedRegion.getLastColumn(); col++)
                    {
                        mergedRegions.put(new CellAddress(row, col), mergedRegion);
                    }
                }
            }
            

            Iterator<Row> iterator = datatypeSheet.iterator();
            while (iterator.hasNext())
            {
                Row currentRow = iterator.next();
                Iterator<Cell> cellIterator = currentRow.iterator();

                while (cellIterator.hasNext())
                {
                    Cell currentCell = cellIterator.next();
                    String val = getCellValueAsString(currentCell, currentCell.getCellType());
                    // No need to set if empty, that's the default:
                    if (!val.isEmpty())
                    {
                        CellRangeAddress merged = mergedRegions.get(currentCell.getAddress());
                        if (merged != null)
                        {
                            for (int row = merged.getFirstRow(); row <= merged.getLastRow(); row++)
                            {
                                for (int col = merged.getFirstColumn(); col <= merged.getLastColumn(); col++)
                                {
                                    setValue(vals, row, col, val);
                                }
                            }
                        }
                        else
                        {
                            setValue(vals, currentCell.getRowIndex(), currentCell.getColumnIndex(), val);
                        }
                    }
                }
            }
            workbook.close();
            
            
            ImporterUtility.rectangulariseAndRemoveBlankRows(vals);
            int numSrcColumns = vals.isEmpty() ? 0 : vals.get(0).size();

            Import<UnitType, PlainImportInfo> importInfo = new ImportPlainTable(numSrcColumns, mgr, vals) {
                @Override
                public Pair<ColumnId, @Localized String> srcColumnName(int index)
                {
                    ColumnId columnId = excelColumnName(index);
                    return new Pair<>(columnId, columnId.getRaw());
                }

                @Override
                public ColumnId destColumnName(TrimChoice trimChoice, int index)
                {
                    if (trimChoice.trimFromTop > 0)
                        return new ColumnId(IdentifierUtility.fixExpressionIdentifier(vals.get(trimChoice.trimFromTop - 1).get(index), excelColumnName(index).getRaw()));
                    else
                        return excelColumnName(index);
                }
            };
            
            @Nullable ImportInfo<PlainImportInfo> outcome = new ImportChoicesDialog<>(parent, src.getName(), importInfo).showAndWait().orElse(null);

            if (outcome != null)
            {
                @NonNull ImportInfo<PlainImportInfo> outcomeNonNull = outcome;
                SimulationSupplier<DataSource> makeDataSource = () -> new ImmediateDataSource(mgr, outcomeNonNull.getInitialLoadDetails(destination), ImporterUtility.makeEditableRecordSet(mgr.getTypeManager(), outcomeNonNull.getFormat().trim.trim(vals), outcomeNonNull.getFormat().columnInfo));
                Workers.onWorkerThread("Loading " + src.getName(), Priority.LOAD_FROM_DISK, () -> FXUtility.alertOnError_(TranslationUtility.getString("error.importing.excel"), () -> {
                    DataSource dataSource = makeDataSource.get();
                    recordLoadedTable.consume(dataSource);
                }));
            }
        }
        catch (IOException | InvalidFormatException e)
        {
            // TODO report an error
        }
    }

    protected void setValue(List<ArrayList<String>> vals, int rowIndex, int columnIndex, String val)
    {
        while (vals.size() <= rowIndex)
        {
            // Can't use Collections.emptyList because this may be later
            // modified by rectangulariseAndRemoveBlankRows
            vals.add(new ArrayList<>());
        }
        ArrayList<String> row = vals.get(rowIndex);
        while (row.size() <= columnIndex)
        {
            row.add("");
        }
        row.set(columnIndex, val);
    }

    @SuppressWarnings("identifier")
    private static ColumnId excelColumnName(int columnIndex)
    {
        String s = "";
        if (columnIndex >= 26 * 26)
        {
            int thirdDig = columnIndex / (26 * 26);
            s += (char) (thirdDig + 'A');
            columnIndex -= thirdDig * 26 * 26;
        }
        if (columnIndex >= 26)
        {
            s += (char) ((columnIndex / 26) + 'A');
            columnIndex = columnIndex % 26;
        }
        s += (char)(columnIndex + 'A');

        return new ColumnId(s);
    }

    @SuppressWarnings("deprecation")
    private String getCellValueAsString(Cell currentCell, CellType cellType)
    {
        //getCellTypeEnum shown as deprecated for version 3.15
        //getCellTypeEnum ill be renamed to getCellType starting from version 4.0
        switch (cellType)
        {
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(currentCell))
                    return String.format("%tF", currentCell.getDateCellValue());
                else
                    return String.format("%f", currentCell.getNumericCellValue());
            case STRING:
                return currentCell.getStringCellValue();
            case FORMULA:
                return getCellValueAsString(currentCell, currentCell.getCachedFormulaResultType());
            case BOOLEAN:
                return Boolean.toString(currentCell.getBooleanCellValue()).toUpperCase();
            case _NONE:
            case BLANK:
            case ERROR:
            default:
                return "";
        }
    }

}
