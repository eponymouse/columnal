package records.rinterop;

import annotation.identifier.qual.ExpressionIdentifier;
import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.primitives.Doubles;
import com.google.common.primitives.Ints;
import records.data.ColumnId;
import records.data.EditableColumn;
import records.data.KnownLengthRecordSet;
import records.data.MemoryNumericColumn;
import records.data.MemoryStringColumn;
import records.data.MemoryTaggedColumn;
import records.data.RecordSet;
import records.data.datatype.DataType;
import records.data.datatype.DataType.TagType;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.NumberInfo;
import records.data.datatype.TaggedTypeDefinition;
import records.data.datatype.TaggedTypeDefinition.TypeVariableKind;
import records.data.datatype.TypeManager;
import records.error.InternalException;
import records.error.UserException;
import records.jellytype.JellyType;
import utility.Either;
import utility.IdentifierUtility;
import utility.Pair;
import utility.SimulationFunction;
import utility.TaggedValue;
import utility.Utility;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

public class RData
{
    private static class V2Header
    {
        private final byte header[];
        private final int formatVersion;
        private final int writerVersion;
        private final int readerVersion;
        
        public V2Header(DataInputStream d) throws IOException
        {
            header = new byte[2];
            d.readFully(header);
            formatVersion = d.readInt();
            writerVersion = d.readInt();
            readerVersion = d.readInt();
        }
    }
    
    private static final class TypeHeader
    {
        private final int headerBits;
        private final int attributes;
        private final int tag;
        private final int ref;
        private final boolean factor;
        
        
        public TypeHeader(DataInputStream d) throws IOException, InternalException, UserException
        {
            headerBits = d.readInt();
            if ((headerBits & 0xFF) == 2)
            {
                if ((headerBits & 0x200) != 0)
                {
                    // Attributes
                    attributes = d.readInt();
                }
                else
                    attributes = 0;

                if ((headerBits & 0x400) != 0)
                {
                    // Attributes
                    tag = d.readInt();
                }
                else
                    tag = 0;
                
                boolean factor[] = new boolean[1];
                
                if (tag == 1)
                {
                    ref = readItem(d).visit(new SpecificRVisitor<Integer>()
                    {
                        @Override
                        public Integer visitString(String s) throws InternalException, UserException
                        {
                            if (s.equals("levels"))
                                factor[0] = true;
                            // TODO add to atom table
                            return 0;
                        }
                    });
                }
                else if ((tag & 0xFF) == 0xFF)
                {
                    ref = tag >>> 8;
                }
                else
                {
                    ref = 0;
                }
                this.factor = factor[0];
            }
            else
            {
                attributes = 0;
                tag = 0;
                ref = 0;
                factor = false;
            }
        }
        
        public int getType()
        {
            return headerBits & 0xFF;
        }
    }
    
    public static RValue readRData(File rFilePath) throws IOException, InternalException, UserException
    {
        InputStream rds = openInputStream(rFilePath);
        DataInputStream d = new DataInputStream(rds);
        byte[] header = new byte[5];
        d.mark(10);
        d.readFully(header);
        boolean rData = false;
        if (header[0] == 'R' && header[1] == 'D' && header[2] == 'X' && header[4] == '\n')
        {
            rData = true;
            throw new InternalException("RData not yet supported");
        }
        else
        {
            d.reset();
        }
        
        V2Header v2Header = new V2Header(d);
        
        if (v2Header.formatVersion == 3)
        {
            String encoding = readLenString(d);
        }

        return readItem(d);
    }

    private static RValue readItem(DataInputStream d) throws IOException, UserException, InternalException
    {
        TypeHeader objHeader = new TypeHeader(d);

        switch (objHeader.getType())
        {
            case 1: // Symbol
                return readItem(d);
            case 2: // Pair list
            {
                ArrayList<Pair<TypeHeader, RValue>> itemsBuilder = new ArrayList<>();
                while (objHeader.getType() == 2)
                {
                    itemsBuilder.add(new Pair<>(objHeader, readItem(d)));
                    objHeader = new TypeHeader(d);
                }
                if (objHeader.getType() != 254)
                    throw new UserException("Unexpected type in pair list (identifier: " + objHeader.getType() + ")");
                if (itemsBuilder.size() == 2 && itemsBuilder.get(1).getFirst().factor)
                {
                    return new RValue()
                    {
                        @Override
                        public <T> T visit(RVisitor<T> visitor) throws InternalException, UserException
                        {
                            return visitor.visitFactorList(itemsBuilder.get(0).getSecond().visit(new SpecificRVisitor<int[]>()
                            {
                                @Override
                                public int[] visitIntList(int[] values) throws InternalException, UserException
                                {
                                    return values;
                                }
                            }), itemsBuilder.get(1).getSecond().visit(new SpecificRVisitor<String[]>()
                            {
                                @Override
                                public String[] visitGenericList(ImmutableList<RValue> values) throws InternalException, UserException
                                {
                                    return Utility.mapListExI(values, v -> v.visit(new SpecificRVisitor<String>()
                                    {
                                        @Override
                                        public String visitString(String s) throws InternalException, UserException
                                        {
                                            return s;
                                        }
                                    })).toArray(new String[0]);
                                }
                            }));
                        }
                    };
                }
                else
                {
                    ImmutableList<RValue> items = Utility.<Pair<TypeHeader, RValue>, RValue>mapListI(itemsBuilder, p -> p.getSecond());
                    return new RValue()
                    {
                        @Override
                        public <T> T visit(RVisitor<T> visitor) throws InternalException, UserException
                        {
                            // Should this indicate it is a pair-list?
                            return visitor.visitGenericList(items);
                        }
                    };
                }
            }
            case 9: // String
            {
                String s = readLenString(d);
                return new RValue()
                {
                    @Override
                    public <T> T visit(RVisitor<T> visitor) throws InternalException, UserException
                    {
                        return visitor.visitString(s);
                    }
                };
            }
            case 13: // Integer vector
            {
                int vecLen = d.readInt();
                int[] values = new int[vecLen];
                for (int i = 0; i < vecLen; i++)
                {
                    values[i] = d.readInt();
                }
                return new RValue()
                {
                    @Override
                    public <T> T visit(RVisitor<T> visitor) throws InternalException, UserException
                    {
                        return visitor.visitIntList(values);
                    }
                };
            }
            case 14: // Floating point vector
            {
                int vecLen = d.readInt();
                double[] values = new double[vecLen];
                for (int i = 0; i < vecLen; i++)
                {
                    values[i] = d.readDouble();
                }
                return new RValue()
                {
                    @Override
                    public <T> T visit(RVisitor<T> visitor) throws InternalException, UserException
                    {
                        return visitor.visitDoubleList(values);
                    }
                };
            }
            case 16: // Character vector
            case 19: // Generic vector
            {
                int vecLen = d.readInt();
                ImmutableList.Builder<RValue> valueBuilder = ImmutableList.builderWithExpectedSize(vecLen);
                for (int i = 0; i < vecLen; i++)
                {
                    valueBuilder.add(readItem(d));
                }
                ImmutableList<RValue> values = valueBuilder.build();
                return new RValue()
                {
                    @Override
                    public <T> T visit(RVisitor<T> visitor) throws InternalException, UserException
                    {
                        return visitor.visitGenericList(values);
                    }
                };
            }
            case 238: // ALTREP_SXP
            {
                RValue info = readItem(d);
                RValue state = readItem(d);
                RValue attr = readItem(d);
                return lookupClass(getString(getListItem(info, 0)), getString(getListItem(info, 1))).load(state, attr);
            }
            default:
                throw new UserException("Unsupported R object type (identifier: " + objHeader.getType() + ")");
        }
    }
    
    private static String getString(RValue rValue) throws UserException, InternalException
    {
        return rValue.visit(new SpecificRVisitor<String>()
        {
            @Override
            public String visitString(String s) throws InternalException, UserException
            {
                return s;
            }
        });
    }

    private static RValue getListItem(RValue info, int index) throws UserException, InternalException
    {
        return info.visit(new SpecificRVisitor<RValue>() {
            @Override
            public RValue visitGenericList(ImmutableList<RValue> values) throws InternalException, UserException
            {
                return values.get(index);
            }
        });
    }
    
    private static interface RClassLoad
    {
        public RValue load(RValue state, RValue attr) throws IOException, InternalException, UserException;
    }

    private static RClassLoad lookupClass(String c, String p) throws InternalException, UserException
    {
        switch (p + "#" + c)
        {
            case "base#wrap_integer":
                return (state, attr) -> {
                    return state;
                };
        }
        throw new UserException("Unsupported R class: " + p + " " + c);
    }

    private static String readLenString(DataInputStream d) throws IOException
    {
        int len = d.readInt();
        byte chars[] = new byte[len];
        d.readFully(chars);
        return new String(chars, StandardCharsets.US_ASCII);
    }

    private static BufferedInputStream openInputStream(File rFilePath) throws IOException, UserException
    {
        byte[] firstBytes = new byte[10];
        new DataInputStream(new FileInputStream(rFilePath)).readFully(firstBytes);
        if (firstBytes[0] == 0x1F && Byte.toUnsignedInt(firstBytes[1]) == 0x8B)
        {
            return new BufferedInputStream(new GZIPInputStream(new FileInputStream(rFilePath))); 
        }
        throw new UserException("Unrecognised file format");
    }
    
    public static interface RVisitor<T>
    {
        public T visitString(String s) throws InternalException, UserException;
        public T visitIntList(int[] values) throws InternalException, UserException;
        public T visitDoubleList(double[] values) throws InternalException, UserException;
        public T visitGenericList(ImmutableList<RValue> values) throws InternalException, UserException;
        public T visitFactorList(int[] values, String[] levelNames) throws InternalException, UserException;
    }
    
    public static abstract class SpecificRVisitor<T> implements RVisitor<T>
    {
        @Override
        public T visitString(String s) throws InternalException, UserException
        {
            throw new UserException("Unexpected type: string");
        }

        @Override
        public T visitIntList(int[] values) throws InternalException, UserException
        {
            throw new UserException("Unexpected type: list of integer");
        }

        @Override
        public T visitDoubleList(double[] values) throws InternalException, UserException
        {
            throw new UserException("Unexpected type: list of floating point");
        }

        @Override
        public T visitGenericList(ImmutableList<RValue> values) throws InternalException, UserException
        {
            throw new UserException("Unexpected type: generic list");
        }

        @Override
        public T visitFactorList(int[] values, String[] levelNames) throws InternalException, UserException
        {
            throw new UserException("Unexpected factor list");
        }
    }
    
    public static interface RValue
    {
        public <T> T visit(RVisitor<T> visitor) throws InternalException, UserException;
    }

    private static final ColumnId RESULT = new ColumnId("Result");
    
    public static Pair<DataType, @Value Object> convertRToTypedValue(TypeManager typeManager, RValue rValue) throws InternalException, UserException
    {
        return rValue.visit(new RVisitor<Pair<DataType, @Value Object>>()
        {
            @Override
            public Pair<DataType, @Value Object> visitString(String s) throws InternalException, UserException
            {
                return new Pair<>(DataType.TEXT, DataTypeUtility.value(s));
            }

            @Override
            public Pair<DataType, @Value Object> visitIntList(int[] values) throws InternalException, UserException
            {
                if (values.length == 1)
                    return new Pair<>(DataType.NUMBER, DataTypeUtility.value(values[0]));
                else
                    return new Pair<>(DataType.array(DataType.NUMBER), DataTypeUtility.value(Ints.asList(values)));
            }

            @Override
            public Pair<DataType, @Value Object> visitDoubleList(double[] values) throws InternalException, UserException
            {
                if (values.length == 1)
                    return new Pair<>(DataType.NUMBER, DataTypeUtility.value(values[0]));
                else
                    return new Pair<>(DataType.array(DataType.NUMBER), DataTypeUtility.value(Doubles.asList(values)));
            }

            @Override
            public Pair<DataType, @Value Object> visitGenericList(ImmutableList<RValue> values) throws InternalException, UserException
            {
                throw new InternalException("TODO generic list to single value");
            }

            @Override
            public Pair<DataType, @Value Object> visitFactorList(int[] values, String[] levelNames) throws InternalException, UserException
            {
                TaggedTypeDefinition taggedTypeDefinition = getTaggedTypeForFactors(levelNames, typeManager);
                return new Pair<>(taggedTypeDefinition.instantiate(ImmutableList.of(), typeManager), DataTypeUtility.value(IntStream.of(values).mapToObj(n -> new TaggedValue(n, null, taggedTypeDefinition)).collect(ImmutableList.<TaggedValue>toImmutableList())));
            }
        });
    }

    private static TaggedTypeDefinition getTaggedTypeForFactors(String[] levelNames, TypeManager typeManager) throws InternalException, UserException
    {
        // TODO Search for existing type
        @ExpressionIdentifier String typeName = "F";
        TaggedTypeDefinition taggedTypeDefinition =  typeManager.registerTaggedType(typeName, ImmutableList.<Pair<TypeVariableKind, @ExpressionIdentifier String>>of(), Stream.of(levelNames).map(s -> new TagType<JellyType>(IdentifierUtility.fixExpressionIdentifier(s, "Factor"), null)).collect(ImmutableList.<TagType<JellyType>>toImmutableList()));
        if (taggedTypeDefinition == null)
            throw new UserException("Type named " + typeName + " already exists but with different tags");
        return taggedTypeDefinition;
    }

    public static Pair<SimulationFunction<RecordSet, EditableColumn>, Integer> convertRToColumn(TypeManager typeManager, RValue rValue) throws UserException, InternalException
    {
        return rValue.visit(new RVisitor<Pair<SimulationFunction<RecordSet, EditableColumn>, Integer>>()
        {
            @Override
            public Pair<SimulationFunction<RecordSet, EditableColumn>, Integer> visitString(String s) throws InternalException, UserException
            {
                return new Pair<>(rs -> new MemoryStringColumn(rs, RESULT, ImmutableList.<Either<String, String>>of(Either.<String, String>right(s)), ""), 1);
            }

            @Override
            public Pair<SimulationFunction<RecordSet, EditableColumn>, Integer> visitIntList(int[] values) throws InternalException, UserException
            {
                return new Pair<>(rs -> new MemoryNumericColumn(rs, RESULT, NumberInfo.DEFAULT, IntStream.of(values).mapToObj(n -> Either.<String, Number>right(n)).collect(ImmutableList.<Either<String, Number>>toImmutableList()), 0L), values.length);
            }

            @Override
            public Pair<SimulationFunction<RecordSet, EditableColumn>, Integer> visitDoubleList(double[] values) throws InternalException, UserException
            {
                return new Pair<>(rs -> new MemoryNumericColumn(rs, RESULT, NumberInfo.DEFAULT, DoubleStream.of(values).mapToObj(n -> Either.<String, Number>right(DataTypeUtility.<Number>value(new BigDecimal(n)))).collect(ImmutableList.<Either<String, Number>>toImmutableList()), 0L), values.length);
            }

            @Override
            public Pair<SimulationFunction<RecordSet, EditableColumn>, Integer> visitFactorList(int[] values, String[] levelNames) throws InternalException, UserException
            {
                TaggedTypeDefinition taggedTypeDefinition = getTaggedTypeForFactors(levelNames, typeManager);
                return new Pair<>(rs -> new MemoryTaggedColumn(rs, RESULT, taggedTypeDefinition.getTaggedTypeName(), ImmutableList.of(), Utility.mapList(taggedTypeDefinition.getTags(), t -> new TagType<>(t.getName(), null)), IntStream.of(values).mapToObj(n -> Either.<String, TaggedValue>right(new TaggedValue(n, null, taggedTypeDefinition))).collect(ImmutableList.<Either<String, TaggedValue>>toImmutableList()), new TaggedValue(0, null, taggedTypeDefinition)), values.length);
            }

            @Override
            public Pair<SimulationFunction<RecordSet, EditableColumn>, Integer> visitGenericList(ImmutableList<RValue> values) throws InternalException, UserException
            {
                ImmutableList<Pair<DataType, @Value Object>> typedPairs = Utility.<RValue, Pair<DataType, @Value Object>>mapListExI(values, v -> convertRToTypedValue(typeManager, v));
                Pair<DataType, ImmutableMap<DataType, SimulationFunction<@Value Object, @Value Object>>> m = generaliseType(Utility.mapListExI(typedPairs, p -> p.getFirst()));
                @ExpressionIdentifier String columnId = IdentifierUtility.asExpressionIdentifier("C" + Math.abs(values.hashCode()));
                if (columnId == null)
                    throw new UserException("Invalid column name");
                return new Pair<>(m.getFirst().makeImmediateColumn(new ColumnId(columnId), Utility.<Pair<DataType, @Value Object>, Either<String, @Value Object>>mapListExI(typedPairs, p -> Either.<String, @Value Object>right(getOrInternal(m.getSecond(), p.getFirst()).apply(p.getSecond()))), DataTypeUtility.makeDefaultValue(m.getFirst())), typedPairs.size());
            }

            private SimulationFunction<@Value Object, @Value Object> getOrInternal(ImmutableMap<DataType, SimulationFunction<@Value Object, @Value Object>> map, DataType key) throws InternalException
            {
                SimulationFunction<@Value Object, @Value Object> f = map.get(key);
                if (f == null)
                    throw new InternalException("No conversion found for type " + key);
                return f;
            }
        });
    }
    
    private static Pair<DataType, ImmutableMap<DataType, SimulationFunction<@Value Object, @Value Object>>> generaliseType(ImmutableList<DataType> types) throws UserException
    {
        if (types.isEmpty())
            return new Pair<>(DataType.TEXT, ImmutableMap.<DataType, SimulationFunction<@Value Object, @Value Object>>of());
        DataType curType = types.get(0);
        HashMap<DataType, SimulationFunction<@Value Object, @Value Object>> conversions = new HashMap<>();
        conversions.put(curType, x -> x);
        for (DataType nextType : types.subList(1, types.size()))
        {
            if (nextType.equals(curType))
                continue;
            if (DataTypeUtility.isNumber(nextType) && DataTypeUtility.isNumber(curType))
            {
                conversions.put(nextType, x -> x);
                continue;
            }
            throw new UserException("Cannot generalise " + curType + " and " + nextType + " into a single type");
        }
        return new Pair<>(curType, ImmutableMap.copyOf(conversions));
    }
    
    public static RecordSet convertRToTable(TypeManager typeManager, RValue rValue) throws UserException, InternalException
    {
        // R tables are usually a list of columns, which suits us:
        return rValue.visit(new RVisitor<RecordSet>()
        {
            private RecordSet singleColumn() throws UserException, InternalException
            {
                Pair<SimulationFunction<RecordSet, EditableColumn>, Integer> p = convertRToColumn(typeManager, rValue);
                return new KnownLengthRecordSet(ImmutableList.<SimulationFunction<RecordSet, EditableColumn>>of(p.getFirst()), p.getSecond());
            }
            
            @Override
            public RecordSet visitString(String s) throws InternalException, UserException
            {
                return singleColumn();
            }

            @Override
            public RecordSet visitIntList(int[] values) throws InternalException, UserException
            {
                return singleColumn();
            }

            @Override
            public RecordSet visitDoubleList(double[] values) throws InternalException, UserException
            {
                return singleColumn();
            }

            @Override
            public RecordSet visitFactorList(int[] values, String[] levelNames) throws InternalException, UserException
            {
                return singleColumn();
            }

            @Override
            public RecordSet visitGenericList(ImmutableList<RValue> values) throws InternalException, UserException
            {
                // Tricky; could be a list of columns or a list of values!
                // First try as table (list of columns:
                ImmutableList<Pair<SimulationFunction<RecordSet, EditableColumn>, Integer>> columns = Utility.mapListExI(values, v -> convertRToColumn(typeManager, v));
                if (!columns.isEmpty() && columns.stream().mapToInt(p -> p.getSecond()).distinct().count() == 1)
                {
                    return new <EditableColumn>KnownLengthRecordSet(Utility.<Pair<SimulationFunction<RecordSet, EditableColumn>, Integer>, SimulationFunction<RecordSet, EditableColumn>>mapList(columns, p -> p.getFirst()), columns.get(0).getSecond());
                }
                return singleColumn();
            }
        });
    }
}
