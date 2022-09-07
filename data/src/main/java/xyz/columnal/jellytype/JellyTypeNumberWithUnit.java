package xyz.columnal.jellytype;

import com.google.common.collect.ImmutableMap;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.NumberInfo;
import xyz.columnal.data.datatype.TypeId;
import xyz.columnal.data.datatype.TypeManager;
import xyz.columnal.data.unit.Unit;
import xyz.columnal.error.InternalException;
import records.grammar.FormatParser;
import xyz.columnal.loadsave.OutputBuilder;
import xyz.columnal.typeExp.MutVar;
import xyz.columnal.typeExp.NumTypeExp;
import xyz.columnal.typeExp.TypeExp;
import xyz.columnal.typeExp.units.MutUnitVar;
import xyz.columnal.utility.Either;

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
    public void save(OutputBuilder output)
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

    // For debugging
    @Override
    public String toString()
    {
        return "JellyTypeNumberWithUnit{" +
                "unit=" + unit +
                '}';
    }
}
