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

package test.gen;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import org.apache.commons.text.StringEscapeUtils;
import xyz.columnal.data.TBasicUtil;
import xyz.columnal.data.columntype.BlankColumnType;
import xyz.columnal.data.columntype.CleanDateColumnType;
import xyz.columnal.data.columntype.NumericColumnType;
import xyz.columnal.data.columntype.TextColumnType;
import xyz.columnal.data.datatype.DataTypeUtility;
import xyz.columnal.importers.ColumnInfo;
import xyz.columnal.importers.GuessFormat.FinalTextFormat;
import xyz.columnal.importers.GuessFormat.TrimChoice;
import test.gen.GenFormattedData.FormatAndData;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.stream.Collectors;

/**
 * Created by neil on 23/12/2016.
 */
public class GenFormattedData extends Generator<FormatAndData>
{
    public GenFormattedData()
    {
        super(FormatAndData.class);
    }

    public static class FormatAndData
    {
        public final FinalTextFormat format;
        public final List<String> textContent;
        public final String htmlContent;
        // Outermost list is list of rows
        // Next list in is list of columns.
        public final List<List<@Value Object>> loadedContent;

        public FormatAndData(FinalTextFormat format, List<String> textContent, String htmlContent, List<List<@Value Object>> loadedContent)
        {
            this.format = format;
            this.textContent = textContent;
            this.htmlContent = htmlContent;
            this.loadedContent = loadedContent;
        }

        @Override
        public String toString()
        {
            return "FormatAndData{" +
                "format=" + format +
                /*", content=" + content +
                ", loadedContent=" + loadedContent +*/
                '}';
        }
    }

    @Override
    public FormatAndData generate(SourceOfRandomness r, GenerationStatus generationStatus)
    {
        List<String> fileContent = new ArrayList<>();
        List<List<@Value Object>> intendedContent = new ArrayList<>();
        FinalTextFormat format = new GenFormat().generate(r, generationStatus);
        
        StringBuilder htmlContent = new StringBuilder("<html><head><title>Testing!</title></head><body><table>");

        fileContent.add(format.columnTypes.stream().map(c -> c.title.getOutput()).collect(Collectors.joining("" + format.initialTextFormat.separator)));
        htmlContent.append("<tr><th>").append(format.columnTypes.stream().map(c -> c.title.getOutput()).collect(Collectors.joining("</th><th>"))).append("</th></tr>");
        int rowCount = r.nextInt(50, 200);
        ArrayList<Integer> columnsWithSingleValue = new ArrayList<>();
        for (int row = 0; row < rowCount; row++)
        {
            List<@Value Object> data = new ArrayList<>();
            StringBuilder line = new StringBuilder();
            htmlContent.append("<tr>");
            List<ColumnInfo> columnTypes = format.columnTypes;
            for (int i = 0; i < columnTypes.size(); i++)
            {
                // TODO generate X-or-blank column types
                ColumnInfo c = columnTypes.get(i);
                // TODO add random spaces, randomise content using generators
                
                StringBuilder entry = new StringBuilder();
                
                if (c.type instanceof NumericColumnType)
                {
                    NumericColumnType numericColumnType = (NumericColumnType) c.type;
                    if (!format.initialTextFormat.charset.newEncoder().canEncode(numericColumnType.unit.getDisplayPrefix()))
                        throw new RuntimeException("Cannot encode prefix: " + numericColumnType.unit.getDisplayPrefix());
                    entry.append(r.nextBoolean() ? "" : numericColumnType.unit.getDisplayPrefix());
                    long value = r.nextLong();

                    entry.append(String.format(",".equals(format.initialTextFormat.separator) ? "%d" : "%,d", value));
                    if (numericColumnType.displayInfo.getMinimumDP() > 0)
                    {
                        String decimalDigs = String.format("%0" + numericColumnType.displayInfo.getMinimumDP() + "d", Math.abs(r.nextInt())).substring(0, numericColumnType.displayInfo.getMinimumDP());
                        entry.append("." + decimalDigs);
                        data.add(DataTypeUtility.value(new BigDecimal(Long.toString(value) + "." + decimalDigs)));
                    }
                    else
                        data.add(DataTypeUtility.value((Long)value));
                    entry.append(r.nextBoolean() ? "" : numericColumnType.unit.getDisplaySuffix());
                }
                else if (c.type instanceof TextColumnType)
                {
                    if (row == 0 && r.nextInt(8) == 1)
                        columnsWithSingleValue.add(i);
                    
                    String str;
                    if (columnsWithSingleValue.contains(i) && row > 0)
                    {
                        str = intendedContent.get(0).get(i).toString();
                    }
                    else
                    {
                        str = TBasicUtil.makeString(r, generationStatus).replace("\n", "").replace("\r", "");
                        // Get rid of any characters which can't be saved in that encoding:
                        str = str.chars().filter(ch -> format.initialTextFormat.charset.newEncoder().canEncode((char) ch)).collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append).toString();
                        // TODO quote separators instead of removing them:
                        if (format.initialTextFormat.quote != null)
                            str = str.replace(format.initialTextFormat.quote, format.initialTextFormat.quote + format.initialTextFormat.quote);
                        if (format.initialTextFormat.separator != null)
                            str = str.replace(format.initialTextFormat.separator, "");

                        // Check that the string isn't blank, otherwise we'll think it's a blank column:
                        if (str.isBlank())
                        {
                            str = "oh no string was blank";
                        }
                        
                        // Check that the string isn't a number, otherwise we'll think it's a numeric column:
                        try
                        {
                            new BigDecimal(str);
                            // Oh dear, it parsed, replace it with something:
                            str = "oh dear my original idea parsed as a number";
                        }
                        catch (NumberFormatException e)
                        {
                            // Won't parse, that's fine then
                        }
                        
                    }
                    data.add(DataTypeUtility.value(str));
                    entry.append(str);
                }
                else if (c.type instanceof CleanDateColumnType)
                {
                    CleanDateColumnType dateColumnType = (CleanDateColumnType) c.type;
                    int year;
                    if (dateColumnType.isShortYear())
                        year = 1950 + r.nextInt(84); // Might need to adjust this in 2030
                    else
                        year = 1900 + r.nextInt(199);
                    int month = 1 + r.nextInt(12);
                    int day = 1 + r.nextInt(28);
                    @Value LocalDate date = LocalDate.of(year, month, day);
                    data.add(date);

                    entry.append(date.format(dateColumnType.getDateTimeFormatter()));
                }
                else if (c.type instanceof BlankColumnType)
                {
                    data.add(DataTypeUtility.value(""));
                }
                else
                    throw new UnsupportedOperationException("Missing case for column columntype? " + c.type.getClass());
                line.append(entry.toString());
                if (i < columnTypes.size() - 1)
                    line.append(format.initialTextFormat.separator);
                                
                // <pre> to preserve whitespace and deal with awkward characters:
                htmlContent.append("<td><pre>").append(StringEscapeUtils.escapeHtml4(entry.toString())).append("</pre></td>");
            }

            htmlContent.append("</tr>\n");
            String lineString = line.toString();
            // Don't add all-blank rows because they weren't intentional and it can screw up guess:
            if (!lineString.replace("" + format.initialTextFormat.separator, "").isEmpty())
            {
                fileContent.add(lineString);
                intendedContent.add(data);
            }
        }

        if (r.nextBoolean())
            fileContent.add(""); // Add trailing newline

        // Strip blanks from beginning and end as trim should now remove them:
        ArrayList<ColumnInfo> withoutBlanks = new ArrayList<>(format.columnTypes);
        int leftTrim = 0;
        for (Iterator<ColumnInfo> iterator = withoutBlanks.iterator(); iterator.hasNext(); )
        {
            ColumnInfo col = iterator.next();
            if (col.type instanceof BlankColumnType)
            {
                iterator.remove();
                leftTrim += 1;
            }
            else
                break;
        }
        int rightTrim = 0;
        for (ListIterator<ColumnInfo> iterator = withoutBlanks.listIterator(withoutBlanks.size()); iterator.hasPrevious();)
        {
            ColumnInfo col = iterator.previous();
            if (col.type instanceof BlankColumnType)
            {
                iterator.remove();
                rightTrim += 1;
            }
            else
                break;
        }

        if (leftTrim != 0 || rightTrim != 0)
        {
            for (int i = 0; i < intendedContent.size(); i++)
            {
                List<@Value Object> row = intendedContent.get(i);
                intendedContent.set(i, row.subList(leftTrim, row.size() - rightTrim));
            }
        }
        
        htmlContent.append("</table></body></html>");
        
        TrimChoice trim = new TrimChoice(format.trimChoice.trimFromTop, format.trimChoice.trimFromBottom,format.trimChoice.trimFromLeft + leftTrim, format.trimChoice.trimFromRight + rightTrim);
        return new FormatAndData(new FinalTextFormat(format.initialTextFormat, trim, ImmutableList.copyOf(withoutBlanks)), fileContent, htmlContent.toString(), intendedContent);
    }

}
