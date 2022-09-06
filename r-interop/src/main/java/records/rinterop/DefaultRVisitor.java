package records.rinterop;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType.DateTimeInfo.DateTimeType;
import records.error.InternalException;
import records.error.UserException;

import java.time.temporal.TemporalAccessor;
import java.util.Optional;

/**
 * Implements RVisitor to return a default value from all methods.  You can then override individual methods if you wish
 */
public abstract class DefaultRVisitor<T> implements RVisitor<T>
{
    private final T def;

    public DefaultRVisitor(T def)
    {
        this.def = def;
    }

    @Override
    public T visitString(@Nullable @Value String s, boolean isSymbol) throws InternalException, UserException
    {
        return def;
    }

    @Override
    public T visitLogicalList(boolean[] values, boolean @Nullable [] isNA, @Nullable RValue attributes) throws InternalException, UserException
    {
        return def;
    }

    @Override
    public T visitIntList(int[] values, @Nullable RValue attributes) throws InternalException, UserException
    {
        return def;
    }

    @Override
    public T visitDoubleList(double[] values, @Nullable RValue attributes) throws InternalException, UserException
    {
        return def;
    }

    @Override
    public T visitGenericList(ImmutableList<RValue> values, @Nullable RValue attributes, boolean isObject) throws InternalException, UserException
    {
        return def;
    }

    @Override
    public T visitStringList(ImmutableList<Optional<@Value String>> values, @Nullable RValue attributes) throws InternalException, UserException
    {
        return def;
    }

    @Override
    public T visitTemporalList(DateTimeType dateTimeType, ImmutableList<Optional<@Value TemporalAccessor>> values, @Nullable RValue attributes) throws InternalException, UserException
    {
        return def;
    }

    @Override
    public T visitPairList(ImmutableList<PairListEntry> items) throws InternalException, UserException
    {
        return def;
    }

    @Override
    public T visitFactorList(int[] values, ImmutableList<String> levelNames) throws InternalException, UserException
    {
        return def;
    }

    @Override
    public T visitNil() throws InternalException, UserException
    {
        return def;
    }
}
