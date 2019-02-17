package test.expressions;

import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Booleans;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.Test;
import records.data.*;
import records.data.Table.InitialLoadDetails;
import records.data.datatype.NumberInfo;
import records.data.datatype.TypeManager;
import records.data.unit.Unit;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.expression.ErrorAndTypeRecorderStorer;
import records.transformations.expression.EvaluateState;
import records.transformations.expression.Expression;
import records.transformations.expression.Expression.CheckedExp;
import records.transformations.expression.Expression.LocationInfo;
import records.transformations.expression.Expression.MultipleTableLookup;
import records.transformations.expression.TypeState;
import styled.StyledString;
import test.DummyManager;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.SimulationFunction;
import utility.Utility;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.OptionalInt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@OnThread(Tag.Simulation)
public class TestExpressionExplanation
{
    private final TableManager tableManager;
    
    public TestExpressionExplanation() throws UserException, InternalException
    {
        tableManager = DummyManager.make();
        List<SimulationFunction<RecordSet, EditableColumn>> columns = new ArrayList<>();
        columns.add(bools("all true", true, true, true, true));
        columns.add(bools("half false", false, true, false, true));
        columns.add(bools("all false", false, false, false, false));
        tableManager.record(new ImmediateDataSource(tableManager, new InitialLoadDetails(new TableId("T1"), null, null), new EditableRecordSet(columns, () -> 4)));
        
        columns.clear();
        columns.add(nums("asc", 0, 1, 2, 3));
        tableManager.record(new ImmediateDataSource(tableManager, new InitialLoadDetails(new TableId("T2"), null, null), new EditableRecordSet(columns, () -> 4)));
    }

    private static SimulationFunction<RecordSet, EditableColumn> bools(String name, boolean... values)
    {
        return rs -> new MemoryBooleanColumn(rs, new ColumnId(name), Utility.<Boolean, Either<String, Boolean>>mapList(Booleans.asList(values), Either::right), false);
    }

    private static SimulationFunction<RecordSet, EditableColumn> nums(String name, Number... values)
    {
        return rs -> new MemoryNumericColumn(rs, new ColumnId(name), new NumberInfo(Unit.SCALAR), Utility.<Number, Either<String, Number>>mapList(Arrays.asList(values), Either::right), 0);
    }

    @Test
    public void testUnexplained1() throws Exception
    {
        testExplanation("1", null);
        testExplanation("true", null);
    }
    
    @Test
    public void testExplainedElement() throws Exception
    {
        testExplanation("@call @function element(@entire T1:all true, 3)", l("T1", "all true", 3));
        testExplanation("@call @function element(@entire T2:asc, 2) > 5", l("T2", "asc", 2));
        testExplanation("(@call @function element(@entire T2:asc, 1) < 5) & (@call @function element(@entire T2:asc, 2) = 5)", l("T2", "asc", 2));
    }

    @Test
    public void testExplainedAll() throws Exception
    {
        testExplanation("@call @function all(@entire T1:all false, (? = true))", l("T1", "all false", 0));
        testExplanation("@call @function all(@entire T1:all true, @function not)", l("T1", "all true", 0));
        testExplanation("@call @function all(@entire T2:asc, (? < 3))", l("T2", "asc", 3));
        testExplanation("@call @function all(@entire T2:asc, (? = (1 \u00B1 1)))", l("T2", "asc", 3));
        testExplanation("@call @function none(@entire T2:asc, (? <> (1 \u00B1 1)))", l("T2", "asc", 3));
    }

    private ImmutableList<ExplanationLocation> l(String tableName, String columnName, int... rowIndexes)
    {
        return Arrays.stream(rowIndexes).mapToObj(rowIndex ->
            new ExplanationLocation(new TableId(tableName), new ColumnId(columnName), rowIndex)
        ).collect(ImmutableList.toImmutableList());
    }
    
    private void testExplanation(String src, @Nullable ImmutableList<ExplanationLocation> expectedExplanation) throws Exception
    {
        TypeManager typeManager = tableManager.getTypeManager();
        Expression expression = Expression.parse(null, src, typeManager);

        ErrorAndTypeRecorderStorer errorAndTypeRecorderStorer = new ErrorAndTypeRecorderStorer();
        CheckedExp typeCheck = expression.check(new MultipleTableLookup(null, tableManager, null), new TypeState(typeManager.getUnitManager(), typeManager), LocationInfo.UNIT_DEFAULT, errorAndTypeRecorderStorer);
        assertNotNull(errorAndTypeRecorderStorer.getAllErrors().collect(StyledString.joining("\n")).toPlain(), typeCheck);
        expression.getValue(new EvaluateState(typeManager, OptionalInt.empty()) {
            @Override
            public boolean recordBooleanExplanation()
            {
                return true;
            }
        });
        
        // Now explanation should be available:
        ImmutableList<ExplanationLocation> actual = expression.getBooleanExplanation();
        
        assertEquals(expectedExplanation, actual);
    }
}
