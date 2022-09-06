package test.gen;

import annotation.identifier.qual.UnitIdentifier;
import com.google.common.primitives.Ints;
import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import org.sosy_lab.common.rationals.Rational;
import records.data.unit.SingleUnit;
import records.data.unit.UnitDeclaration;
import test.gen.GenUnitDefinition.UnitDetails;
import xyz.columnal.utility.Either;
import xyz.columnal.utility.Pair;

import java.math.BigInteger;
import java.util.ArrayList;

public class GenUnitDefinition extends Generator<UnitDetails>
{
    private static final int[] valid;
    
    static {
        ArrayList<Integer> validBuild = new ArrayList<>();
        
        for (int i = 0; i <= 0x10FFFF; i++)
        {
            if (Character.isAlphabetic(i) 
                || Character.getType(i) == Character.OTHER_LETTER
                || Character.getType(i) == Character.CURRENCY_SYMBOL)
                validBuild.add(i);
        }
        valid = Ints.toArray(validBuild);
    }
    
    public GenUnitDefinition()
    {
        super(UnitDetails.class);
    }
    
    @Override
    public UnitDetails generate(SourceOfRandomness random, GenerationStatus status)
    {
        @UnitIdentifier String id = genIdent(random);
        if (random.nextInt(4) == 1)
        {
            return new UnitDetails(id, Either.left(genIdent(random)));
        }
        else
        {
            UnitDeclaration unitDeclaration = new UnitDeclaration(
                    new SingleUnit(id, new GenString().generate(random, status), "", ""),
                random.nextInt(3) == 1 ? null :
                    new Pair<>(genRational(random), new GenUnit().generate(random, status)), ""
            );
            return new UnitDetails(id, Either.right(unitDeclaration));
        }
    }

    private Rational genRational(SourceOfRandomness random)
    {
        BigInteger num = random.nextBigInteger(128).abs();
        if (num.equals(BigInteger.ZERO))
            num = BigInteger.ONE;
        BigInteger den = random.nextBigInteger(128).abs();
        if (den.equals(BigInteger.ZERO))
            den = BigInteger.ONE;
        return Rational.of(num, den);
    }

    @SuppressWarnings("identifier")
    private @UnitIdentifier String genIdent(SourceOfRandomness random)
    {
        StringBuilder sb = new StringBuilder();
        sb.appendCodePoint(pickValid(random));
        int segments = random.nextInt(1, 3);
        for (int i = 0; i < segments; i++)
        {
            if (i > 0)
                sb.append('_');
            
            int length = random.nextInt(1, 4);
            for (int j = 0; j < length; j++)
            {
                sb.appendCodePoint(pickValid(random));
            }
        }
        return sb.toString();
    }

    private int pickValid(SourceOfRandomness random)
    {
        return valid[random.nextInt(valid.length)];
    }

    public static class UnitDetails
    {
        public final @UnitIdentifier String name;
        public final Either<@UnitIdentifier String, UnitDeclaration> aliasOrDeclaration;

        public UnitDetails(@UnitIdentifier String name, Either<@UnitIdentifier String, UnitDeclaration> aliasOrDeclaration)
        {
            this.name = name;
            this.aliasOrDeclaration = aliasOrDeclaration;
        }

        // For debugging:
        @Override
        public String toString()
        {
            return "UnitDetails{" +
                    "name='" + name + '\'' +
                    ", aliasOrDeclaration=" + aliasOrDeclaration +
                    '}';
        }
    }
}
