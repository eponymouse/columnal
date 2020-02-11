package test.gen;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import records.data.*;
import records.data.Table.InitialLoadDetails;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.Aggregate;
import records.transformations.Calculate;
import records.transformations.Concatenate;
import records.transformations.Concatenate.IncompleteColumnHandling;
import records.transformations.Filter;
import records.transformations.HideColumns;
import records.transformations.Join;
import records.transformations.RTransformation;
import records.transformations.Sort;
import records.transformations.Sort.Direction;
import records.transformations.expression.BooleanLiteral;
import records.transformations.expression.IdentExpression;
import records.transformations.expression.TypeState;
import test.DummyManager;
import test.TestUtil;
import test.gen.type.GenDataTypeMaker;
import test.gen.type.GenDataTypeMaker.DataTypeAndValueMaker;
import test.gen.type.GenDataTypeMaker.DataTypeMaker;
import utility.Either;
import utility.ExSupplier;
import utility.IdentifierUtility;
import utility.Pair;
import utility.SimulationFunction;

import java.util.ArrayList;

// Most of the transformations are currently identity transform or similar,
// but that's fine for the manual edit test.
public class GenDataAndTransforms extends Generator<TableManager>
{
    public GenDataAndTransforms()
    {
        super(TableManager.class);
    }

    @Override
    public TableManager generate(SourceOfRandomness random, GenerationStatus status)
    {
        try
        {
            DataTypeMaker dataTypeMaker = new GenDataTypeMaker(true).generate(random, status);
            TableManager mgr = new DummyManager();
            
            // Must have at least one immediate and one transformation (at end):
            addImmediateTable(mgr, dataTypeMaker, random);

            // Add a mix of others:
            int other = random.nextInt(0, 10);
            for (int i = 0; i < other; i++)
            {
                if (random.nextInt(3) == 1)
                    addImmediateTable(mgr, dataTypeMaker, random);
                else
                    addTransformation(mgr, dataTypeMaker, random);
            }
            addTransformation(mgr, dataTypeMaker, random);

            // Must copy at end, as types are generated while adding tables:
            mgr.getTypeManager()._test_copyTaggedTypesFrom(dataTypeMaker.getTypeManager());
            
            return mgr;
        }
        catch (Throwable t)
        {
            throw new RuntimeException(t);
        }
    }

    private void addTransformation(TableManager mgr, DataTypeMaker dataTypeMaker, SourceOfRandomness r) throws InternalException, UserException
    {
        ImmutableList.Builder<ExSupplier<Transformation>> genTransforms = ImmutableList.builder();
        
        genTransforms.addAll(ImmutableList.<ExSupplier<Transformation>>of(
        // Sort:
        () -> {
            Table src = pickSrc(mgr, r);
            ImmutableList.Builder<Pair<ColumnId, Direction>> sortBy = ImmutableList.builder();
            ArrayList<ColumnId> possibles = new ArrayList<>(src.getData().getColumnIds());
            int numSortBy = r.nextInt(0, possibles.size());
            for (int i = 0; i < numSortBy; i++)
            {
                sortBy.add(new Pair<>(possibles.remove(r.nextInt(possibles.size())), r.nextBoolean() ? Direction.ASCENDING : Direction.DESCENDING));
            }
            return new Sort(mgr, TestUtil.ILD, src.getId(), sortBy.build());
        },
        // Filter:
        () -> {
            Table src = pickSrc(mgr, r);
            return new Filter(mgr, TestUtil.ILD, src.getId(), new BooleanLiteral(true));
        },
        // Join:
        /* TODO avoid issue with duplicate column names from merging with self
        () -> {
            Table srcLeft = pickSrc(mgr, r);
            Table srcRight = pickSrc(mgr, r);
            return new Join(mgr, TestUtil.ILD, srcLeft.getId(), srcRight.getId(), true, ImmutableList.of());
        },
        */
        // Hide:
        () -> {
            Table src = pickSrc(mgr, r);
            return new HideColumns(mgr, TestUtil.ILD, src.getId(), ImmutableList.of());
        },
        // Calculate:
        () -> {
            Table src = pickSrc(mgr, r);
            return new Calculate(mgr, TestUtil.ILD, src.getId(), ImmutableMap.of());
        },
        // Concatenate:
        /* TODO fix issues with columns having different types
        () -> {
            Table srcTop = pickSrc(mgr, r);
            Table srcBottom = pickSrc(mgr, r);
            return new Concatenate(mgr, TestUtil.ILD, ImmutableList.of(srcTop.getId(), srcBottom.getId()), IncompleteColumnHandling.DEFAULT, true);
        },
        */
        // Aggregate:
        () -> {
            Table src = pickSrc(mgr, r);
            return new Aggregate(mgr, TestUtil.ILD, src.getId(), ImmutableList.of(new Pair<>(new ColumnId("Count"), IdentExpression.load(TypeState.GROUP_COUNT))), ImmutableList.of());
        }//,
        // R:
                /*
        () -> {
            Table src = pickSrc(mgr, r);
            return new RTransformation(mgr, TestUtil.ILD, ImmutableList.of(src.getId()), ImmutableList.of(), "rnorm(100)");
        }
        */
        ));


        ImmutableList<ExSupplier<Transformation>> l = genTransforms.build();
        mgr.record(l.get(r.nextInt(l.size())).get());
    }

    private Table pickSrc(TableManager mgr, SourceOfRandomness r)
    {
        ImmutableList<Table> allTables = mgr.getAllTables();
        return allTables.get(r.nextInt(allTables.size()));
    }

    private void addImmediateTable(TableManager mgr, DataTypeMaker dataTypeMaker, SourceOfRandomness r) throws UserException, InternalException
    {
        int numRows = r.nextInt(1, 20);
        int numCols = r.nextInt(1, 6);

        ImmutableList.Builder<SimulationFunction<RecordSet, EditableColumn>> cols = ImmutableList.builderWithExpectedSize(numCols);
        
        for (int i = 0; i < numCols; i++)
        {
            DataTypeAndValueMaker t = dataTypeMaker.makeType();
            cols.add(t.getDataType().makeImmediateColumn(new ColumnId(IdentifierUtility.identNum("Col", i)), DataTestUtil.makeList(r, numRows, numRows, () -> Either.right(t.makeValue())), t.makeValue()));
        }
        
        mgr.record(new ImmediateDataSource(mgr, TestUtil.ILD,new EditableRecordSet(cols.build(), () -> numRows)));
    }
}
