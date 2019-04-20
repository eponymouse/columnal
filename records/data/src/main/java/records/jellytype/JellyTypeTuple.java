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
import records.typeExp.TupleTypeExp;
import records.typeExp.TypeExp;
import records.typeExp.units.MutUnitVar;
import utility.Either;
import utility.Utility;

import java.util.Objects;
import java.util.function.Consumer;

public class JellyTypeTuple extends JellyType
{
    private final ImmutableList<@Recorded JellyType> types;
    private final boolean complete;

    public JellyTypeTuple(ImmutableList<@Recorded JellyType> types, boolean complete)
    {
        this.types = types;
        this.complete = complete;
    }

    @Override
    public TypeExp makeTypeExp(ImmutableMap<String, Either<MutUnitVar, MutVar>> typeVariables) throws InternalException
    {
        return new TupleTypeExp(null, Utility.mapListInt(types, t -> t.makeTypeExp(typeVariables)), complete);
    }

    @Override
    public DataType makeDataType(@Recorded JellyTypeTuple this, ImmutableMap<String, Either<Unit, DataType>> typeVariables, TypeManager mgr) throws InternalException, UnknownTypeException, TaggedInstantiationException
    {
        if (!complete)
            throw new UnknownTypeException("Cannot turn tuple of unknown size into concrete type", this, ImmutableList.of()); // Not quite the right exception, admittedly
        
        ImmutableList.Builder<DataType> members = ImmutableList.builderWithExpectedSize(types.size());

        for (@Recorded JellyType type : types)
        {
            members.add(type.makeDataType(typeVariables, mgr));
        }
        
        return DataType.tuple(members.build());
    }

    @Override
    public void save(OutputBuilder output)
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

    @Override
    public String toString()
    {
        return "JellyTypeTuple{" +
                "types=" + Utility.listToString(types) +
                ", complete=" + complete +
                '}';
    }
}
