package test;

import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.When;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import org.junit.runner.RunWith;
import records.data.Table;
import records.data.TableId;
import records.data.TableManager;
import records.error.InternalException;
import records.error.UserException;
import test.gen.GenImmediateData;
import test.gen.GenImmediateData.NumTables;
import test.gen.GenTableManager;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

/**
 * Created by neil on 07/12/2016.
 */
@RunWith(JUnitQuickcheck.class)
public class PropLoadSaveData
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
            Map<TableId, Table> loaded = toMap(mgr1.loadAll(savedMangled));
            String savedAgain = TestUtil.save(mgr1);
            Map<TableId, Table> loadedAgain = toMap(mgr2.loadAll(savedAgain));


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

}
