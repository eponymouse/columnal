package records.rinterop;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.datatype.DataType.DateTimeInfo.DateTimeType;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;

import java.time.temporal.TemporalAccessor;
import java.util.Optional;

/**
 * A visitor which effectively reveals the structure of RValue.
 */
public interface RVisitor<T>
{
    public static class PairListEntry
    {
        public final @Nullable RValue attributes;
        public final @Nullable RValue tag;
        public final RValue item;

        public PairListEntry(@Nullable RValue attributes, @Nullable RValue tag, RValue item)
        {
            this.attributes = attributes;
            this.tag = tag;
            this.item = item;
        }
    }
    
    public T visitString(@Nullable @Value String s, boolean isSymbol) throws InternalException, UserException;
    // If attributes reveal this is a factor, it won't be called; visitFactorList will be instead
    public T visitIntList(int[] values, @Nullable RValue attributes) throws InternalException, UserException;
    public T visitDoubleList(double[] values, @Nullable RValue attributes) throws InternalException, UserException;
    public T visitLogicalList(boolean[] values, boolean @Nullable [] isNA, @Nullable RValue attributes) throws InternalException, UserException;
    public T visitStringList(ImmutableList<Optional<@Value String>> values, @Nullable RValue attributes) throws InternalException, UserException;
    public T visitTemporalList(DateTimeType dateTimeType, ImmutableList<Optional<@Value TemporalAccessor>> values, @Nullable RValue attributes) throws InternalException, UserException;
    public T visitGenericList(ImmutableList<RValue> values, @Nullable RValue attributes, boolean isObject) throws InternalException, UserException;
    public T visitPairList(ImmutableList<PairListEntry> items) throws InternalException, UserException;
    public T visitFactorList(int[] values, ImmutableList<String> levelNames) throws InternalException, UserException;
    public T visitNil() throws  InternalException, UserException;
}
