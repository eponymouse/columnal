package records.jellytype;

import annotation.identifier.qual.ExpressionIdentifier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
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
import records.typeExp.TypeCons;
import records.typeExp.TypeExp;
import records.typeExp.units.MutUnitVar;
import utility.Either;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * A plain ident, could be
 * 
 * 
 * - A tagged type without arguments
 * - A type variable, like in a function definition or tag definition
 */
class JellyTypeIdent extends JellyType
{
    private final @ExpressionIdentifier String name;

    JellyTypeIdent(@ExpressionIdentifier String name)
    {
        this.name = name;
    }
    
    @Override
    public TypeExp makeTypeExp(ImmutableMap<String, Either<MutUnitVar, MutVar>> typeVariables) throws InternalException
    {
        Either<MutUnitVar, MutVar> var = typeVariables.get(name);
        if (var != null)
            return var.getRight("Variable " + name + " should be type variable but was unit variable");
        
        return new TypeCons(null, name, ImmutableList.of(), ImmutableSet.of());
        
    }

    @Override
    public DataType makeDataType(ImmutableMap<String, Either<Unit, DataType>> typeVariables, TypeManager mgr) throws InternalException, UnknownTypeException, TaggedInstantiationException
    {
        Either<Unit, DataType> var = typeVariables.get(name);
        if (var != null)
            return var.getRight("Variable " + name + " should be type variable but was unit variable");
        DataType dataType = mgr.lookupType(new TypeId(name), ImmutableList.of());
        if (dataType != null)
            return dataType;
        throw new UnknownTypeException("Unknown type or type variable: " + name);
        
    }

    @Override
    public void forNestedTagged(Consumer<TypeId> nestedTagged)
    {
        // No nested tagged types
    }

    @Override
    public <R, E extends Throwable> R apply(JellyTypeVisitorEx<R, E> visitor) throws InternalException, E
    {
        return visitor.ident(name);
    }

    @Override
    public void save(OutputBuilder output)
    {
        output.raw(name);
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JellyTypeIdent that = (JellyTypeIdent) o;
        return Objects.equals(name, that.name);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(name);
    }

    @Override
    public String toString()
    {
        return "JellyTypeIdent{" +
                "name='" + name + '\'' +
                '}';
    }
}
