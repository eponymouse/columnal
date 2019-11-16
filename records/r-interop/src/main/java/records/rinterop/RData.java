package records.rinterop;

import annotation.identifier.qual.ExpressionIdentifier;
import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.primitives.Booleans;
import com.google.common.primitives.Doubles;
import com.google.common.primitives.Ints;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.*;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataType.DateTimeInfo.DateTimeType;
import records.data.datatype.DataType.TagType;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.DataTypeValue;
import records.data.datatype.DataTypeValue.DataTypeVisitorGet;
import records.data.datatype.DataTypeValue.GetValue;
import records.data.datatype.NumberInfo;
import records.data.datatype.TaggedTypeDefinition;
import records.data.datatype.TaggedTypeDefinition.TypeVariableKind;
import records.data.datatype.TypeId;
import records.data.datatype.TypeManager;
import records.data.unit.Unit;
import records.error.InternalException;
import records.error.UserException;
import records.jellytype.JellyType;
import utility.Either;
import utility.IdentifierUtility;
import utility.Pair;
import utility.SimulationFunction;
import utility.TaggedValue;
import utility.Utility;
import utility.Utility.ListEx;
import utility.Utility.Record;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAccessor;
import java.util.Arrays;
import java.util.HashMap;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

public class RData
{    
    public static final int STRING_SINGLE = 9;
    public static final int LOGICAL_VECTOR = 10;
    public static final int INT_VECTOR = 13;
    public static final int DOUBLE_VECTOR = 14;
    public static final int STRING_VECTOR = 16;
    public static final int GENERIC_VECTOR = 19;
    public static final int PAIR_LIST = 2;
    public static final int NIL = 0;

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
            return new ColumnId(IdentifierUtility.fixExpressionIdentifier(getString(getListItem(listColumnNames, index)), def));
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
        int after = d.read();
        if (after != -1)
            throw new UserException("Unexpected data at end of file: " + after);
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
            case NIL: // Nil
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
                return string(atom);
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
            case PAIR_LIST: // Pair list
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
            case STRING_SINGLE: // String
            {
                String s = readLenString(d);
                return string(s);
            }
            case INT_VECTOR: // Integer vector
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
                                public ImmutableList<String> visitStringList(ImmutableList<String> values, @Nullable RValue attributes) throws InternalException, UserException
                                {
                                    return values;
                                }

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
                {
                    // R factors index from 1, we index TaggedValue from 0, so adjust:
                    for (int i = 0; i < values.length; i++)
                    {
                        values[i] -= 1;
                    }
                    return new RValue()
                    {
                        @Override
                        public <T> T visit(RVisitor<T> visitor) throws InternalException, UserException
                        {
                            return visitor.visitFactorList(values, factorNames);
                        }
                    };
                }
                else
                    return intVector(values, attr);
            }
            case DOUBLE_VECTOR: // Floating point vector
            {
                int vecLen = d.readInt();
                double[] values = new double[vecLen];
                for (int i = 0; i < vecLen; i++)
                {
                    values[i] = d.readDouble();
                }
                final @Nullable RValue attr = objHeader.readAttributes(d, atoms);
                ImmutableMap<String, RValue> attrMap = pairListToMap(attr);
                if (isClass(attrMap, "Date"))
                    return dateVector(values, attr);
                else if (isClass(attrMap, "POSIXct"))
                    return dateTimeZonedVector(values, attr);
                else 
                    return doubleVector(values, attr);
            }
            case STRING_VECTOR: // Character vector
            {
                int vecLen = d.readInt();
                ImmutableList.Builder<String> valueBuilder = ImmutableList.builderWithExpectedSize(vecLen);
                for (int i = 0; i < vecLen; i++)
                {
                    valueBuilder.add(getString(readItem(d, atoms)));
                }
                ImmutableList<String> values = valueBuilder.build();
                final @Nullable RValue attr = objHeader.readAttributes(d, atoms);
                return stringVector(values, attr);
            }
            case GENERIC_VECTOR: // Generic vector
            {
                int vecLen = d.readInt();
                ImmutableList.Builder<RValue> valueBuilder = ImmutableList.builderWithExpectedSize(vecLen);
                for (int i = 0; i < vecLen; i++)
                {
                    valueBuilder.add(readItem(d, atoms));
                }
                ImmutableList<RValue> values = valueBuilder.build();
                final @Nullable RValue attr = objHeader.readAttributes(d, atoms);
                return genericVector(values, attr);
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

    private static RValue dateTimeZonedVector(double[] values, @Nullable RValue attr) throws InternalException, UserException
    {
        ImmutableMap<String, RValue> attrMap = pairListToMap(attr);
        RValue tzone = attrMap.get("tzone");
        if (tzone != null)
        {
            ImmutableList.Builder<@Value TemporalAccessor> b = ImmutableList.builderWithExpectedSize(values.length);
            for (double value : values)
            {
                @SuppressWarnings("valuetype")
                @Value ZonedDateTime zdt = ZonedDateTime.ofInstant(Instant.ofEpochSecond((long) value), ZoneId.of(getString(getListItem(tzone, 0))));
                b.add(zdt);
            }
            return temporalVector(new DateTimeInfo(DateTimeType.DATETIMEZONED), b.build());
        }
        else
        {
            ImmutableList.Builder<@Value TemporalAccessor> b = ImmutableList.builderWithExpectedSize(values.length);
            for (double value : values)
            {
                @SuppressWarnings("valuetype")
                @Value LocalDateTime ldt = LocalDateTime.ofInstant(Instant.ofEpochSecond((long) value), ZoneId.of("UTC"));
                b.add(ldt);
            }
            return temporalVector(new DateTimeInfo(DateTimeType.DATETIME), b.build());
        }
    }

    private static RValue dateVector(double[] values, @Nullable RValue attr) throws InternalException
    {
        if (DoubleStream.of(values).allMatch(d -> d == (int)d))
        {
            ImmutableList<@Value TemporalAccessor> dates = DoubleStream.of(values).<@Value TemporalAccessor>mapToObj(d -> {
                @SuppressWarnings("valuetype")
                @Value LocalDate date = LocalDate.ofEpochDay((int) d);
                return date;
            }).collect(ImmutableList.<@Value TemporalAccessor>toImmutableList());
            return new RValue()
            {
                @Override
                public <T> T visit(RVisitor<T> visitor) throws InternalException, UserException
                {
                    return visitor.visitTemporalList(DateTimeType.YEARMONTHDAY, dates, attr);
                }
            };
        }
        else
        {
            ImmutableList<@Value TemporalAccessor> dates = DoubleStream.of(values).<@Value TemporalAccessor>mapToObj(d -> {
                double seconds = d * (60.0 * 60.0 * 24.0);
                double wholeSeconds = Math.floor(seconds);
                @SuppressWarnings("valuetype")
                @Value LocalDateTime date = LocalDateTime.ofEpochSecond((long)wholeSeconds, (int)(1_000_000_000 * (seconds - wholeSeconds)), ZoneOffset.UTC);
                return date;
            }).collect(ImmutableList.<@Value TemporalAccessor>toImmutableList());
            return new RValue()
            {
                @Override
                public <T> T visit(RVisitor<T> visitor) throws InternalException, UserException
                {
                    return visitor.visitTemporalList(DateTimeType.DATETIME, dates, attr);
                }
            };
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
            public RValue visitStringList(ImmutableList<String> values, @Nullable RValue attributes) throws InternalException, UserException
            {
                return string(values.get(index));
            }

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
        //throw new UserException("Unrecognised file format");
        return new BufferedInputStream(new FileInputStream(rFilePath));
    }
    
    public static interface RVisitor<T>
    {
        public T visitString(String s) throws InternalException, UserException;
        // If attributes reveal this is a factor, it won't be called; visitFactorList will be instead
        public T visitIntList(int[] values, @Nullable RValue attributes) throws InternalException, UserException;
        public T visitDoubleList(double[] values, @Nullable RValue attributes) throws InternalException, UserException;
        public T visitLogicalList(boolean[] values, @Nullable RValue attributes) throws InternalException, UserException;
        public T visitStringList(ImmutableList<String> values, @Nullable RValue attributes) throws InternalException, UserException;
        public T visitTemporalList(DateTimeType dateTimeType, ImmutableList<@Value TemporalAccessor> values, @Nullable RValue attributes) throws InternalException, UserException;
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
        public T visitLogicalList(boolean[] values, @Nullable RValue attributes) throws InternalException, UserException
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
        public T visitStringList(ImmutableList<String> values, @Nullable RValue attributes) throws InternalException, UserException
        {
            return makeDefault();
        }

        @Override
        public T visitTemporalList(DateTimeType dateTimeType, ImmutableList<@Value TemporalAccessor> values, @Nullable RValue attributes) throws InternalException, UserException
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
        public T visitLogicalList(boolean[] values, @Nullable RValue attributes) throws InternalException, UserException
        {
            throw new UserException("Unexpected type: list of booleans");
        }

        @Override
        public T visitTemporalList(DateTimeType dateTimeType, ImmutableList<@Value TemporalAccessor> values, @Nullable RValue attributes) throws InternalException, UserException
        {
            throw new UserException("Unexpected type: list of date/time type");
        }

        @Override
        public T visitStringList(ImmutableList<String> values, @Nullable RValue attributes) throws InternalException, UserException
        {
            throw new UserException("Unexpected type: list of strings");
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
            public Pair<DataType, @Value Object> visitLogicalList(boolean[] values, @Nullable RValue attributes) throws InternalException, UserException
            {
                if (values.length == 1)
                    return new Pair<>(DataType.BOOLEAN, DataTypeUtility.value(values[0]));
                else
                    return new Pair<>(DataType.array(DataType.BOOLEAN), DataTypeUtility.value(Booleans.asList(values)));
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
            public Pair<DataType, @Value Object> visitStringList(ImmutableList<String> values, @Nullable RValue attributes) throws InternalException, UserException
            {
                if (values.size() == 1)
                    return new Pair<>(DataType.TEXT, DataTypeUtility.value(values.get(0)));
                else
                    return new Pair<>(DataType.array(DataType.TEXT), DataTypeUtility.value(values));
            }

            @Override
            public Pair<DataType, @Value Object> visitTemporalList(DateTimeType dateTimeType, ImmutableList<@Value TemporalAccessor> values, @Nullable RValue attributes) throws InternalException, UserException
            {
                DateTimeInfo t = new DateTimeInfo(dateTimeType);
                if (values.size() == 1)
                    return new Pair<>(DataType.date(t), values.get(0));
                else
                    return new Pair<>(DataType.array(DataType.date(t)), DataTypeUtility.value(values));
            }

            @Override
            public Pair<DataType, @Value Object> visitGenericList(ImmutableList<RValue> values, @Nullable RValue attributes) throws InternalException, UserException
            {
                throw new InternalException("TODO generic list to single value: " + prettyPrint(rValue));
            }

            @Override
            public Pair<DataType, @Value Object> visitPairList(ImmutableList<PairListEntry> items) throws InternalException, UserException
            {
                throw new InternalException("TODO pair list to single value: "  + prettyPrint(rValue));
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
        for (int i = 0; i < 100; i++)
        {
            @SuppressWarnings("identifier")
            @ExpressionIdentifier String hint = "F " + i;
            @ExpressionIdentifier String typeName = IdentifierUtility.fixExpressionIdentifier(levelNames.stream().sorted().findFirst().orElse("F") + " " + levelNames.size(), hint);
            TaggedTypeDefinition taggedTypeDefinition = typeManager.registerTaggedType(typeName, ImmutableList.<Pair<TypeVariableKind, @ExpressionIdentifier String>>of(), levelNames.stream().map(s -> new TagType<JellyType>(IdentifierUtility.fixExpressionIdentifier(s, "Factor"), null)).collect(ImmutableList.<TagType<JellyType>>toImmutableList()));
            if (taggedTypeDefinition != null)
                return taggedTypeDefinition;
        }
        throw new UserException("Type named F1 through F100 already exists but with different tags");
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
            public Pair<SimulationFunction<RecordSet, EditableColumn>, Integer> visitStringList(ImmutableList<String> values, @Nullable RValue attributes) throws InternalException, UserException
            {
                return new Pair<>(rs -> new MemoryStringColumn(rs, columnName, Utility.mapList(values, v -> Either.<String, String>right(v)), ""), values.size());
            }

            @Override
            public Pair<SimulationFunction<RecordSet, EditableColumn>, Integer> visitTemporalList(DateTimeType dateTimeType, ImmutableList<@Value TemporalAccessor> values, @Nullable RValue attributes) throws InternalException, UserException
            {
                DateTimeInfo t = new DateTimeInfo(dateTimeType);
                return new Pair<>(rs -> new MemoryTemporalColumn(rs, columnName, t, Utility.mapList(values, v -> Either.<String, TemporalAccessor>right(v)), t.getDefaultValue()), values.size());
            }

            @Override
            public Pair<SimulationFunction<RecordSet, EditableColumn>, Integer> visitLogicalList(boolean[] values, @Nullable RValue attributes) throws InternalException, UserException
            {
                return new Pair<>(rs -> new MemoryBooleanColumn(rs, columnName, Booleans.asList(values).stream().map(n -> Either.<String, Boolean>right(n)).collect(ImmutableList.<Either<String, Boolean>>toImmutableList()), false), values.length);
            }

            @Override
            public Pair<SimulationFunction<RecordSet, EditableColumn>, Integer> visitIntList(int[] values, @Nullable RValue attributes) throws InternalException, UserException
            {
                return new Pair<>(rs -> new MemoryNumericColumn(rs, columnName, NumberInfo.DEFAULT, IntStream.of(values).mapToObj(n -> Either.<String, Number>right(n)).collect(ImmutableList.<Either<String, Number>>toImmutableList()), 0L), values.length);
            }

            @Override
            public Pair<SimulationFunction<RecordSet, EditableColumn>, Integer> visitDoubleList(double[] values, @Nullable RValue attributes) throws InternalException, UserException
            {
                return new Pair<>(rs -> new MemoryNumericColumn(rs, columnName, NumberInfo.DEFAULT, DoubleStream.of(values).mapToObj(n -> {
                    try
                    {
                        return Either.<String, Number>right(DataTypeUtility.<Number>value(new BigDecimal(n)));
                    }
                    catch (NumberFormatException e)
                    {
                        return Either.<String, Number>left(Double.toString(n));
                    }
                }).collect(ImmutableList.<Either<String, Number>>toImmutableList()), 0L), values.length);
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
                return new Pair<>(m.getFirst().makeImmediateColumn(columnName, Utility.<Pair<DataType, @Value Object>, Either<String, @Value Object>>mapListExI(typedPairs, p -> Either.<String, @Value Object>right(getOrInternal(m.getSecond(), p.getFirst()).apply(p.getSecond()))), DataTypeUtility.makeDefaultValue(m.getFirst())), typedPairs.size());
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
                    @Nullable Pair<SimulationFunction<RecordSet, EditableColumn>, Integer> asFactors = items.get(0).item.visit(new DefaultRVisitor<@Nullable Pair<SimulationFunction<RecordSet, EditableColumn>, Integer>>(null)
                    {
                        @Override
                        public @Nullable Pair<SimulationFunction<RecordSet, EditableColumn>, Integer> visitFactorList(int[] values, ImmutableList<String> levelNames) throws InternalException, UserException
                        {
                            return outer.visitFactorList(values, levelNames);
                        }
                    });
                    if (asFactors != null)
                        return asFactors;
                }
                
                throw new InternalException("TODO pair list to column: " + prettyPrint(rValue));
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
    
    public static ImmutableList<RecordSet> convertRToTable(TypeManager typeManager, RValue rValue) throws UserException, InternalException
    {
        // R tables are usually a list of columns, which suits us:
        return rValue.visit(new RVisitor<ImmutableList<RecordSet>>()
        {
            private ImmutableList<RecordSet> singleColumn() throws UserException, InternalException
            {
                Pair<SimulationFunction<RecordSet, EditableColumn>, Integer> p = convertRToColumn(typeManager, rValue, new ColumnId("Result"));
                return ImmutableList.<RecordSet>of(new <EditableColumn>KnownLengthRecordSet(ImmutableList.<SimulationFunction<RecordSet, EditableColumn>>of(p.getFirst()), p.getSecond()));
            }

            @Override
            public ImmutableList<RecordSet> visitNil() throws InternalException, UserException
            {
                return ImmutableList.of();
            }

            @Override
            public ImmutableList<RecordSet> visitString(String s) throws InternalException, UserException
            {
                return singleColumn();
            }

            @Override
            public ImmutableList<RecordSet> visitStringList(ImmutableList<String> values, @Nullable RValue attributes) throws InternalException, UserException
            {
                return singleColumn();
            }

            @Override
            public ImmutableList<RecordSet> visitLogicalList(boolean[] values, @Nullable RValue attributes) throws InternalException, UserException
            {
                return singleColumn();
            }

            @Override
            public ImmutableList<RecordSet> visitIntList(int[] values, @Nullable RValue attributes) throws InternalException, UserException
            {
                return singleColumn();
            }

            @Override
            public ImmutableList<RecordSet> visitDoubleList(double[] values, @Nullable RValue attributes) throws InternalException, UserException
            {
                return singleColumn();
            }

            @Override
            public ImmutableList<RecordSet> visitTemporalList(DateTimeType dateTimeType, ImmutableList<@Value TemporalAccessor> values, @Nullable RValue attributes) throws InternalException, UserException
            {
                return singleColumn();
            }

            @Override
            public ImmutableList<RecordSet> visitFactorList(int[] values, ImmutableList<String> levelNames) throws InternalException, UserException
            {
                return singleColumn();
            }

            @Override
            public ImmutableList<RecordSet> visitPairList(ImmutableList<PairListEntry> items) throws InternalException, UserException
            {
                ImmutableList.Builder<RecordSet> r = ImmutableList.builder();
                for (PairListEntry item : items)
                {
                    r.addAll(convertRToTable(typeManager, item.item));
                }
                return r.build();
            }

            @Override
            public ImmutableList<RecordSet> visitGenericList(ImmutableList<RValue> values, @Nullable RValue attributes) throws InternalException, UserException
            {
                // Tricky; could be a list of tables, a list of columns or a list of values!
                // First try as table (list of columns):
                final ImmutableMap<String, RValue> attrMap = pairListToMap(attributes);

                boolean isDataFrame = isClass(attrMap, "data.frame");
                if (isDataFrame)
                {
                    ImmutableList<Pair<SimulationFunction<RecordSet, EditableColumn>, Integer>> columns = Utility.mapListExI_Index(values, (i, v) -> convertRToColumn(typeManager, v, getColumnName(attrMap.get("names"), i)));
                    if (!columns.isEmpty() && columns.stream().mapToInt(p -> p.getSecond()).distinct().count() == 1)
                    {
                        return ImmutableList.of(new <EditableColumn>KnownLengthRecordSet(Utility.<Pair<SimulationFunction<RecordSet, EditableColumn>, Integer>, SimulationFunction<RecordSet, EditableColumn>>mapList(columns, p -> p.getFirst()), columns.get(0).getSecond()));
                    }
                    throw new UserException("Columns are of differing lengths: " + columns.stream().map(p -> "" + p.getSecond()).collect(Collectors.joining(", ")));
                }
                else
                {
                    boolean hasDataFrames = false;
                    for (RValue value : values)
                    {
                        boolean valueIsDataFrame = value.visit(new DefaultRVisitor<Boolean>(false)
                        {
                            @Override
                            public Boolean visitGenericList(ImmutableList<RValue> values, @Nullable RValue attributes) throws InternalException, UserException
                            {
                                final ImmutableMap<String, RValue> valueAttrMap = pairListToMap(attributes);
                                return isClass(valueAttrMap, "data.frame");
                            }
                        });
                        
                        if (valueIsDataFrame)
                        {
                            hasDataFrames = true;
                            break;
                        }
                    }
                    if (!hasDataFrames)
                        return singleColumn();
                    
                    ImmutableList.Builder<RecordSet> r = ImmutableList.builder();
                    for (RValue value : values)
                    {
                        r.addAll(convertRToTable(typeManager, value));
                    }
                    return r.build();
                }
            }
        });
    }

    private static boolean isClass(ImmutableMap<String, RValue> attrMap, String className) throws UserException, InternalException
    {
        RValue classRList = attrMap.get("class");
        RValue classRValue = classRList == null ? null : getListItem(classRList, 0);
        return classRValue != null && className.equals(getString(classRValue));
    }

    private static ImmutableMap<String, RValue> pairListToMap(@Nullable RValue attributes) throws UserException, InternalException
    {
        if (attributes == null)
            return ImmutableMap.of();
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

    public static String prettyPrint(RValue rValue) throws UserException, InternalException
    {
        StringBuilder b = new StringBuilder();
        prettyPrint(rValue, b, "");
        return b.toString();
    }
    
    private static void prettyPrint(RValue rValue, StringBuilder b, String indent) throws UserException, InternalException
    {
        rValue.visit(new RVisitor<@Nullable Void>() {
            @Override
            public @Nullable Void visitNil() throws InternalException, UserException
            {
                b.append("<nil>");
                return null;
            }

            @Override
            public @Nullable Void visitString(String s) throws InternalException, UserException
            {
                b.append("\"" + s + "\"");
                return null;
            }

            @Override
            public @Nullable Void visitLogicalList(boolean[] values, @Nullable RValue attributes) throws InternalException, UserException
            {
                b.append("boolean[" + values.length);
                if (attributes != null)
                {
                    b.append(", attr=");
                    attributes.visit(this);
                }
                b.append("]");
                return null;
            }

            @Override
            public @Nullable Void visitIntList(int[] values, @Nullable RValue attributes) throws InternalException, UserException
            {
                b.append("int[" + values.length);
                if (attributes != null)
                {
                    b.append(", attr=");
                    attributes.visit(this);
                }
                b.append("]");
                return null;
            }

            @Override
            public @Nullable Void visitDoubleList(double[] values, @Nullable RValue attributes) throws InternalException, UserException
            {
                b.append("double[" + values.length);
                if (attributes != null)
                {
                    b.append(", attr=");
                    attributes.visit(this);
                }
                b.append("]");
                return null;
            }

            @Override
            public @Nullable Void visitStringList(ImmutableList<String> values, @Nullable RValue attributes) throws InternalException, UserException
            {
                b.append("String[" + values.size());
                if (attributes != null)
                {
                    b.append(", attr=");
                    attributes.visit(this);
                }
                b.append("]");
                return null;
            }

            @Override
            public @Nullable Void visitTemporalList(DateTimeType dateTimeType, ImmutableList<@Value TemporalAccessor> values, @Nullable RValue attributes) throws InternalException, UserException
            {
                b.append(dateTimeType.toString() + "[" + values.size());
                if (attributes != null)
                {
                    b.append(", attr=");
                    attributes.visit(this);
                }
                b.append("]");
                return null;
            }

            @Override
            public @Nullable Void visitGenericList(ImmutableList<RValue> values, @Nullable RValue attributes) throws InternalException, UserException
            {
                b.append("generic{\n");
                for (RValue value : values)
                {
                    b.append(indent);
                    prettyPrint(value, b, indent + "  ");
                    b.append(",\n");
                }
                if (attributes != null)
                {
                    b.append(indent);
                    b.append("attr=");
                    attributes.visit(this);
                    b.append("\n");
                }
                b.append(indent);
                b.append("}");
                return null;
            }

            @Override
            public @Nullable Void visitPairList(ImmutableList<PairListEntry> items) throws InternalException, UserException
            {
                b.append("pair{");
                for (PairListEntry value : items)
                {
                    if (value.attributes != null)
                    {
                        value.attributes.visit(this);
                        b.append(" -> ");
                    }
                    if (value.tag != null)
                    {
                        b.append("[");
                        value.tag.visit(this);
                        b.append("]@");
                    }
                    value.item.visit(this);
                    b.append(", ");
                }
                b.append("}");
                return null;
            }

            @Override
            public @Nullable Void visitFactorList(int[] values, ImmutableList<String> levelNames) throws InternalException, UserException
            {
                b.append("factor[" + values.length + ", levels=" + levelNames.stream().collect(Collectors.joining(",")) + "]");
                return null;
            }
        });
    }
    
    public static RValue convertTableToR(RecordSet recordSet) throws UserException, InternalException
    {
        // A table is a generic list of columns with class data.frame
        return genericVector(Utility.mapListExI(recordSet.getColumns(), c -> convertColumnToR(c)), makeClassAttributes("data.frame", ImmutableMap.<String, RValue>of("names", stringVector(Utility.mapListExI(recordSet.getColumns(), c -> c.getName().getRaw()), null))));
    }

    private static RValue makeClassAttributes(String className, ImmutableMap<String, RValue> otherItems)
    {
        return pairListFromMap(Utility.appendToMap(otherItems, "class", genericVector(ImmutableList.of(string(className)), null), null));
    }

    private static RValue convertColumnToR(Column column) throws UserException, InternalException
    {
        DataTypeValue dataTypeValue = column.getType();
        int length = column.getLength();
        return dataTypeValue.applyGet(new DataTypeVisitorGet<RValue>()
        {
            @Override
            public RValue number(GetValue<@Value Number> g, NumberInfo displayInfo) throws InternalException, UserException
            {
                // Need to work out if they are all ints, otherwise must use doubles.
                // Start with ints and go from there
                int[] ints = new int[length];
                int i;
                for (i = 0; i < length; i++)
                {
                    @Value Number n = g.get(i);
                    @Value Integer nInt = getIfInteger(n);
                    if (nInt != null)
                        ints[i] = nInt;
                    else
                        break;
                }
                if (i == length)
                    return intVector(ints, null);
                
                // Convert all ints to doubles:
                double[] doubles = new double[length];
                for (int j = 0; j < i; j++)
                {
                    doubles[j] = ints[j];
                }
                for (; i < length; i++)
                {
                    doubles[i] = Utility.toBigDecimal(g.get(i)).doubleValue();
                }
                return doubleVector(doubles, null);
            }

            @Override
            public RValue text(GetValue<@Value String> g) throws InternalException, UserException
            {
                ImmutableList.Builder<String> list = ImmutableList.builderWithExpectedSize(length);
                for (int i = 0; i < length; i++)
                {
                    list.add(g.get(i));
                }
                return stringVector(list.build(), null);
            }

            @Override
            public RValue bool(GetValue<@Value Boolean> g) throws InternalException, UserException
            {
                boolean[] bools = new boolean[length];
                for (int i = 0; i < length; i++)
                {
                    bools[i] = g.get(i);
                }
                return booleanVector(bools, null);
            }

            @Override
            public RValue date(DateTimeInfo dateTimeInfo, GetValue<@Value TemporalAccessor> g) throws InternalException, UserException
            {
                ImmutableList.Builder<@Value TemporalAccessor> valueBuilder = ImmutableList.builderWithExpectedSize(length);
                for (int i = 0; i < length; i++)
                {
                    valueBuilder.add(g.get(i));
                }
                ImmutableList<@Value TemporalAccessor> values = valueBuilder.build();
                return temporalVector(dateTimeInfo, values);
            }

            @Override
            public RValue tagged(TypeId typeName, ImmutableList<Either<Unit, DataType>> typeVars, ImmutableList<TagType<DataType>> tagTypes, GetValue<@Value TaggedValue> g) throws InternalException, UserException
            {
                throw new InternalException("TODO");
            }

            @Override
            public RValue record(ImmutableMap<@ExpressionIdentifier String, DataType> types, GetValue<@Value Record> g) throws InternalException, UserException
            {
                throw new InternalException("TODO");
            }

            @Override
            public RValue array(DataType inner, GetValue<@Value ListEx> g) throws InternalException, UserException
            {
                throw new InternalException("TODO");
            }
        });
    }

    private static RValue temporalVector(DateTimeInfo dateTimeInfo, ImmutableList<@Value TemporalAccessor> values)
    {
        return new RValue()
        {
            @Override
            public <T> T visit(RVisitor<T> visitor) throws InternalException, UserException
            {
                return visitor.visitTemporalList(dateTimeInfo.getType(), values, makeClassAttributes("Date", ImmutableMap.of()));
            }
        };
    }

    private static @Nullable @Value Integer getIfInteger(@Value Number n) throws InternalException
    {
        return Utility.<@Nullable @Value Integer>withNumberInt(n, l -> {
            if (l.longValue() != l.intValue())
                return null;
            return DataTypeUtility.value(l.intValue());
        }, bd -> {
            try
            {
                return DataTypeUtility.value(bd.intValueExact());
            }
            catch (ArithmeticException e)
            {
                return null;
            }
        });
    }

    private static RValue booleanVector(boolean[] values, @Nullable RValue attributes)
    {
        return new RValue()
        {
            @Override
            public <T> T visit(RVisitor<T> visitor) throws InternalException, UserException
            {
                return visitor.visitLogicalList(values, attributes);
            }
        };
    }

    private static RValue intVector(int[] values, @Nullable RValue attributes)
    {
        return new RValue()
        {
            @Override
            public <T> T visit(RVisitor<T> visitor) throws InternalException, UserException
            {
                return visitor.visitIntList(values, attributes);
            }
        };
    }

    private static RValue doubleVector(double[] values, @Nullable RValue attributes)
    {
        return new RValue()
        {
            @Override
            public <T> T visit(RVisitor<T> visitor) throws InternalException, UserException
            {
                return visitor.visitDoubleList(values, attributes);
            }
        };
    }

    private static RValue logicalVector(boolean[] values, @Nullable RValue attributes)
    {
        return new RValue()
        {
            @Override
            public <T> T visit(RVisitor<T> visitor) throws InternalException, UserException
            {
                return visitor.visitLogicalList(values, attributes);
            }
        };
    }

    private static RValue genericVector(ImmutableList<RValue> values, @Nullable RValue attributes)
    {
        return new RValue()
        {
            @Override
            public <T> T visit(RVisitor<T> visitor) throws InternalException, UserException
            {
                return visitor.visitGenericList(values, attributes);
            }
        };
    }

    private static RValue stringVector(ImmutableList<String> values, @Nullable RValue attributes)
    {
        return new RValue()
        {
            @Override
            public <T> T visit(RVisitor<T> visitor) throws InternalException, UserException
            {
                return visitor.visitStringList(values, attributes);
            }
        };
    }
    
    private static RValue string(String value)
    {
        return new RValue()
        {
            @Override
            public <T> T visit(RVisitor<T> visitor) throws InternalException, UserException
            {
                return visitor.visitString(value);
            }
        };
    }

    private static RValue pairListFromMap(ImmutableMap<String, RValue> values)
    {
        return makePairList(values.entrySet().stream().map(e -> new PairListEntry(null, string(e.getKey()), e.getValue())).collect(ImmutableList.<PairListEntry>toImmutableList()));
    }
    
    private static RValue makePairList(ImmutableList<PairListEntry> values)
    {
        return new RValue()
        {
            @Override
            public <T> T visit(RVisitor<T> visitor) throws InternalException, UserException
            {
                return visitor.visitPairList(values);
            }
        };
    }

    public static void writeRData(File destFile, RValue topLevel) throws IOException, UserException, InternalException
    {
        final DataOutputStream d = new DataOutputStream(new FileOutputStream(destFile, false));
        // TODO compress
        d.writeByte('X');
        d.writeByte('\n');
        d.writeInt(0);
        d.writeInt(0);
        d.writeInt(0);
        topLevel.visit(new RVisitor<@Nullable Void>()
        {
            private void writeInt(int value) throws UserException
            {
                try
                {
                    d.writeInt(value);
                }
                catch (IOException e)
                {
                    throw new UserException("IO error", e);
                }
            }

            private void writeHeader(int value, @Nullable RValue attributes, @Nullable RValue tag) throws UserException, InternalException
            {
                try
                {
                    d.writeInt(value | (attributes != null ? 0x200 : 0) | (tag != null ? 0x400 : 0));
                    if (value == PAIR_LIST)
                    {
                        writeAttributes(attributes);
                        writeAttributes(tag);
                    }
                }
                catch (IOException e)
                {
                    throw new UserException("IO error", e);
                }
            }



            private void writeDouble(double value) throws UserException
            {
                try
                {
                    d.writeDouble(value);
                }
                catch (IOException e)
                {
                    throw new UserException("IO error", e);
                }
            }

            private void writeBytes(byte[] value) throws UserException
            {
                try
                {
                    d.write(value);
                }
                catch (IOException e)
                {
                    throw new UserException("IO error", e);
                }
            }
            
            private void writeAttributes(@Nullable RValue attributes) throws UserException, InternalException
            {
                if (attributes == null)
                    return;
                attributes.visit(this);
            }
            
            @Override
            public @Nullable Void visitString(String s) throws InternalException, UserException
            {
                writeInt(STRING_SINGLE);
                writeLenString(s);
                return null;
            }

            private void writeLenString(String s) throws UserException
            {
                byte[] bytes = s.getBytes(StandardCharsets.US_ASCII);
                writeInt(bytes.length);
                writeBytes(bytes);
            }

            @Override
            public @Nullable Void visitStringList(ImmutableList<String> values, @Nullable RValue attributes) throws InternalException, UserException
            {
                writeHeader(STRING_VECTOR, attributes, null);
                writeInt(values.size());
                for (String value : values)
                {
                    writeInt(STRING_SINGLE);
                    writeLenString(value);
                }
                writeAttributes(attributes);
                return null;
            }

            @Override
            public @Nullable Void visitTemporalList(DateTimeType dateTimeType, ImmutableList<@Value TemporalAccessor> values, @Nullable RValue attributes) throws InternalException, UserException
            {
                switch (dateTimeType)
                {
                    case YEARMONTHDAY:
                        writeHeader(DOUBLE_VECTOR, attributes, null);
                        writeInt(values.size());
                        for (TemporalAccessor value : values)
                        {
                            writeDouble(((LocalDate)value).toEpochDay());
                        }
                        writeAttributes(attributes);
                        break;
                    case DATETIME:
                        writeHeader(DOUBLE_VECTOR, attributes, null);
                        writeInt(values.size());
                        for (TemporalAccessor value : values)
                        {
                            writeDouble(((LocalDateTime)value).toEpochSecond(ZoneOffset.UTC));
                        }
                        writeAttributes(attributes);
                        break;
                    case DATETIMEZONED:
                        writeHeader(DOUBLE_VECTOR, attributes, null);
                        writeInt(values.size());
                        for (TemporalAccessor value : values)
                        {
                            writeDouble(((ZonedDateTime)value).toEpochSecond());
                        }
                        writeAttributes(attributes);
                        break;
                    default:
                        throw new InternalException("TODO: " + dateTimeType);
                }
                return null;
            }

            @Override
            public @Nullable Void visitIntList(int[] values, @Nullable RValue attributes) throws InternalException, UserException
            {
                writeHeader(INT_VECTOR, attributes, null);
                writeInt(values.length);
                for (int value : values)
                {
                    writeInt(value);
                }
                writeAttributes(attributes);
                return null;
            }

            @Override
            public @Nullable Void visitDoubleList(double[] values, @Nullable RValue attributes) throws InternalException, UserException
            {
                writeHeader(DOUBLE_VECTOR, attributes, null);
                writeInt(values.length);
                for (double value : values)
                {
                    writeDouble(value);
                }
                writeAttributes(attributes);
                return null;
            }

            @Override
            public @Nullable Void visitLogicalList(boolean[] values, @Nullable RValue attributes) throws InternalException, UserException
            {
                writeHeader(LOGICAL_VECTOR, attributes, null);
                writeInt(values.length);
                for (boolean value : values)
                {
                    writeInt(value ? 1 : 0);
                }
                writeAttributes(attributes);
                return null;
            }

            @Override
            public @Nullable Void visitGenericList(ImmutableList<RValue> values, @Nullable RValue attributes) throws InternalException, UserException
            {
                writeHeader(GENERIC_VECTOR, attributes, null);
                writeInt(values.size());
                for (RValue value : values)
                {
                    value.visit(this);
                }
                writeAttributes(attributes);
                return null;
            }

            @Override
            public @Nullable Void visitPairList(ImmutableList<PairListEntry> items) throws InternalException, UserException
            {
                switch (items.size())
                {
                    case 0:
                        break;
                    case 1:
                        writeHeader(PAIR_LIST, items.get(0).attributes, items.get(0).tag);
                        items.get(0).item.visit(this);
                        visitNil();
                        break;
                    /*
                    case 2:
                        writeHeader(PAIR_LIST, items.get(0).attributes, items.get(0).tag);
                        items.get(0).item.visit(this);
                        items.get(1).item.visit(this);
                        break;*/
                    default:
                        writeHeader(PAIR_LIST, items.get(0).attributes, items.get(0).tag);
                        items.get(0).item.visit(this);
                        visitPairList(items.subList(1, items.size()));
                        break;
                }
                return null;
            }

            @Override
            public @Nullable Void visitFactorList(int[] values, ImmutableList<String> levelNames) throws InternalException, UserException
            {
                throw new InternalException("TODO");
                //return null;
            }

            @Override
            public @Nullable Void visitNil() throws InternalException, UserException
            {
                writeInt(NIL);
                return null;
            }
        });
    }

}
