package test.gen;

import com.pholser.junit.quickcheck.generator.java.lang.AbstractStringGenerator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;

public class UnicodeStringGenerator extends AbstractStringGenerator
{
    protected int nextCodePoint(SourceOfRandomness random) {
        return random.nextInt(0, 0x10FFFF);
    }

    protected boolean codePointInRange(int codePoint) {
        return codePoint >= 0 && codePoint < 0x10FFFF;
    }
}
