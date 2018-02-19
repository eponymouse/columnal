package test;

import annotation.qual.Value;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import org.junit.runner.RunWith;
import records.data.Column;
import records.data.ColumnId;
import records.data.EditableRecordSet;
import records.error.InternalException;
import records.error.UserException;
import test.gen.GenRandom;
import test.gen.GenTypeAndValueGen;
import test.gen.GenTypeAndValueGen.TypeAndValueGen;
import threadchecker.OnThread;
import threadchecker.Tag;
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
        int length = 1 + r.nextInt(100);
        List<@Value Object> originals = new ArrayList<>();
        List<@Value Object> replacements = new ArrayList<>();
        for (int i = 0; i < length; i++)
        {
            originals.add(typeAndValueGen.makeValue());
            replacements.add(typeAndValueGen.makeValue());
        }
        @SuppressWarnings("keyfor")
        EditableRecordSet rs = new EditableRecordSet(Collections.singletonList(typeAndValueGen.getType().makeImmediateColumn(new ColumnId("C0"), originals, typeAndValueGen.makeValue())), () -> length);
        Column col = rs.getColumns().get(0);

        List<Pair<Integer, @Value Object>> pendingReplacements = new ArrayList<>();

        // Check initial store worked:
        for (int i = 0; i < length; i++)
        {
            TestUtil.assertValueEqual("Value " + i, originals.get(i), col.getType().getCollapsed(i));
            pendingReplacements.add(new Pair<>(i, replacements.get(i)));
        }

        // Do replacements:
        while (!pendingReplacements.isEmpty())
        {
            Pair<Integer, @Value Object> repl = TestUtil.removeRandom(r, pendingReplacements);
            col.getType().setCollapsed(repl.getFirst(), repl.getSecond());
        }

        // Check replacement worked:
        for (int i = 0; i < length; i++)
        {
            TestUtil.assertValueEqual("Value " + i, replacements.get(i), col.getType().getCollapsed(i));
        }
    }
}
