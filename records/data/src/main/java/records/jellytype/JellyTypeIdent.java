package records.jellytype;

import annotation.identifier.qual.ExpressionIdentifier;
import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataType.DateTimeInfo.DateTimeType;
import records.data.datatype.TaggedTypeDefinition;
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
import utility.Pair;
import utility.Utility;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Stream;

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
    public DataType makeDataType(@Recorded JellyTypeIdent this, ImmutableMap<String, Either<Unit, DataType>> typeVariables, TypeManager mgr) throws InternalException, UnknownTypeException, TaggedInstantiationException
    {
        Either<Unit, DataType> var = typeVariables.get(name);
        if (var != null)
            return var.getRight("Variable " + name + " should be type variable but was unit variable");
        DataType dataType = mgr.lookupType(new TypeId(name), ImmutableList.of());
        if (dataType != null)
            return dataType;
        @SuppressWarnings("identifier") // Due to DataType.toString
        ImmutableList<JellyType> fixes = Utility.findAlternatives(name, streamKnownTypes(mgr), t -> t.either(ttd -> Stream.of(ttd.getTaggedTypeName().getRaw()), dts -> Stream.<String>concat(Stream.<String>of(dts.getFirst().toString()), dts.getSecond().stream()))).map(t -> new JellyTypeIdent(t.either(ttd -> ttd.getTaggedTypeName().getRaw(), dts -> dts.getFirst().toString()))).collect(ImmutableList.<JellyType>toImmutableList());
        throw new UnknownTypeException("Unknown type or type variable: " + name, this, fixes);
        
    }

    private static Stream<Either<TaggedTypeDefinition, Pair<DataType, ImmutableList<String>>>> streamKnownTypes(TypeManager mgr)
    {
        Stream<Either<TaggedTypeDefinition, Pair<DataType, ImmutableList<String>>>> taggedTypes = mgr.getKnownTaggedTypes().values().stream().<Either<TaggedTypeDefinition, Pair<DataType, ImmutableList<String>>>>map(ttd -> Either.<TaggedTypeDefinition, Pair<DataType, ImmutableList<String>>>left(ttd));
        Stream<Pair<DataType, ImmutableList<String>>> basicTypes = Stream.<Pair<DataType, ImmutableList<String>>>of(
            new Pair<>(DataType.BOOLEAN, ImmutableList.of("bool")),
            new Pair<>(DataType.NUMBER, ImmutableList.of("int", "integer", "float", "double")),
            new Pair<>(DataType.TEXT, ImmutableList.of("string"))
        );
        Stream<Pair<DataType, ImmutableList<String>>> dateTypes = Arrays.stream(DateTimeType.values()).<Pair<DataType, ImmutableList<String>>>map(dtt -> new Pair<>(DataType.date(new DateTimeInfo(dtt)), ImmutableList.of()));
        return Stream.<Either<TaggedTypeDefinition, Pair<DataType, ImmutableList<String>>>>concat(taggedTypes, Stream.<Pair<DataType, ImmutableList<String>>>concat(basicTypes, dateTypes).<Either<TaggedTypeDefinition, Pair<DataType, ImmutableList<String>>>>map(t -> Either.<TaggedTypeDefinition, Pair<DataType, ImmutableList<String>>>right(t)));
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
