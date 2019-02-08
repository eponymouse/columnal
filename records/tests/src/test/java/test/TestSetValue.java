package test;

import annotation.qual.Value;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import org.checkerframework.checker.initialization.qual.Initialized;
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
import test.gen.GenTypeAndValueGen.TypeAndValueGen;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.Pair;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

@RunWith(JUnitQuickcheck.class)
public class TestSetValue
{
    @Property(trials=200)
    @OnThread(Tag.Simulation)
    public void propSetValue(@From(GenTypeAndValueGen.class)TypeAndValueGen typeAndValueGen, @From(GenRandom.class) Random r) throws UserException, InternalException
    {
        @Initialized int length = 1 + r.nextInt(100);
        List<Either<String, @Value Object>> originals = new ArrayList<>();
        List<Either<String, @Value Object>> replacements = new ArrayList<>();
        for (int i = 0; i < length; i++)
        {
            originals.add(r.nextInt(8) == 1 ? Either.left("~R" + r.nextInt(100)) : Either.right(typeAndValueGen.makeValue()));
            replacements.add(r.nextInt(8) == 1 ? Either.left("#R" + r.nextInt(100)) : Either.right(typeAndValueGen.makeValue()));
        }
        @SuppressWarnings({"keyfor", "units"})
        EditableRecordSet rs = new EditableRecordSet(Collections.singletonList(typeAndValueGen.getType().makeImmediateColumn(new ColumnId("C0"), originals, typeAndValueGen.makeValue())), () -> length);
        Column col = rs.getColumns().get(0);

        List<Pair<Integer, Either<String, @Value Object>>> pendingReplacements = new ArrayList<>();

        // Check initial store worked:
        for (int i = 0; i < length; i++)
        {
            TestUtil.assertValueEitherEqual("Value " + i, originals.get(i), collapseErr(col.getType(), i));
            pendingReplacements.add(new Pair<>(i, replacements.get(i)));
        }

        // Do replacements:
        while (!pendingReplacements.isEmpty())
        {
            Pair<Integer, Either<String, @Value Object>> repl = TestUtil.removeRandom(r, pendingReplacements);
            col.getType().setCollapsed(repl.getFirst(), repl.getSecond());
        }

        // Check replacement worked:
        for (int i = 0; i < length; i++)
        {
            TestUtil.assertValueEitherEqual("Value " + i, replacements.get(i), collapseErr(col.getType(), i));
        }
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