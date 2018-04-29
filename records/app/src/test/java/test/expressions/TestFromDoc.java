package test.expressions;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.Assert;
import org.junit.Test;
import records.data.RecordSet;
import records.data.TableId;
import records.data.datatype.DataType;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.expression.ErrorAndTypeRecorderStorer;
import records.transformations.expression.EvaluateState;
import records.transformations.expression.Expression;
import records.transformations.expression.Expression.TableLookup;
import records.transformations.expression.TypeState;
import records.types.TypeConcretisationError;
import records.types.TypeExp;
import test.DummyManager;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class TestFromDoc
{
    @Test
    @OnThread(Tag.Simulation)
    public void testFromDoc() throws IOException, InternalException, UserException
    {        
        for (File file : FileUtils.listFiles(new File("target/classes"), new String[]{"test"}, false))
        {
            List<String> lines = FileUtils.readLines(file, StandardCharsets.UTF_8);
            for (String line : lines)
            {
                if (line.trim().isEmpty())
                    continue;
                
                boolean errorLine = false;
                if (line.startsWith("!!!"))
                {
                    errorLine = true;
                    line = StringUtils.removeStart(line, "!!!");
                }
                
                Expression expression = Expression.parse(null, line, DummyManager.INSTANCE.getTypeManager());
                ErrorAndTypeRecorderStorer errors = new ErrorAndTypeRecorderStorer();
                TypeExp typeExp = expression.check(new TableLookup()
                {
                    @Override
                    public @Nullable RecordSet getTable(@Nullable TableId tableId)
                    {
                        return null;
                    }
                }, new TypeState(DummyManager.INSTANCE.getUnitManager(), DummyManager.INSTANCE.getTypeManager()), errors);
                assertEquals("Errors for " + line, Arrays.asList(), errors.getAllErrors().collect(Collectors.toList()));
                assertNotNull(line, typeExp);
                if (typeExp == null) continue; // Won't happen
                Either<TypeConcretisationError, DataType> concreteType = typeExp.toConcreteType(DummyManager.INSTANCE.getTypeManager());
                // It may be a type concretisation error e.g. for minimum([])
                if (!errorLine)
                    assertEquals(line, Either.right(DataType.BOOLEAN), concreteType);
                if (errorLine)
                {
                    // Must be user exception
                    try
                    {
                        expression.getBoolean(0, new EvaluateState(DummyManager.INSTANCE.getTypeManager()), null);
                        Assert.fail("Expected error but got none for\n" + line);
                    }
                    catch (UserException e)
                    {
                        // As expected!
                    }
                }
                else
                {
                    boolean result = expression.getBoolean(0, new EvaluateState(DummyManager.INSTANCE.getTypeManager()), null);
                    assertTrue(line, result);
                }
            }
        }
    }
}
