package test.functions;

import annotation.qual.Value;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.Test;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeUtility;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.function.FunctionDefinition;
import records.transformations.function.Not;
import records.transformations.function.Xor;
import test.TestUtil;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
import utility.ValueFunction;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class TestBooleanFunctions
{
    private UnitManager mgr;
    {
        try
        {
            mgr = new UnitManager();
        }
        catch (InternalException | UserException e)
        {
            throw new RuntimeException(e);
        }
    }

    @Test
    @OnThread(Tag.Simulation)
    public void testNot() throws UserException, InternalException
    {
        FunctionDefinition function = new Not();
        @Nullable Pair<ValueFunction, DataType> checked = TestUtil.typeCheckFunction(function, DataType.BOOLEAN);
        if (checked == null)
        {
            fail("Type check failure");
        }
        else
        {
            assertEquals(DataType.BOOLEAN, checked.getSecond());
            // Not too hard to exhaustively test this one:
            assertEquals(true, (Boolean) checked.getFirst().call(DataTypeUtility.value(false)));
            assertEquals(false, (Boolean) checked.getFirst().call(DataTypeUtility.value(true)));
        }
    }

    @Test
    @OnThread(Tag.Simulation)
    public void testXor() throws InternalException, UserException
    {
        FunctionDefinition function = new Xor();
        @Nullable Pair<ValueFunction, DataType> checked = TestUtil.typeCheckFunction(function, DataType.tuple(DataType.BOOLEAN, DataType.BOOLEAN));
        if (checked == null)
        {
            fail("Type check failure");
        }
        else
        {
            assertEquals(DataType.BOOLEAN, checked.getSecond());
            // Not too hard to exhaustively test this one:
            assertEquals(true, (Boolean) checked.getFirst().call(DataTypeUtility.value(new @Value Object[]{DataTypeUtility.value(true), DataTypeUtility.value(false)})));
            assertEquals(true, (Boolean) checked.getFirst().call(DataTypeUtility.value(new @Value Object[]{DataTypeUtility.value(false), DataTypeUtility.value(true)})));
            assertEquals(false, (Boolean) checked.getFirst().call(DataTypeUtility.value(new @Value Object[]{DataTypeUtility.value(true), DataTypeUtility.value(true)})));
            assertEquals(false, (Boolean) checked.getFirst().call(DataTypeUtility.value(new @Value Object[]{DataTypeUtility.value(false), DataTypeUtility.value(false)})));
        }
    }

}
