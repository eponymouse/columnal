package test;

import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.When;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import org.junit.runner.RunWith;
import records.data.Column;
import records.data.EditableColumn;
import records.data.ImmediateDataSource;
import records.data.Table;
import records.data.TableId;
import records.data.TableManager;
import records.error.InternalException;
import records.error.UserException;
import test.gen.GenImmediateData;
import test.gen.GenImmediateData.NumTables;
import test.gen.GenRandom;
import test.gen.GenTableManager;
import test.gui.util.FXApplicationTest;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;

import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

/**
 * Created by neil on 07/12/2016.
 */
@RunWith(JUnitQuickcheck.class)
public class PropLoadSaveData extends FXApplicationTest
{
    @Property(trials = 20)
    @OnThread(value = Tag.Simulation, ignoreParent = true)
    public void testImmediate(
            @From(GenTableManager.class) TableManager mgr1,
            @From(GenTableManager.class) TableManager mgr2,
            @From(GenImmediateData.class) @NumTables(maxTables = 4) GenImmediateData.ImmediateData_Mgr original)
        throws ExecutionException, InterruptedException, UserException, InternalException, InvocationTargetException
    {
        String saved = TestUtil.save(original.mgr);
        try
        {
            //Assume users destroy leading whitespace:
            String savedMangled = saved.replaceAll("\n +", "\n");
            Map<TableId, Table> loaded = toMap(mgr1.loadAll(savedMangled, w -> {}));
            String savedAgain = TestUtil.save(mgr1);
            Map<TableId, Table> loadedAgain = toMap(mgr2.loadAll(savedAgain, w -> {}));


            assertEquals(saved, savedAgain);
            assertEquals(toMap(original.data), loaded);
            assertEquals(loaded, loadedAgain);
            assertEquals(original.mgr.getTypeManager().getKnownTaggedTypes(), mgr1.getTypeManager().getKnownTaggedTypes());
            assertEquals(original.mgr.getTypeManager().getKnownTaggedTypes(), mgr2.getTypeManager().getKnownTaggedTypes());
            assertEquals(original.mgr.getUnitManager().getAllDeclared(), mgr1.getUnitManager().getAllDeclared());
            assertEquals(original.mgr.getUnitManager().getAllDeclared(), mgr2.getUnitManager().getAllDeclared());
        }
        catch (Throwable t)
        {
            System.err.println("Original:\n" + saved);
            System.err.flush();
            throw t;
        }
    }

    private static Map<TableId, Table> toMap(List<? extends Table> tables)
    {
        return tables.stream().collect(Collectors.<Table, TableId, Table>toMap(Table::getId, Function.identity()));
    }

    @Property(trials = 20)
    @OnThread(value = Tag.Simulation, ignoreParent = true)
    public void testImmediateInclError(
            //@When(seed=4184268094197695469L)
            @From(GenTableManager.class) TableManager mgr1,
            //@When(seed=6457512938358589961L)
            @From(GenTableManager.class) TableManager mgr2,
            //@When(seed=1860968919937845412L)
            @From(GenImmediateData.class) @NumTables(maxTables = 4) GenImmediateData.ImmediateData_Mgr original,
            //@When(seed=2075546916866037623L)
            @From(GenRandom.class) Random r)
            throws Exception
    {
        TestUtil.printSeedOnFail(() -> {
            // Introduce some errors:
            for (int i = 0; i < 20; i++)
            {
                ImmediateDataSource table = original.data.get(r.nextInt(original.data.size()));
                if (table.getData().getLength() > 0)
                {
                    int row = r.nextInt(table.getData().getLength());
                    int colIndex = r.nextInt(table.getData().getColumns().size());
                    setInvalid(table.getData().getColumns().get(colIndex), row, r);
                }
            }

            testImmediate(mgr1, mgr2, original);
        });
    }

    private void setInvalid(Column column, int row, Random r) throws UserException, InternalException
    {
        column.getType().setCollapsed(row, Either.left(r.nextInt(10) == 1 ? "@END" : ("@" + r.nextInt())));
    }

}
