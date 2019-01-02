package test.gen;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.javaruntype.type.TypeParameter;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeUtility;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.expression.Expression;
import test.DummyManager;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;

import java.math.BigDecimal;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.stream.Stream;

public abstract class GenExpressionValueBase extends GenValueBase<ExpressionValue>
{
    private final IdentityHashMap<Expression, Pair<DataType, @Value Object>> expressionValues = new IdentityHashMap<>();
    
    protected GenExpressionValueBase()
    {
        super(ExpressionValue.class);
    }

    @Override
    @OnThread(value = Tag.Simulation, ignoreParent = true)
    public final ExpressionValue generate(SourceOfRandomness r, GenerationStatus generationStatus)
    {
        this.r = r;
        this.gs = generationStatus;
        expressionValues.clear();
        return generate();
    }

    @OnThread(value = Tag.Simulation, ignoreParent = true)
    protected abstract ExpressionValue generate();
    
    public final Expression register(Expression expression, DataType dataType, @Value Object value)
    {
        expressionValues.put(expression, new Pair<DataType, @Value Object>(dataType, value));
        return expression;
    }

    @Override
    public boolean canShrink(Object larger)
    {
        return larger instanceof ExpressionValue && ((ExpressionValue)larger).generator == this;
    }

    @Override
    @OnThread(value = Tag.Simulation, ignoreParent = true)
    public List<ExpressionValue> doShrink(SourceOfRandomness random, ExpressionValue larger)
    {
        return expressionValues.entrySet().stream().flatMap((Entry<Expression, Pair<DataType, @Value Object>> e) -> {
            Expression replacement = makeLiteral(e.getValue().getFirst(), e.getValue().getSecond());
            if (replacement == null)
                return Stream.of();
            Expression after = larger.expression.replaceSubExpression(e.getKey(), replacement);
            if (after.equals(larger.expression))
                return Stream.of(); // No longer within
            return Stream.of(larger.withExpression(after));
        }).collect(ImmutableList.toImmutableList());
    }

    @Override
    public BigDecimal magnitude(Object value)
    {
        if (value instanceof ExpressionValue)
        {
            return BigDecimal.valueOf(((ExpressionValue)value).expression.toString().length());
        }
        return super.magnitude(value);
    }

    @OnThread(Tag.Simulation)
    private @Nullable Expression makeLiteral(DataType dataType, @Value Object object)
    {
        try
        {
            return Expression.parse(null, DataTypeUtility.valueToString(dataType, object, null, true), DummyManager.INSTANCE.getTypeManager());
        }
        catch (InternalException | UserException e)
        {
            return null;
        }
    }
}
