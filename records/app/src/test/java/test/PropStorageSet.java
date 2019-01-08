package test;

import annotation.qual.Value;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.When;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import org.checkerframework.checker.nullness.qual.KeyFor;
import org.junit.runner.RunWith;
import records.data.Column;
import records.data.ColumnId;
import records.data.EditableRecordSet;
import records.data.datatype.DataTypeValue;
import records.error.InternalException;
import records.error.InvalidImmediateValueException;
import records.error.UserException;
import test.gen.GenRandom;
import test.gen.GenTypeAndValueGen;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;

import javax.swing.SwingUtilities;
import java.lang.reflect.InvocationTargetException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Created by neil on 05/06/2017.
 */
@RunWith(JUnitQuickcheck.class)
public class PropStorageSet
{
    @Property(trials = 100)
    @OnThread(Tag.Simulation)
    public void testSet(@When(seed=1L) @From(GenTypeAndValueGen.class) GenTypeAndValueGen.TypeAndValueGen typeAndValueGen, @When(seed=1L) @From(GenRandom.class) Random r) throws UserException, InternalException, Exception
    {
        // Make sure FX is initialised:
        SwingUtilities.invokeAndWait(() -> new JFXPanel());
        Platform.runLater(() -> {});
        
        @SuppressWarnings({"keyfor", "units"})
        EditableRecordSet recordSet = new EditableRecordSet(Collections.singletonList(rs -> typeAndValueGen.getType().makeImmediateColumn(new ColumnId("C"), Collections.emptyList(), typeAndValueGen.makeValue()).apply(rs)), () -> 0);
        Column c = recordSet.getColumns().get(0);
        assertEquals(0, c.getLength());
        recordSet.insertRows(0, 20);
        assertEquals(20, c.getLength());

        Map<Integer, Either<String, @Value Object>> vals = new HashMap<>();

        // Do many writes:
        for (int i = 0; i < 40; i++)
        {
            int rowIndex = r.nextInt(20);
            @Value Object value = typeAndValueGen.makeValue();
            DataTypeValue columnType = c.getType();
            Either<String, @Value Object> valueOrErr = r.nextInt(5) == 1 ? Either.left(("Err " + i)) : Either.right(value);
            columnType.setCollapsed(rowIndex, valueOrErr);
            TestUtil.assertValueEitherEqual("Type: " + typeAndValueGen.getType() + " index " + rowIndex, valueOrErr, collapseErr(c.getType(), rowIndex));
            vals.put(rowIndex, valueOrErr);
        }

        assertEquals(20, c.getLength());
        
        // Test all at end, helps test post overwrites:
        for (Entry<@KeyFor("vals") Integer, Either<String, @Value Object>> entry : vals.entrySet())
        {
            TestUtil.assertValueEitherEqual("Type: " + typeAndValueGen.getType() + " index " + entry.getKey(), entry.getValue(), collapseErr(c.getType(), entry.getKey()));
        }
        // TODO test reverting
    }

    @OnThread(Tag.Simulation)
    private Either<String, @Value Object> collapseErr(DataTypeValue type, int rowIndex) throws UserException, InternalException
    {
        try
        {
            return Either.right(type.getCollapsed(rowIndex));
        }
        catch (InvalidImmediateValueException e)
        {
            return Either.left(e.getInvalid());
        }
    }

}
