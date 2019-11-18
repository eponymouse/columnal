package test.gen;

import annotation.qual.Value;
import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataTypeUtility;
import records.error.UserException;
import utility.Utility;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by neil on 13/12/2016.
 */
public class GenNumber extends Generator<@Value Number>
{
    private final boolean fixBits;

    @SuppressWarnings("valuetype")
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
    public @Value Number generate(SourceOfRandomness sourceOfRandomness, @Nullable GenerationStatus generationStatus)
    {
        @Value Number n;
        try
        {
            n = Utility.parseNumber(new GenNumberAsString(fixBits).generate(sourceOfRandomness, generationStatus));
        }
        catch (UserException e)
        {
            throw new RuntimeException(e);
        }
        List<@Value Number> rets = new ArrayList<>();
        rets.add(n);
        if (n.doubleValue() == (double)n.intValue())
        {
            // If it fits in smaller, we may randomly choose to use smaller:
            rets.add(DataTypeUtility.value(BigDecimal.valueOf(DataTypeUtility.value(n.intValue()))));
            if ((long) n.intValue() == n.longValue())
                rets.add(DataTypeUtility.value(n.intValue()));
            if ((long) n.shortValue() == n.longValue())
                rets.add(DataTypeUtility.value(n.shortValue()));
            if ((long) n.byteValue() == n.longValue())
                rets.add(DataTypeUtility.value(n.byteValue()));
        }
        return sourceOfRandomness.choose(rets);
    }
}
