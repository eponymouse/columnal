package test.gen;

import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.generator.java.time.LocalDateGenerator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.Column;
import records.data.KnownLengthRecordSet;
import records.data.RecordSet;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DataTypeVisitor;
import records.data.datatype.DataType.NumberInfo;
import records.data.datatype.DataType.TagType;
import records.error.FunctionInt;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.expression.BooleanLiteral;
import records.transformations.expression.EqualExpression;
import records.transformations.expression.Expression;
import records.transformations.expression.NumericLiteral;
import records.transformations.expression.StringLiteral;
import test.TestUtil;
import test.gen.GenExpressionValue.ExpressionValue;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
import utility.Utility;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static test.TestUtil.distinctTypes;

/**
 * Created by neil on 10/12/2016.
 */
public class GenExpressionValue extends Generator<ExpressionValue>
{
    public static class ExpressionValue
    {
        public final DataType type;
        public final List<Object> value;
        public final RecordSet recordSet;
        public final Expression expression;

        public ExpressionValue(DataType type, List<Object> value, RecordSet recordSet, Expression expression)
        {
            this.type = type;
            this.value = value;
            this.recordSet = recordSet;
            this.expression = expression;
        }
    }

    @SuppressWarnings("initialization")
    public GenExpressionValue()
    {
        super(ExpressionValue.class);
    }

    // Easier than passing parameters around:
    private SourceOfRandomness r;
    private GenerationStatus gs;
    private List<FunctionInt<RecordSet, Column>> columns;

    @Override
    @OnThread(value = Tag.Simulation, ignoreParent = true)
    public ExpressionValue generate(SourceOfRandomness r, GenerationStatus generationStatus)
    {
        this.r = r;
        this.gs = generationStatus;
        this.columns = new ArrayList<>();
        try
        {
            DataType type = makeType();
            List<Object> value = makeValue(type);
            Expression expression = make(type, value, 4);
            return new ExpressionValue(type, value, new KnownLengthRecordSet("", columns, 1), expression);
        }
        catch (InternalException | UserException e)
        {
            throw new RuntimeException(e);
        }
    }

    private DataType makeType()
    {
        return r.choose(distinctTypes);
    }

    private Expression make(DataType type, List<Object> targetValue, int maxLevels) throws UserException, InternalException
    {
        return type.apply(new DataTypeVisitor<Expression>()
        {
            @Override
            public Expression number(NumberInfo displayInfo) throws InternalException, UserException
            {
                return termDeep(maxLevels, l(() -> new NumericLiteral(Utility.parseNumber(new GenNumber().generate(r, gs)))),
                    l());
            }

            @Override
            public Expression text() throws InternalException, UserException
            {
                return termDeep(maxLevels, l(() -> new StringLiteral((String)targetValue.get(0))), l());
            }

            @Override
            public Expression date() throws InternalException, UserException
            {
                return termDeep(maxLevels, l(), l());
            }

            @Override
            public Expression bool() throws InternalException, UserException
            {
                return termDeep(maxLevels, l(() -> new BooleanLiteral(r.nextBoolean())), l(
                    () -> {
                        DataType t = makeType();
                        List<Object> val = makeValue(t);
                        return new EqualExpression(make(t, val, maxLevels - 1), make(t, val, maxLevels - 1));
                    }
                ));
            }

            @Override
            public Expression tagged(List<TagType<DataType>> tags) throws InternalException, UserException
            {
                int tagIndex = r.nextInt(0, tags.size() - 1);
                return termDeep(maxLevels, l(), l());
            }
        });
    }
    
    @FunctionalInterface
    private static interface ExpressionMaker
    {
        public Expression make() throws InternalException, UserException;
    }

    private List<ExpressionMaker> l(ExpressionMaker... suppliers)
    {
        return Arrays.asList(suppliers);
    }

    private List<Object> makeValue(DataType t) throws UserException, InternalException
    {
        return t.apply(new DataTypeVisitor<List<Object>>()
        {
            @Override
            public List<Object> number(NumberInfo displayInfo) throws InternalException, UserException
            {
                return Collections.singletonList(Utility.parseNumber(new GenNumber().generate(r, gs)));
            }

            @Override
            public List<Object> text() throws InternalException, UserException
            {
                return Collections.singletonList(TestUtil.makeString(r, gs));
            }

            @Override
            public List<Object> date() throws InternalException, UserException
            {
                return Collections.singletonList(new LocalDateGenerator().generate(r, gs));
            }

            @Override
            public List<Object> bool() throws InternalException, UserException
            {
                return Collections.singletonList(r.nextBoolean());
            }

            @Override
            public List<Object> tagged(List<TagType<DataType>> tags) throws InternalException, UserException
            {
                int tagIndex = r.nextInt(0, tags.size() - 1);
                ArrayList<Object> o;
                @Nullable DataType inner = tags.get(tagIndex).getInner();
                if (inner != null)
                    o = new ArrayList<>(makeValue(inner));
                else
                    o = new ArrayList<>();
                o.add(0, tagIndex);
                return o;
            }
        });
    }

    private Expression termDeep(int maxLevels, List<ExpressionMaker> terminals, List<ExpressionMaker> deeper) throws UserException, InternalException
    {
        if (!terminals.isEmpty() && (maxLevels <= 1 || deeper.isEmpty() || r.nextBoolean()))
            return r.choose(terminals).make();
        else
            return r.choose(deeper).make();
    }
}
