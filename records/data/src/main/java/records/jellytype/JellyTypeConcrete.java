package records.jellytype;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DataTypeVisitorEx;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataType.TagType;
import records.data.datatype.NumberInfo;
import records.data.datatype.TypeId;
import records.data.datatype.TypeManager;
import records.data.unit.Unit;
import records.error.InternalException;
import records.loadsave.OutputBuilder;
import records.typeExp.MutVar;
import records.typeExp.TypeExp;
import records.typeExp.units.MutUnitVar;
import utility.Either;
import utility.Utility;

import java.util.Objects;
import java.util.function.Consumer;

class JellyTypeConcrete extends JellyType
{
    private final DataType dataType;

    JellyTypeConcrete(DataType dataType)
    {
        this.dataType = dataType;
    }

    @Override
    public TypeExp makeTypeExp(ImmutableMap<String, Either<MutUnitVar, MutVar>> typeVariables) throws InternalException
    {
        return TypeExp.fromDataType(null, dataType);
    }

    @Override
    public DataType makeDataType(ImmutableMap<String, Either<Unit, DataType>> typeVariables, TypeManager mgr) throws InternalException
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
        JellyTypeConcrete that = (JellyTypeConcrete) o;
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
        return dataType.apply(new DataTypeVisitorEx<R, E>()
        {
            @Override
            public R number(NumberInfo numberInfo) throws InternalException, E
            {
                return visitor.number(JellyUnit.fromConcrete(numberInfo.getUnit()));
            }

            @Override
            public R text() throws InternalException, E
            {
                return visitor.text();
            }

            @Override
            public R date(DateTimeInfo dateTimeInfo) throws InternalException, E
            {
                return visitor.date(dateTimeInfo);
            }

            @Override
            public R bool() throws InternalException, E
            {
                return visitor.bool();
            }

            @Override
            public R tagged(TypeId typeName, ImmutableList<Either<Unit, DataType>> typeVars, ImmutableList<TagType<DataType>> tags) throws InternalException, E
            {
                return visitor.tagged(typeName, Utility.mapListInt(typeVars, e -> e.mapBothInt(JellyUnit::fromConcrete, JellyTypeConcrete::new)));
            }

            @Override
            public R tuple(ImmutableList<DataType> inner) throws InternalException, E
            {
                return visitor.tuple(Utility.mapListInt(inner, JellyTypeConcrete::new));
            }

            @Override
            public R array(DataType inner) throws InternalException, E
            {
                return visitor.array(new JellyTypeConcrete(inner));
            }

            @Override
            public R function(DataType argType, DataType resultType) throws InternalException, E
            {
                return visitor.function(new JellyTypeConcrete(argType), new JellyTypeConcrete(resultType));
            }
        });
    }
}
