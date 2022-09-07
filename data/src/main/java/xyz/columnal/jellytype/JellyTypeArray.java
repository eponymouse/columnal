package xyz.columnal.jellytype;

import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableMap;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.TaggedTypeDefinition.TaggedInstantiationException;
import xyz.columnal.data.datatype.TypeId;
import xyz.columnal.data.datatype.TypeManager;
import xyz.columnal.data.unit.Unit;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.loadsave.OutputBuilder;
import xyz.columnal.typeExp.MutVar;
import xyz.columnal.typeExp.TypeExp;
import xyz.columnal.typeExp.units.MutUnitVar;
import xyz.columnal.utility.Either;

import java.util.Objects;
import java.util.function.Consumer;

class JellyTypeArray extends JellyType
{
    private final @Recorded JellyType inner;

    JellyTypeArray(@Recorded JellyType inner)
    {
        this.inner = inner;
    }

    @Override
    public TypeExp makeTypeExp(ImmutableMap<String, Either<MutUnitVar, MutVar>> typeVariables) throws InternalException
    {
        return TypeExp.list(null, inner.makeTypeExp(typeVariables));
    }

    @Override
    public DataType makeDataType(ImmutableMap<String, Either<Unit, DataType>> typeVariables, TypeManager mgr) throws InternalException, UnknownTypeException, TaggedInstantiationException
    {
        return DataType.array(inner.makeDataType(typeVariables, mgr));
    }

    @Override
    public void forNestedTagged(Consumer<TypeId> nestedTagged)
    {
        inner.forNestedTagged(nestedTagged);
    }

    @Override
    public <R, E extends Throwable> R apply(JellyTypeVisitorEx<R, E> visitor) throws InternalException, E
    {
        return visitor.array(inner);
    }

    @Override
    public void save(OutputBuilder output)
    {
        output.raw("[");
        inner.save(output);
        output.raw("]");
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JellyTypeArray that = (JellyTypeArray) o;
        return Objects.equals(inner, that.inner);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(inner);
    }
}
