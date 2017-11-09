package test.gen;

import com.pholser.junit.quickcheck.generator.java.lang.AbstractStringGenerator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;

public class GenString extends AbstractStringGenerator
{
    protected int nextCodePoint(SourceOfRandomness random) {
        int n = random.nextInt(0x20, 0x10FFFF);
        if (n == 0x7F || !Character.isDefined(n))
            n = random.nextInt(0x20, 0x7F);
        return n;
    }

    protected boolean codePointInRange(int codePoint) {
        return codePoint >= 0x20 && codePoint < 0x10FFFF && codePoint != 0x7F && Character.isDefined(codePoint);
    }

}
