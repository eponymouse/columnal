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

package xyz.columnal.exporters;

import annotation.identifier.qual.ExpressionIdentifier;
import annotation.qual.Value;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import xyz.columnal.log.Log;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.checkerframework.checker.i18n.qual.Localized;
import xyz.columnal.data.Column;
import xyz.columnal.data.RecordSet;
import xyz.columnal.data.Table;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.DataType.DateTimeInfo;
import xyz.columnal.data.datatype.DataType.TagType;
import xyz.columnal.data.datatype.DataTypeUtility;
import xyz.columnal.data.datatype.DataTypeValue.DataTypeVisitorGet;
import xyz.columnal.data.datatype.DataTypeValue.GetValue;
import xyz.columnal.data.datatype.NumberInfo;
import xyz.columnal.data.datatype.TypeId;
import xyz.columnal.data.unit.Unit;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.TaggedValue;
import xyz.columnal.utility.TranslationUtility;
import xyz.columnal.utility.UnitType;
import xyz.columnal.utility.Utility.ListEx;
import xyz.columnal.utility.Utility.Record;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAccessor;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class ExcelExporter implements Exporter
{
    @Override
    public @OnThread(Tag.Simulation) void exportData(File destination, Table data) throws UserException, InternalException
    {
        XSSFWorkbook workbook = new XSSFWorkbook();
        
        XSSFSheet sheet = workbook.createSheet();

        DataFormat dataFormat = workbook.createDataFormat();
        
        final LoadingCache<String, CellStyle> formats = CacheBuilder.newBuilder().maximumSize(100).build(new CacheLoader<String, CellStyle>()
        {
            @Override
            public CellStyle load(String format) throws Exception
            {
                CellStyle cellStyle = workbook.createCellStyle();
                cellStyle.setDataFormat(dataFormat.getFormat(format));
                return cellStyle;
            }
        });

        RecordSet rs = data.getData();
        List<Column> columns = rs.getColumns();
        XSSFRow headerRow = sheet.createRow(0);
        for (int columnIndex = 0; columnIndex < columns.size(); columnIndex++)
        {
            Column column = columns.get(columnIndex);
            headerRow.createCell(columnIndex).setCellValue(column.getName().getRaw());
        }

        for (int row = 0; rs.indexValid(row); row++)
        {
            XSSFRow dataRow = sheet.createRow(1 + row);
            final int rowIndexFinal = row;
            
            for (int columnIndex = 0; columnIndex < columns.size(); columnIndex++)
            {
                Column column = columns.get(columnIndex);
                XSSFCell cell = dataRow.createCell(columnIndex);
                try
                {
                    column.getType().applyGet(new DataTypeVisitorGet<UnitType>()
                    {                        
                        @Override
                        @OnThread(Tag.Simulation)
                        public UnitType number(GetValue<@Value Number> g, NumberInfo displayInfo) throws InternalException, UserException
                        {
                            cell.setCellValue(g.get(rowIndexFinal).doubleValue());
                            return UnitType.UNIT;
                        }

                        @Override
                        @OnThread(Tag.Simulation)
                        public UnitType text(GetValue<@Value String> g) throws InternalException, UserException
                        {
                            cell.setCellValue(g.get(rowIndexFinal));
                            return UnitType.UNIT;
                        }

                        @Override
                        @OnThread(Tag.Simulation)
                        public UnitType bool(GetValue<@Value Boolean> g) throws InternalException, UserException
                        {
                            cell.setCellValue(g.get(rowIndexFinal));
                            return UnitType.UNIT;
                        }

                        @Override
                        @OnThread(Tag.Simulation)
                        public UnitType date(DateTimeInfo dateTimeInfo, GetValue<@Value TemporalAccessor> g) throws InternalException, UserException
                        {
                            try
                            {
                                switch (dateTimeInfo.getType())
                                {
                                    case YEARMONTHDAY:
                                        cell.setCellStyle(formats.get("yyyy-MM-dd"));
                                        cell.setCellValue(Date.from(LocalDate.from(g.get(rowIndexFinal)).atStartOfDay(ZoneId.systemDefault()).toInstant()));
                                        break;
                                    case YEARMONTH:
                                        cell.setCellValue(YearMonth.from(g.get(rowIndexFinal)).toString());
                                        break;
                                    case TIMEOFDAY:
                                        cell.setCellStyle(formats.get("HH:mm:ss"));
                                        cell.setCellValue((double)LocalTime.from(g.get(rowIndexFinal)).toNanoOfDay() / (1_000_000_000.0 * 24.0 * 60.0 * 60.0));
                                        break;
                                    case DATETIME:
                                        cell.setCellStyle(formats.get("yyyy-MM-dd HH:mm:ss"));
                                        cell.setCellValue(Date.from(LocalDateTime.from(g.get(rowIndexFinal)).toInstant(ZoneOffset.UTC)));
                                        break;
                                    case DATETIMEZONED:
                                        cell.setCellStyle(formats.get("yyyy-MM-dd HH:mm:ss"));
                                        cell.setCellValue(Date.from(ZonedDateTime.from(g.get(rowIndexFinal)).toInstant()));
                                        break;
                                }
                            }
                            catch (ExecutionException e)
                            {
                                throw new InternalException("Unexpected error in setting format", e);
                            }
                            return UnitType.UNIT;
                        }

                        private void setDateFormat()
                        {
                            CellStyle cellStyle = workbook.createCellStyle();
                            cellStyle.setDataFormat((short)14);
                            cell.setCellStyle(cellStyle);
                        }

                        @Override
                        @OnThread(Tag.Simulation)
                        public UnitType tagged(TypeId typeName, ImmutableList<Either<Unit, DataType>> typeVars, ImmutableList<TagType<DataType>> tagTypes, GetValue<@Value TaggedValue> g) throws InternalException, UserException
                        {
                            DataType dataType = data.getManager().getTypeManager().lookupType(typeName, typeVars);
                            if (dataType != null)
                                return putString(dataType, g);
                            else
                                throw new UserException("Could not find column type: " + typeName.getRaw());
                        }

                        @Override
                        @OnThread(Tag.Simulation)
                        public UnitType record(ImmutableMap<@ExpressionIdentifier String, DataType> types, GetValue<@Value Record> g) throws InternalException, UserException
                        {
                            return putString(DataType.record(types), g);
                        }

                        @Override
                        @OnThread(Tag.Simulation)
                        public UnitType array(DataType inner, GetValue<@Value ListEx> g) throws InternalException, UserException
                        {
                            return putString(DataType.array(inner), g);
                        }

                        @OnThread(Tag.Simulation)
                        private UnitType putString(DataType dataType, GetValue<?> g) throws InternalException, UserException
                        {
                            cell.setCellValue(DataTypeUtility.valueToString(g.get(rowIndexFinal)));
                            return UnitType.UNIT;
                        }
                    });
                }
                catch (InternalException | UserException e)
                {
                    if (e instanceof InternalException)
                        Log.log(e);
                    cell.setCellValue("Error: " + e.getLocalizedMessage());
                }
            }
        }

        try (FileOutputStream os = new FileOutputStream(destination))
        {
            workbook.write(os);
        }
        catch (IOException e)
        {
            throw new UserException("Error writing file " + destination.getAbsolutePath(), e);
        }
    }

    @Override
    public @Localized String getName()
    {
        return TranslationUtility.getString("importer.excel.files");
    }

    @Override
    public @OnThread(Tag.Any) ImmutableList<String> getSupportedFileTypes()
    {
        return ImmutableList.of("*.xlsx");
    }
}
