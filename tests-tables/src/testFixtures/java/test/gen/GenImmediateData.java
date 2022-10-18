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

import com.google.common.collect.ImmutableList;
import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.generator.GeneratorConfiguration;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import org.checkerframework.checker.initialization.qual.Initialized;
import org.checkerframework.checker.nullness.qual.Nullable;
import test.functions.TFunctionUtil;
import xyz.columnal.data.Column;
import xyz.columnal.data.EditableColumn;
import xyz.columnal.data.EditableRecordSet;
import xyz.columnal.data.ImmediateDataSource;
import xyz.columnal.data.RecordSet;
import xyz.columnal.data.Table;
import xyz.columnal.data.Table.InitialLoadDetails;
import xyz.columnal.id.TableId;
import xyz.columnal.data.TableManager;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import test.DummyManager;
import test.gen.GenImmediateData.ImmediateData_Mgr;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.function.ExBiFunction;
import xyz.columnal.utility.IdentifierUtility;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.function.simulation.SimulationFunction;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.List;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Created by neil on 07/12/2016.
 */
public class GenImmediateData extends Generator<ImmediateData_Mgr>
{
    private @Nullable NumTables numTables;
    private boolean mustIncludeNumber = false;
    private int nextTableNum = 0;
    private boolean canHaveErrorValues = false;

    public GenImmediateData()
    {
        super(ImmediateData_Mgr.class);
    }

    public void configure(NumTables numTables)
    {
        this.numTables = numTables;
    }

    public void configure(MustIncludeNumber mustIncludeNumber)
    {
        this.mustIncludeNumber = true;
    }

    public void configure(CanHaveErrorValues canHaveErrorValues)
    {
        this.canHaveErrorValues = true;
    }

    @Override
    @OnThread(value = Tag.Simulation,ignoreParent = true)
    public ImmediateData_Mgr generate(SourceOfRandomness r, GenerationStatus generationStatus)
    {
        try
        {
            Pair<DummyManager, List<DataType>> mgrAndTypes = TFunctionUtil.managerWithTestTypes();

            List<ImmediateDataSource> tables = new ArrayList<>();

            int numTables = this.numTables == null ? 1 : r.nextInt(this.numTables.minTables(), this.numTables.maxTables());

            for (int t = 0; t < numTables; t++)
            {
                // Bias towards small:
                final @Initialized int length = r.nextBoolean() ? r.nextInt(0, 10) : r.nextInt(0, 400);

                int numColumns = r.nextInt(1, 12);
                List<SimulationFunction<RecordSet, EditableColumn>> columns = new ArrayList<>();
                GenColumn genColumn = new GenColumn(mgrAndTypes.getFirst(), mgrAndTypes.getSecond(), canHaveErrorValues);
                for (int i = 0; i < numColumns; i++)
                {
                    ExBiFunction<Integer, RecordSet, Column> col = genColumn.generate(r, generationStatus);
                    columns.add(rs -> (EditableColumn/*TODO*/)col.apply(length, rs));
                }
                if (mustIncludeNumber)
                {
                    // Can't tell what types have been added, so just add another number one in case:
                    ExBiFunction<Integer, RecordSet, Column> col = genColumn.columnForType(DataType.NUMBER, r);
                    columns.add(rs -> (EditableColumn)col.apply(length, rs));
                }

                @SuppressWarnings({"keyfor", "units"})
                ImmediateDataSource dataSource = new ImmediateDataSource(mgrAndTypes.getFirst(), new InitialLoadDetails(new TableId(IdentifierUtility.identNum("Test", nextTableNum++)), null, null, null), new EditableRecordSet(columns, () -> length));
                mgrAndTypes.getFirst().record(dataSource);
                tables.add(dataSource);
            }
            return new ImmediateData_Mgr(mgrAndTypes.getFirst(), tables);
        }
        catch (InternalException | UserException e)
        {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public static class ImmediateData_Mgr
    {
        public final TableManager mgr;
        // Amount is set by Precision annotation (for laziness), default is 1
        public final ImmutableList<ImmediateDataSource> data;

        public ImmediateData_Mgr(TableManager mgr, List<ImmediateDataSource> data)
        {
            this.mgr = mgr;
            this.data = ImmutableList.copyOf(data);
        }

        // Shortcut for first item:
        public Table data()
        {
            return data.get(0);
        }
    }

    @Target({PARAMETER, FIELD, ANNOTATION_TYPE, TYPE_USE})
    @Retention(RUNTIME)
    @GeneratorConfiguration
    public @interface NumTables {
        int minTables() default 1;
        int maxTables() default 1;
    }

    @Target({PARAMETER, FIELD, ANNOTATION_TYPE, TYPE_USE})
    @Retention(RUNTIME)
    @GeneratorConfiguration
    public @interface MustIncludeNumber {
    }

    @Target({PARAMETER, FIELD, ANNOTATION_TYPE, TYPE_USE})
    @Retention(RUNTIME)
    @GeneratorConfiguration
    public @interface CanHaveErrorValues {
    }

/*
    @Override
    @OnThread(value = Tag.Simulation, ignoreParent = true)
    public List<ImmediateData_Mgr> doShrink(SourceOfRandomness random, ImmediateData_Mgr larger)
    {
        try
        {
            TableManager mgr = new DummyManager();
            // Don't shrink to zero, gets weird:

            int shrunkLength = Math.max(1, larger.data.getData().getLength() / 4);
            List<FunctionInt<RecordSet, Column>> columns = new ArrayList<>();
            for (Column column : larger.data.getData().getColumns())
            {
                columns.add(rs -> column._test_shrink(rs, shrunkLength));
            }
            //Could also remove arbitrary column(s)
            return Collections.singletonList(new ImmediateData_Mgr(mgr, new ImmediateDataSource(mgr, new KnownLengthRecordSet(larger.data.getData().getTitle(), columns, shrunkLength)), larger.dataB));
        }
        catch (Exception e)
        {
            e.printStackTrace();
            return super.doShrink(random, larger);
        }
    }*/
}
