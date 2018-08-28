package records.jellytype;

import com.google.common.collect.ImmutableMap;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;
import records.data.datatype.TypeId;
import records.data.datatype.TypeManager;
import records.data.unit.Unit;
import records.error.InternalException;
import records.error.UserException;
import records.loadsave.OutputBuilder;
import records.typeExp.MutVar;
import records.typeExp.TypeExp;
import records.typeExp.units.MutUnitVar;
import utility.Either;

import java.util.Objects;
import java.util.function.Consumer;

class JellyTypeArray extends JellyType
{
    private final JellyType inner;

    JellyTypeArray(JellyType inner)
    {
        this.inner = inner;
    }

    @Override
    public TypeExp makeTypeExp(ImmutableMap<String, Either<MutUnitVar, MutVar>> typeVariables) throws InternalException
    {
        return TypeExp.list(null, inner.makeTypeExp(typeVariables));
    }

    @Override
    public DataType makeDataType(ImmutableMap<String, Either<Unit, DataType>> typeVariables, TypeManager mgr) throws InternalException, UserException
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
