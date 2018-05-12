package records.jellytype;

import com.google.common.collect.ImmutableMap;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;
import records.data.datatype.TypeId;
import records.data.unit.Unit;
import records.error.InternalException;
import records.loadsave.OutputBuilder;
import records.typeExp.MutVar;
import records.typeExp.TypeExp;
import records.typeExp.units.MutUnitVar;
import utility.Either;

import java.util.Objects;
import java.util.function.Consumer;

class JellyTypePrimitive extends JellyType
{
    private final DataType dataType;

    JellyTypePrimitive(DataType dataType)
    {
        this.dataType = dataType;
    }

    @Override
    public TypeExp makeTypeExp(ImmutableMap<String, Either<MutUnitVar, MutVar>> typeVariables) throws InternalException
    {
        return TypeExp.fromDataType(null, dataType);
    }

    @Override
    public DataType makeDataType(ImmutableMap<String, Either<Unit, DataType>> typeVariables) throws InternalException
    {
        return dataType;
    }

    @Override
    public void save(OutputBuilder output) throws InternalException
    {
        dataType.save(output);
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JellyTypePrimitive that = (JellyTypePrimitive) o;
        return Objects.equals(dataType, that.dataType);
    }

    @Override
    public int hashCode()
    {

        return Objects.hash(dataType);
    }

    @Override
    public void forNestedTagged(Consumer<TypeId> nestedTagged)
    {
        // No nested tagged types
    }

    @Override
    public <R, E extends Throwable> R apply(JellyTypeVisitorEx<R, E> visitor) throws InternalException, E
    {
        if (dataType.equals(DataType.TEXT))
            return visitor.text();
        else if (dataType.isDateTime())
            return visitor.date(dataType.getDateTimeInfo());
        else if (dataType.equals(DataType.BOOLEAN))
            return visitor.bool();
        throw new InternalException("Unhandled primitive type case: " + dataType);
    }
}
