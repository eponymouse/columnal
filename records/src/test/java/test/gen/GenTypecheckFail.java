package test.gen;

import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import records.data.KnownLengthRecordSet;
import records.data.RecordSet;
import records.data.datatype.DataType;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.expression.BooleanLiteral;
import records.transformations.expression.Expression;
import test.gen.GenTypecheckFail.TypecheckInfo;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;

import java.util.Arrays;

import static test.TestUtil.distinctTypes;

/**
 * Created by neil on 10/12/2016.
 */
public class GenTypecheckFail extends Generator<TypecheckInfo>
{
    public GenTypecheckFail()
    {
        super(TypecheckInfo.class);
    }

    public class TypecheckInfo
    {
        public final RecordSet recordSet;
        public final Expression expression;

        public TypecheckInfo(RecordSet recordSet, Expression expression)
        {
            this.recordSet = recordSet;
            this.expression = expression;
        }
    }


    @Override
    @OnThread(value = Tag.Simulation,ignoreParent = true)
    public TypecheckInfo generate(SourceOfRandomness r, GenerationStatus generationStatus)
    {
        try
        {
            Pair<Boolean, Expression> p = makeExpression(true, r.choose(distinctTypes), r);
            if (!p.getFirst())
                throw new RuntimeException("Generated expression will not fail type check!");
            return new TypecheckInfo(new KnownLengthRecordSet("", Arrays.asList(), 0), p.getSecond());
        }
        catch (InternalException | UserException e)
        {
            throw new RuntimeException(e);
        }
    }

    private Pair<Boolean, Expression> makeExpression(boolean forceFail, DataType type, SourceOfRandomness r)
    {
        return new Pair<>(true, new BooleanLiteral(true)); //TODO
    }
}
