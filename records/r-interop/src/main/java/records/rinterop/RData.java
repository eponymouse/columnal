package records.rinterop;

import annotation.identifier.qual.ExpressionIdentifier;
import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.primitives.Doubles;
import com.google.common.primitives.Ints;
import org.checkerframework.checker.nullness.qual.Nullable;
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
import java.util.Arrays;
import java.util.HashMap;
import java.util.stream.Collectors;
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
        private final int pairListAttributes;
        private final int pairListTag;
        private final int pairListRef;
        private final boolean factor;
        
        
        public TypeHeader(DataInputStream d, HashMap<Integer, String> atoms) throws IOException, InternalException, UserException
        {
            headerBits = d.readInt();
            /*
            if ((headerBits & 0xFF) == 2)
            {
                if ((headerBits & 0x200) != 0)
                {
                    // Attributes
                    pairListAttributes = d.readInt();
                }
                else
                    pairListAttributes = 0;

                if ((headerBits & 0x400) != 0)
                {
                    // Attributes
                    pairListTag = d.readInt();
                }
                else
                    pairListTag = 0;
                
                boolean factor[] = new boolean[1];
                
                if (pairListTag == 1)
                {
                    pairListRef = readItem(d, atoms).visit(new SpecificRVisitor<Integer>()
                    {
                        @Override
                        public Integer visitString(String s) throws InternalException, UserException
                        {
                            // TODO this isn't the right check, look at class=factor
                            if (s.equals("levels"))
                                factor[0] = true;
                            return addAtom(atoms, s);
                        }
                    });
                }
                else if ((pairListTag & 0xFF) == 0xFF)
                {
                    pairListRef = pairListTag >>> 8;
                }
                else
                {
                    pairListRef = 0;
                }
                this.factor = factor[0];
            }
            else*/
            {
                pairListAttributes = 0;
                pairListTag = 0;
                pairListRef = 0;
                factor = false;
            }
        }

        private @Nullable RValue readAttributes(DataInputStream d, HashMap<Integer, String> atoms) throws IOException, UserException, InternalException
        {
            final @Nullable RValue attr;
            if (hasAttributes())
            {
                // Also read trailing attributes:
                attr = readItem(d, atoms);
            }
            else
            {
                attr = null;
            }
            return attr;
        }

        private @Nullable RValue readTag(DataInputStream d, HashMap<Integer, String> atoms) throws IOException, UserException, InternalException
        {
            final @Nullable RValue tag;
            if (hasTag())
            {
                // Also read trailing attributes:
                tag = readItem(d, atoms);
            }
            else
            {
                tag = null;
            }
            return tag;
        }

        public int getType()
        {
            return headerBits & 0xFF;
        }
        
        public boolean hasAttributes()
        {
            return (headerBits & 0x200) != 0;
        }

        public boolean hasTag()
        {
            return (headerBits & 0x400) != 0;
        }

        public int getReference(DataInputStream d) throws IOException
        {
            int ref = headerBits >>> 8;
            if (ref == 0)
                return d.readInt();
            else
                return ref;
        }
    }

    private static int addAtom(HashMap<Integer, String> atoms, String atom)
    {
        int newKey = atoms.size() + 1;
        atoms.put(newKey, atom);
        return newKey;
    }
    
    private static String getAtom(HashMap<Integer, String> atoms, int key) throws InternalException
    {
        String atom = atoms.get(key);
        if (atom != null)
            return atom;
        throw new InternalException("Could not find atom: " + key + " in atoms sized: " + atoms.size());
    }
    
    private static ColumnId getColumnName(@Nullable RValue listColumnNames, int index) throws UserException, InternalException
    {
        @SuppressWarnings("identifier")
        @ExpressionIdentifier String def = "Column " + index;
        if (listColumnNames != null)
        {
            return listColumnNames.visit(new SpecificRVisitor<ColumnId>()
            {
                @Override
                public ColumnId visitGenericList(ImmutableList<RValue> values, @Nullable RValue attributes) throws InternalException, UserException
                {
                    return new ColumnId(IdentifierUtility.fixExpressionIdentifier(getString(values.get(index)), def));
                }
            });
        }
        return new ColumnId(def);
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

        RValue result = readItem(d, new HashMap<>());
        if (d.read() != -1)
            throw new UserException("Unexpected data at end of file");
        return result;
    }
    
    private static class PairListEntry
    {
        public final @Nullable RValue attributes;
        public final @Nullable RValue tag;
        public final RValue item;

        public PairListEntry(@Nullable RValue attributes, @Nullable RValue tag, RValue item)
        {
            this.attributes = attributes;
            this.tag = tag;
            this.item = item;
        }
    }

    private static RValue readItem(DataInputStream d, HashMap<Integer, String> atoms) throws IOException, UserException, InternalException
    {
        TypeHeader objHeader = new TypeHeader(d, atoms);
        String indent = Arrays.stream(Thread.currentThread().getStackTrace()).map(s -> s.getMethodName().equals("readItem") ? "  " : "").collect(Collectors.joining());
        System.out.println(indent + "Read: " + objHeader.getType() + " " + objHeader.hasAttributes() + "/" + objHeader.hasTag());

        switch (objHeader.getType())
        {
            case 0: // Nil
            case 254: // Pseudo-nil
                return new RValue() {

                    @Override
                    public <T> T visit(RVisitor<T> visitor) throws InternalException, UserException
                    {
                        return visitor.visitNil();
                    }
                };
            case 255: // Ref
            {
                int ref = objHeader.getReference(d);
                String atom = getAtom(atoms, ref);
                return new RValue()
                {
                    @Override
                    public <T> T visit(RVisitor<T> visitor) throws InternalException, UserException
                    {
                        return visitor.visitString(atom);
                    }
                };
            }
            case 1: // Symbol
            {
                RValue symbol = readItem(d, atoms);
                symbol.visit(new SpecificRVisitor<String>()
                {
                    @Override
                    public String visitString(String s) throws InternalException, UserException
                    {
                        addAtom(atoms, s);
                        return s;
                    }
                });
                return symbol;
            }
            case 2: // Pair list
            {
                RValue attr = objHeader.readAttributes(d, atoms);
                RValue tag = objHeader.readTag(d, atoms);
                RValue head = readItem(d, atoms);
                RValue tail = readItem(d, atoms);
                ImmutableList<PairListEntry> items = flattenPairList(head, tail, attr, tag).collect(ImmutableList.<PairListEntry>toImmutableList());
                //System.out.println(indent + "List had " + prettyPrint(itemsBuilder.get(0).getSecond()) + " and " + prettyPrint(itemsBuilder.get(1).getSecond()));
                //if (objHeader.getType() != 254)
                    //throw new UserException("Unexpected type in pair list (identifier: " + objHeader.getType() + ")");
                /*
                if (items.size() == 2 && TODO items.get(1).getFirst().factor)
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
                                public String[] visitGenericList(ImmutableList<RValue> values, @Nullable RValue attributes) throws InternalException, UserException
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
                */
                {
                    /*
                    ImmutableList<Pair<@Nullable String, RValue>> items = Utility.<Pair<TypeHeader, RValue>, Pair<@Nullable String, RValue>>mapListI(itemsBuilder, p -> {
                        String key = null;
                        if (p.getFirst().pairListRef != 0)
                            key = atoms.get(p.getFirst().pairListRef);
                        return new Pair<@Nullable String, RValue>(key, p.getSecond());
                    });
                    */
                    return new RValue()
                    {
                        @Override
                        public <T> T visit(RVisitor<T> visitor) throws InternalException, UserException
                        {
                            return visitor.visitPairList(items);
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
                final @Nullable RValue attr = objHeader.readAttributes(d, atoms);
                ImmutableList<String> factorNames = attr == null ? null : attr.visit(new DefaultRVisitor<@Nullable ImmutableList<String>>(null) {
                    @Override
                    public @Nullable ImmutableList<String> visitPairList(ImmutableList<PairListEntry> items) throws InternalException, UserException
                    {
                        if (items.size() >= 1 && items.get(0).tag != null && items.get(0).tag.visit(new DefaultRVisitor<Boolean>(false)
                        {
                            @Override
                            public Boolean visitString(String s) throws InternalException, UserException
                            {
                                return s.equals("levels");
                            }
                        }))
                            return items.get(0).item.visit(new SpecificRVisitor<ImmutableList<String>>()
                            {
                                @Override
                                public ImmutableList<String> visitGenericList(ImmutableList<RValue> values, @Nullable RValue attributes) throws InternalException, UserException
                                {
                                    return Utility.mapListExI(values, RData::getString);
                                }
                            });
                        return null;
                    }
                });
                
                if (factorNames != null)
                    return new RValue()
                    {
                        @Override
                        public <T> T visit(RVisitor<T> visitor) throws InternalException, UserException
                        {
                            return visitor.visitFactorList(values, factorNames);
                        }
                    };
                else
                    return new RValue()
                    {
                        @Override
                        public <T> T visit(RVisitor<T> visitor) throws InternalException, UserException
                        {
                            return visitor.visitIntList(values, attr);
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
                final @Nullable RValue attr = objHeader.readAttributes(d, atoms);
                return new RValue()
                {
                    @Override
                    public <T> T visit(RVisitor<T> visitor) throws InternalException, UserException
                    {
                        return visitor.visitDoubleList(values, attr);
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
                    valueBuilder.add(readItem(d, atoms));
                }
                ImmutableList<RValue> values = valueBuilder.build();
                final @Nullable RValue attr = objHeader.readAttributes(d, atoms);
                return new RValue()
                {
                    @Override
                    public <T> T visit(RVisitor<T> visitor) throws InternalException, UserException
                    {
                        return visitor.visitGenericList(values, attr);
                    }
                };
            }
            case 238: // ALTREP_SXP
            {
                RValue info = readItem(d, atoms);
                RValue state = readItem(d, atoms);
                RValue attr = readItem(d, atoms);
                return lookupClass(getString(getListItem(info, 0)), getString(getListItem(info, 1))).load(state, attr);
            }
            default:
                throw new UserException("Unsupported R object type (identifier: " + objHeader.getType() + ")");
        }
    }

    private static Stream<PairListEntry> flattenPairList(RValue head, RValue tail, @Nullable RValue attr, @Nullable RValue tag) throws UserException, InternalException
    {
        return Stream.<PairListEntry>concat(Stream.<PairListEntry>of(new PairListEntry(attr, tag, head)), tail.<Stream<PairListEntry>>visit(new DefaultRVisitor<Stream<PairListEntry>>(Stream.of(new PairListEntry(null, null, tail)))
        {
            @Override
            public Stream<PairListEntry> visitNil() throws InternalException, UserException
            {
                // No need to add to list
                return Stream.of();
            }

            @Override
            public Stream<PairListEntry> visitPairList(ImmutableList<PairListEntry> items) throws InternalException, UserException
            {
                return items.stream();
            }
        }));
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
            public RValue visitGenericList(ImmutableList<RValue> values, @Nullable RValue attributes) throws InternalException, UserException
            {
                return values.get(index);
            }

            @Override
            public RValue visitPairList(ImmutableList<PairListEntry> items) throws InternalException, UserException
            {
                return items.get(index).item;
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
        // If attributes reveal this is a factor, it won't be called; visitFactorList will be instead
        public T visitIntList(int[] values, @Nullable RValue attributes) throws InternalException, UserException;
        public T visitDoubleList(double[] values, @Nullable RValue attributes) throws InternalException, UserException;
        public T visitGenericList(ImmutableList<RValue> values, @Nullable RValue attributes) throws InternalException, UserException;
        public T visitPairList(ImmutableList<PairListEntry> items) throws InternalException, UserException;
        public T visitFactorList(int[] values, ImmutableList<String> levelNames) throws InternalException, UserException;
        public T visitNil() throws  InternalException, UserException;
    }
    
    public static abstract class DefaultRVisitor<T> implements RVisitor<T>
    {
        private final T def;

        public DefaultRVisitor(T def)
        {
            this.def = def;
        }

        protected final T makeDefault() throws InternalException, UserException
        {
            return def;
        }

        @Override
        public T visitString(String s) throws InternalException, UserException
        {
            return makeDefault();
        }

        @Override
        public T visitIntList(int[] values, @Nullable RValue attributes) throws InternalException, UserException
        {
            return makeDefault();
        }

        @Override
        public T visitDoubleList(double[] values, @Nullable RValue attributes) throws InternalException, UserException
        {
            return makeDefault();
        }

        @Override
        public T visitGenericList(ImmutableList<RValue> values, @Nullable RValue attributes) throws InternalException, UserException
        {
            return makeDefault();
        }

        @Override
        public T visitPairList(ImmutableList<PairListEntry> items) throws InternalException, UserException
        {
            return makeDefault();
        }

        @Override
        public T visitFactorList(int[] values, ImmutableList<String> levelNames) throws InternalException, UserException
        {
            return makeDefault();
        }

        @Override
        public T visitNil() throws InternalException, UserException
        {
            return makeDefault();
        }
    }
    
    public static abstract class SpecificRVisitor<T> implements RVisitor<T>
    {
        @Override
        public T visitString(String s) throws InternalException, UserException
        {
            throw new UserException("Unexpected type: string");
        }

        @Override
        public T visitIntList(int[] values, @Nullable RValue attributes) throws InternalException, UserException
        {
            throw new UserException("Unexpected type: list of integer");
        }

        @Override
        public T visitDoubleList(double[] values, @Nullable RValue attributes) throws InternalException, UserException
        {
            throw new UserException("Unexpected type: list of floating point");
        }

        @Override
        public T visitGenericList(ImmutableList<RValue> values, @Nullable RValue attributes) throws InternalException, UserException
        {
            throw new UserException("Unexpected type: generic list");
        }

        @Override
        public T visitPairList(ImmutableList<PairListEntry> items) throws InternalException, UserException
        {
            throw new UserException("Unexpected type: pair list");
        }

        @Override
        public T visitFactorList(int[] values, ImmutableList<String> levelNames) throws InternalException, UserException
        {
            throw new UserException("Unexpected factor list");
        }

        @Override
        public T visitNil() throws InternalException, UserException
        {
            throw new UserException("Unexpected nil");
        }
    }
    
    public static interface RValue
    {
        public <T> T visit(RVisitor<T> visitor) throws InternalException, UserException;
    }

    public static Pair<DataType, @Value Object> convertRToTypedValue(TypeManager typeManager, RValue rValue) throws InternalException, UserException
    {
        return rValue.visit(new RVisitor<Pair<DataType, @Value Object>>()
        {
            @Override
            public Pair<DataType, @Value Object> visitNil() throws InternalException, UserException
            {
                throw new UserException("Cannot turn nil into value");
            }

            @Override
            public Pair<DataType, @Value Object> visitString(String s) throws InternalException, UserException
            {
                return new Pair<>(DataType.TEXT, DataTypeUtility.value(s));
            }

            @Override
            public Pair<DataType, @Value Object> visitIntList(int[] values, @Nullable RValue attributes) throws InternalException, UserException
            {
                if (values.length == 1)
                    return new Pair<>(DataType.NUMBER, DataTypeUtility.value(values[0]));
                else
                    return new Pair<>(DataType.array(DataType.NUMBER), DataTypeUtility.value(Ints.asList(values)));
            }

            @Override
            public Pair<DataType, @Value Object> visitDoubleList(double[] values, @Nullable RValue attributes) throws InternalException, UserException
            {
                if (values.length == 1)
                    return new Pair<>(DataType.NUMBER, DataTypeUtility.value(values[0]));
                else
                    return new Pair<>(DataType.array(DataType.NUMBER), DataTypeUtility.value(Doubles.asList(values)));
            }

            @Override
            public Pair<DataType, @Value Object> visitGenericList(ImmutableList<RValue> values, @Nullable RValue attributes) throws InternalException, UserException
            {
                throw new InternalException("TODO generic list to single value");
            }

            @Override
            public Pair<DataType, @Value Object> visitPairList(ImmutableList<PairListEntry> items) throws InternalException, UserException
            {
                throw new InternalException("TODO pair list to single value");
            }

            @Override
            public Pair<DataType, @Value Object> visitFactorList(int[] values, ImmutableList<String> levelNames) throws InternalException, UserException
            {
                TaggedTypeDefinition taggedTypeDefinition = getTaggedTypeForFactors(levelNames, typeManager);
                return new Pair<>(taggedTypeDefinition.instantiate(ImmutableList.of(), typeManager), DataTypeUtility.value(IntStream.of(values).mapToObj(n -> new TaggedValue(n, null, taggedTypeDefinition)).collect(ImmutableList.<TaggedValue>toImmutableList())));
            }
        });
    }

    private static TaggedTypeDefinition getTaggedTypeForFactors(ImmutableList<String> levelNames, TypeManager typeManager) throws InternalException, UserException
    {
        // TODO Search for existing type
        @ExpressionIdentifier String typeName = "F";
        TaggedTypeDefinition taggedTypeDefinition =  typeManager.registerTaggedType(typeName, ImmutableList.<Pair<TypeVariableKind, @ExpressionIdentifier String>>of(), levelNames.stream().map(s -> new TagType<JellyType>(IdentifierUtility.fixExpressionIdentifier(s, "Factor"), null)).collect(ImmutableList.<TagType<JellyType>>toImmutableList()));
        if (taggedTypeDefinition == null)
            throw new UserException("Type named " + typeName + " already exists but with different tags");
        return taggedTypeDefinition;
    }

    public static Pair<SimulationFunction<RecordSet, EditableColumn>, Integer> convertRToColumn(TypeManager typeManager, RValue rValue, ColumnId columnName) throws UserException, InternalException
    {
        return rValue.visit(new RVisitor<Pair<SimulationFunction<RecordSet, EditableColumn>, Integer>>()
        {
            @Override
            public Pair<SimulationFunction<RecordSet, EditableColumn>, Integer> visitNil() throws InternalException, UserException
            {
                throw new UserException("Cannot make column from nil value");
            }

            @Override
            public Pair<SimulationFunction<RecordSet, EditableColumn>, Integer> visitString(String s) throws InternalException, UserException
            {
                return new Pair<>(rs -> new MemoryStringColumn(rs, columnName, ImmutableList.<Either<String, String>>of(Either.<String, String>right(s)), ""), 1);
            }

            @Override
            public Pair<SimulationFunction<RecordSet, EditableColumn>, Integer> visitIntList(int[] values, @Nullable RValue attributes) throws InternalException, UserException
            {
                return new Pair<>(rs -> new MemoryNumericColumn(rs, columnName, NumberInfo.DEFAULT, IntStream.of(values).mapToObj(n -> Either.<String, Number>right(n)).collect(ImmutableList.<Either<String, Number>>toImmutableList()), 0L), values.length);
            }

            @Override
            public Pair<SimulationFunction<RecordSet, EditableColumn>, Integer> visitDoubleList(double[] values, @Nullable RValue attributes) throws InternalException, UserException
            {
                return new Pair<>(rs -> new MemoryNumericColumn(rs, columnName, NumberInfo.DEFAULT, DoubleStream.of(values).mapToObj(n -> Either.<String, Number>right(DataTypeUtility.<Number>value(new BigDecimal(n)))).collect(ImmutableList.<Either<String, Number>>toImmutableList()), 0L), values.length);
            }

            @Override
            public Pair<SimulationFunction<RecordSet, EditableColumn>, Integer> visitFactorList(int[] values, ImmutableList<String> levelNames) throws InternalException, UserException
            {
                TaggedTypeDefinition taggedTypeDefinition = getTaggedTypeForFactors(levelNames, typeManager);
                return new Pair<>(rs -> new MemoryTaggedColumn(rs, columnName, taggedTypeDefinition.getTaggedTypeName(), ImmutableList.of(), Utility.mapList(taggedTypeDefinition.getTags(), t -> new TagType<>(t.getName(), null)), IntStream.of(values).mapToObj(n -> Either.<String, TaggedValue>right(new TaggedValue(n, null, taggedTypeDefinition))).collect(ImmutableList.<Either<String, TaggedValue>>toImmutableList()), new TaggedValue(0, null, taggedTypeDefinition)), values.length);
            }

            @Override
            public Pair<SimulationFunction<RecordSet, EditableColumn>, Integer> visitGenericList(ImmutableList<RValue> values, @Nullable RValue attributes) throws InternalException, UserException
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

            @Override
            public Pair<SimulationFunction<RecordSet, EditableColumn>, Integer> visitPairList(ImmutableList<PairListEntry> items) throws InternalException, UserException
            {
                // For some reason, factor columns appear as pair list of two with an int[] as second item:
                if (items.size() == 2)
                {
                    RVisitor<Pair<SimulationFunction<RecordSet, EditableColumn>, Integer>> outer = this;
                    return items.get(0).item.visit(new SpecificRVisitor<Pair<SimulationFunction<RecordSet, EditableColumn>, Integer>>()
                    {
                        @Override
                        public Pair<SimulationFunction<RecordSet, EditableColumn>, Integer> visitFactorList(int[] values, ImmutableList<String> levelNames) throws InternalException, UserException
                        {
                            return outer.visitFactorList(values, levelNames);
                        }
                    });
                }
                
                throw new InternalException("TODO pair list to column");
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
                Pair<SimulationFunction<RecordSet, EditableColumn>, Integer> p = convertRToColumn(typeManager, rValue, new ColumnId("Result"));
                return new KnownLengthRecordSet(ImmutableList.<SimulationFunction<RecordSet, EditableColumn>>of(p.getFirst()), p.getSecond());
            }

            @Override
            public RecordSet visitNil() throws InternalException, UserException
            {
                throw new UserException("Cannot make table from nil value");
            }

            @Override
            public RecordSet visitString(String s) throws InternalException, UserException
            {
                return singleColumn();
            }

            @Override
            public RecordSet visitIntList(int[] values, @Nullable RValue attributes) throws InternalException, UserException
            {
                return singleColumn();
            }

            @Override
            public RecordSet visitDoubleList(double[] values, @Nullable RValue attributes) throws InternalException, UserException
            {
                return singleColumn();
            }

            @Override
            public RecordSet visitFactorList(int[] values, ImmutableList<String> levelNames) throws InternalException, UserException
            {
                return singleColumn();
            }

            @Override
            public RecordSet visitPairList(ImmutableList<PairListEntry> items) throws InternalException, UserException
            {
                // Is this right?  Can a pair list be a list of columns?
                return singleColumn();
            }

            @Override
            public RecordSet visitGenericList(ImmutableList<RValue> values, @Nullable RValue attributes) throws InternalException, UserException
            {
                ImmutableMap<String, RValue> attrMap;
                if (attributes != null)
                {
                    attrMap = pairListToMap(attributes);
                }
                else
                {
                    attrMap = ImmutableMap.of();
                }
                
                // Tricky; could be a list of columns or a list of values!
                // First try as table (list of columns:

                RValue classRList = attrMap.get("class");
                RValue classRValue = classRList == null ? null : getListItem(classRList, 0);
                if (classRValue != null && "data.frame".equals(getString(classRValue)))
                {
                    ImmutableList<Pair<SimulationFunction<RecordSet, EditableColumn>, Integer>> columns = Utility.mapListExI_Index(values, (i, v) -> convertRToColumn(typeManager, v, getColumnName(attrMap.get("names"), i)));
                    if (!columns.isEmpty() && columns.stream().mapToInt(p -> p.getSecond()).distinct().count() == 1)
                    {
                        return new <EditableColumn>KnownLengthRecordSet(Utility.<Pair<SimulationFunction<RecordSet, EditableColumn>, Integer>, SimulationFunction<RecordSet, EditableColumn>>mapList(columns, p -> p.getFirst()), columns.get(0).getSecond());
                    }
                    throw new UserException("Columns are of differing lengths");
                }
                else
                    return singleColumn();
            }

            private ImmutableMap<String, RValue> pairListToMap(RValue attributes) throws UserException, InternalException
            {
                return Utility.<String, RValue>pairListToMap(attributes.<ImmutableList<Pair<String, RValue>>>visit(new SpecificRVisitor<ImmutableList<Pair<String, RValue>>>()
                {
                    @Override
                    public ImmutableList<Pair<String, RValue>> visitPairList(ImmutableList<PairListEntry> items) throws InternalException, UserException
                    {
                        return Utility.mapListExI(items, e -> {
                            if (e.tag == null)
                                throw new UserException("Missing tag name ");
                            return new Pair<>(getString(e.tag), e.item);
                        });
                    }
                }));
            }
        });
    }
    
    public static String prettyPrint(RValue rValue) throws UserException, InternalException
    {
        return rValue.visit(new RVisitor<StringBuilder>() {
            StringBuilder b = new StringBuilder();

            @Override
            public StringBuilder visitNil() throws InternalException, UserException
            {
                return b.append("<nil>");
            }

            @Override
            public StringBuilder visitString(String s) throws InternalException, UserException
            {
                return b.append("\"" + s + "\"");
            }

            @Override
            public StringBuilder visitIntList(int[] values, @Nullable RValue attributes) throws InternalException, UserException
            {
                b.append("int[" + values.length);
                if (attributes != null)
                {
                    b.append(", attr=");
                    attributes.visit(this);
                }
                return b.append("]");
            }

            @Override
            public StringBuilder visitDoubleList(double[] values, @Nullable RValue attributes) throws InternalException, UserException
            {
                b.append("double[" + values.length);
                if (attributes != null)
                {
                    b.append(", attr=");
                    attributes.visit(this);
                }
                return b.append("]");
            }

            @Override
            public StringBuilder visitGenericList(ImmutableList<RValue> values, @Nullable RValue attributes) throws InternalException, UserException
            {
                b.append("generic{");
                for (RValue value : values)
                {
                    value.visit(this).append(", ");
                }
                if (attributes != null)
                {
                    b.append("attr=");
                    attributes.visit(this);
                }
                return b.append("}");
            }

            @Override
            public StringBuilder visitPairList(ImmutableList<PairListEntry> items) throws InternalException, UserException
            {
                b.append("pair{");
                for (PairListEntry value : items)
                {
                    if (value.attributes != null)
                        value.attributes.visit(this).append(" -> ");
                    if (value.tag != null)
                    {
                        b.append("[");
                        value.tag.visit(this);
                        b.append("]@");
                    }
                    value.item.visit(this).append(", ");
                }
                return b.append("}");
            }

            @Override
            public StringBuilder visitFactorList(int[] values, ImmutableList<String> levelNames) throws InternalException, UserException
            {
                return b.append("factor[" + values.length + "]");
            }
        }).toString();
    }
}
