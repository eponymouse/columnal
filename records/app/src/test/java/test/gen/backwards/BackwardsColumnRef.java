package test.gen.backwards;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.*;
import records.data.datatype.DataType;
import records.data.datatype.DataType.ConcreteDataTypeVisitor;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataType.TagType;
import records.data.datatype.NumberInfo;
import records.data.datatype.TypeId;
import records.data.unit.Unit;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.expression.ColumnReference;
import records.transformations.expression.ColumnReference.ColumnReferenceType;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.SimulationFunction;
import utility.TaggedValue;
import utility.Utility;
import utility.Utility.ListEx;
import utility.Utility.ListExList;

import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

public class BackwardsColumnRef extends BackwardsProvider
{
    private final ArrayList<SimulationFunction<RecordSet, Column>> columns = new ArrayList<>();
    
    public BackwardsColumnRef(SourceOfRandomness r, RequestBackwardsExpression parent)
    {
        super(r, parent);
    }

    @Override
    public List<ExpressionMaker> terminals(DataType type, @Value Object value) throws InternalException, UserException
    {
        return ImmutableList.of(() -> {
            ColumnId name = new ColumnId("GEV Col " + columns.size());
            columns.add(rs -> type.apply(new ConcreteDataTypeVisitor<Column>()
            {
                @Override
                @OnThread(Tag.Simulation)
                public Column number(NumberInfo numberInfo) throws InternalException, UserException
                {
                    return new MemoryNumericColumn(rs, name, numberInfo, Stream.of(Utility.toBigDecimal((Number) value).toPlainString()));
                }

                @Override
                @OnThread(Tag.Simulation)
                public Column text() throws InternalException, UserException
                {
                    return new MemoryStringColumn(rs, name, Collections.singletonList((String)value), "");
                }

                @Override
                @OnThread(Tag.Simulation)
                public Column date(DateTimeInfo dateTimeInfo) throws InternalException, UserException
                {
                    return new MemoryTemporalColumn(rs, name, dateTimeInfo, Collections.singletonList((Temporal)value), DateTimeInfo.DEFAULT_VALUE);
                }

                @Override
                @OnThread(Tag.Simulation)
                public Column bool() throws InternalException, UserException
                {
                    return new MemoryBooleanColumn(rs, name, Collections.singletonList((Boolean) value), false);
                }

                @Override
                @OnThread(Tag.Simulation)
                public Column tagged(TypeId typeName, ImmutableList<Either<Unit, DataType>> typeVars, ImmutableList<TagType<DataType>> tags) throws InternalException, UserException
                {
                    return new MemoryTaggedColumn(rs, name, typeName, typeVars, tags, Collections.singletonList((TaggedValue) value), (TaggedValue)parent.makeValue(type));
                }

                @Override
                @SuppressWarnings("value")
                @OnThread(Tag.Simulation)
                public Column tuple(ImmutableList<DataType> inner) throws InternalException, UserException
                {
                    return new MemoryTupleColumn(rs, name, inner, Collections.singletonList((Object[])value), (Object[])parent.makeValue(type));
                }

                @Override
                @OnThread(Tag.Simulation)
                public Column array(@Nullable DataType inner) throws InternalException, UserException
                {
                    return new MemoryArrayColumn(rs, name, inner, Collections.singletonList((ListEx)value), new ListExList(Collections.emptyList()));
                }
            }));
            return new ColumnReference(name, ColumnReferenceType.CORRESPONDING_ROW);

        });
    }

    @Override
    public List<ExpressionMaker> deep(int maxLevels, DataType targetType, @Value Object targetValue) throws InternalException, UserException
    {
        return ImmutableList.of();
    }

    public List<SimulationFunction<RecordSet, Column>> getColumns()
    {
        return columns;
    }
}
