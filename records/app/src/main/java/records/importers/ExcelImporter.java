package records.importers;

import com.google.common.collect.ImmutableList;
import javafx.application.Platform;
import javafx.stage.Window;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.DataSource;
import records.data.EditableRecordSet;
import records.data.ImmediateDataSource;
import records.data.TableManager;
import records.importers.GuessFormat.ImportInfo;
import records.importers.base.Importer;
import records.importers.gui.ImportChoicesDialog;
import records.importers.gui.ImportChoicesDialog.SourceInfo;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformConsumer;
import utility.Pair;
import utility.SimulationFunction;
import utility.SimulationSupplier;
import utility.Utility;
import utility.Workers;
import utility.Workers.Priority;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class ExcelImporter implements Importer
{
    @Override
    public @Localized String getName()
    {
        return "Excel Files";
    }

    @Override
    public @OnThread(Tag.Any) ImmutableList<Pair<@Localized String, ImmutableList<String>>> getSupportedFileTypes()
    {
        return ImmutableList.of(new Pair<>("Excel files", ImmutableList.of("*.xls", "*.xlsx", "*.xlsm")));
    }

    @SuppressWarnings("deprecation")
    @Override
    public @OnThread(Tag.FXPlatform) void importFile(Window parent, TableManager mgr, File src, FXPlatformConsumer<DataSource> onLoad)
    {
        try
        {
            FileInputStream excelFile = new FileInputStream(src);
            Workbook workbook = new XSSFWorkbook(excelFile);
            // TODO offer sheet list as import choice
            Sheet datatypeSheet = workbook.getSheetAt(0);
            Iterator<Row> iterator = datatypeSheet.iterator();

            List<List<String>> vals = new ArrayList<>();

            while (iterator.hasNext())
            {
                List<String> row = new ArrayList<>();
                Row currentRow = iterator.next();
                Iterator<Cell> cellIterator = currentRow.iterator();

                while (cellIterator.hasNext())
                {
                    Cell currentCell = cellIterator.next();
                    while (vals.size() < currentCell.getRowIndex())
                    {
                        // Can't use Collections.emptyList because this may be later
                        // modified by rectangularise
                        vals.add(new ArrayList<>());
                    }
                    while (row.size() < currentCell.getColumnIndex())
                    {
                        row.add("");
                    }
                    String val = getCellValueAsString(currentCell, currentCell.getCellTypeEnum());
                    row.add(val);
                }

                vals.add(row);
            }
            ImporterUtility.rectangularise(vals);

            SimulationFunction<Format, EditableRecordSet> loadData = f -> {
                return new EditableRecordSet(Collections.emptyList(), () -> 0);
            };
            SourceInfo sourceInfo = ImporterUtility.makeSourceInfo(vals);
            @Nullable Pair<ImportInfo, Format> outcome = new ImportChoicesDialog<>(mgr, src.getName(), GuessFormat.guessGeneralFormat(mgr.getUnitManager(), vals), loadData, c -> sourceInfo).showAndWait().orElse(null);

            if (outcome != null)
            {
                @NonNull Pair<ImportInfo, Format> outcomeNonNull = outcome;
                SimulationSupplier<DataSource> makeDataSource = () -> new ImmediateDataSource(mgr, loadData.apply(outcomeNonNull.getSecond()));
                Workers.onWorkerThread("Loading " + src.getName(), Priority.LOAD_FROM_DISK, () -> Utility.alertOnError_(() -> {
                    DataSource dataSource = makeDataSource.get();
                    Platform.runLater(() -> onLoad.consume(dataSource));
                }));
            }
        }
        catch (IOException e)
        {

        }
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
                return getCellValueAsString(currentCell, currentCell.getCachedFormulaResultTypeEnum());
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
