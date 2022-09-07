package test.gen.backwards;

import annotation.identifier.qual.ExpressionIdentifier;
import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import xyz.columnal.data.Column;
import xyz.columnal.data.ColumnId;
import xyz.columnal.data.MemoryArrayColumn;
import xyz.columnal.data.MemoryBooleanColumn;
import xyz.columnal.data.MemoryNumericColumn;
import xyz.columnal.data.MemoryStringColumn;
import xyz.columnal.data.MemoryTaggedColumn;
import xyz.columnal.data.MemoryTemporalColumn;
import xyz.columnal.data.MemoryRecordColumn;
import xyz.columnal.data.RecordSet;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.DataType.DataTypeVisitor;
import xyz.columnal.data.datatype.DataType.DateTimeInfo;
import xyz.columnal.data.datatype.DataType.TagType;
import xyz.columnal.data.datatype.NumberInfo;
import xyz.columnal.data.datatype.TypeId;
import xyz.columnal.data.unit.Unit;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import records.transformations.expression.IdentExpression;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.Either;
import xyz.columnal.utility.IdentifierUtility;
import xyz.columnal.utility.SimulationFunction;
import xyz.columnal.utility.TaggedValue;
import xyz.columnal.utility.Utility;
import xyz.columnal.utility.Utility.ListEx;
import xyz.columnal.utility.Utility.ListExList;
import xyz.columnal.utility.Utility.Record;

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