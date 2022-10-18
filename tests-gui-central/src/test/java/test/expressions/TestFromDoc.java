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

package test.expressions;

import annotation.qual.Value;
import com.google.common.collect.ImmutableMap;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.Assert;
import org.junit.runner.RunWith;
import test.functions.TFunctionUtil;
import xyz.columnal.data.Column;
import xyz.columnal.data.ColumnUtility;
import xyz.columnal.id.ColumnId;
import xyz.columnal.data.EditableColumn;
import xyz.columnal.data.KnownLengthRecordSet;
import xyz.columnal.data.RecordSet;
import xyz.columnal.id.TableId;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.DataTypeUtility;
import xyz.columnal.data.datatype.DataTypeValue;
import xyz.columnal.data.datatype.TypeManager;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.grammar.DataLexer2;
import xyz.columnal.grammar.DataParser2;
import xyz.columnal.jellytype.JellyType;
import xyz.columnal.transformations.expression.ErrorAndTypeRecorderStorer;
import xyz.columnal.transformations.expression.EvaluateState;
import xyz.columnal.transformations.expression.Expression;
import xyz.columnal.transformations.expression.Expression.ColumnLookup;
import xyz.columnal.transformations.expression.TypeState;
import xyz.columnal.transformations.function.FromString;
import xyz.columnal.transformations.function.FunctionList;
import xyz.columnal.typeExp.MutVar;
import xyz.columnal.typeExp.TypeClassRequirements;
import xyz.columnal.typeExp.TypeConcretisationError;
import xyz.columnal.typeExp.TypeExp;
import xyz.columnal.typeExp.units.MutUnitVar;
import test.gen.type.GenTypeAndValueGen;
import test.gen.type.GenTypeAndValueGen.TypeAndValueGen;
import test.gen.GenValueSpecifiedType;
import test.gen.GenValueSpecifiedType.ValueGenerator;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.function.simulation.SimulationFunction;
import xyz.columnal.utility.Utility;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.OptionalInt;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.*;

@RunWith(JUnitQuickcheck.class)
public class TestFromDoc
{
    @Property(trials=100)
    @OnThread(Tag.Simulation)
    @SuppressWarnings("identifier")
    public void testFromDoc(
        @From(GenValueSpecifiedType.class) ValueGenerator valueGen,
        @From(GenTypeAndValueGen.class) TypeAndValueGen typeAndValueGen) throws IOException, InternalException, UserException
    {
        TypeManager typeManager = typeAndValueGen.getTypeManager();
        for (File file : FileUtils.listFiles(new File(System.getProperty("user.dir") + "/../app/build/resources/main"), new String[]{"test"}, false))
        {
            //Log.debug("Processing: " + file.getName());
            // Tables are scoped by file:
            Map<TableId, RecordSet> tables = new HashMap<>();
            
            List<String> lines = FileUtils.readLines(file, StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size(); i++)
            {
                String line = lines.get(i);
                if (line.trim().isEmpty())
                    continue;

                Map<String, Either<MutUnitVar, MutVar>> typeVariables = new HashMap<>();
                Map<String, TypeExp> variables = new HashMap<>();
                Map<String, Pair<String, String>> minMax = new HashMap<>();
                boolean errorLine = false;
                boolean typeError = false;

                if (line.startsWith("!!!"))
                {
                    errorLine = true;
                    line = StringUtils.removeStart(line, "!!!");
                }
                else if (line.startsWith("== ") || line.startsWith("==* "))
                {
                    // Hoover up for the variables:
                    while (i < lines.size())
                    {
                        line = lines.get(i);
                        if (line.startsWith("== "))
                        {
                            String nameType[] = StringUtils.removeStart(line, "== ").trim().split("//");
                            variables.put(nameType[0], JellyType.parse(nameType[1], typeManager).makeTypeExp(ImmutableMap.copyOf(typeVariables)));
                            if (nameType.length >= 4)
                                minMax.put(nameType[0], new Pair<>(nameType[2], nameType[3]));
                            
                            i += 1;
                        }
                        else if (line.startsWith("==* "))
                        {
                            String nameType[] = StringUtils.removeStart(line, "==* ").trim().split("//");
                            TypeExp picked = TypeExp.fromDataType(null, typeAndValueGen.getType());
                            MutVar mutVar = nameType.length == 1 ? new MutVar(null) : new MutVar(null, TypeClassRequirements.require(StringUtils.removeEnd(nameType[1], " " + nameType[0]), ""));
                            typeError = TypeExp.unifyTypes(picked, mutVar).isLeft();
                            typeVariables.put(nameType[0], Either.right(mutVar));
                            i += 1;
                        }
                        else
                        {
                            break;
                        }
                    }
                    String lhs = StringUtils.removeStart(lines.get(i++), "==== ");
                    String rhs = StringUtils.removeStart(lines.get(i), "==== ");

                    line = "(" + lhs + ")=(" + rhs + ")";
                }
                else if (line.startsWith("## "))
                {
                    String tableName = StringUtils.removeStart(lines.get(i), "##").trim();
                    String[] columnNames = StringUtils.removeStart(lines.get(++i), "##").trim().split("//");
                    String[] columnTypes = StringUtils.removeStart(lines.get(++i), "##").trim().split("//");
                    // Stored as column major:
                    List<List<String>> columnValues = Utility.replicateM(columnNames.length, ArrayList::new);
                    int length = -1;
                    i += 1;
                    while (i < lines.size()) // while (true), really -- file shouldn't end that early
                    {
                        line = lines.get(i);
                        if (line.startsWith("#### "))
                        {
                            String[] values = StringUtils.removeStart(line, "####").trim().split("//");
                            for (int j = 0; j < values.length; j++)
                            {
                                columnValues.get(j).add(values[j]); 
                            }
                            i += 1;
                        }
                        else
                        {
                            break;
                        }
                    }

                    List<SimulationFunction<RecordSet, EditableColumn>> columns = new ArrayList<>();
                    for (int c = 0; c < columnNames.length; c++)
                    {
                        DataType dataType = typeManager.loadTypeUse(columnTypes[c]);
                        List<Either<String, @Value Object>> loadedValues = Utility.<String, Either<String, @Value Object>>mapListEx(columnValues.get(c), unparsed -> {
                            return Utility.<Either<String, @Value Object>, DataParser2>parseAsOne(unparsed, DataLexer2::new, DataParser2::new, p -> 
                                DataType.loadSingleItem(dataType, p, false));
                        });
                        if (length == -1)
                            length = loadedValues.size();
                        else if (length != loadedValues.size())
                            throw new InternalException("Column length mismatch in table data for " + tableName);
                            
                        columns.add(ColumnUtility.makeImmediateColumn(dataType, new ColumnId(columnNames[c]),
                            loadedValues,
                            loadedValues.get(0).getRight("No error")    
                        ));
                    }
                    
                    tables.put(new TableId(tableName), new KnownLengthRecordSet(columns, length));
                }

                Expression expression = TFunctionUtil.parseExpression(line, typeManager, FunctionList.getFunctionLookup(typeManager.getUnitManager()));
                ErrorAndTypeRecorderStorer errors = new ErrorAndTypeRecorderStorer();
                TypeState typeState = TFunctionUtil.createTypeState(typeManager);
                for (Entry<String, TypeExp> e : variables.entrySet())
                {
                    typeState = typeState.add(e.getKey(), e.getValue(), s -> {throw new RuntimeException(e.toString());});
                    assertNotNull(typeState);
                    if (typeState == null) // Won't happen
                        return;
                }
                TypeExp typeExp = expression.checkExpression(new DocColumnLookup(tables), typeState, errors);
                if (!typeError)
                {
                    assertEquals("Errors for " + line, Arrays.asList(), errors.getAllErrors().collect(Collectors.toList()));
                    assertNotNull("Type check for " + line, typeExp);
                    if (typeExp == null) continue; // Won't happen
                }
                else
                {
                    assertNull(line + typeState, typeExp);
                    continue;
                }
                Either<TypeConcretisationError, DataType> concreteReturnType = typeExp.toConcreteType(typeManager);
                // It may be a type concretisation error e.g. for minimum([])
                if (!errorLine)
                    assertEquals(line, Either.right(DataType.BOOLEAN), concreteReturnType);

                EvaluateState evaluateState = new EvaluateState(typeManager, OptionalInt.empty());
                List<Pair<String, String>> varValues = new ArrayList<>();
                for (Entry<String, TypeExp> e : variables.entrySet())
                {
                    Either<TypeConcretisationError, DataType> errorOrType = e.getValue().toConcreteType(typeManager);
                    if (errorOrType.isLeft())
                        fail(errorOrType.getLeft("").toString());
                    DataType concreteVarType = errorOrType.getRight("");
                    @Nullable Pair<@Value Object, @Value Object> parsedMinMax = null;
                    if (minMax.containsKey(e.getKey()))
                    {
                        Pair<String, String> unparsed = minMax.get(e.getKey());
                        parsedMinMax = new Pair<>(
                            FromString._test_fromString(unparsed.getFirst(), concreteVarType),
                            FromString._test_fromString(unparsed.getSecond(), concreteVarType)
                        );
                    }

                    @Value Object value;
                    do
                    {
                        value = valueGen.makeValue(concreteVarType);
                    }
                    while (parsedMinMax != null && (Utility.compareValues(parsedMinMax.getFirst(), value) > 0 || Utility.compareValues(parsedMinMax.getSecond(), value) < 0));
                    evaluateState = evaluateState.add(e.getKey(), value);
                    varValues.add(new Pair<>(e.getKey(), DataTypeUtility.valueToString(value)));
                }
                
                if (errorLine)
                {
                    // Must be user exception
                    try
                    {
                        expression.calculateValue(evaluateState);
                        Assert.fail("Expected error but got none for\n" + line);
                    }
                    catch (UserException e)
                    {
                        // As expected!
                    }
                }
                else
                {
                    boolean result = (Boolean)expression.calculateValue(evaluateState).value;
                    assertTrue(line + " values: " + Utility.listToString(varValues), result);
                }
            }
        }
    }

    private class DocColumnLookup implements ColumnLookup
    {
        private final Map<TableId, RecordSet> tables;

        public DocColumnLookup(Map<TableId, RecordSet> tables)
        {
            this.tables = tables;
        }

        @Override
        @SuppressWarnings("nullness")
        public @Nullable FoundColumn getColumn(Expression expression, @Nullable TableId tableId, ColumnId columnId)
        {
            try
            {
                Column column = tables.get(tableId).getColumn(columnId);
                DataTypeValue type = column.getType();
                return new FoundColumn(tableId, false, type, null);
            }
            catch (Exception e)
            {
                return null;
            }
        }

        @Override
        public @Nullable FoundTable getTable(@Nullable TableId tableName) throws UserException, InternalException
        {
            if (tableName == null || !tables.containsKey(tableName))
                return null;
            RecordSet recordSet = tables.get(tableName);
            
            return new FoundTable()
            {
                @Override
                public TableId getTableId()
                {
                    return tableName;
                }

                @Override
                public ImmutableMap<ColumnId, DataTypeValue> getColumnTypes() throws InternalException, UserException
                {
                    ImmutableMap.Builder<ColumnId, DataTypeValue> columns = ImmutableMap.builder();
                    for (Column column : recordSet.getColumns())
                    {
                        columns.put(column.getName(), column.getType());
                    }
                    return columns.build();
                }

                @Override
                public int getRowCount() throws InternalException, UserException
                {
                    return recordSet.getLength();
                }
            };
        }

        @Override
        public Stream<Pair<@Nullable TableId, ColumnId>> getAvailableColumnReferences()
        {
            return Stream.empty();
        }

        @Override
        public Stream<TableId> getAvailableTableReferences()
        {
            return tables.entrySet().stream().map(t -> t.getKey());
        }

        @Override
        public Stream<ClickedReference> getPossibleColumnReferences(TableId tableId, ColumnId columnId)
        {
            return Stream.empty(); // Not used here
        }
    }
}
