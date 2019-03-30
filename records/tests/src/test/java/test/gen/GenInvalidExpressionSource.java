package test.gen;

import com.google.common.collect.ImmutableList;
import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import records.gui.expressioneditor.GeneralExpressionEntry.Keyword;
import records.gui.expressioneditor.GeneralExpressionEntry.Op;

import java.util.Arrays;
import java.util.function.Supplier;

public class GenInvalidExpressionSource extends Generator<String>
{
    public GenInvalidExpressionSource()
    {
        super(String.class);
    }

    @Override
    public String generate(SourceOfRandomness random, GenerationStatus status)
    {
        ImmutableList<Supplier<String>> tokenMakers = ImmutableList.of(
            ts(random, Arrays.stream(Keyword.values()).map(o -> o.getContent()).toArray(String[]::new)),
            ts(random, Arrays.stream(Op.values()).filter(o -> o != Op.ADD).map(o -> o.getContent()).toArray(String[]::new)),
            ts(random, "a", "z"),
            ts(random, "1", "9", "1.2")
        );
        
        int totalTokens = random.nextInt(20);
        StringBuilder r = new StringBuilder();
        for (int i = 0; i < totalTokens; i++)
        {
            r.append(tokenMakers.get(random.nextInt(tokenMakers.size())).get());
        }
        
        return r.toString();
    }
    
    private static Supplier<String> ts(SourceOfRandomness r, String... options)
    {
        return () -> options[r.nextInt(options.length)];
    }
}
