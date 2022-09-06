package records.rinterop;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import records.rinterop.RVisitor.PairListEntry;
import xyz.columnal.utility.Utility;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

public class RRead
{
    private static int addAtom(HashMap<Integer, String> atoms, String atom)
    {
        int newKey = atoms.size() + 1;
        atoms.put(newKey, atom);
        return newKey;
    }

    private static String getAtom(HashMap<Integer, String> atoms, int key) throws UserException
    {
        String atom = atoms.get(key);
        if (atom != null)
            return atom;
        throw new UserException("Could not find atom: " + key + " in atoms sized: " + atoms.size());
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

    static RValue readItem(DataInputStream d, HashMap<Integer, String> atoms) throws IOException, UserException, InternalException
    {
        TypeHeader objHeader = new TypeHeader(d, atoms);
        String indent = Arrays.stream(Thread.currentThread().getStackTrace()).map(s -> s.getMethodName().equals("readItem") ? "  " : "").collect(Collectors.joining());
        //System.out.println(indent + "Read: " + objHeader.getType() + " " + objHeader.hasAttributes() + "/" + objHeader.hasTag());

        switch (objHeader.getType())
        {
            case 242: // Empty environment; not sure how to handle:
            case 253: // GLOBALENV_SXP; not sure how to handle:
            case RUtility.NIL: // Nil
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
                return RUtility.string(atom, false);
            }
            case RUtility.SYMBOL: // Symbol
            {
                RValue symbol = readItem(d, atoms);
                symbol.visit(new SpecificRVisitor<@Nullable @Value String>()
                {
                    @Override
                    public @Nullable @Value String visitString(@Nullable @Value String s, boolean isSymbol) throws InternalException, UserException
                    {
                        if (s != null)
                            addAtom(atoms, s);
                        return s;
                    }
                });
                return RUtility.string(RUtility.getString(symbol), true);
            }
            case 6:
            case RUtility.PAIR_LIST: // Pair list
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
            case RUtility.STRING_SINGLE: // String
            {
                @Nullable String s = readLenString(d);
                return RUtility.string(s, false);
            }
            case RUtility.INT_VECTOR: // Integer vector
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
                            public Boolean visitString(@Nullable String s, boolean isSymbol) throws InternalException, UserException
                            {
                                return "levels".equals(s);
                            }
                        }))
                            return items.get(0).item.visit(new SpecificRVisitor<ImmutableList<String>>()
                            {
                                @SuppressWarnings("optional")
                                @Override
                                public ImmutableList<String> visitStringList(ImmutableList<Optional<@Value String>> values, @Nullable RValue attributes) throws InternalException, UserException
                                {
                                    return Utility.<Optional<@Value String>, String>mapListExI(values, v -> v.orElseThrow(() -> new UserException("Unexpected NA in factors")));
                                }

                                @Override
                                public ImmutableList<String> visitGenericList(ImmutableList<RValue> values, @Nullable RValue attributes, boolean isObject) throws InternalException, UserException
                                {
                                    return Utility.mapListExI(values, RUtility::getStringNN);
                                }
                            });
                        return null;
                    }
                });
                
                if (factorNames != null)
                {
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
                    return RUtility.intVector(values, attr);
            }
            case RUtility.LOGICAL_VECTOR:
            {
                int vecLen = d.readInt();
                boolean[] values = new boolean[vecLen];
                boolean @MonotonicNonNull [] isNA = null;
                for (int i = 0; i < vecLen; i++)
                {
                    int n = d.readInt();
                    values[i] = n != 0;
                    if (n == RUtility.NA_AS_INTEGER)
                    {
                        if (isNA == null)
                            isNA = new boolean[vecLen];
                        isNA[i] = true;
                    }
                }
                final @Nullable RValue attr = objHeader.readAttributes(d, atoms);
                return RUtility.logicalVector(values, isNA, attr);
            }
            case RUtility.DOUBLE_VECTOR: // Floating point vector
            {
                int vecLen = d.readInt();
                double[] values = new double[vecLen];
                for (int i = 0; i < vecLen; i++)
                {
                    values[i] = d.readDouble();
                }
                final @Nullable RValue attr = objHeader.readAttributes(d, atoms);
                ImmutableMap<String, RValue> attrMap = RUtility.pairListToMap(attr);
                if (RUtility.isClass(attrMap, "Date"))
                    return RUtility.dateVector(values, attr);
                else if (RUtility.isClass(attrMap, "POSIXct"))
                    return RUtility.dateTimeZonedVector(values, attr);
                else 
                    return RUtility.doubleVector(values, attr);
            }
            case RUtility.STRING_VECTOR: // Character vector
            {
                int vecLen = d.readInt();
                ImmutableList.Builder<Optional<@Value String>> valueBuilder = ImmutableList.builderWithExpectedSize(vecLen);
                for (int i = 0; i < vecLen; i++)
                {
                    valueBuilder.add(Optional.ofNullable(RUtility.getString(readItem(d, atoms))));
                }
                ImmutableList<Optional<@Value String>> values = valueBuilder.build();
                final @Nullable RValue attr = objHeader.readAttributes(d, atoms);
                return RUtility.stringVector(values, attr);
            }
            case RUtility.GENERIC_VECTOR: // Generic vector
            {
                int vecLen = d.readInt();
                ImmutableList.Builder<RValue> valueBuilder = ImmutableList.builderWithExpectedSize(vecLen);
                for (int i = 0; i < vecLen; i++)
                {
                    valueBuilder.add(readItem(d, atoms));
                }
                ImmutableList<RValue> values = valueBuilder.build();
                final @Nullable RValue attr = objHeader.readAttributes(d, atoms);
                return RUtility.genericVector(values, attr, objHeader.isObject());
            }
            case 238: // ALTREP_SXP
            {
                RValue info = readItem(d, atoms);
                RValue state = readItem(d, atoms);
                RValue attr = readItem(d, atoms);
                return lookupClass(RUtility.getStringNN(RUtility.getListItem(info, 0)), RUtility.getStringNN(RUtility.getListItem(info, 1))).load(state, attr);
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

    private static RClassLoad lookupClass(String c, String p) throws InternalException, UserException
    {
        switch (p + "#" + c)
        {
            case "base#wrap_integer":
                return (state, attr) -> {
                    return state;
                };
            case "base#compact_intseq":
                return (state, attr) -> {
                    // It's a triple: number of steps, the start value, and the step increment
                    return state.visit(new SpecificRVisitor<RValue>()
                    {
                        @Override
                        public RValue visitDoubleList(double[] values, @Nullable RValue attributes) throws InternalException, UserException
                        {
                            double[] expanded = new double[(int)values[0]];
                            double x = values[1];
                            for (int i = 0; i < expanded.length; i++)
                            {
                                expanded[i] = x;
                                x += values[2];
                            }
                            return RUtility.doubleVector(expanded, null);
                        }
                    });
                };
            case "base#deferred_string":
                return (state, attr) -> {
                    return RUtility.string("Unsupported base#deferred_string", false);
                };
            case "base#wrap_real":
                return (state, attr) -> RUtility.getListItem(state, 0);
            
        }
        throw new UserException("Unsupported R class: " + p + " " + c);
    }

    private static @Nullable String readLenString(DataInputStream d) throws IOException
    {
        int len = d.readInt();
        if (len < 0)
            return null;
        byte chars[] = new byte[len];
        d.readFully(chars);
        return new String(chars, StandardCharsets.UTF_8);
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

    private static interface RClassLoad
    {
        public RValue load(RValue state, RValue attr) throws IOException, InternalException, UserException;
    }
}
