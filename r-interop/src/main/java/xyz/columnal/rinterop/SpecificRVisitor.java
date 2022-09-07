package xyz.columnal.rinterop;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.datatype.DataType.DateTimeInfo.DateTimeType;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;

import java.time.temporal.TemporalAccessor;
import java.util.Optional;

/**
 * Implements RVisitor to throw an error from each method.  You can then override individual methods.
 */
public abstract class SpecificRVisitor<T> implements RVisitor<T>
{
    @Override
    public T visitString(@Nullable @Value String s, boolean isSymbol) throws InternalException, UserException
    {
        throw new UserException("Unexpected type: string");
    }

    @Override
    public T visitIntList(int[] values, @Nullable RValue attributes) throws InternalException, UserException
    {
        throw new UserException("Unexpected type: list of integer");
    }

    @Override
    public T visitDoubleList(double[] values, @Nullable RValue attributes) throws InternalException, UserException
    {
        throw new UserException("Unexpected type: list of floating point");
    }

    @Override
    public T visitLogicalList(boolean[] values, boolean @Nullable [] isNA, @Nullable RValue attributes) throws InternalException, UserException
    {
        throw new UserException("Unexpected type: list of booleans");
    }

    @Override
    public T visitTemporalList(DateTimeType dateTimeType, ImmutableList<Optional<@Value TemporalAccessor>> values, @Nullable RValue attributes) throws InternalException, UserException
    {
        throw new UserException("Unexpected type: list of date/time type");
    }

    @Override
    public T visitStringList(ImmutableList<Optional<@Value String>> values, @Nullable RValue attributes) throws InternalException, UserException
    {
        throw new UserException("Unexpected type: list of strings");
    }

    @Override
    public T visitGenericList(ImmutableList<RValue> values, @Nullable RValue attributes, boolean isObject) throws InternalException, UserException
    {
        throw new UserException("Unexpected type: generic list");
    }

    @Override
    public T visitPairList(ImmutableList<PairListEntry> items) throws InternalException, UserException
    {
        throw new UserException("Unexpected type: pair list");
    }

    @Override
    public T visitFactorList(int[] values, ImmutableList<String> levelNames) throws InternalException, UserException
    {
        throw new UserException("Unexpected factor list");
    }

    @Override
    public T visitNil() throws InternalException, UserException
    {
        throw new UserException("Unexpected nil");
    }
}
