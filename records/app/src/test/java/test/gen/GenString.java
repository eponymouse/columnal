package test.gen;

import com.pholser.junit.quickcheck.generator.java.lang.AbstractStringGenerator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;

public class GenString extends AbstractStringGenerator
{
    protected int nextCodePoint(SourceOfRandomness random) {
        int n = random.nextInt(0x20, 0x10FFFF);
        if (n == 0x7F || !Character.isDefined(n) || (n >= 0xD800 && n < 0xE000))
            n = random.nextInt(0x20, 0x7E);
        return n;
    }

    protected boolean codePointInRange(int codePoint) {
        return codePoint >= 0x20 && codePoint < 0x10FFFF && codePoint != 0x7F && Character.isDefined(codePoint);
    }

}
