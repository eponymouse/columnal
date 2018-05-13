package records.jellytype;

import com.google.common.collect.ImmutableMap;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;
import records.data.datatype.NumberInfo;
import records.data.datatype.TypeId;
import records.data.datatype.TypeManager;
import records.data.unit.Unit;
import records.error.InternalException;
import records.grammar.FormatParser;
import records.loadsave.OutputBuilder;
import records.typeExp.MutVar;
import records.typeExp.NumTypeExp;
import records.typeExp.TypeExp;
import records.typeExp.units.MutUnitVar;
import utility.Either;

import java.util.Objects;
import java.util.function.Consumer;

class JellyTypeNumberWithUnit extends JellyType
{
    private final JellyUnit unit;

    JellyTypeNumberWithUnit(JellyUnit unit)
    {
        this.unit = unit;
    }

    @Override
    public TypeExp makeTypeExp(ImmutableMap<String, Either<MutUnitVar, MutVar>> typeVariables) throws InternalException
    {
        return new NumTypeExp(null, unit.makeUnitExp(typeVariables));
    }

    @Override
    public DataType makeDataType(ImmutableMap<String, Either<Unit, DataType>> typeVariables, TypeManager mgr) throws InternalException
    {
        return DataType.number(new NumberInfo(unit.makeUnit(typeVariables)));
    }

    @Override
    public void forNestedTagged(Consumer<TypeId> nestedTagged)
    {
        // No nested tagged types
    }

    @Override
    public <R, E extends Throwable> R apply(JellyTypeVisitorEx<R, E> visitor) throws InternalException, E
    {
        return visitor.number(unit);
    }

    @Override
    public void save(OutputBuilder output) throws InternalException
    {
        output.t(FormatParser.NUMBER, FormatParser.VOCABULARY);
        if (!unit.isScalar())
        {
            output.raw("{");
            unit.save(output);
            output.raw("}");
        }
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JellyTypeNumberWithUnit that = (JellyTypeNumberWithUnit) o;
        return Objects.equals(unit, that.unit);
    }

    @Override
    public int hashCode()
    {

        return Objects.hash(unit);
    }
}
