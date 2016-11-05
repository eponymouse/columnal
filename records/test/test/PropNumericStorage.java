package test;

import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import org.junit.runner.RunWith;
import records.data.NumericColumnStorage;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Created by neil on 05/11/2016.
 */
@RunWith(JUnitQuickcheck.class)
public class PropNumericStorage
{
    @Property
    @OnThread(Tag.Simulation)
    public void testPureNumbers(@From(GenNumbers.class) List<String> input) throws IOException, InternalException, UserException
    {
        NumericColumnStorage storage = new NumericColumnStorage(0);
        for (String s : input)
            storage.addNumber(s);

        assertEquals(input.size(), storage.filled());

        List<String> out = new ArrayList<>();
        for (int i = 0; i < input.size(); i++)
            out.add(storage.get(i).toString());
        TestUtil.assertEqualList(input, out);
    }

    public static class GenNumbers extends Generator<List<String>>
    {
        @SuppressWarnings("unchecked")
        public GenNumbers()
        {
            super((Class<List<String>>)(Class<?>)List.class);
        }

        @Override
        public List<String> generate(SourceOfRandomness sourceOfRandomness, GenerationStatus generationStatus)
        {
            List<String> r = new ArrayList<>();
            int length = sourceOfRandomness.nextInt(0, 100);
            boolean includeFractional = sourceOfRandomness.nextBoolean();
            int maxBits = sourceOfRandomness.nextInt(6, 80);
            for (int i = 0; i < length; i++)
            {
                if (includeFractional && sourceOfRandomness.nextBoolean())
                {
                    // I don't think it matters here whether we come up with
                    // double or big decimal; will be stored in big decimal either way.
                    r.add(String.format("%f", sourceOfRandomness.nextDouble()));
                }
                else
                {
                    // We geometrically distribute by uniformly distributing number of bits:
                    r.add(sourceOfRandomness.nextBigInteger(sourceOfRandomness.nextInt(1, maxBits)).toString());

                }
            }
            return r;
        }
    }
}
