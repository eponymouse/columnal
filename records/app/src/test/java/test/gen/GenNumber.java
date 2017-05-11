package test.gen;

import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import records.error.UserException;
import utility.Utility;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by neil on 13/12/2016.
 */
public class GenNumber extends Generator<Number>
{
    private final boolean fixBits;

    public GenNumber(boolean fixBits)
    {
        super(Number.class);
        this.fixBits = fixBits;
    }

    public GenNumber()
    {
        this(false);
    }

    @Override
    public Number generate(SourceOfRandomness sourceOfRandomness, GenerationStatus generationStatus)
    {
        Number n;
        try
        {
            n = Utility.parseNumber(new GenNumberAsString(fixBits).generate(sourceOfRandomness, generationStatus));
        }
        catch (UserException e)
        {
            throw new RuntimeException(e);
        }
        List<Number> rets = new ArrayList<>();
        rets.add(n);
        if (n.doubleValue() == (double)n.intValue())
        {
            // If it fits in smaller, we may randomly choose to use smaller:
            rets.add(BigDecimal.valueOf(n.intValue()));
            if ((long) n.intValue() == n.longValue())
                rets.add(n.intValue());
            if ((long) n.shortValue() == n.longValue())
                rets.add(n.shortValue());
            if ((long) n.byteValue() == n.longValue())
                rets.add(n.byteValue());
        }
        return sourceOfRandomness.choose(rets);
    }
}
