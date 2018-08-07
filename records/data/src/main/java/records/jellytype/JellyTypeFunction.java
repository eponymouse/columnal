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

class JellyTypeFunction extends JellyType
{
    private final JellyType param;
    private final JellyType result;

    JellyTypeFunction(JellyType param, JellyType result)
    {
        this.param = param;
        this.result = result;
    }

    @Override
    public TypeExp makeTypeExp(ImmutableMap<String, Either<MutUnitVar, MutVar>> typeVariables) throws InternalException
    {
        return TypeExp.function(null, param.makeTypeExp(typeVariables), result.makeTypeExp(typeVariables));
    }

    @Override
    public DataType makeDataType(ImmutableMap<String, Either<Unit, DataType>> typeVariables, TypeManager mgr) throws InternalException, UserException
    {
        return DataType.function(param.makeDataType(typeVariables, mgr), param.makeDataType(typeVariables, mgr));
    }

    @Override
    public void forNestedTagged(Consumer<TypeId> nestedTagged)
    {
        param.forNestedTagged(nestedTagged);
        result.forNestedTagged(nestedTagged);
    }

    @Override
    public <R, E extends Throwable> R apply(JellyTypeVisitorEx<R, E> visitor) throws InternalException, E
    {
        return visitor.function(param, result);
    }

    @Override
    public void save(OutputBuilder output)
    {
        output.raw("(");
        param.save(output);
        output.raw(" -> ");
        result.save(output);
        output.raw(")");
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JellyTypeFunction that = (JellyTypeFunction) o;
        return Objects.equals(param, that.param) &&
            Objects.equals(result, that.result);
    }

    @Override
    public int hashCode()
    {

        return Objects.hash(param, result);
    }
}
