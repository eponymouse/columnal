package test.gen;

import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.RecordSet;
import records.data.datatype.DataType;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.expression.EqualExpression;
import records.transformations.expression.Expression;
import test.TestUtil;
import test.gen.GenExpressionValue.ExpressionValue;
import test.gen.GenTypecheckFail.TypecheckInfo;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by neil on 10/12/2016.
 */
public class GenTypecheckFail extends Generator<TypecheckInfo>
{

    @SuppressWarnings("initialization")
    public GenTypecheckFail()
    {
        super(TypecheckInfo.class);
    }

    public class TypecheckInfo
    {
        public final RecordSet recordSet;
        public final List<Expression> expressionFailures;

        public TypecheckInfo(RecordSet recordSet, List<Expression> expressionFailures)
        {
            this.recordSet = recordSet;
            this.expressionFailures = expressionFailures;
        }
    }


    @Override
    @OnThread(value = Tag.Simulation,ignoreParent = true)
    @SuppressWarnings("nullness")
    public TypecheckInfo generate(SourceOfRandomness r, GenerationStatus generationStatus)
    {
        GenExpressionValue gen = new GenExpressionValue();
        ExpressionValue valid = gen.generate(r, generationStatus);
        try
        {
            valid.expression.check(valid.recordSet, TestUtil.typeState(), (e, s) ->
            {
                throw new RuntimeException(s);
            });

            List<Expression> failures = valid.expression._test_allMutationPoints().map(p -> {
                try
                {
                    @Nullable Expression failedCopy = p.getFirst()._test_typeFailure(r.toJDKRandom(), type -> gen.makeOfType(pickTypeOtherThan(type, r)).getSecond());
                    if (failedCopy == null)
                        return null;
                    else
                        return p.getSecond().apply(failedCopy);
                }
                catch (InternalException | UserException e)
                {
                    throw new RuntimeException(e);
                }
            }).filter(p -> p != null).collect(Collectors.toList());

            return new TypecheckInfo(gen.getRecordSet(), failures);
        }
        catch (InternalException | UserException e)
        {
            throw new RuntimeException(e);
        }
    }

    private DataType pickType(SourceOfRandomness r)
    {
        return GenExpressionValue.makeType(r);
    }

    @SuppressWarnings("intern")
    private DataType pickTypeOtherThan(@Nullable DataType type, SourceOfRandomness r)
    {
        DataType picked;
        do
        {
            picked = pickType(r);
        }
        while (picked == type);
        return picked;

    }
}
