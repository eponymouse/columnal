package records.rinterop;

import annotation.identifier.qual.ExpressionIdentifier;
import annotation.qual.ImmediateValue;
import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Streams;
import com.google.common.primitives.Booleans;
import com.google.common.primitives.Ints;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.*;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DataTypeVisitorEx;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataType.DateTimeInfo.DateTimeType;
import records.data.datatype.DataType.FlatDataTypeVisitor;
import records.data.datatype.DataType.SpecificDataTypeVisitor;
import records.data.datatype.DataType.TagType;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.NumberInfo;
import records.data.datatype.TaggedTypeDefinition;
import records.data.datatype.TaggedTypeDefinition.TaggedInstantiationException;
import records.data.datatype.TaggedTypeDefinition.TypeVariableKind;
import records.data.datatype.TypeId;
import records.data.datatype.TypeManager;
import records.data.unit.Unit;
import records.error.InternalException;
import records.error.UserException;
import records.jellytype.JellyType;
import records.jellytype.JellyType.UnknownTypeException;
import utility.Either;
import utility.IdentifierUtility;
import utility.Pair;
import utility.SimulationFunction;
import utility.TaggedValue;
import utility.Utility;
import utility.Utility.ListEx;
import utility.Utility.ListExList;

import java.math.BigDecimal;
import java.time.temporal.TemporalAccessor;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;

public class ConvertFromR
{

    private static ColumnId getColumnName(@Nullable RValue listColumnNames, int index) throws UserException, InternalException
    {
        @SuppressWarnings("identifier")
        @ExpressionIdentifier String def = "Column " + index;
        if (listColumnNames != null)
        {
            @Value String s = RUtility.getString(RUtility.getListItem(listColumnNames, index));
            return new ColumnId(IdentifierUtility.fixExpressionIdentifier(s == null ? "" : s, def));
        }
        return new ColumnId(def);
    }

    /**
     * The returned type is the type of each list element (i.e. is not [necessarily] an array type).
     */
    public static Pair<DataType, ImmutableList<@Value Object>> convertRToTypedValueList(TypeManager typeManager, RValue rValue) throws InternalException, UserException
    {
        return rValue.visit(new RVisitor<Pair<DataType, ImmutableList<@Value Object>>>()
        {
            @Override
            public Pair<DataType, ImmutableList<@Value Object>> visitNil() throws InternalException, UserException
            {
                throw new UserException("Cannot turn nil into value");
            }

            @Override
            public Pair<DataType, ImmutableList<@Value Object>> visitString(@Nullable @Value String s, boolean isSymbol) throws InternalException, UserException
            {
                return visitStringList(ImmutableList.<Optional<@Value String>>of(Optional.<@Value String>ofNullable(s)), null);
            }

            @Override
            public Pair<DataType, ImmutableList<@Value Object>> visitLogicalList(boolean[] values, boolean @Nullable [] isNA, @Nullable RValue attributes) throws InternalException, UserException
            {
                if (isNA == null)
                    return new Pair<>(DataType.BOOLEAN, Utility.<@ImmediateValue Boolean, @Value Object>mapListI(Booleans.asList(values), b -> DataTypeUtility.value(b)));

                ImmutableList.Builder<@Value Object> maybeValues = ImmutableList.builderWithExpectedSize(values.length);
                for (int i = 0; i < values.length; i++)
                {
                    if (isNA[i])
                        maybeValues.add(typeManager.maybeMissing());
                    else
                        maybeValues.add(typeManager.maybePresent(DataTypeUtility.value(values[i])));
                }
                return new Pair<>(typeManager.makeMaybeType(DataType.BOOLEAN), maybeValues.build());
            }

            @Override
            public Pair<DataType,ImmutableList<@Value Object>> visitIntList(int[] values, @Nullable RValue attributes) throws InternalException, UserException
            {
                // int[] doesn't use NA; always doubles if has NA
                return new Pair<>(DataType.NUMBER, Utility.<@ImmediateValue Integer, @Value Object>mapListI(Ints.asList(values), i -> DataTypeUtility.value(i)));
            }

            @Override
            public Pair<DataType, ImmutableList<@Value Object>> visitDoubleList(double[] values, @Nullable RValue attributes) throws InternalException, UserException
            {
                boolean hasNaNs = false;
                for (int i = 0; i < values.length; i++)
                {
                    if (Double.isNaN(values[i]))
                    {
                        hasNaNs = true;
                        break;
                    }
                }
                if (hasNaNs)
                {
                    ImmutableList.Builder<@Value Object> maybeValues = ImmutableList.builderWithExpectedSize(values.length);
                    for (int i = 0; i < values.length; i++)
                    {
                        if (Double.isNaN(values[i]))
                            maybeValues.add(typeManager.maybeMissing());
                        else
                            maybeValues.add(typeManager.maybePresent(doubleToValue(values[i])));
                    }

                    return new Pair<>(typeManager.makeMaybeType(DataType.NUMBER), maybeValues.build());
                }
                else
                {
                    return new Pair<>(DataType.NUMBER, DoubleStream.of(values).<@ImmediateValue Object>mapToObj(d -> doubleToValue(d)).collect(ImmutableList.<@Value @NonNull Object>toImmutableList()));
                }
            }

            @Override
            @SuppressWarnings("optional")
            public Pair<DataType, ImmutableList<@Value Object>> visitStringList(ImmutableList<Optional<@Value String>> values, @Nullable RValue attributes) throws InternalException, UserException
            {
                if (values.stream().allMatch(v -> v.isPresent()))
                {
                    return new Pair<>(DataType.TEXT, Utility.<Optional<@Value String>, @Value Object>mapListI(values, v -> v.get()));
                }
                else 
                {
                    return new Pair<>(typeManager.makeMaybeType(DataType.TEXT), Utility.<Optional<@Value String>, @Value Object>mapListI(values, v -> v.<@Value Object>map(s -> typeManager.maybePresent(s)).orElseGet(typeManager::maybeMissing)));
                }
            }

            @SuppressWarnings("optional")
            @Override
            public Pair<DataType, ImmutableList<@Value Object>> visitTemporalList(DateTimeType dateTimeType, ImmutableList<Optional<@Value TemporalAccessor>> values, @Nullable RValue attributes) throws InternalException, UserException
            {
                DateTimeInfo t = new DateTimeInfo(dateTimeType);
                if (values.stream().allMatch(v -> v.isPresent()))
                {
                    return new Pair<>(DataType.date(t), Utility.<Optional<@Value TemporalAccessor>, @Value Object>mapListI(values, v -> v.get()));
                }
                else
                {
                    return new Pair<>(typeManager.makeMaybeType(DataType.date(t)), Utility.<Optional<@Value TemporalAccessor>, @Value Object>mapListI(values, v -> v.map(typeManager::maybePresent).orElseGet(typeManager::maybeMissing)));
                }
            }

            @Override
            public Pair<DataType, ImmutableList<@Value Object>> visitGenericList(ImmutableList<RValue> values, @Nullable RValue attributes, boolean isObject) throws InternalException, UserException
            {
                return rListToValueList(typeManager, values);
            }

            @Override
            public Pair<DataType, ImmutableList<@Value Object>> visitPairList(ImmutableList<PairListEntry> items) throws InternalException, UserException
            {
                // For some reason, factor columns appear as pair list of two with an int[] as second item:
                if (items.size() == 2)
                {
                    RVisitor<Pair<DataType, ImmutableList<@Value Object>>> outer = this;
                    @Nullable Pair<DataType, ImmutableList<@Value Object>> asFactors = items.get(0).item.visit(new DefaultRVisitor<@Nullable Pair<DataType, ImmutableList<@Value Object>>>(null)
                    {
                        @Override
                        public @Nullable Pair<DataType, ImmutableList<@Value Object>> visitFactorList(int[] values, ImmutableList<String> levelNames) throws InternalException, UserException
                        {
                            return outer.visitFactorList(values, levelNames);
                        }
                    });
                    if (asFactors != null)
                        return asFactors;
                }

                throw new UserException("Pair list found when column expected: " + RPrettyPrint.prettyPrint(rValue));
            }

            @Override
            public Pair<DataType, ImmutableList<@Value Object>> visitFactorList(int[] values, ImmutableList<String> levelNames) throws InternalException, UserException
            {
                boolean hasNAs = false;
                for (int value : values)
                {
                    if (value == RUtility.NA_AS_INTEGER)
                    {
                        hasNAs = true;
                        break;
                    }
                }

                TaggedTypeDefinition taggedTypeDefinition = getTaggedTypeForFactors(levelNames, typeManager);
                ImmutableList.Builder<@Value Object> factorValueBuilder = ImmutableList.builderWithExpectedSize(values.length);
                for (int n : values)
                {
                    if (hasNAs && n == RUtility.NA_AS_INTEGER)
                    {
                        factorValueBuilder.add(typeManager.maybeMissing());
                    }
                    else
                    {
                        @Value TaggedValue taggedValue = new TaggedValue(lookupTag(n, levelNames, taggedTypeDefinition), null, taggedTypeDefinition);
                        factorValueBuilder.add(hasNAs ? typeManager.maybePresent(taggedValue) : taggedValue);
                    }
                }
                ImmutableList<@Value Object> factorValues = factorValueBuilder.build();

                DataType taggedDataType = taggedTypeDefinition.instantiate(ImmutableList.of(), typeManager);
                return new Pair<>(hasNAs ? typeManager.makeMaybeType(taggedDataType) : taggedDataType, factorValues);
            }

            private int lookupTag(int tagIndex, ImmutableList<String> levelNames, TaggedTypeDefinition taggedTypeDefinition) throws UserException
            {
                if (tagIndex > levelNames.size())
                    throw new UserException("Factor index does not have name");
                // Map one-based back to zero-based:
                @SuppressWarnings("identifier")
                String name = IdentifierUtility.fixExpressionIdentifier(levelNames.get(tagIndex - 1), levelNames.get(tagIndex - 1));
                return Utility.findFirstIndex(taggedTypeDefinition.getTags(), tt -> tt.getName().equals(name)).orElseThrow(() -> new UserException("Could not find tag named " + name + " in definition"));
            }
        });
    }

    private static TaggedTypeDefinition getTaggedTypeForFactors(ImmutableList<String> levelNames, TypeManager typeManager) throws InternalException, UserException
    {
        ImmutableList<@ExpressionIdentifier String> processedNames = Streams.<String, @ExpressionIdentifier String>mapWithIndex(levelNames.stream(), (s, i) -> IdentifierUtility.fixExpressionIdentifier(s, IdentifierUtility.identNum("Factor", (int) i))).collect(ImmutableList.<@ExpressionIdentifier String>toImmutableList());

        ImmutableSet<@ExpressionIdentifier String> namesAsSet = ImmutableSet.copyOf(processedNames);
        TaggedTypeDefinition existing = typeManager.getKnownTaggedTypes().values().stream().filter(ttd ->
                ttd.getTags().stream().<@ExpressionIdentifier String>map(tt -> tt.getName()).collect(ImmutableSet.<@ExpressionIdentifier String>toImmutableSet()).equals(namesAsSet)
        ).findFirst().orElse(null);
        if (existing != null)
            return existing;
        
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
        Pair<DataType, ImmutableList<@Value Object>> converted = convertRToTypedValueList(typeManager, rValue);
        return new Pair<>(converted.getFirst().makeImmediateColumn(columnName, Utility.<@Value Object, Either<String, @Value Object>>mapListI(converted.getSecond(), x -> Either.<String, @Value Object>right(x)), DataTypeUtility.makeDefaultValue(converted.getFirst())), converted.getSecond().size());
    }          

    private static @ImmediateValue BigDecimal doubleToValue(double value)
    {
        // Go through Double.toString which zeroes out the boring end part:
        return DataTypeUtility.value(new BigDecimal(Double.toString(value)));
    }

    private static SimulationFunction<@Value Object, @Value Object> getOrInternal(ImmutableMap<DataType, SimulationFunction<@Value Object, @Value Object>> map, DataType key) throws InternalException
    {
        SimulationFunction<@Value Object, @Value Object> f = map.get(key);
        if (f == null)
            throw new InternalException("No conversion found for type " + key);
        return f;
    }
    
    // The DataType is the type of the list *elements*, not the list as a whole.
    // e.g. the return may be (Number, [1,2,3])
    private static Pair<DataType, ImmutableList<@Value Object>> rListToValueList(TypeManager typeManager, List<RValue> values) throws UserException, InternalException
    {
        // The type here will be the type of the object on the right
        ImmutableList<Pair<DataType, @Value Object>> typedPairs = Utility.<RValue, Pair<DataType, @Value Object>>mapListExI(values, v -> {
            Pair<DataType, ImmutableList<@Value Object>> r = convertRToTypedValueList(typeManager, v);
            if (r.getSecond().size() == 1)
                return r.replaceSecond(r.getSecond().get(0));
            else
                return new Pair<>(DataType.array(r.getFirst()), DataTypeUtility.value(r.getSecond()));
        });
        Pair<DataType, ImmutableMap<DataType, SimulationFunction<@Value Object, @Value Object>>> m = generaliseType(typeManager, Utility.mapListExI(typedPairs, p -> p.getFirst()));
        ImmutableList<@Value Object> loaded = Utility.<Pair<DataType, @Value Object>, @Value Object>mapListExI(typedPairs, p -> getOrInternal(m.getSecond(), p.getFirst()).apply(p.getSecond()));
        return new Pair<DataType, ImmutableList<@Value Object>>(m.getFirst(), loaded);
    }
    
    // If smaller can be generalised into larger, returns the conversion function from small to large.  If not (including case where larger can generalise to smaller), return null
    static @Nullable SimulationFunction<@Value Object, @Value Object> generaliseType(TypeManager typeManager, DataType smaller, DataType larger) throws InternalException, TaggedInstantiationException, UnknownTypeException
    {
        if (smaller.equals(larger))
            return x -> x;
        if (larger.equals(typeManager.makeMaybeType(smaller)))
            return x -> typeManager.maybePresent(x);
        DataType largeInner = getArrayInner(larger);
        if (largeInner != null)
        {
            if (largeInner.equals(smaller))
                return x -> DataTypeUtility.value(ImmutableList.of(x));
            else if (largeInner.equals(typeManager.makeMaybeType(smaller)))
                return x -> DataTypeUtility.value(ImmutableList.of(typeManager.maybePresent(x)));

            DataType smallInner = getArrayInner(smaller);
            @Nullable SimulationFunction<@Value Object, @Value Object> genInners = smallInner == null ? null : generaliseType(typeManager, smallInner, largeInner);
            if (genInners != null)
            {
                return x -> {
                    @Value ListEx list = Utility.cast(x, ListEx.class);
                    int length = list.size();
                    ImmutableList.Builder<@Value Object> processed = ImmutableList.builderWithExpectedSize(length);
                    for (int i = 0; i < length; i++)
                    {
                        processed.add(genInners.apply(list.get(i)));
                    }
                    return DataTypeUtility.value(processed.build());
                };
            }
        }
        return null;
    }

    private static @Nullable DataType getArrayInner(DataType dataType) throws InternalException
    {
        return dataType.apply(new FlatDataTypeVisitor<@Nullable DataType>(null) {
            @Override
            public DataType array(DataType inner) throws InternalException, InternalException
            {
                return inner;
            }
        });
    }

    /**
     * The generalisations available here are x -> Optional(x), and x -> List(x), as well as List(x) -> List(Optional(x))
     * @param typeManager
     * @param types
     * @return
     * @throws UserException
     * @throws InternalException
     */
    
    private static Pair<DataType, ImmutableMap<DataType, SimulationFunction<@Value Object, @Value Object>>> generaliseType(TypeManager typeManager, ImmutableList<DataType> types) throws UserException, InternalException
    {
        if (types.isEmpty())
            return new Pair<>(DataType.TEXT, ImmutableMap.<DataType, SimulationFunction<@Value Object, @Value Object>>of());
        DataType curType = types.get(0);
        HashMap<DataType, SimulationFunction<@Value Object, @Value Object>> conversions = new HashMap<>();
        conversions.put(curType, x -> x);
        for (DataType nextType : types.subList(1, types.size()))
        {
            if (conversions.containsKey(nextType))
            {
                // Fine, already dealt with
                continue;
            }
            @Nullable SimulationFunction<@Value Object, @Value Object> curToNext = generaliseType(typeManager, curType, nextType);
            if (curToNext != null)
            {
                conversions.put(curType, curToNext);
                curType = nextType;
                conversions.put(curType, x -> x);
                continue;
            }
            @Nullable SimulationFunction<@Value Object, @Value Object> nextToCur = generaliseType(typeManager, nextType, curType);
            if (nextToCur != null)
            {
                conversions.put(nextType, nextToCur);
                continue;
            }
            throw new UserException("Cannot generalise " + curType + " and " + nextType + " into a single type");
        }
        // Have to redo in case we generalised twice:
        for (Entry<DataType, SimulationFunction<@Value Object, @Value Object>> entry : conversions.entrySet())
        {
            SimulationFunction<@Value Object, @Value Object> f = generaliseType(typeManager, entry.getKey(), curType);
            if (f == null)
                throw new InternalException("No generalisation from " + entry.getKey() + " to " + curType);
            entry.setValue(f);
        }
        
        return new Pair<>(curType, ImmutableMap.copyOf(conversions));
    }
    
    public static ImmutableList<Pair<String, EditableRecordSet>> convertRToTable(TypeManager typeManager, RValue rValue) throws UserException, InternalException
    {
        // R tables are usually a list of columns, which suits us:
        return rValue.visit(new RVisitor<ImmutableList<Pair<String, EditableRecordSet>>>()
        {
            private ImmutableList<Pair<String, EditableRecordSet>> singleColumn() throws UserException, InternalException
            {
                Pair<SimulationFunction<RecordSet, EditableColumn>, Integer> p = convertRToColumn(typeManager, rValue, new ColumnId("Result"));
                return ImmutableList.<Pair<String, EditableRecordSet>>of(new Pair<>("Value", new <EditableColumn>EditableRecordSet(ImmutableList.<SimulationFunction<RecordSet, EditableColumn>>of(p.getFirst()), () -> p.getSecond())));
            }

            @Override
            public ImmutableList<Pair<String, EditableRecordSet>> visitNil() throws InternalException, UserException
            {
                return ImmutableList.of();
            }

            @Override
            public ImmutableList<Pair<String, EditableRecordSet>> visitString(@Nullable @Value String s, boolean isSymbol) throws InternalException, UserException
            {
                return singleColumn();
            }

            @Override
            public ImmutableList<Pair<String, EditableRecordSet>> visitStringList(ImmutableList<Optional<@Value String>> values, @Nullable RValue attributes) throws InternalException, UserException
            {
                return singleColumn();
            }

            @Override
            public ImmutableList<Pair<String, EditableRecordSet>> visitLogicalList(boolean[] values, boolean @Nullable [] isNA, @Nullable RValue attributes) throws InternalException, UserException
            {
                return singleColumn();
            }

            @Override
            public ImmutableList<Pair<String, EditableRecordSet>> visitIntList(int[] values, @Nullable RValue attributes) throws InternalException, UserException
            {
                return singleColumn();
            }

            @Override
            public ImmutableList<Pair<String, EditableRecordSet>> visitDoubleList(double[] values, @Nullable RValue attributes) throws InternalException, UserException
            {
                return singleColumn();
            }

            @Override
            public ImmutableList<Pair<String, EditableRecordSet>> visitTemporalList(DateTimeType dateTimeType, ImmutableList<Optional<@Value TemporalAccessor>> values, @Nullable RValue attributes) throws InternalException, UserException
            {
                return singleColumn();
            }

            @Override
            public ImmutableList<Pair<String, EditableRecordSet>> visitFactorList(int[] values, ImmutableList<String> levelNames) throws InternalException, UserException
            {
                return singleColumn();
            }

            @Override
            public ImmutableList<Pair<String, EditableRecordSet>> visitPairList(ImmutableList<PairListEntry> items) throws InternalException, UserException
            {
                ImmutableList.Builder<Pair<String, EditableRecordSet>> r = ImmutableList.builder();
                for (PairListEntry item : items)
                {
                    ImmutableList<Pair<String, EditableRecordSet>> found = convertRToTable(typeManager, item.item);
                    if (item.tag != null && found.size() == 1)
                    {
                        String name = RUtility.getString(item.tag);
                        if (name != null)
                        {
                            // Skip the random seed; very unlikely they want that as a table:
                            if (name.equals(".Random.seed"))
                                continue;
                            
                            found = ImmutableList.<Pair<String, EditableRecordSet>>of(found.get(0).<String>replaceFirst(name));
                        }
                    }
                    r.addAll(found);
                }
                return r.build();
            }

            @Override
            public ImmutableList<Pair<String, EditableRecordSet>> visitGenericList(ImmutableList<RValue> values, @Nullable RValue attributes, boolean isObject) throws InternalException, UserException
            {
                // Tricky; could be a list of tables, a list of columns or a list of values!
                // First try as table (list of columns):
                final ImmutableMap<String, RValue> attrMap = RUtility.pairListToMap(attributes);

                boolean isDataFrame = isObject && isDataFrameOrTibble(attrMap);
                if (isDataFrame)
                {
                    ImmutableList<Pair<SimulationFunction<RecordSet, EditableColumn>, Integer>> columns = Utility.mapListExI_Index(values, (i, v) -> convertRToColumn(typeManager, v, getColumnName(attrMap.get("names"), i)));
                    // Check columns are all the same length:
                    if (!columns.isEmpty() && columns.stream().mapToInt(p -> p.getSecond()).distinct().count() == 1)
                    {
                        return ImmutableList.of(new Pair<>("Table", new <EditableColumn>EditableRecordSet(Utility.<Pair<SimulationFunction<RecordSet, EditableColumn>, Integer>, SimulationFunction<RecordSet, EditableColumn>>mapList(columns, p -> p.getFirst()), () -> columns.get(0).getSecond())));
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
                            public Boolean visitGenericList(ImmutableList<RValue> values, @Nullable RValue attributes, boolean isObject) throws InternalException, UserException
                            {
                                final ImmutableMap<String, RValue> valueAttrMap = RUtility.pairListToMap(attributes);
                                return isObject && isDataFrameOrTibble(valueAttrMap);
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
                    
                    ImmutableList.Builder<Pair<String, EditableRecordSet>> r = ImmutableList.builder();
                    for (RValue value : values)
                    {
                        r.addAll(convertRToTable(typeManager, value));
                    }
                    return r.build();
                }
            }
        });
    }

    private static boolean isDataFrameOrTibble(ImmutableMap<String, RValue> attrMap) throws InternalException, UserException
    {
        return RUtility.isClass(attrMap, RUtility.CLASS_DATA_FRAME) || RUtility.isClass(attrMap, RUtility.CLASS_TIBBLE);
    }

    public static enum TableType { DATA_FRAME, TIBBLE }

    public static String usToRTable(TableId tableId)
    {
        return tableId.getRaw().replace(" ", ".");
    }

}
