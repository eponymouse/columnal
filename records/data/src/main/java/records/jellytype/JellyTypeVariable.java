package records.jellytype;

import com.google.common.collect.ImmutableMap;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;
import records.data.datatype.TypeId;
import records.data.datatype.TypeManager;
import records.data.unit.Unit;
import records.error.InternalException;
import records.loadsave.OutputBuilder;
import records.typeExp.MutVar;
import records.typeExp.TypeExp;
import records.typeExp.units.MutUnitVar;
import utility.Either;

import java.util.Objects;
import java.util.function.Consumer;

// A type variable, like in a function definition or tag definition
class JellyTypeVariable extends JellyType
{
    private final String name;

    JellyTypeVariable(String name)
    {
        this.name = name;
    }

    @Override
    public TypeExp makeTypeExp(ImmutableMap<String, Either<MutUnitVar, MutVar>> typeVariables) throws InternalException
    {
        return typeVariables.get(name).getRight("Variable " + name + " should be type variable but was unit variable");
    }

    @Override
    public DataType makeDataType(ImmutableMap<String, Either<Unit, DataType>> typeVariables, TypeManager mgr) throws InternalException
    {
        Either<Unit, DataType> var = typeVariables.get(name);
        if (var == null)
            throw new InternalException("No such type variable: " + name);
        return var.getRight("Variable " + name + " should be type variable but was unit variable");
    }

    @Override
    public void forNestedTagged(Consumer<TypeId> nestedTagged)
    {
        // No nested tagged types
    }

    @Override
    public <R, E extends Throwable> R apply(JellyTypeVisitorEx<R, E> visitor) throws InternalException, E
    {
        return visitor.typeVariable(name);
    }

    @Override
    public void save(OutputBuilder output)
    {
        output.raw("@typevar " + OutputBuilder.quotedIfNecessary(name));
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JellyTypeVariable that = (JellyTypeVariable) o;
        return Objects.equals(name, that.name);
    }

    @Override
    public int hashCode()
    {

        return Objects.hash(name);
    }
}
