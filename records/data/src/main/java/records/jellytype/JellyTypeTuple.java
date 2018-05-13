package records.jellytype;

import com.google.common.collect.ImmutableList;
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
import records.typeExp.TupleTypeExp;
import records.typeExp.TypeExp;
import records.typeExp.units.MutUnitVar;
import utility.Either;
import utility.Utility;

import java.util.Objects;
import java.util.function.Consumer;

public class JellyTypeTuple extends JellyType
{
    private final ImmutableList<JellyType> types;
    
    public JellyTypeTuple(ImmutableList<JellyType> types)
    {
        this.types = types;
    }

    @Override
    public TypeExp makeTypeExp(ImmutableMap<String, Either<MutUnitVar, MutVar>> typeVariables) throws InternalException
    {
        return new TupleTypeExp(null, Utility.mapListInt(types, t -> t.makeTypeExp(typeVariables)), true);
    }

    @Override
    public DataType makeDataType(ImmutableMap<String, Either<Unit, DataType>> typeVariables, TypeManager mgr) throws InternalException, UserException
    {
        return DataType.tuple(Utility.mapListExI(types, t -> t.makeDataType(typeVariables, mgr)));
    }

    @Override
    public void save(OutputBuilder output) throws InternalException
    {
        output.raw("(");
        for (int i = 0; i < types.size(); i++)
        {
            if (i > 0)
                output.raw(", ");
            types.get(i).save(output);
        }
        output.raw(")");
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JellyTypeTuple that = (JellyTypeTuple) o;
        return Objects.equals(types, that.types);
    }

    @Override
    public int hashCode()
    {

        return Objects.hash(types);
    }

    @Override
    public void forNestedTagged(Consumer<TypeId> nestedTagged)
    {
        for (JellyType type : types)
        {
            type.forNestedTagged(nestedTagged);
        }
    }

    @Override
    public <R, E extends Throwable> R apply(JellyTypeVisitorEx<R, E> visitor) throws InternalException, E
    {
        return visitor.tuple(types);
    }
}
