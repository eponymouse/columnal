package test.gen;

import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.generator.java.time.LocalDateGenerator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.jetbrains.annotations.NotNull;
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
import records.transformations.expression.NotEqualExpression;
import records.transformations.expression.NumericLiteral;
import records.transformations.expression.StringLiteral;
import records.transformations.expression.TagExpression;
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
import java.util.stream.Collectors;

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
            DataType type = makeType(r);
            Pair<List<Object>, Expression> p = makeOfType(type);
            return new ExpressionValue(type, p.getFirst(), new KnownLengthRecordSet("", this.columns, 1), p.getSecond());
        }
        catch (InternalException | UserException e)
        {
            throw new RuntimeException(e);
        }
    }

    // Only valid after calling generate
    @NotNull
    @OnThread(value = Tag.Simulation, ignoreParent = true)
    public Pair<List<Object>, Expression> makeOfType(DataType type) throws UserException, InternalException
    {
        List<Object> value = makeValue(type);
        Expression expression = make(type, value, 4);
        return new Pair<>(value, expression);
    }

    @SuppressWarnings("intern")
    public static DataType makeType(SourceOfRandomness r)
    {
        // Leave out dates until we can actually make date values:
        return r.choose(distinctTypes.stream().filter(t -> t != DataType.DATE).collect(Collectors.<DataType>toList()));
    }

    private Expression make(DataType type, List<Object> targetValue, int maxLevels) throws UserException, InternalException
    {
        return type.apply(new DataTypeVisitor<Expression>()
        {
            @Override
            public Expression number(NumberInfo displayInfo) throws InternalException, UserException
            {
                //TODO add units
                return termDeep(maxLevels, l(() -> new NumericLiteral(Utility.parseNumber(new GenNumber().generate(r, gs)), displayInfo.getUnit())),
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
                        DataType t = makeType(r);
                        List<Object> val = makeValue(t);
                        return new EqualExpression(make(t, val, maxLevels - 1), make(t, val, maxLevels - 1));
                    },
                    () -> {
                        DataType t = makeType(r);
                        List<Object> val = makeValue(t);
                        return new NotEqualExpression(make(t, val, maxLevels - 1), make(t, val, maxLevels - 1));
                    }
                ));
            }

            @Override
            public Expression tagged(String typeName, List<TagType<DataType>> tags) throws InternalException, UserException
            {
                int tagIndex = r.nextInt(0, tags.size() - 1);
                List<ExpressionMaker> terminals = new ArrayList<>();
                List<ExpressionMaker> nonTerm = new ArrayList<>();
                for (TagType<DataType> tag : tags)
                {
                    Pair<@Nullable String, String> name = new Pair<>(typeName, tag.getName());
                    final @Nullable DataType inner = tag.getInner();
                    if (inner == null)
                        terminals.add(() -> new TagExpression(name, null));
                    else
                    {
                        final @NonNull DataType nonNullInner = inner;
                        nonTerm.add(() -> new TagExpression(name, make(nonNullInner, makeValue(nonNullInner), maxLevels - 1)));
                    }
                }
                return termDeep(maxLevels, terminals, nonTerm);
            }
        });
    }
    
    @FunctionalInterface
    public static interface ExpressionMaker
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
            public List<Object> tagged(String typeName, List<TagType<DataType>> tags) throws InternalException, UserException
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
