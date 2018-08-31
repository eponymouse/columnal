package records.jellytype;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;
import records.data.datatype.TypeId;
import records.data.datatype.TypeManager;
import records.data.unit.Unit;
import records.error.InternalException;
import records.error.UserException;
import records.grammar.FormatLexer;
import records.grammar.FormatParser;
import records.loadsave.OutputBuilder;
import records.typeExp.MutVar;
import records.typeExp.TypeCons;
import records.typeExp.TypeExp;
import records.typeExp.units.MutUnitVar;
import utility.Either;
import utility.UnitType;
import utility.Utility;

import java.util.Objects;
import java.util.function.Consumer;

import static records.typeExp.TypeExp.ALL_TYPE_CLASSES;

public class JellyTypeTagged extends JellyType
{
    private final TypeId typeName;
    private final ImmutableList<Either<JellyUnit, JellyType>> typeParams;

    public JellyTypeTagged(TypeId typeName, ImmutableList<Either<JellyUnit, JellyType>> typeParams)
    {
        this.typeName = typeName;
        this.typeParams = typeParams;
    }

    @Override
    public TypeExp makeTypeExp(ImmutableMap<String, Either<MutUnitVar, MutVar>> typeVariables) throws InternalException
    {
        return new TypeCons(null, typeName.getRaw(), Utility.mapListInt(typeParams, p ->
            p.mapBothInt(u -> u.makeUnitExp(typeVariables), t -> t.makeTypeExp(typeVariables))
        ), ALL_TYPE_CLASSES);
    }

    @Override
    public DataType makeDataType(ImmutableMap<String, Either<Unit, DataType>> typeVariables, TypeManager mgr) throws InternalException, UserException
    {
        ImmutableList<Either<Unit, DataType>> typeParamConcrete = Utility.mapListExI(typeParams, p -> p.mapBothEx(u -> u.makeUnit(typeVariables), t -> t.makeDataType(typeVariables, mgr)));
        
        DataType dataType = mgr.lookupType(typeName, typeParamConcrete);
        if (dataType != null)
            return dataType;
        throw new UserException("Could not find data type: " + typeName);
    }

    @Override
    public void save(OutputBuilder output)
    {
        output.t(FormatLexer.TAGGED, FormatLexer.VOCABULARY);
        output.quote(typeName);
        for (Either<JellyUnit, JellyType> typeParam : typeParams)
        {
            output.raw("(");
            typeParam.either(u -> {
                output.raw("{");
                u.save(output);
                output.raw("}");
                return UnitType.UNIT;
            },t -> {t.save(output); return UnitType.UNIT;});
            output.raw(")");
        }
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JellyTypeTagged that = (JellyTypeTagged) o;
        return Objects.equals(typeName, that.typeName) &&
            Objects.equals(typeParams, that.typeParams);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(typeName, typeParams);
    }

    @Override
    public void forNestedTagged(Consumer<TypeId> nestedTagged)
    {
        nestedTagged.accept(typeName);
        for (Either<JellyUnit, JellyType> typeParam : typeParams)
        {
            typeParam.ifRight(t -> t.forNestedTagged(nestedTagged));
        }
    }

    @Override
    public <R, E extends Throwable> R apply(JellyTypeVisitorEx<R, E> visitor) throws InternalException, E
    {
        return visitor.tagged(typeName, typeParams);
    }

    public TypeId getName()
    {
        return typeName;
    }
    
    public ImmutableList<Either<JellyUnit, JellyType>> getTypeParams()
    {
        return typeParams;
    }
}
