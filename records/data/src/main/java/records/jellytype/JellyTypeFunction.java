package records.jellytype;

import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;
import records.data.datatype.TaggedTypeDefinition.TaggedInstantiationException;
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
import utility.Utility;

import java.util.Objects;
import java.util.function.Consumer;

class JellyTypeFunction extends JellyType
{
    private final ImmutableList<@Recorded JellyType> params;
    private final @Recorded JellyType result;

    JellyTypeFunction(ImmutableList<@Recorded JellyType> params, @Recorded JellyType result)
    {
        this.params = params;
        this.result = result;
    }

    @Override
    public TypeExp makeTypeExp(ImmutableMap<String, Either<MutUnitVar, MutVar>> typeVariables) throws InternalException
    {
        return TypeExp.function(null, Utility.mapListInt(params, p -> p.makeTypeExp(typeVariables)), result.makeTypeExp(typeVariables));
    }

    @Override
    public DataType makeDataType(ImmutableMap<String, Either<Unit, DataType>> typeVariables, TypeManager mgr) throws InternalException, UnknownTypeException, TaggedInstantiationException
    {
        ImmutableList.Builder<DataType> paramTypes = ImmutableList.builderWithExpectedSize(params.size());

        for (@Recorded JellyType param : params)
        {
            paramTypes.add(param.makeDataType(typeVariables, mgr));
        }
        
        return DataType.function(paramTypes.build(), result.makeDataType(typeVariables, mgr));
    }

    @Override
    public void forNestedTagged(Consumer<TypeId> nestedTagged)
    {
        params.forEach(p -> p.forNestedTagged(nestedTagged));
        result.forNestedTagged(nestedTagged);
    }

    @Override
    public <R, E extends Throwable> R apply(JellyTypeVisitorEx<R, E> visitor) throws InternalException, E
    {
        return visitor.function(params, result);
    }

    @Override
    public void save(OutputBuilder output)
    {
        output.raw("((");
        for (int i = 0; i < params.size(); i++)
        {
            params.get(i).save(output);
            if (i > 0)
                output.raw(", ");
        }
        output.raw(") -> ");
        result.save(output);
        output.raw(")");
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JellyTypeFunction that = (JellyTypeFunction) o;
        return Objects.equals(params, that.params) &&
            Objects.equals(result, that.result);
    }

    @Override
    public int hashCode()
    {

        return Objects.hash(params, result);
    }
}
