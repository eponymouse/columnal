package test.gen.backwards;

import annotation.identifier.qual.ExpressionIdentifier;
import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import records.data.Column;
import records.data.ColumnId;
import records.data.MemoryArrayColumn;
import records.data.MemoryBooleanColumn;
import records.data.MemoryNumericColumn;
import records.data.MemoryStringColumn;
import records.data.MemoryTaggedColumn;
import records.data.MemoryTemporalColumn;
import records.data.MemoryRecordColumn;
import records.data.RecordSet;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DataTypeVisitor;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataType.TagType;
import records.data.datatype.NumberInfo;
import records.data.datatype.TypeId;
import records.data.unit.Unit;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.expression.IdentExpression;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.IdentifierUtility;
import utility.SimulationFunction;
import utility.TaggedValue;
import utility.Utility;
import utility.Utility.ListEx;
import utility.Utility.ListExList;
import utility.Utility.Record;

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
            ColumnId name = new ColumnId(IdentifierUtility.identNum("GEV Col", columns.size()));
            columns.add(rs -> type.apply(new DataTypeVisitor<Column>()
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
                    return new MemoryStringColumn(rs, name, Collections.singletonList(Either.right((String)value)), "");
                }

                @Override
                @OnThread(Tag.Simulation)
                public Column date(DateTimeInfo dateTimeInfo) throws InternalException, UserException
                {
                    return new MemoryTemporalColumn(rs, name, dateTimeInfo, Collections.singletonList(Either.right((Temporal)value)), dateTimeInfo.getDefaultValue());
                }

                @Override
                @OnThread(Tag.Simulation)
                public Column bool() throws InternalException, UserException
                {
                    return new MemoryBooleanColumn(rs, name, Collections.singletonList(Either.right((Boolean) value)), false);
                }

                @Override
                @OnThread(Tag.Simulation)
                public Column tagged(TypeId typeName, ImmutableList<Either<Unit, DataType>> typeVars, ImmutableList<TagType<DataType>> tags) throws InternalException, UserException
                {
                    return new MemoryTaggedColumn(rs, name, typeName, typeVars, tags, Collections.singletonList(Either.right((TaggedValue) value)), (TaggedValue)parent.makeValue(type));
                }

                @Override
                @SuppressWarnings("valuetype")
                @OnThread(Tag.Simulation)
                public Column record(ImmutableMap<@ExpressionIdentifier String, DataType> fields) throws InternalException, UserException
                {
                    return new MemoryRecordColumn(rs, name, fields, Collections.singletonList(Either.right((Record)value)), (Record) parent.makeValue(type));
                }

                @Override
                @OnThread(Tag.Simulation)
                public Column array(DataType inner) throws InternalException, UserException
                {
                    return new MemoryArrayColumn(rs, name, inner, Collections.singletonList(Either.right((ListEx)value)), new ListExList(Collections.emptyList()));
                }
            }));
            return IdentExpression.column(name);

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
